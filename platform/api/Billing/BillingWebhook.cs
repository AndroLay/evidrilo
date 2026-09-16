using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using System.Text.Json.Serialization;
using System.Globalization;
using Evidrilo.Api.Common;
using Evidrilo.Api.Configuration;
using Npgsql;
using NpgsqlTypes;

namespace Evidrilo.Api.Billing;

public sealed record BillingWebhookEnvelope(
    [property: JsonPropertyName("eventId")] string EventId,
    [property: JsonPropertyName("accountId")] Guid AccountId,
    [property: JsonPropertyName("entitlement")] string Entitlement,
    [property: JsonPropertyName("status")] string Status,
    [property: JsonPropertyName("occurredAt")] DateTimeOffset OccurredAt);

public sealed record RevenueCatWebhookEnvelope(
    [property: JsonPropertyName("api_version")] string? ApiVersion,
    [property: JsonPropertyName("event")] RevenueCatWebhookEvent? Event);

public sealed record RevenueCatWebhookEvent(
    [property: JsonPropertyName("id")] string? Id,
    [property: JsonPropertyName("type")] string? Type,
    [property: JsonPropertyName("product_id")] string? ProductId,
    [property: JsonPropertyName("app_user_id")] string? AppUserId,
    [property: JsonPropertyName("entitlement_ids")] IReadOnlyList<string>? EntitlementIds,
    [property: JsonPropertyName("entitlement_id")] string? EntitlementId,
    [property: JsonPropertyName("event_timestamp_ms")] long? EventTimestampMs,
    [property: JsonPropertyName("expiration_at_ms")] long? ExpirationAtMs,
    [property: JsonPropertyName("cancel_reason")] string? CancellationReason);

public sealed record BillingWebhookResult(
    [property: JsonPropertyName("schema")] string Schema,
    [property: JsonPropertyName("version")] string Version,
    [property: JsonPropertyName("outcome")] string Outcome,
    [property: JsonPropertyName("requestId")] string RequestId);

public interface IBillingStore
{
    Task<string> RecordAsync(BillingWebhookEnvelope envelope, CancellationToken cancellationToken);
}

public sealed class DatabaseUnavailableBillingStore : IBillingStore
{
    public Task<string> RecordAsync(BillingWebhookEnvelope envelope, CancellationToken cancellationToken) => throw new ApiException(
        StatusCodes.Status503ServiceUnavailable,
        "DATABASE_NOT_CONFIGURED",
        "Billing is not configured.");
}

public sealed class NpgsqlBillingStore : IBillingStore, IDisposable
{
    private readonly NpgsqlDataSource dataSource;

    public NpgsqlBillingStore(string connectionString)
    {
        dataSource = NpgsqlDataSource.Create(connectionString);
    }

