using System.Security.Cryptography;
using System.Text;
using Evidrilo.Api.Billing;
using Evidrilo.Api.Common;
using Evidrilo.Api.Configuration;
using Microsoft.Extensions.Configuration;

namespace Evidrilo.Api.Tests;

public sealed class BillingTests
{
    [Fact]
    public void Billing_signature_uses_constant_time_verifiable_hmac_shape()
    {
        var body = Encoding.UTF8.GetBytes("synthetic billing event");
        var timestamp = DateTimeOffset.UtcNow.ToUnixTimeSeconds();
        var signature = BillingSignature.Create("synthetic-secret", timestamp, body);

        Assert.StartsWith("t=", signature, StringComparison.Ordinal);
        Assert.True(BillingSignature.IsValid("synthetic-secret", signature, body));
        Assert.False(BillingSignature.IsValid("wrong-secret", signature, body));
        Assert.False(BillingSignature.IsValid("synthetic-secret", signature + "x", body));
    }

    [Fact]
    public void Billing_signature_rejects_duplicate_signed_components()
    {
        var body = Encoding.UTF8.GetBytes("synthetic billing event");
        var timestamp = DateTimeOffset.UtcNow.ToUnixTimeSeconds();
        var signature = BillingSignature.Create("synthetic-secret", timestamp, body);

        Assert.False(BillingSignature.IsValid(
            "synthetic-secret",
            signature + $",t={timestamp}",
            body));
    }

    [Fact]
    public async Task Billing_service_rejects_invalid_signature_before_store()
    {
        var options = PlatformOptions.From(new ConfigurationBuilder()
            .AddInMemoryCollection(new Dictionary<string, string?>
            {
                ["Platform:Environment"] = "Testing",
                ["Platform:RevenueCatWebhookSecret"] = "synthetic-secret",
                ["Platform:RevenueCatEntitlementId"] = "evidrilo_pro",
            })
            .Build(), "Testing");
        var store = new RecordingBillingStore();
        var service = new BillingWebhookService(options, store);

        await Assert.ThrowsAsync<ApiException>(() => service.ProcessAsync(
            "sha256=invalid",
            Encoding.UTF8.GetBytes("{}"),
            CancellationToken.None));
        Assert.False(store.Called);
    }

    [Fact]
    public async Task Billing_service_rejects_events_without_an_occurrence_time()
    {
        var options = PlatformOptions.From(new ConfigurationBuilder()
            .AddInMemoryCollection(new Dictionary<string, string?>
            {
                ["Platform:Environment"] = "Testing",
                ["Platform:RevenueCatWebhookSecret"] = "synthetic-secret",
                ["Platform:RevenueCatEntitlementId"] = "evidrilo_pro",
            })
            .Build(), "Testing");
        var store = new RecordingBillingStore();
        var service = new BillingWebhookService(options, store);
        var body = Encoding.UTF8.GetBytes(
            "{\"eventId\":\"event-1\",\"accountId\":\"123e4567-e89b-42d3-a456-426614174000\",\"entitlement\":\"evidrilo_pro\",\"status\":\"active\"}");
        var timestamp = DateTimeOffset.UtcNow.ToUnixTimeSeconds();

        var exception = await Assert.ThrowsAsync<ApiException>(() => service.ProcessAsync(
            BillingSignature.Create("synthetic-secret", timestamp, body),
            body,
            CancellationToken.None));

        Assert.Equal(400, exception.StatusCode);
        Assert.Equal("INVALID_BILLING_EVENT", exception.Code);
        Assert.False(store.Called);
    }

    [Fact]
    public async Task Billing_service_accepts_a_revenuecat_signed_purchase_event()
    {
        var options = CreateOptions();
        var store = new RecordingBillingStore();
        var service = new BillingWebhookService(options, store);
        var timestamp = DateTimeOffset.UtcNow.ToUnixTimeSeconds();
        var body = Encoding.UTF8.GetBytes(
            "{\"api_version\":\"1.0\",\"event\":{\"id\":\"rc-event-1\",\"type\":\"INITIAL_PURCHASE\",\"product_id\":\"monthly\",\"app_user_id\":\"123e4567-e89b-42d3-a456-426614174000\",\"entitlement_ids\":[\"evidrilo_pro\"],\"event_timestamp_ms\":"
            + (timestamp * 1000)
            + "}}");

        var result = await service.ProcessAsync(
            CreateRevenueCatSignature("synthetic-secret", timestamp, body),
            body,
            CancellationToken.None);

        Assert.Equal("accepted", result);
        Assert.True(store.Called);
        Assert.Equal("rc-event-1", store.LastEnvelope?.EventId);
        Assert.Equal(Guid.Parse("123e4567-e89b-42d3-a456-426614174000"), store.LastEnvelope?.AccountId);
        Assert.Equal("evidrilo_pro", store.LastEnvelope?.Entitlement);
        Assert.Equal("active", store.LastEnvelope?.Status);
    }

