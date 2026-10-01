using System.Globalization;
using System.Net.Http.Headers;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json;

namespace Evidrilo.Api.Billing;

public interface IRevenueCatSandboxReconciler
{
    Task RefreshAsync(Guid accountId, CancellationToken cancellationToken);
}

public sealed class DisabledRevenueCatSandboxReconciler : IRevenueCatSandboxReconciler
{
    public Task RefreshAsync(Guid accountId, CancellationToken cancellationToken) => Task.CompletedTask;
}

/// <summary>
/// Server-to-provider reconciliation for local Staging Test Store runs without
/// a public webhook destination. Never accepts client entitlement/credit flags.
/// Production continues to use authenticated webhooks.
/// </summary>
public sealed class RevenueCatSandboxReconciler(
    HttpClient client, string testStoreSdkKey, IBillingStore store,
    ILogger<RevenueCatSandboxReconciler> logger) : IRevenueCatSandboxReconciler
{
    public async Task RefreshAsync(Guid accountId, CancellationToken cancellationToken)
    {
        if (accountId == Guid.Empty || !testStoreSdkKey.StartsWith("test_", StringComparison.Ordinal)) return;
        using var request = new HttpRequestMessage(HttpMethod.Get,
            $"https://api.revenuecat.com/v1/subscribers/{accountId:D}");
        request.Headers.Authorization = new AuthenticationHeaderValue("Bearer", testStoreSdkKey);
        try
        {
            using var response = await client.SendAsync(request, HttpCompletionOption.ResponseHeadersRead, cancellationToken);
            if (!response.IsSuccessStatusCode) { logger.LogWarning("Sandbox billing lookup failed: HTTP {Status}", (int)response.StatusCode); return; }
            await using var input = await response.Content.ReadAsStreamAsync(cancellationToken);
            using var body = new MemoryStream();
            var buffer = new byte[8192];
            int read;
            while ((read = await input.ReadAsync(buffer, cancellationToken)) > 0)
            {
                if (body.Length + read > 262144) return;
                await body.WriteAsync(buffer.AsMemory(0, read), cancellationToken);
            }
            var envelope = ParseSnapshot(accountId, body.ToArray(), DateTimeOffset.UtcNow);
            if (envelope is not null)
            {
                // Recover the verified original purchase if the local API was
                // offline when checkout happened. Earned credits survive expiry.
                if (envelope.PeriodStartedAt is { } original && original < envelope.OccurredAt)
                    await store.RecordAsync(envelope with
                    {
                        EventId = envelope.EventId + "-earned", Status = "active", OccurredAt = original,
                    }, cancellationToken);
                await store.RecordAsync(envelope, cancellationToken);
            }
        }
        catch (HttpRequestException) { logger.LogWarning("Sandbox billing provider is unavailable."); }
        catch (TaskCanceledException) when (!cancellationToken.IsCancellationRequested) { logger.LogWarning("Sandbox billing lookup timed out."); }
        catch (JsonException) { logger.LogWarning("Sandbox billing response was malformed."); }
        catch (InvalidOperationException) { logger.LogWarning("Sandbox billing response was invalid."); }
    }

    public static BillingWebhookEnvelope? ParseSnapshot(Guid accountId, ReadOnlyMemory<byte> body, DateTimeOffset now)
    {
        if (accountId == Guid.Empty) return null;
        using var json = JsonDocument.Parse(body);
        if (!json.RootElement.TryGetProperty("subscriber", out var subscriber) ||
            !subscriber.TryGetProperty("entitlements", out var entitlements) ||
            !entitlements.TryGetProperty("evidrilo_pro", out var entitlement) ||
            !entitlement.TryGetProperty("product_identifier", out var productValue)) return null;
        var product = productValue.GetString();
        if (product is not ("monthly" or "yearly") ||
            !subscriber.TryGetProperty("subscriptions", out var subscriptions) ||
            !subscriptions.TryGetProperty(product, out var subscription) ||
            !subscription.TryGetProperty("is_sandbox", out var sandbox) || sandbox.ValueKind != JsonValueKind.True ||
            !subscription.TryGetProperty("store", out var storeValue) || storeValue.GetString() != "test_store" ||
            !Date(subscription, "purchase_date", out var purchased) ||
            !Date(subscription, "original_purchase_date", out var original) ||
            !Date(subscription, "expires_date", out var expires) ||
            !Date(entitlement, "expires_date", out var entitlementExpires) || entitlementExpires != expires ||
            original > purchased || purchased > now || expires <= purchased || expires - original > TimeSpan.FromDays(370)) return null;

        // Keep the original monthly anniversary. Accelerated Test Store renewals
        // extend coverage without minting 200 credits every five minutes.
        var status = expires > now ? "active" : "expired";
        var occurred = status == "active" ? purchased : expires;
        if (Date(subscription, "refunded_at", out var refunded))
        {
            if (refunded < original || refunded > now) return null;
            status = "revoked"; occurred = refunded;
        }
        var identity = $"{accountId:D}|{product}|{original:O}|{purchased:O}|{expires:O}|{status}";
        var eventId = "rc-test-lookup-" + Convert.ToHexString(SHA256.HashData(Encoding.UTF8.GetBytes(identity))).ToLowerInvariant();
        return new BillingWebhookEnvelope(eventId, accountId, "evidrilo_pro", status, occurred, original, expires);
    }

    private static bool Date(JsonElement value, string name, out DateTimeOffset date)
    {
        date = default;
        return value.TryGetProperty(name, out var field) && field.ValueKind == JsonValueKind.String &&
            DateTimeOffset.TryParse(field.GetString(), CultureInfo.InvariantCulture, DateTimeStyles.AssumeUniversal, out date);
    }
}