    public async Task<string> RecordAsync(BillingWebhookEnvelope envelope, CancellationToken cancellationToken)
    {
        try
        {
            await using var connection = await dataSource.OpenConnectionAsync(cancellationToken);
            await using var transaction = await connection.BeginTransactionAsync(cancellationToken);
            await using var eventCommand = connection.CreateCommand();
            eventCommand.Transaction = transaction;
            eventCommand.CommandText = """
                insert into public.entitlement_events (
                    provider_event_id, account_id, entitlement, status, occurred_at
                ) values (@event_id, @account_id, @entitlement, @status, @occurred_at)
                on conflict (provider_event_id) do nothing
                returning provider_event_id;
                """;
            eventCommand.Parameters.AddWithValue("event_id", NpgsqlDbType.Text, envelope.EventId);
            eventCommand.Parameters.AddWithValue("account_id", NpgsqlDbType.Uuid, envelope.AccountId);
            eventCommand.Parameters.AddWithValue("entitlement", NpgsqlDbType.Text, envelope.Entitlement);
            eventCommand.Parameters.AddWithValue("status", NpgsqlDbType.Text, envelope.Status);
            eventCommand.Parameters.AddWithValue("occurred_at", NpgsqlDbType.TimestampTz, envelope.OccurredAt);
            var inserted = await eventCommand.ExecuteScalarAsync(cancellationToken);
            if (inserted is null or DBNull)
            {
                await using var existingEventCommand = connection.CreateCommand();
                existingEventCommand.Transaction = transaction;
                existingEventCommand.CommandText = """
                    select 1
                    from public.entitlement_events
                    where provider_event_id = @event_id
                      and account_id = @account_id
                      and entitlement = @entitlement
                      and status = @status
                      and occurred_at = @occurred_at;
                    """;
                existingEventCommand.Parameters.AddWithValue("event_id", NpgsqlDbType.Text, envelope.EventId);
                existingEventCommand.Parameters.AddWithValue("account_id", NpgsqlDbType.Uuid, envelope.AccountId);
                existingEventCommand.Parameters.AddWithValue("entitlement", NpgsqlDbType.Text, envelope.Entitlement);
                existingEventCommand.Parameters.AddWithValue("status", NpgsqlDbType.Text, envelope.Status);
                existingEventCommand.Parameters.AddWithValue("occurred_at", NpgsqlDbType.TimestampTz, envelope.OccurredAt);
                var matchesExistingEvent = await existingEventCommand.ExecuteScalarAsync(cancellationToken) is not null;
                if (!matchesExistingEvent)
                    throw new ApiException(
                        StatusCodes.Status409Conflict,
                        "BILLING_EVENT_ID_REUSE",
                        "The billing event identifier was reused with different data.");

                await transaction.CommitAsync(cancellationToken);
                return "duplicate";
            }

            await using var entitlementCommand = connection.CreateCommand();
            entitlementCommand.Transaction = transaction;
            entitlementCommand.CommandText = """
                insert into public.entitlements (
                    account_id, entitlement, status, updated_at, source_occurred_at
                ) values (@account_id, @entitlement, @status, now(), @occurred_at)
                on conflict (account_id, entitlement) do update
                set status = excluded.status,
                    updated_at = excluded.updated_at,
                    source_occurred_at = excluded.source_occurred_at
                where public.entitlements.source_occurred_at <= excluded.source_occurred_at;
                """;
            entitlementCommand.Parameters.AddWithValue("account_id", NpgsqlDbType.Uuid, envelope.AccountId);
            entitlementCommand.Parameters.AddWithValue("entitlement", NpgsqlDbType.Text, envelope.Entitlement);
            entitlementCommand.Parameters.AddWithValue("status", NpgsqlDbType.Text, envelope.Status);
            entitlementCommand.Parameters.AddWithValue("occurred_at", NpgsqlDbType.TimestampTz, envelope.OccurredAt);
            await entitlementCommand.ExecuteNonQueryAsync(cancellationToken);
            await transaction.CommitAsync(cancellationToken);
            return "accepted";
        }
        catch (OperationCanceledException)
        {
            throw;
        }
        catch (NpgsqlException exception)
        {
            throw new ApiException(
                StatusCodes.Status503ServiceUnavailable,
                "DATABASE_UNAVAILABLE",
                "Billing is temporarily unavailable.",
                exception);
        }
    }

    public void Dispose() => dataSource.Dispose();
}

public sealed class BillingWebhookService
{
    private const long SignatureTimestampToleranceSeconds = 5 * 60;
    private static readonly JsonSerializerOptions JsonOptions = new(JsonSerializerDefaults.Web);
    private readonly PlatformOptions options;
    private readonly IBillingStore store;

    public BillingWebhookService(PlatformOptions options, IBillingStore store)
    {
        this.options = options;
        this.store = store;
    }

    public Task<string> ProcessAsync(
        string? signature,
        ReadOnlyMemory<byte> body,
        CancellationToken cancellationToken) => ProcessAsync(
        authorization: null,
        signature,
        body,
        cancellationToken);