    [Fact]
    public async Task Billing_service_ignores_an_unapproved_lifetime_product_even_with_the_canonical_entitlement()
    {
        var options = CreateOptions();
        var store = new RecordingBillingStore();
        var service = new BillingWebhookService(options, store);
        var timestamp = DateTimeOffset.UtcNow.ToUnixTimeSeconds();
        var body = Encoding.UTF8.GetBytes(
            "{\"api_version\":\"1.0\",\"event\":{\"id\":\"rc-lifetime-1\",\"type\":\"INITIAL_PURCHASE\",\"product_id\":\"lifetime\",\"app_user_id\":\"123e4567-e89b-42d3-a456-426614174000\",\"entitlement_ids\":[\"evidrilo_pro\"],\"event_timestamp_ms\":"
            + (timestamp * 1000)
            + "}}");

        var result = await service.ProcessAsync(
            CreateRevenueCatSignature("synthetic-secret", timestamp, body),
            body,
            CancellationToken.None);

        Assert.Equal("ignored", result);
        Assert.False(store.Called);
    }

    [Fact]
    public async Task Billing_service_ignores_a_whitespace_padded_product_id()
    {
        var options = CreateOptions();
        var store = new RecordingBillingStore();
        var service = new BillingWebhookService(options, store);
        var timestamp = DateTimeOffset.UtcNow.ToUnixTimeSeconds();
        var body = Encoding.UTF8.GetBytes(
            "{\"api_version\":\"1.0\",\"event\":{\"id\":\"rc-whitespace-product-1\",\"type\":\"INITIAL_PURCHASE\",\"product_id\":\" monthly \",\"app_user_id\":\"123e4567-e89b-42d3-a456-426614174000\",\"entitlement_ids\":[\"evidrilo_pro\"],\"event_timestamp_ms\":"
            + (timestamp * 1000)
            + "}}");

        var result = await service.ProcessAsync(
            CreateRevenueCatSignature("synthetic-secret", timestamp, body),
            body,
            CancellationToken.None);

        Assert.Equal("ignored", result);
        Assert.False(store.Called);
    }

    [Fact]
    public async Task Billing_service_ignores_a_whitespace_padded_entitlement_id()
    {
        var options = CreateOptions();
        var store = new RecordingBillingStore();
        var service = new BillingWebhookService(options, store);
        var timestamp = DateTimeOffset.UtcNow.ToUnixTimeSeconds();
        var body = Encoding.UTF8.GetBytes(
            "{\"api_version\":\"1.0\",\"event\":{\"id\":\"rc-whitespace-entitlement-1\",\"type\":\"INITIAL_PURCHASE\",\"product_id\":\"monthly\",\"app_user_id\":\"123e4567-e89b-42d3-a456-426614174000\",\"entitlement_ids\":[\" evidrilo_pro \"],\"event_timestamp_ms\":"
            + (timestamp * 1000)
            + "}}");

        var result = await service.ProcessAsync(
            CreateRevenueCatSignature("synthetic-secret", timestamp, body),
            body,
            CancellationToken.None);

        Assert.Equal("ignored", result);
        Assert.False(store.Called);
    }

    [Fact]
    public async Task Billing_service_revokes_access_for_a_customer_support_refund()
    {
        var options = CreateOptions();
        var store = new RecordingBillingStore();
        var service = new BillingWebhookService(options, store);
        var timestamp = DateTimeOffset.UtcNow.ToUnixTimeSeconds();
        var body = Encoding.UTF8.GetBytes(
            "{\"api_version\":\"1.0\",\"event\":{\"id\":\"rc-refund-1\",\"type\":\"CANCELLATION\",\"cancel_reason\":\"CUSTOMER_SUPPORT\",\"app_user_id\":\"123e4567-e89b-42d3-a456-426614174000\",\"entitlement_ids\":[\"evidrilo_pro\"],\"event_timestamp_ms\":"
            + (timestamp * 1000)
            + ",\"expiration_at_ms\":"
            + ((timestamp + 86_400) * 1000)
            + "}}");

        var result = await service.ProcessAsync(
            CreateRevenueCatSignature("synthetic-secret", timestamp, body),
            body,
            CancellationToken.None);

        Assert.Equal("accepted", result);
        Assert.Equal("revoked", store.LastEnvelope?.Status);
    }

    [Fact]
    public async Task Billing_service_acknowledges_anonymous_revenuecat_events_without_writing_an_account()
    {
        var options = CreateOptions();
        var store = new RecordingBillingStore();
        var service = new BillingWebhookService(options, store);
        var timestamp = DateTimeOffset.UtcNow.ToUnixTimeSeconds();
        var body = Encoding.UTF8.GetBytes(
            "{\"api_version\":\"1.0\",\"event\":{\"id\":\"rc-anonymous-1\",\"type\":\"INITIAL_PURCHASE\",\"app_user_id\":\"$RCAnonymousID:anonymous\",\"entitlement_ids\":[\"evidrilo_pro\"],\"event_timestamp_ms\":"
            + (timestamp * 1000)
            + "}}");

        var result = await service.ProcessAsync(
            CreateRevenueCatSignature("synthetic-secret", timestamp, body),
            body,
            CancellationToken.None);

        Assert.Equal("ignored", result);
        Assert.False(store.Called);
    }

