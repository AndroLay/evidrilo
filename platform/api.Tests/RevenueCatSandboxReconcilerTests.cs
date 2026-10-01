using System.Text;
using System.Text.Json.Nodes;
using Evidrilo.Api.Billing;

namespace Evidrilo.Api.Tests;

public sealed class RevenueCatSandboxReconcilerTests
{
    private static readonly Guid Account = Guid.Parse("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
    private static readonly DateTimeOffset Start = DateTimeOffset.Parse("2026-01-01T12:00:00Z");
    private static JsonObject Snapshot(string product = "monthly") => new()
    {
        ["subscriber"] = new JsonObject
        {
            ["entitlements"] = new JsonObject { ["evidrilo_pro"] = new JsonObject
                { ["product_identifier"] = product, ["expires_date"] = Start.AddMonths(product == "yearly" ? 12 : 1).ToString("O") } },
            ["subscriptions"] = new JsonObject { [product] = new JsonObject
                { ["is_sandbox"] = true, ["store"] = "test_store", ["original_purchase_date"] = Start.ToString("O"),
                  ["purchase_date"] = Start.ToString("O"), ["expires_date"] = Start.AddMonths(product == "yearly" ? 12 : 1).ToString("O") } },
        },
    };
    private static BillingWebhookEnvelope? Parse(JsonObject body, DateTimeOffset? now = null) =>
        RevenueCatSandboxReconciler.ParseSnapshot(Account, Encoding.UTF8.GetBytes(body.ToJsonString()), now ?? Start.AddDays(1));

    [Theory]
    [InlineData("monthly")]
    [InlineData("yearly")]
    public void Verified_sandbox_periods_project_active_entitlement(string product)
    {
        var value = Parse(Snapshot(product));
        Assert.NotNull(value); Assert.Equal("active", value.Status);
        Assert.Equal(Account, value.AccountId); Assert.Equal(Start, value.PeriodStartedAt);
    }

    [Fact]
    public void Restore_and_retry_use_the_same_provider_event_identity()
    {
        Assert.Equal(Parse(Snapshot()), Parse(Snapshot()));
    }

    [Fact]
    public void Accelerated_renewals_preserve_the_original_credit_anniversary()
    {
        var body = Snapshot(); body["subscriber"]!["subscriptions"]!["monthly"]!["purchase_date"] = Start.AddMinutes(5).ToString("O");
        var value = Parse(body);
        Assert.NotNull(value); Assert.Equal(Start, value.PeriodStartedAt);
        Assert.Equal(Start.AddMinutes(5), value.OccurredAt);
    }

    [Fact]
    public void Expiry_and_refund_remove_access_without_minting_active_events()
    {
        Assert.Equal("expired", Parse(Snapshot(), Start.AddMonths(2))!.Status);
        var body = Snapshot(); body["subscriber"]!["subscriptions"]!["monthly"]!["refunded_at"] = Start.AddHours(1).ToString("O");
        Assert.Equal("revoked", Parse(body)!.Status);
    }

    [Theory]
    [InlineData("is_sandbox", "false")]
    [InlineData("store", "\"play_store\"")]
    [InlineData("expires_date", "null")]
    [InlineData("original_purchase_date", "\"2030-01-01T00:00:00Z\"")]
    public void Wrong_environment_store_or_dates_cannot_grant(string field, string value)
    {
        var body = Snapshot(); body["subscriber"]!["subscriptions"]!["monthly"]![field] = JsonNode.Parse(value);
        Assert.Null(Parse(body));
    }

    [Fact]
    public void Unapproved_products_cannot_grant() => Assert.Null(Parse(Snapshot("lifetime")));
}