    public async Task<string> ProcessAsync(
        string? authorization,
        string? signature,
        ReadOnlyMemory<byte> body,
        CancellationToken cancellationToken)
    {
        if (!options.BillingConfigured || string.IsNullOrWhiteSpace(options.RevenueCatEntitlementId))
            throw new ApiException(StatusCodes.Status503ServiceUnavailable, "BILLING_NOT_CONFIGURED", "Billing is not configured.");
        if (!BillingSignature.IsValid(
                options.RevenueCatWebhookSecret,
                signature,
                body.Span)
            && !BillingSignature.IsAuthorizationValid(
                options.RevenueCatWebhookAuthorization,
                authorization))
            throw new ApiException(StatusCodes.Status401Unauthorized, "INVALID_BILLING_SIGNATURE", "The billing webhook is not authorized.");

        RevenueCatWebhookEnvelope? payload;
        try
        {
            payload = JsonSerializer.Deserialize<RevenueCatWebhookEnvelope>(body.Span, JsonOptions);
        }
        catch (JsonException exception)
        {
            throw new ApiException(StatusCodes.Status400BadRequest, "INVALID_BILLING_EVENT", "The billing event is invalid.", exception);
        }

        if (payload is null
            || string.IsNullOrWhiteSpace(payload.ApiVersion)
            || payload.ApiVersion.Length > 32
            || payload.Event is null)
            throw new ApiException(StatusCodes.Status400BadRequest, "INVALID_BILLING_EVENT", "The billing event is invalid.");

        var providerEvent = payload.Event;
        if (string.IsNullOrWhiteSpace(providerEvent.Id)
            || providerEvent.Id.Length > 128
            || string.IsNullOrWhiteSpace(providerEvent.Type)
            || providerEvent.Type.Length > 64
            || providerEvent.ProductId?.Length > 128
            || string.IsNullOrWhiteSpace(providerEvent.AppUserId)
            || providerEvent.AppUserId.Length > 256
            || providerEvent.EventTimestampMs is not > 0)
            throw new ApiException(StatusCodes.Status400BadRequest, "INVALID_BILLING_EVENT", "The billing event is invalid.");

        var entitlement = SelectEntitlement(providerEvent);
        var status = RevenueCatEventStatus.Map(
            providerEvent.Type,
            providerEvent.EventTimestampMs.Value,
            providerEvent.ExpirationAtMs,
            providerEvent.CancellationReason);
        if (entitlement is null || status is null)
            return "ignored";
        if (!Guid.TryParse(providerEvent.AppUserId, out var accountId) || accountId == Guid.Empty)
            return "ignored";
        if ((providerEvent.ProductId is not null && !IsApprovedProductId(providerEvent.ProductId))
            || (status == "active" && providerEvent.ProductId is null))
            return "ignored";

        DateTimeOffset occurredAt;
        try
        {
            occurredAt = DateTimeOffset.FromUnixTimeMilliseconds(providerEvent.EventTimestampMs.Value);
        }
        catch (ArgumentOutOfRangeException exception)
        {
            throw new ApiException(StatusCodes.Status400BadRequest, "INVALID_BILLING_EVENT", "The billing event is invalid.", exception);
        }

        return await store.RecordAsync(
            new BillingWebhookEnvelope(
                providerEvent.Id,
                accountId,
                entitlement,
                status,
                occurredAt),
            cancellationToken);
    }

    private string? SelectEntitlement(RevenueCatWebhookEvent providerEvent)
    {
        var configuredEntitlement = options.RevenueCatEntitlementId!;
        var candidates = (providerEvent.EntitlementIds ?? Array.Empty<string>())
            .Append(providerEvent.EntitlementId)
            .Where(candidate => !string.IsNullOrWhiteSpace(candidate))
            .Where(candidate => candidate!.Length <= 128)
            .Select(candidate => candidate!)
            .Distinct(StringComparer.Ordinal);

        return candidates.FirstOrDefault(candidate =>
            string.Equals(candidate, configuredEntitlement, StringComparison.Ordinal));
    }

    private static bool IsApprovedProductId(string? productId) =>
        productId is "monthly" or "yearly";
}

public static class BillingSignature
{
    public static string Create(string secret, long timestamp, ReadOnlySpan<byte> body)
    {
        var digest = Compute(secret, timestamp, body);
        return $"t={timestamp},v1={Convert.ToHexString(digest).ToLowerInvariant()}";
    }