    [Fact]
    public async Task Billing_service_ignores_an_empty_guid_account_id_without_writing_an_account()
    {
        var options = CreateOptions();
        var store = new RecordingBillingStore();
        var service = new BillingWebhookService(options, store);
        var timestamp = DateTimeOffset.UtcNow.ToUnixTimeSeconds();
        var body = Encoding.UTF8.GetBytes(
            "{\"api_version\":\"1.0\",\"event\":{\"id\":\"rc-empty-account-1\",\"type\":\"INITIAL_PURCHASE\",\"product_id\":\"monthly\",\"app_user_id\":\"00000000-0000-0000-0000-000000000000\",\"entitlement_ids\":[\"evidrilo_pro\"],\"event_timestamp_ms\":"
            + (timestamp * 1000)
            + "}}");

        var result = await service.ProcessAsync(
            CreateRevenueCatSignature("synthetic-secret", timestamp, body),
            body,
            CancellationToken.None);

        Assert.Equal("ignored", result);
        Assert.False(store.Called);
    }

    [Fact]
    public async Task Billing_service_acknowledges_unknown_revenuecat_event_types_for_forward_compatibility()
    {
        var options = CreateOptions();
        var store = new RecordingBillingStore();
        var service = new BillingWebhookService(options, store);
        var timestamp = DateTimeOffset.UtcNow.ToUnixTimeSeconds();
        var body = Encoding.UTF8.GetBytes(
            "{\"api_version\":\"1.0\",\"event\":{\"id\":\"rc-future-1\",\"type\":\"FUTURE_EVENT\",\"app_user_id\":\"123e4567-e89b-42d3-a456-426614174000\",\"entitlement_ids\":[\"evidrilo_pro\"],\"event_timestamp_ms\":"
            + (timestamp * 1000)
            + "}}");

        var result = await service.ProcessAsync(
            CreateRevenueCatSignature("synthetic-secret", timestamp, body),
            body,
            CancellationToken.None);

        Assert.Equal("ignored", result);
        Assert.False(store.Called);
    }

    [Fact]
    public async Task Billing_service_can_use_the_configured_authorization_header()
    {
        var options = PlatformOptions.From(new ConfigurationBuilder()
            .AddInMemoryCollection(new Dictionary<string, string?>
            {
                ["Platform:Environment"] = "Testing",
                ["Platform:RevenueCatWebhookAuthorization"] = "Bearer synthetic-authorization",
                ["Platform:RevenueCatEntitlementId"] = "evidrilo_pro",
            })
            .Build(), "Testing");
        var store = new RecordingBillingStore();
        var service = new BillingWebhookService(options, store);
        var timestamp = DateTimeOffset.UtcNow.ToUnixTimeSeconds();
        var body = Encoding.UTF8.GetBytes(
            "{\"api_version\":\"1.0\",\"event\":{\"id\":\"rc-auth-1\",\"type\":\"INITIAL_PURCHASE\",\"product_id\":\"monthly\",\"app_user_id\":\"123e4567-e89b-42d3-a456-426614174000\",\"entitlement_ids\":[\"evidrilo_pro\"],\"event_timestamp_ms\":"
            + (timestamp * 1000)
            + "}}");

        var result = await service.ProcessAsync(
            "Bearer synthetic-authorization",
            signature: null,
            body: body,
            cancellationToken: CancellationToken.None);

        Assert.Equal("accepted", result);
        Assert.True(store.Called);
    }

    private static PlatformOptions CreateOptions() => PlatformOptions.From(new ConfigurationBuilder()
        .AddInMemoryCollection(new Dictionary<string, string?>
        {
            ["Platform:Environment"] = "Testing",
            ["Platform:RevenueCatWebhookSecret"] = "synthetic-secret",
            ["Platform:RevenueCatEntitlementId"] = "evidrilo_pro",
        })
        .Build(), "Testing");

    private static string CreateRevenueCatSignature(string secret, long timestamp, byte[] body)
    {
        var signedPayload = Encoding.UTF8.GetBytes($"{timestamp}.");
        using var hmac = new HMACSHA256(Encoding.UTF8.GetBytes(secret));
        var input = new byte[signedPayload.Length + body.Length];
        signedPayload.CopyTo(input, 0);
        body.CopyTo(input, signedPayload.Length);
        return $"t={timestamp},v1={Convert.ToHexString(hmac.ComputeHash(input)).ToLowerInvariant()}";
    }

    private sealed class RecordingBillingStore : IBillingStore
    {
        public bool Called { get; private set; }

        public BillingWebhookEnvelope? LastEnvelope { get; private set; }

        public Task<string> RecordAsync(BillingWebhookEnvelope envelope, CancellationToken cancellationToken)
        {
            Called = true;
            LastEnvelope = envelope;
            return Task.FromResult("accepted");
        }
    }
}