    public static bool IsValid(
        string? secret,
        string? signature,
        ReadOnlySpan<byte> body,
        DateTimeOffset? now = null,
        long timestampToleranceSeconds = 5 * 60)
    {
        if (string.IsNullOrWhiteSpace(secret)
            || string.IsNullOrWhiteSpace(signature)
            || timestampToleranceSeconds < 0
            || !TryParse(signature, out var timestamp, out var providedSignature))
            return false;

        var currentTimestamp = (now ?? DateTimeOffset.UtcNow).ToUnixTimeSeconds();
        if (timestamp < currentTimestamp - timestampToleranceSeconds
            || timestamp > currentTimestamp + timestampToleranceSeconds)
            return false;

        var expectedSignature = Compute(secret, timestamp, body);
        return CryptographicOperations.FixedTimeEquals(expectedSignature, providedSignature);
    }

    public static bool IsAuthorizationValid(string? expected, string? actual)
    {
        if (string.IsNullOrWhiteSpace(expected) || string.IsNullOrWhiteSpace(actual))
            return false;

        return CryptographicOperations.FixedTimeEquals(
            Encoding.UTF8.GetBytes(expected),
            Encoding.UTF8.GetBytes(actual));
    }

    private static bool TryParse(
        string signature,
        out long timestamp,
        out byte[] providedSignature)
    {
        timestamp = default;
        providedSignature = Array.Empty<byte>();
        var timestampFound = false;
        var signatureFound = false;
        foreach (var part in signature.Split(',', StringSplitOptions.TrimEntries | StringSplitOptions.RemoveEmptyEntries))
        {
            var separator = part.IndexOf('=');
            if (separator <= 0 || separator == part.Length - 1)
                return false;

            var key = part[..separator].Trim();
            var value = part[(separator + 1)..].Trim();
            switch (key)
            {
                case "t":
                    if (timestampFound)
                        return false;
                    if (!long.TryParse(value, NumberStyles.None, CultureInfo.InvariantCulture, out timestamp))
                        return false;
                    timestampFound = true;
                    break;
                case "v1":
                    if (signatureFound)
                        return false;
                    try
                    {
                        providedSignature = Convert.FromHexString(value);
                    }
                    catch (FormatException)
                    {
                        return false;
                    }

                    if (providedSignature.Length != 32)
                        return false;
                    signatureFound = true;
                    break;
            }
        }

        return timestampFound && signatureFound;
    }

    private static byte[] Compute(string secret, long timestamp, ReadOnlySpan<byte> body)
    {
        var prefix = Encoding.UTF8.GetBytes(timestamp.ToString(CultureInfo.InvariantCulture) + ".");
        var signedPayload = new byte[prefix.Length + body.Length];
        prefix.CopyTo(signedPayload, 0);
        body.CopyTo(signedPayload.AsSpan(prefix.Length));
        return HMACSHA256.HashData(Encoding.UTF8.GetBytes(secret), signedPayload);
    }
}

internal static class RevenueCatEventStatus
{
    public static string? Map(
        string type,
        long eventTimestampMs,
        long? expirationAtMs,
        string? cancellationReason)
    {
        var normalizedType = type.Trim().ToUpperInvariant();
        var normalizedCancellationReason = cancellationReason?.Trim().ToUpperInvariant();
        return normalizedType switch
        {
            "INITIAL_PURCHASE"
                or "RENEWAL"
                or "UNCANCELLATION"
                or "NON_RENEWING_PURCHASE"
                or "SUBSCRIPTION_EXTENDED"
                or "REFUND_REVERSED"
                or "BILLING_ISSUE"
                or "PRODUCT_CHANGE"
                or "SUBSCRIPTION_PAUSED"
                or "TRANSFER"
                or "PURCHASE_REDEEMED"
                or "TEMPORARY_ENTITLEMENT_GRANT"
                or "TEST"
                => "active",
            "EXPIRATION" => "expired",
            "CANCELLATION" => normalizedCancellationReason == "CUSTOMER_SUPPORT"
                ? "revoked"
                : expirationAtMs.HasValue && expirationAtMs.Value > eventTimestampMs
                    ? "active"
                    : "revoked",
            _ => null,
        };
    }
}
