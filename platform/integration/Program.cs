using System.Diagnostics;
using System.Net;
using System.Net.Http.Json;
using System.Reflection;
using System.Security.Claims;
using System.Text;
using System.Text.Json;
using Microsoft.AspNetCore.Authentication;
using Microsoft.AspNetCore.Hosting;
using Microsoft.AspNetCore.Mvc.Testing;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Logging;
using Microsoft.Extensions.Options;
using Microsoft.Extensions.DependencyInjection.Extensions;
using Evidrilo.Api.Ai;
using Evidrilo.Api.Billing;
using Evidrilo.Worker;
using Npgsql;

namespace Evidrilo.LocalE2e;

public static class EntryPoint
{
    private static readonly Guid AccountId =
        Guid.Parse("99999999-9999-9999-9999-999999999990");
    private static readonly Guid OtherAccountId =
        Guid.Parse("99999999-9999-9999-9999-999999999991");
    private static readonly Guid ReviewerAccountId =
        Guid.Parse("99999999-9999-9999-9999-999999999992");
    private static readonly Guid MaintainerAccountId =
        Guid.Parse("99999999-9999-9999-9999-999999999993");
    private static readonly Guid DeletionTestAccountId =
        Guid.Parse("99999999-9999-9999-9999-999999999994");
    private static readonly Guid OrganizationId =
        Guid.Parse("99999999-9999-4999-8999-999999999990");
    private static readonly Guid AttemptId =
        Guid.Parse("99999999-0000-0000-0000-000000000990");
    private static readonly Guid CommandId =
        Guid.Parse("99999999-0000-0000-0000-000000000991");
    private static readonly Guid ClientEventId =
        Guid.Parse("99999999-0000-0000-0000-000000000992");
    private static DateTimeOffset AiEntitlementPeriodStart;
    private static DateTimeOffset AiEntitlementExpiresAt;
    private const string CaseVersionId = "M0_T2:1";
    private const string DraftCaseVersionId = "M0_T2:draft";
    private const string CaseContentHash =
        "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    private const string SnapshotDigest =
        "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    internal const string BillingWebhookSecret = "synthetic-billing-secret";
    internal const string BillingEntitlement = "evidrilo_pro";

    public static async Task Main()
    {
        var databaseConnectionString = Environment.GetEnvironmentVariable("EVIDRILO_E2E_DATABASE_URL");
        if (string.IsNullOrWhiteSpace(databaseConnectionString))
        {
            throw new InvalidOperationException(
                "EVIDRILO_E2E_DATABASE_URL must be set by the local smoke runner.");
        }

        var repositoryRoot = Environment.GetEnvironmentVariable("EVIDRILO_REPO_ROOT");
        if (string.IsNullOrWhiteSpace(repositoryRoot)) repositoryRoot = Directory.GetCurrentDirectory();

        var dotnetRoot = Environment.GetEnvironmentVariable("EVIDRILO_DOTNET_ROOT");
        if (string.IsNullOrWhiteSpace(dotnetRoot))
        {
            throw new InvalidOperationException(
                "EVIDRILO_DOTNET_ROOT must point to the local .NET runtime.");
        }

        var configuredWorkerAssembly = Environment.GetEnvironmentVariable("EVIDRILO_WORKER_ASSEMBLY");
        var workerAssembly = string.IsNullOrWhiteSpace(configuredWorkerAssembly)
            ? Path.Combine(
                repositoryRoot,
                "platform",
                "worker",
                "bin",
                "Release",
                "net10.0",
                "Evidrilo.Worker.dll")
            : Path.GetFullPath(configuredWorkerAssembly);
        if (!File.Exists(workerAssembly))
        {
            throw new FileNotFoundException(
                "The Release worker artifact is required for the local E2E smoke.",
                workerAssembly);
        }

        await SeedAsync(databaseConnectionString);
        Process? worker = null;
        try
        {
            using (var apiFactory = new E2eApiFactory(databaseConnectionString, repositoryRoot))
            using (var client = apiFactory.CreateClient())
            {
                worker = StartWorker(dotnetRoot, workerAssembly,
                    Environment.GetEnvironmentVariable("EVIDRILO_E2E_WORKER_DATABASE_URL") ?? databaseConnectionString);
                await AssertReadyAsync(client);
                await AssertBillingLifecycleFlowAsync(client, databaseConnectionString);
                await AssertContentLifecycleFlowAsync(client, databaseConnectionString);
                await AssertProjectTemplateCatalogFlowAsync(databaseConnectionString, repositoryRoot);
                await AssertAiCreditFlowAsync(client, databaseConnectionString);
                await AssertPublishedCaseEvidenceFlowAsync(client);
                await AssertAiConversationFlowAsync(databaseConnectionString, repositoryRoot);
                await AssertAiProviderSpendBudgetAsync(databaseConnectionString);
                await AssertSyncAndProjectionFlowAsync(client);
                await AssertStudentProjectFlowAsync(client, databaseConnectionString);
            }

            // The suite exercises more routes than one account's production
            // minute quota; start a fresh host for the remaining account flows.
            using (var accountApiFactory = new E2eApiFactory(databaseConnectionString, repositoryRoot))
            using (var accountClient = accountApiFactory.CreateClient())
            {
                await AssertNotificationAndAccountExportFlowAsync(accountClient, databaseConnectionString, repositoryRoot);
                await AssertIsolationAsync(accountClient);
                await AssertAccountDeletionOutboxFlowAsync(accountClient, databaseConnectionString);
            }

            Console.WriteLine("EVIDRILO_API_DATABASE_WORKER_PUBLISHED_CASE_EVIDENCE_GRAPH_E2E_PASS");
        }
        finally
        {
            if (worker is not null) StopWorker(worker);
        }
    }

    private static async Task AssertBillingLifecycleFlowAsync(
        HttpClient client,
        string databaseConnectionString)
    {
        AiEntitlementPeriodStart = DateTimeOffset.FromUnixTimeMilliseconds(
            DateTimeOffset.UtcNow.AddMonths(-2).AddDays(-10).ToUnixTimeMilliseconds());
        AiEntitlementExpiresAt = AiEntitlementPeriodStart.AddYears(1);
        var baseEventTimestampMs = AiEntitlementPeriodStart.ToUnixTimeMilliseconds();
        var purchase = CreateRevenueCatEventBody(
            "rc-e2e-purchase",
            "INITIAL_PURCHASE",
            "monthly",
            AccountId,
            baseEventTimestampMs,
            periodStartedAtMs: baseEventTimestampMs,
            periodExpiresAtMs: AiEntitlementPeriodStart.AddMonths(1).ToUnixTimeMilliseconds());

        using (var invalidSignature = await SendBillingWebhookAsync(client, purchase, "t=0,v1=invalid"))
        {
            RequireStatus(invalidSignature, HttpStatusCode.Unauthorized, "invalid billing signature");
            var body = await ReadJsonAsync(invalidSignature);
            RequireString(body, "code", "INVALID_BILLING_SIGNATURE");
        }

        using (var accepted = await SendBillingWebhookAsync(client, purchase))
        {
            RequireStatus(accepted, HttpStatusCode.OK, "billing purchase");
            await RequireBillingOutcomeAsync(accepted, "accepted");
        }

        using (var duplicate = await SendBillingWebhookAsync(client, purchase))
        {
            RequireStatus(duplicate, HttpStatusCode.OK, "billing purchase replay");
            await RequireBillingOutcomeAsync(duplicate, "duplicate");
        }

        var changedPurchasePeriod = CreateRevenueCatEventBody(
            "rc-e2e-purchase",
            "INITIAL_PURCHASE",
            "monthly",
            AccountId,
            baseEventTimestampMs,
            periodStartedAtMs: baseEventTimestampMs + 1,
            periodExpiresAtMs: AiEntitlementPeriodStart.AddMonths(1).ToUnixTimeMilliseconds());
        using (var reusedEventId = await SendBillingWebhookAsync(client, changedPurchasePeriod))
        {
            RequireStatus(reusedEventId, HttpStatusCode.Conflict, "billing event period mismatch");
            RequireString(await ReadJsonAsync(reusedEventId), "code", "BILLING_EVENT_ID_REUSE");
        }

        var concurrentBillingReplays = await Task.WhenAll(
            Enumerable.Range(0, 4).Select(_ => SendBillingWebhookAsync(client, purchase)));
        try
        {
            foreach (var replay in concurrentBillingReplays)
            {
                RequireStatus(replay, HttpStatusCode.OK, "concurrent billing replay");
                await RequireBillingOutcomeAsync(replay, "duplicate");
            }
        }
        finally
        {
            foreach (var replay in concurrentBillingReplays) replay.Dispose();
        }

        var unknown = CreateRevenueCatEventBody(
            "rc-e2e-unknown",
            "UNSUPPORTED_EVENT",
            "monthly",
            AccountId,
            baseEventTimestampMs + 1_000);
        using (var ignored = await SendBillingWebhookAsync(client, unknown))
        {
            RequireStatus(ignored, HttpStatusCode.OK, "unknown billing event");
            await RequireBillingOutcomeAsync(ignored, "ignored");
        }

        var lifetime = CreateRevenueCatEventBody(
            "rc-e2e-lifetime",
            "INITIAL_PURCHASE",
            "lifetime",
            AccountId,
            baseEventTimestampMs + 1_500);
        using (var ignoredLifetime = await SendBillingWebhookAsync(client, lifetime))
        {
            RequireStatus(ignoredLifetime, HttpStatusCode.OK, "unapproved billing product");
            await RequireBillingOutcomeAsync(ignoredLifetime, "ignored");
        }

        var revoked = CreateRevenueCatEventBody(
            "rc-e2e-revoked",
            "CANCELLATION",
            productId: null,
            accountId: AccountId,
            eventTimestampMs: baseEventTimestampMs + 2_000,
            cancellationReason: "CUSTOMER_SUPPORT");
        using (var revokedResponse = await SendBillingWebhookAsync(client, revoked))
        {
            RequireStatus(revokedResponse, HttpStatusCode.OK, "billing revoke");
            await RequireBillingOutcomeAsync(revokedResponse, "accepted");
        }

        var restored = CreateRevenueCatEventBody(
            "rc-e2e-restored",
            "UNCANCELLATION",
            "yearly",
            AccountId,
            baseEventTimestampMs + 4_000,
            periodStartedAtMs: baseEventTimestampMs,
            periodExpiresAtMs: AiEntitlementExpiresAt.ToUnixTimeMilliseconds());
        using (var restoredResponse = await SendBillingWebhookAsync(client, restored))
        {
            RequireStatus(restoredResponse, HttpStatusCode.OK, "billing restore");
            await RequireBillingOutcomeAsync(restoredResponse, "accepted");
        }

        var staleExpiration = CreateRevenueCatEventBody(
            "rc-e2e-stale-expiration",
            "EXPIRATION",
            productId: null,
            accountId: AccountId,
            eventTimestampMs: baseEventTimestampMs + 3_000);
        using (var staleResponse = await SendBillingWebhookAsync(client, staleExpiration))
        {
            RequireStatus(staleResponse, HttpStatusCode.OK, "stale billing expiration");
            await RequireBillingOutcomeAsync(staleResponse, "accepted");
        }

        using (var ownEntitlements = await SendAsync(
            client,
            HttpMethod.Get,
            "/v1/billing/entitlements",
            AccountId,
            body: null))
        {
            RequireStatus(ownEntitlements, HttpStatusCode.OK, "own entitlement read");
            var body = await ReadJsonAsync(ownEntitlements);
            RequireString(body, "schema", "evidrilo.entitlements");
            var entitlements = body.GetProperty("entitlements");
            if (entitlements.GetArrayLength() != 1
                || entitlements[0].GetProperty("entitlement").GetString() != BillingEntitlement
                || entitlements[0].GetProperty("status").GetString() != "active")
            {
                throw new InvalidOperationException(
                    "Billing restore did not leave the account with the active canonical entitlement.");
            }
        }

        using (var otherEntitlements = await SendAsync(
            client,
            HttpMethod.Get,
            "/v1/billing/entitlements",
            OtherAccountId,
            body: null))
        {
            RequireStatus(otherEntitlements, HttpStatusCode.OK, "cross-account entitlement read");
            var body = await ReadJsonAsync(otherEntitlements);
            if (body.GetProperty("entitlements").GetArrayLength() != 0)
            {
                throw new InvalidOperationException(
                    "Cross-account entitlement isolation returned another account's billing state.");
            }
        }

        await AssertBillingDatabaseStateAsync(databaseConnectionString);
    }

    private static async Task<HttpResponseMessage> SendBillingWebhookAsync(
        HttpClient client,
        byte[] body,
        string? signature = null)
    {
        using var request = new HttpRequestMessage(HttpMethod.Post, "/v1/billing/webhook")
        {
            Content = new StringContent(Encoding.UTF8.GetString(body), Encoding.UTF8, "application/json"),
        };
        request.Headers.Add(
            "X-RevenueCat-Webhook-Signature",
            signature ?? BillingSignature.Create(
                BillingWebhookSecret,
                DateTimeOffset.UtcNow.ToUnixTimeSeconds(),
                body));
        return await client.SendAsync(request);
    }

    private static byte[] CreateRevenueCatEventBody(
        string eventId,
        string eventType,
        string? productId,
        Guid accountId,
        long eventTimestampMs,
        string? cancellationReason = null,
        long? periodStartedAtMs = null,
        long? periodExpiresAtMs = null)
    {
        var providerEvent = new Dictionary<string, object?>
        {
            ["id"] = eventId,
            ["type"] = eventType,
            ["app_user_id"] = accountId.ToString(),
            ["entitlement_ids"] = new[] { BillingEntitlement },
            ["event_timestamp_ms"] = eventTimestampMs,
        };
        if (productId is not null) providerEvent["product_id"] = productId;
        if (cancellationReason is not null) providerEvent["cancel_reason"] = cancellationReason;
        if (periodStartedAtMs is not null) providerEvent["purchased_at_ms"] = periodStartedAtMs;
        if (periodExpiresAtMs is not null) providerEvent["expiration_at_ms"] = periodExpiresAtMs;

        return JsonSerializer.SerializeToUtf8Bytes(new Dictionary<string, object?>
        {
            ["api_version"] = "1.0",
            ["event"] = providerEvent,
        });
    }

    private static async Task SeedExpiredAiReservationAsync(
        string databaseConnectionString,
        Guid accountId)
    {
        await using var dataSource = NpgsqlDataSource.Create(databaseConnectionString);
        await using var connection = await dataSource.OpenConnectionAsync();
        await using var transaction = await connection.BeginTransactionAsync();
        await using var selectGrant = connection.CreateCommand();
        selectGrant.Transaction = transaction;
        selectGrant.CommandText = """
            select grant_id
            from public.ai_credit_grants
            where account_id = @account_id and grant_kind = 'free_once'
            for update;
            """;
        selectGrant.Parameters.AddWithValue("account_id", accountId);
        var grantValue = await selectGrant.ExecuteScalarAsync();
        if (grantValue is not Guid grantId)
            throw new InvalidOperationException("The synthetic free AI grant is missing.");

        await using var reserveGrant = connection.CreateCommand();
        reserveGrant.Transaction = transaction;
        reserveGrant.CommandText = """
            update public.ai_credit_grants
            set reserved_credits = reserved_credits + 1
            where grant_id = @grant_id;
            """;
        reserveGrant.Parameters.AddWithValue("grant_id", grantId);
        await reserveGrant.ExecuteNonQueryAsync();

        await using var insertReservation = connection.CreateCommand();
        insertReservation.Transaction = transaction;
        insertReservation.CommandText = """
            insert into public.ai_credit_reservations (
                account_id, request_id, request_hash, grant_id, status,
                reserved_at, lease_expires_at
            ) values (
                @account_id, 'ai-e2e-stale-lease-001', repeat('c', 64), @grant_id,
                'reserved', now() - interval '10 minutes', now() - interval '8 minutes'
            );
            """;
        insertReservation.Parameters.AddWithValue("account_id", accountId);
        insertReservation.Parameters.AddWithValue("grant_id", grantId);
        await insertReservation.ExecuteNonQueryAsync();

        await using var insertAllocation = connection.CreateCommand();
        insertAllocation.Transaction = transaction;
        insertAllocation.CommandText = """
            insert into public.ai_credit_reservation_allocations (
                account_id, request_id, allocation_index, grant_id, reserved_credits, settled_credits
            ) values (
                @account_id, 'ai-e2e-stale-lease-001', 0, @grant_id, 1, null
            );
            """;
        insertAllocation.Parameters.AddWithValue("account_id", accountId);
        insertAllocation.Parameters.AddWithValue("grant_id", grantId);
        await insertAllocation.ExecuteNonQueryAsync();
        await transaction.CommitAsync();
    }

    private static async Task RequireExpiredAiReservationReleasedAsync(
        string databaseConnectionString,
        Guid accountId)
    {
        await using var dataSource = NpgsqlDataSource.Create(databaseConnectionString);
        await using var connection = await dataSource.OpenConnectionAsync();
        await using var command = connection.CreateCommand();
        command.CommandText = """
            select status, release_reason
            from public.ai_credit_reservations
            where account_id = @account_id and request_id = 'ai-e2e-stale-lease-001';
            """;
        command.Parameters.AddWithValue("account_id", accountId);
        await using var reader = await command.ExecuteReaderAsync();
        if (!await reader.ReadAsync()
            || reader.GetString(0) != "released"
            || reader.GetString(1) != "lease_expired")
        {
            throw new InvalidOperationException("The expired AI reservation was not released.");
        }
    }

    private static async Task RequireBillingOutcomeAsync(
        HttpResponseMessage response,
        string expectedOutcome)
    {
        var body = await ReadJsonAsync(response);
        RequireString(body, "schema", "evidrilo.billing-webhook-result");
        RequireString(body, "version", "1");
        RequireString(body, "outcome", expectedOutcome);
    }

    private static async Task AssertBillingDatabaseStateAsync(string databaseConnectionString)
    {
        await using var dataSource = NpgsqlDataSource.Create(databaseConnectionString);
        await using var connection = await dataSource.OpenConnectionAsync();
        await using var command = connection.CreateCommand();
        command.CommandText = """
            select
                (select count(*) from public.entitlement_events
                  where account_id = @account_id) as event_count,
                (select status from public.entitlements
                  where account_id = @account_id and entitlement = @entitlement) as entitlement_status,
                (select count(*) from public.entitlements
                  where account_id = @account_id) as entitlement_count,
                (select count(*) from public.entitlement_events
                  where account_id = @other_account_id) as other_event_count;
            """;
        command.Parameters.AddWithValue("account_id", AccountId);
        command.Parameters.AddWithValue("entitlement", BillingEntitlement);
        command.Parameters.AddWithValue("other_account_id", OtherAccountId);
        await using var reader = await command.ExecuteReaderAsync();
        if (!await reader.ReadAsync()
            || reader.GetInt64(0) != 4
            || reader.IsDBNull(1)
            || reader.GetString(1) != "active"
            || reader.GetInt64(2) != 1
            || reader.GetInt64(3) != 0)
        {
            throw new InvalidOperationException(
                "Billing database state did not preserve replay idempotency, restore ordering, or isolation.");
        }
    }

    private static async Task AssertAiCreditFlowAsync(
        HttpClient client,
        string databaseConnectionString)
    {
        using (var initialBalance = await SendAsync(
            client,
            HttpMethod.Get,
            "/v1/ai/credits",
            AccountId,
            body: null))
        {
            RequireStatus(initialBalance, HttpStatusCode.OK, "initial AI credit balance");
            var body = await ReadJsonAsync(initialBalance);
            RequireString(body, "schema", "evidrilo.ai-credits");
            RequireNumber(body, "available", 0);
            if (body.GetProperty("consentRecorded").GetBoolean())
                throw new InvalidOperationException("AI consent was recorded before explicit opt-in.");
        }

        var assistRequest = new
        {
            purpose = "explain_feedback",
            input = "Explain why this deterministic feedback requires a narrower claim.",
            locale = "en-US",
            optedIn = true,
            context = new
            {
                caseVersionId = CaseVersionId,
                feedbackCode = "MISSING_EVIDENCE",
                feedbackStatus = "ACTION_REQUIRED",
                anchorIds = new[] { "OBS-E2E-01" },
                limitationIds = new[] { "LIMIT-E2E-01" },
                claimText = "The supplied observation supports a bounded comparison.",
                claimScope = "LIMITED_COMPARISON",
                nextAction = "State the limitation before revising.",
            },
        };
        using (var assist = await SendAiAsync(client, assistRequest, "ai-e2e-request-001"))
        {
            RequireStatus(assist, HttpStatusCode.OK, "AI provider-disabled assist");
            var body = await ReadJsonAsync(assist);
            RequireString(body, "status", "fallback");
            RequireString(body, "reasonCode", "AI_PROVIDER_UNAVAILABLE");
        }

        var concurrentAiReplays = await Task.WhenAll(
            Enumerable.Range(0, 4).Select(_ => SendAiAsync(client, assistRequest, "ai-e2e-request-001")));
        try
        {
            foreach (var replay in concurrentAiReplays)
            {
                RequireStatus(replay, HttpStatusCode.OK, "concurrent AI replay");
                var body = await ReadJsonAsync(replay);
                RequireString(body, "reasonCode", "AI_IDEMPOTENCY_REPLAY");
            }
        }
        finally
        {
            foreach (var replay in concurrentAiReplays) replay.Dispose();
        }

        using (var replay = await SendAiAsync(client, assistRequest, "ai-e2e-request-001"))
        {
            RequireStatus(replay, HttpStatusCode.OK, "AI idempotent replay");
            var body = await ReadJsonAsync(replay);
            RequireString(body, "status", "fallback");
            RequireString(body, "reasonCode", "AI_IDEMPOTENCY_REPLAY");
        }

        using (var reused = await SendAiAsync(
                   client,
                   new
                   {
                       purpose = "explain_feedback",
                       input = "A different payload must not reuse the same AI operation key.",
                       locale = "en-US",
                       optedIn = true,
                       context = new
                       {
                           caseVersionId = CaseVersionId,
                           feedbackCode = "MISSING_EVIDENCE",
                           feedbackStatus = "ACTION_REQUIRED",
                           anchorIds = new[] { "OBS-E2E-01" },
                           limitationIds = new[] { "LIMIT-E2E-01" },
                           claimText = "A different request should have a different fingerprint.",
                           claimScope = "LIMITED_COMPARISON",
                           nextAction = "Keep the operation idempotent.",
                       },
                   },
                   "ai-e2e-request-001"))
        {
            RequireStatus(reused, HttpStatusCode.Conflict, "AI idempotency-key reuse");
            RequireString(await ReadJsonAsync(reused), "code", "AI_IDEMPOTENCY_KEY_REUSE");
        }

        using (var balance = await SendAsync(
            client,
            HttpMethod.Get,
            "/v1/ai/credits",
            AccountId,
            body: null))
        {
            RequireStatus(balance, HttpStatusCode.OK, "AI credit balance after opt-in");
            var body = await ReadJsonAsync(balance);
            RequireString(body, "schema", "evidrilo.ai-credits");
            RequireNumber(body, "available", 620);
            var grants = body.GetProperty("grants");
            var freeGrant = grants.EnumerateArray().SingleOrDefault(grant =>
                grant.GetProperty("grantKind").GetString() == "free_once");
            var subscriptionGrants = grants.EnumerateArray().Where(grant =>
                grant.GetProperty("grantKind").GetString() == "subscription_month");
            if (!body.GetProperty("consentRecorded").GetBoolean()
                || grants.GetArrayLength() != 4
                || freeGrant.ValueKind != JsonValueKind.Object
                || freeGrant.GetProperty("granted").GetInt32() != 20
                || subscriptionGrants.Count() != 3
                || subscriptionGrants.Any(grant =>
                    grant.GetProperty("granted").GetInt32() != 200
                    || grant.GetProperty("expiresAt").ValueKind != JsonValueKind.Null))
            {
                throw new InvalidOperationException(
                    "AI opt-in did not preserve the Free grant and accrue one non-expiring Pro grant for each active entitlement month.");
            }
        }

        await SeedExpiredAiReservationAsync(databaseConnectionString, AccountId);
        using (var recoveredBalance = await SendAsync(
            client,
            HttpMethod.Get,
            "/v1/ai/credits",
            AccountId,
            body: null))
        {
            RequireStatus(recoveredBalance, HttpStatusCode.OK, "AI balance after stale reservation recovery");
            var body = await ReadJsonAsync(recoveredBalance);
            RequireNumber(body, "available", 620);
        }

        await RequireExpiredAiReservationReleasedAsync(databaseConnectionString, AccountId);

        var revokedAt = DateTimeOffset.UtcNow;
        var revokedPeriod = CreateRevenueCatEventBody(
            "rc-e2e-credit-revoke",
            "CANCELLATION",
            productId: null,
            accountId: AccountId,
            eventTimestampMs: revokedAt.ToUnixTimeMilliseconds(),
            cancellationReason: "CUSTOMER_SUPPORT",
            periodStartedAtMs: AiEntitlementPeriodStart.ToUnixTimeMilliseconds(),
            periodExpiresAtMs: AiEntitlementExpiresAt.ToUnixTimeMilliseconds());
        using (var revoked = await SendBillingWebhookAsync(client, revokedPeriod))
        {
            RequireStatus(revoked, HttpStatusCode.OK, "AI credit entitlement revoke");
            await RequireBillingOutcomeAsync(revoked, "accepted");
        }

        using (var accruedBalance = await SendAsync(
            client,
            HttpMethod.Get,
            "/v1/ai/credits",
            AccountId,
            body: null))
        {
            RequireStatus(accruedBalance, HttpStatusCode.OK, "accrued balance after Pro ends");
            RequireNumber(await ReadJsonAsync(accruedBalance), "available", 620);
        }

        await SeedFragmentedAiCreditBalanceAsync(databaseConnectionString, AccountId, 125);
        using (var ledger = new NpgsqlAiCreditLedger(databaseConnectionString))
        {
            var reservation = await ledger.TryReserveAsync(
                AccountId,
                "ai-accumulated-credit-001",
                new string('a', 64),
                creditCost: 200,
                cancellationToken: CancellationToken.None);
            if (reservation is null)
                throw new InvalidOperationException("Accumulated AI credits could not be reserved across grants.");
            if (!await ledger.CompleteAsync(
                    AccountId,
                    reservation,
                    accepted: true,
                    settledCreditCost: 150,
                    cancellationToken: CancellationToken.None))
            {
                throw new InvalidOperationException("Accumulated AI credit reservation did not settle.");
            }
        }

        using (var settledBalance = await SendAsync(
            client,
            HttpMethod.Get,
            "/v1/ai/credits",
            AccountId,
            body: null))
        {
            RequireStatus(settledBalance, HttpStatusCode.OK, "balance after multi-grant settlement");
            RequireNumber(await ReadJsonAsync(settledBalance), "available", 95);
        }

        using (var otherBalance = await SendAsync(
            client,
            HttpMethod.Get,
            "/v1/ai/credits",
            OtherAccountId,
            body: null))
        {
            RequireStatus(otherBalance, HttpStatusCode.OK, "cross-account AI credit balance");
            var body = await ReadJsonAsync(otherBalance);
            RequireNumber(body, "available", 0);
            if (body.GetProperty("grants").GetArrayLength() != 0)
                throw new InvalidOperationException("Cross-account AI credit isolation returned another account's grant.");
        }

        await using var dataSource = NpgsqlDataSource.Create(databaseConnectionString);
        await using var connection = await dataSource.OpenConnectionAsync();
        await using var command = connection.CreateCommand();
        command.CommandText = """
            select
                (select count(*) from public.ai_credit_consents where account_id = @account_id),
                (select count(*) from public.ai_credit_grants where account_id = @account_id),
                (select count(*) from public.ai_credit_reservations where account_id = @account_id),
                (select count(*) from public.ai_credit_reservations
                  where account_id = @account_id and status = 'released');
            """;
        command.Parameters.AddWithValue("account_id", AccountId);
        await using var reader = await command.ExecuteReaderAsync();
        if (!await reader.ReadAsync()
            || reader.GetInt64(0) != 1
            || reader.GetInt64(1) != 4
            || reader.GetInt64(2) != 3
            || reader.GetInt64(3) != 2)
        {
            throw new InvalidOperationException(
                "AI credit database state did not preserve consent, grants, or provider-failure refund.");
        }
        Console.WriteLine("EVIDRILO_AI_CREDIT_LEDGER_PASS");
    }

    private static async Task SeedFragmentedAiCreditBalanceAsync(
        string databaseConnectionString,
        Guid accountId,
        int consumedCreditsPerGrant)
    {
        await using var dataSource = NpgsqlDataSource.Create(databaseConnectionString);
        await using var connection = await dataSource.OpenConnectionAsync();
        await using var command = connection.CreateCommand();
        command.CommandText = """
            update public.ai_credit_grants
            set consumed_credits = @consumed_credits
            where account_id = @account_id
              and grant_kind = 'subscription_month';
            """;
        command.Parameters.AddWithValue("account_id", accountId);
        command.Parameters.AddWithValue("consumed_credits", consumedCreditsPerGrant);
        if (await command.ExecuteNonQueryAsync() != 3)
            throw new InvalidOperationException("The synthetic multi-month Pro grant rows were not available.");
    }

    private static async Task AssertNotificationAndAccountExportFlowAsync(
        HttpClient client,
        string databaseConnectionString,
        string repositoryRoot)
    {
        var preferencesRequest = new
        {
            schema = "evidrilo.notification-preferences-update",
            version = "1",
            enabled = true,
            continueUnfinishedEnabled = true,
            reviewCompletedEnabled = false,
            cadence = "weekly",
            localHour = 18,
            localMinute = 45,
            expectedRevision = 0L,
        };

        using (var updated = await SendAsync(
            client,
            HttpMethod.Put,
            "/v1/notifications/preferences",
            AccountId,
            preferencesRequest))
        {
            RequireStatus(updated, HttpStatusCode.OK, "notification preference update");
            var body = await ReadJsonAsync(updated);
            RequireString(body, "outcome", "accepted");
            RequireNumber(body, "revision", 1);
        }

        var stalePreferencesRequest = new
        {
            schema = "evidrilo.notification-preferences-update",
            version = "1",
            enabled = false,
            continueUnfinishedEnabled = false,
            reviewCompletedEnabled = true,
            cadence = "daily",
            localHour = 7,
            localMinute = 15,
            expectedRevision = 0L,
        };
        using var staleRevisionFactory = new E2eApiFactory(databaseConnectionString, repositoryRoot);
        using var staleRevisionClient = staleRevisionFactory.CreateClient();
        using (var staleUpdate = await SendAsync(
            staleRevisionClient,
            HttpMethod.Put,
            "/v1/notifications/preferences",
            AccountId,
            stalePreferencesRequest))
        {
            RequireStatus(staleUpdate, HttpStatusCode.Conflict, "stale notification preference update");
        }

        using (var preferences = await SendAsync(
            client,
            HttpMethod.Get,
            "/v1/notifications/preferences",
            AccountId,
            body: null))
        {
            RequireStatus(preferences, HttpStatusCode.OK, "notification preferences after stale update");
            var body = await ReadJsonAsync(preferences);
            if (!body.GetProperty("enabled").GetBoolean()
                || !body.GetProperty("continueUnfinishedEnabled").GetBoolean()
                || body.GetProperty("reviewCompletedEnabled").GetBoolean()
                || body.GetProperty("cadence").GetString() != "weekly"
                || body.GetProperty("localHour").GetInt32() != 18
                || body.GetProperty("localMinute").GetInt32() != 45
                || body.GetProperty("revision").GetInt64() != 1)
            {
                throw new InvalidOperationException(
                    "Notification preference read did not return the account-owned update.");
            }
        }

        await SeedLargeAccountExportFixtureAsync(databaseConnectionString, firstEvent: 1, eventCount: 128);

        using (var export = await SendAsync(
            client,
            HttpMethod.Get,
            "/v1/account/me/export",
            AccountId,
            body: null))
        {
            RequireStatus(export, HttpStatusCode.OK, "account export");
            var body = await ReadJsonAsync(export);
            RequireString(body, "schema", "evidrilo.account-export");
            var data = body.GetProperty("data");
            var preferences = data.GetProperty("notificationPreferences");
            if (!preferences.GetProperty("enabled").GetBoolean()
                || preferences.GetProperty("cadence").GetString() != "weekly"
                || preferences.GetProperty("revision").GetInt64() != 1)
            {
                throw new InvalidOperationException(
                    "Account export did not include the account-owned notification mirror.");
            }

            if (data.GetProperty("analyticsEvents").GetArrayLength() < 128)
                throw new InvalidOperationException(
                    "Account export did not stream all rows from a multi-buffer analytics collection.");
        }

        await SeedLargeAccountExportFixtureAsync(databaseConnectionString, firstEvent: 129, eventCount: 384);
        using (var oversizedExport = await SendAsync(
            client,
            HttpMethod.Get,
            "/v1/account/me/export",
            AccountId,
            body: null))
        {
            RequireStatus(oversizedExport, HttpStatusCode.RequestEntityTooLarge, "oversized account export");
            RequireString(await ReadJsonAsync(oversizedExport), "code", "ACCOUNT_EXPORT_TOO_LARGE");
        }

        await AssertAccountExportQueueLimitAsync(client, databaseConnectionString, OtherAccountId);
        await SeedProjectAiExportFixtureAsync(databaseConnectionString);
        var exportId = await CreateAccountExportAsync(client, AccountId, "account_export_e2e_0001");
        var replayedExportId = await CreateAccountExportAsync(client, AccountId, "account_export_e2e_0001");
        if (replayedExportId != exportId)
            throw new InvalidOperationException("Account export idempotency replay returned a different export ID.");

        var ready = false;
        string? lastExportStatus = null;
        for (var attempt = 0; attempt < 30; attempt++)
        {
            using var status = await SendAccountExportAsync(
                client,
                HttpMethod.Get,
                $"/v2/account/exports/{exportId:D}",
                AccountId);
            RequireStatus(status, HttpStatusCode.OK, "account export job status");
            var statusBody = await ReadJsonAsync(status);
            var jobStatus = statusBody.GetProperty("status").GetString();
            lastExportStatus = jobStatus;
            if (jobStatus == "ready")
            {
                ready = true;
                break;
            }
            if (jobStatus == "failed")
            {
                throw new InvalidOperationException(
                    $"Account export worker failed with safe code {statusBody.GetProperty("errorCode").GetString()}.");
            }
            await Task.Delay(TimeSpan.FromSeconds(1));
        }
        if (!ready)
            throw new InvalidOperationException(
                $"Account export worker did not complete the job within 30 seconds; last status was {lastExportStatus ?? "unknown"}.");

        using (var download = await SendAccountExportAsync(
            client,
            HttpMethod.Get,
            $"/v2/account/exports/{exportId:D}/download",
            AccountId))
        {
            RequireStatus(download, HttpStatusCode.OK, "owner account export download");
            if (download.Headers.CacheControl?.NoStore != true
                || download.Content.Headers.ContentDisposition?.FileName?.Contains(
                    "evidrilo-account-export-v2.json",
                    StringComparison.Ordinal) != true)
            {
                throw new InvalidOperationException("Account export download cache or attachment headers were unsafe.");
            }

            var body = await ReadJsonAsync(download);
            RequireString(body, "schema", "evidrilo.account-export");
            RequireString(body, "version", "2");
            RequireString(body, "accountId", AccountId.ToString("D"));
            var data = body.GetProperty("data");
            if (!data.GetProperty("projectAiConsent").GetProperty("granted").GetBoolean()
                || data.GetProperty("projectAiConsentEvents").GetArrayLength() != 1
                || data.GetProperty("projectAiActivity").GetArrayLength() != 1
                || data.GetProperty("aiConversationSessions").GetArrayLength() != 1
                || data.GetProperty("aiConversationTurnRequests").GetArrayLength() != 1
                || ContainsForbiddenExportFields(body))
            {
                throw new InvalidOperationException(
                    "Account export v2 omitted AI metadata or included transcript or request-fingerprint fields.");
            }
        }

        foreach (var path in new[]
        {
            $"/v2/account/exports/{exportId:D}",
            $"/v2/account/exports/{exportId:D}/download",
        })
        {
            using var hidden = await SendAccountExportAsync(client, HttpMethod.Get, path, OtherAccountId);
            RequireStatus(hidden, HttpStatusCode.NotFound, "cross-account export lookup");
        }
        using (var hiddenCancel = await SendAccountExportAsync(
            client,
            HttpMethod.Delete,
            $"/v2/account/exports/{exportId:D}",
            OtherAccountId))
        {
            RequireStatus(hiddenCancel, HttpStatusCode.NotFound, "cross-account export cancellation");
        }
        using (var cancelled = await SendAccountExportAsync(
            client,
            HttpMethod.Delete,
            $"/v2/account/exports/{exportId:D}",
            AccountId))
        {
            RequireStatus(cancelled, HttpStatusCode.OK, "owner account export cancellation");
            RequireString(await ReadJsonAsync(cancelled), "status", "cancelled");
        }
        using (var deletedDownload = await SendAccountExportAsync(
            client,
            HttpMethod.Get,
            $"/v2/account/exports/{exportId:D}/download",
            AccountId))
        {
            RequireStatus(deletedDownload, HttpStatusCode.NotFound, "cancelled account export download");
        }

        using (var otherExport = await SendAsync(
            client,
            HttpMethod.Get,
            "/v1/account/me/export",
            OtherAccountId,
            body: null))
        {
            RequireStatus(otherExport, HttpStatusCode.OK, "cross-account export");
            var body = await ReadJsonAsync(otherExport);
            var preferences = body.GetProperty("data").GetProperty("notificationPreferences");
            if (preferences.EnumerateObject().Any())
            {
                throw new InvalidOperationException(
                    "Cross-account export leaked notification preference fields.");
            }
        }

        Console.WriteLine("EVIDRILO_NOTIFICATION_ACCOUNT_EXPORT_V1_V2_PASS");
    }

    private static async Task AssertStudentProjectFlowAsync(
        HttpClient client,
        string databaseConnectionString)
    {
        var initialProject = CreateStudentProject("Project entered by a student.");
        using (var initialConsent = await SendAsync(
            client,
            HttpMethod.Get,
            "/v1/projects/cloud-consent",
            AccountId,
            body: null))
        {
            RequireStatus(initialConsent, HttpStatusCode.OK, "initial project cloud consent");
            if ((await ReadJsonAsync(initialConsent)).GetProperty("granted").GetBoolean())
                throw new InvalidOperationException("Project cloud storage was granted without an explicit user decision.");
        }

        using (var blockedCreate = await SendProjectAsync(
            client,
            HttpMethod.Post,
            "/v1/projects",
            AccountId,
            "student-project-create-e2e-001",
            new
            {
                schema = "evidrilo.student-project-create",
                version = "1",
                project = initialProject,
            }))
        {
            RequireStatus(blockedCreate, HttpStatusCode.Forbidden, "project create without cloud consent");
            RequireString(await ReadJsonAsync(blockedCreate), "code", "PROJECT_CLOUD_CONSENT_REQUIRED");
        }

        using (var grant = await SendAsync(
            client,
            HttpMethod.Put,
            "/v1/projects/cloud-consent",
            AccountId,
            new
            {
                schema = "evidrilo.project-cloud-consent-update",
                version = "1",
                policyVersion = "student-project-cloud.v1",
                decision = "grant",
            }))
        {
            RequireStatus(grant, HttpStatusCode.OK, "explicit project cloud consent grant");
            var body = await ReadJsonAsync(grant);
            if (!body.GetProperty("granted").GetBoolean())
                throw new InvalidOperationException("Explicit project cloud consent was not recorded.");
            RequireString(body, "policyVersion", "student-project-cloud.v1");
        }

        using (var repeatedGrant = await SendAsync(
            client,
            HttpMethod.Put,
            "/v1/projects/cloud-consent",
            AccountId,
            new
            {
                schema = "evidrilo.project-cloud-consent-update",
                version = "1",
                policyVersion = "student-project-cloud.v1",
                decision = "grant",
            }))
        {
            RequireStatus(repeatedGrant, HttpStatusCode.OK, "idempotent project cloud consent grant");
        }

        using var create = await SendProjectAsync(
            client,
            HttpMethod.Post,
            "/v1/projects",
            AccountId,
            "student-project-create-e2e-001",
            new
            {
                schema = "evidrilo.student-project-create",
                version = "1",
                project = initialProject,
            });
        RequireStatus(create, HttpStatusCode.Created, "student project create");
        var createBody = await ReadJsonAsync(create);
        RequireString(createBody, "outcome", "created");
        RequireNumber(createBody, "projectVersion", 1);
        var projectId = createBody.GetProperty("projectId").GetGuid();

        using (var replay = await SendProjectAsync(
            client,
            HttpMethod.Post,
            "/v1/projects",
            AccountId,
            "student-project-create-e2e-001",
            new
            {
                schema = "evidrilo.student-project-create",
                version = "1",
                project = initialProject,
            }))
        {
            RequireStatus(replay, HttpStatusCode.OK, "student project create replay");
            var body = await ReadJsonAsync(replay);
            RequireString(body, "outcome", "replayed");
            if (body.GetProperty("projectId").GetGuid() != projectId)
                throw new InvalidOperationException("Student project replay returned a different project.");
        }

        using (var reusedKey = await SendProjectAsync(
            client,
            HttpMethod.Post,
            "/v1/projects",
            AccountId,
            "student-project-create-e2e-001",
            new
            {
                schema = "evidrilo.student-project-create",
                version = "1",
                project = CreateStudentProject("Changed payload."),
            }))
        {
            RequireStatus(reusedKey, HttpStatusCode.Conflict, "student project idempotency key reuse");
            RequireString(await ReadJsonAsync(reusedKey), "code", "IDEMPOTENCY_KEY_REUSED");
        }

        using (var crossAccount = await SendAsync(
            client,
            HttpMethod.Get,
            $"/v1/projects/{projectId:D}",
            OtherAccountId,
            body: null))
        {
            RequireStatus(crossAccount, HttpStatusCode.NotFound, "cross-account project read");
        }

        using (var crossAccountRevisions = await SendAsync(
            client,
            HttpMethod.Get,
            $"/v1/projects/{projectId:D}/revisions",
            OtherAccountId,
            body: null))
        {
            RequireStatus(crossAccountRevisions, HttpStatusCode.NotFound, "cross-account project revision read");
        }

        using (var crossAccountReport = await SendAsync(
            client,
            HttpMethod.Get,
            $"/v1/projects/{projectId:D}/structure-report",
            OtherAccountId,
            body: null))
        {
            RequireStatus(crossAccountReport, HttpStatusCode.NotFound, "cross-account project structure report");
        }

        using (var crossAccountSave = await SendProjectAsync(
            client,
            HttpMethod.Put,
            $"/v1/projects/{projectId:D}",
            OtherAccountId,
            "foreign-project-save-e2e-001",
            new
            {
                schema = "evidrilo.student-project-save",
                version = "1",
                expectedVersion = 1,
                project = CreateStudentProject("This foreign project must not be writable."),
            }))
        {
            RequireStatus(crossAccountSave, HttpStatusCode.NotFound, "cross-account project save");
        }

        using (var report = await SendAsync(
            client,
            HttpMethod.Get,
            $"/v1/projects/{projectId:D}/structure-report",
            AccountId,
            body: null))
        {
            RequireStatus(report, HttpStatusCode.OK, "student project structure report");
            var body = await ReadJsonAsync(report);
            RequireString(body, "assessmentStatus", "not_assessed");
            if (body.GetProperty("academicMeritAssessed").GetBoolean())
                throw new InvalidOperationException("Structure report incorrectly assessed academic merit.");
        }

        var savedProject = CreateStudentProject(
            "Project revised by the student.",
            "A bounded student-authored claim.",
            "The student-authored optional hypothesis.");
        using (var save = await SendProjectAsync(
            client,
            HttpMethod.Put,
            $"/v1/projects/{projectId:D}",
            AccountId,
            "student-project-save-e2e-001",
            new
            {
                schema = "evidrilo.student-project-save",
                version = "1",
                expectedVersion = 1,
                project = savedProject,
            }))
        {
            RequireStatus(save, HttpStatusCode.OK, "student project save");
            var body = await ReadJsonAsync(save);
            RequireString(body, "outcome", "saved");
            RequireNumber(body, "projectVersion", 2);
        }

        using (var staleSave = await SendProjectAsync(
            client,
            HttpMethod.Put,
            $"/v1/projects/{projectId:D}",
            AccountId,
            "student-project-save-stale-001",
            new
            {
                schema = "evidrilo.student-project-save",
                version = "1",
                expectedVersion = 1,
                project = savedProject,
            }))
        {
            RequireStatus(staleSave, HttpStatusCode.Conflict, "stale student project save");
            RequireString(await ReadJsonAsync(staleSave), "code", "PROJECT_VERSION_CONFLICT");
        }

        using (var detail = await SendAsync(
            client,
            HttpMethod.Get,
            $"/v1/projects/{projectId:D}",
            AccountId,
            body: null))
        {
            RequireStatus(detail, HttpStatusCode.OK, "student project detail");
            var project = (await ReadJsonAsync(detail)).GetProperty("project");
            RequireString(project, "hypothesis", "The student-authored optional hypothesis.");
            RequireString(project, "claim", "A bounded student-authored claim.");
        }

        using (var revisions = await SendAsync(
            client,
            HttpMethod.Get,
            $"/v1/projects/{projectId:D}/revisions?limit=1",
            AccountId,
            body: null))
        {
            RequireStatus(revisions, HttpStatusCode.OK, "student project revisions");
            var body = await ReadJsonAsync(revisions);
            if (body.GetProperty("revisions").GetArrayLength() != 1
                || body.GetProperty("revisions")[0].GetProperty("version").GetInt32() != 2
                || body.GetProperty("nextBeforeVersion").GetInt32() != 2)
            {
                throw new InvalidOperationException("Student project revision cursor did not preserve version history.");
            }
        }

        using (var earlierRevisions = await SendAsync(
            client,
            HttpMethod.Get,
            $"/v1/projects/{projectId:D}/revisions?beforeVersion=2&limit=1",
            AccountId,
            body: null))
        {
            RequireStatus(earlierRevisions, HttpStatusCode.OK, "student project revision cursor continuation");
            var body = await ReadJsonAsync(earlierRevisions);
            if (body.GetProperty("revisions").GetArrayLength() != 1
                || body.GetProperty("revisions")[0].GetProperty("version").GetInt32() != 1
                || body.GetProperty("nextBeforeVersion").ValueKind != JsonValueKind.Null)
            {
                throw new InvalidOperationException("Student project revision cursor failed to return the earlier version.");
            }
        }

        using (var exported = await SendAsync(
            client,
            HttpMethod.Get,
            "/v1/account/me/export",
            AccountId,
            body: null))
        {
            RequireStatus(exported, HttpStatusCode.OK, "student project account export");
            var data = (await ReadJsonAsync(exported)).GetProperty("data");
            var projects = data.GetProperty("studentProjects");
            var revisions = data.GetProperty("studentProjectRevisions");
            var consent = data.GetProperty("studentProjectCloudConsent");
            var consentEvents = data.GetProperty("studentProjectCloudConsentEvents");
            if (projects.GetArrayLength() != 1
                || revisions.GetArrayLength() != 2
                || projects[0].GetProperty("projectId").GetGuid() != projectId
                || !consent.GetProperty("granted").GetBoolean()
                || consentEvents.GetArrayLength() != 1
                || data.TryGetProperty("studentProjectCommands", out _))
            {
                throw new InvalidOperationException("Student project export omitted owned history/consent or exposed command receipts.");
            }
        }

        using (var otherExport = await SendAsync(
            client,
            HttpMethod.Get,
            "/v1/account/me/export",
            OtherAccountId,
            body: null))
        {
            RequireStatus(otherExport, HttpStatusCode.OK, "other account student project export");
            var data = (await ReadJsonAsync(otherExport)).GetProperty("data");
            if (data.GetProperty("studentProjects").GetArrayLength() != 0
                || data.GetProperty("studentProjectRevisions").GetArrayLength() != 0
                || data.GetProperty("studentProjectCloudConsentEvents").GetArrayLength() != 0)
            {
                throw new InvalidOperationException("Student project export leaked another account's records.");
            }
        }

        var competingSaves = await Task.WhenAll(
            new[] { "Concurrent revision A.", "Concurrent revision B." }
                .Select((claim, index) => SendProjectAsync(
                    client,
                    HttpMethod.Put,
                    $"/v1/projects/{projectId:D}",
                    AccountId,
                    $"student-project-concurrent-save-{index + 1:D2}",
                    new
                    {
                        schema = "evidrilo.student-project-save",
                        version = "1",
                        expectedVersion = 2,
                        project = CreateStudentProject("Concurrent project revision.", claim),
                    })));
        try
        {
            if (competingSaves.Count(response => response.StatusCode == HttpStatusCode.OK) != 1
                || competingSaves.Count(response => response.StatusCode == HttpStatusCode.Conflict) != 1)
            {
                throw new InvalidOperationException("Concurrent student project saves did not produce one winner and one version conflict.");
            }

            var conflict = competingSaves.Single(response => response.StatusCode == HttpStatusCode.Conflict);
            RequireString(await ReadJsonAsync(conflict), "code", "PROJECT_VERSION_CONFLICT");
            var accepted = competingSaves.Single(response => response.StatusCode == HttpStatusCode.OK);
            RequireNumber(await ReadJsonAsync(accepted), "projectVersion", 3);
        }
        finally
        {
            foreach (var response in competingSaves) response.Dispose();
        }

        await AssertConsentRevocationSerializesWithProjectWriteAsync(
            client,
            databaseConnectionString,
            projectId,
            expectedVersion: 3);

        using (var blockedSave = await SendProjectAsync(
            client,
            HttpMethod.Put,
            $"/v1/projects/{projectId:D}",
            AccountId,
            "student-project-save-after-revoke-001",
            new
            {
                schema = "evidrilo.student-project-save",
                version = "1",
                expectedVersion = 3,
                project = CreateStudentProject("This write must remain local after consent revocation."),
            }))
        {
            RequireStatus(blockedSave, HttpStatusCode.Forbidden, "project save after cloud consent revoke");
            RequireString(await ReadJsonAsync(blockedSave), "code", "PROJECT_CLOUD_CONSENT_REQUIRED");
        }

        using (var revokedExport = await SendAsync(
            client,
            HttpMethod.Get,
            "/v1/account/me/export",
            AccountId,
            body: null))
        {
            RequireStatus(revokedExport, HttpStatusCode.OK, "revoked project cloud consent export");
            var data = (await ReadJsonAsync(revokedExport)).GetProperty("data");
            if (data.GetProperty("studentProjectCloudConsent").GetProperty("granted").GetBoolean()
                || data.GetProperty("studentProjectCloudConsentEvents").GetArrayLength() != 2)
            {
                throw new InvalidOperationException("Consent revocation was not included in the account export.");
            }
        }

        using (var list = await SendAsync(client, HttpMethod.Get, "/v1/projects", AccountId, body: null))
        {
            RequireStatus(list, HttpStatusCode.OK, "student project list");
            if ((await ReadJsonAsync(list)).GetProperty("projects").GetArrayLength() != 1)
                throw new InvalidOperationException("Student project list did not return the owned project.");
        }

        using (var delete = await SendProjectAsync(
            client,
            HttpMethod.Delete,
            $"/v1/projects/{projectId:D}/permanent",
            AccountId,
            "student-project-delete-e2e-001",
            new
            {
                schema = "evidrilo.student-project-permanent-delete",
                version = "1",
                expectedVersion = 3,
                confirmPermanently = true,
            }))
        {
            RequireStatus(delete, HttpStatusCode.OK, "student project delete");
            RequireString(await ReadJsonAsync(delete), "outcome", "deleted");
        }

        using (var deletedRead = await SendAsync(
            client,
            HttpMethod.Get,
            $"/v1/projects/{projectId:D}",
            AccountId,
            body: null))
        {
            RequireStatus(deletedRead, HttpStatusCode.NotFound, "deleted student project read");
        }

        using (var deletedHistory = await SendAsync(
            client,
            HttpMethod.Get,
            $"/v1/projects/{projectId:D}/revisions",
            AccountId,
            body: null))
        {
            RequireStatus(deletedHistory, HttpStatusCode.NotFound, "deleted student project revision read");
        }

        using (var deleteReplay = await SendProjectAsync(
            client,
            HttpMethod.Delete,
            $"/v1/projects/{projectId:D}/permanent",
            AccountId,
            "student-project-delete-e2e-001",
            new
            {
                schema = "evidrilo.student-project-permanent-delete",
                version = "1",
                expectedVersion = 3,
                confirmPermanently = true,
            }))
        {
            RequireStatus(deleteReplay, HttpStatusCode.OK, "student project delete replay");
            RequireString(await ReadJsonAsync(deleteReplay), "outcome", "replayed");
        }

        Console.WriteLine("EVIDRILO_STUDENT_PROJECT_IDEMPOTENCY_CONCURRENCY_EXPORT_DELETE_PASS");
    }

    private static async Task AssertConsentRevocationSerializesWithProjectWriteAsync(
        HttpClient client,
        string databaseConnectionString,
        Guid projectId,
        int expectedVersion)
    {
        await using var dataSource = NpgsqlDataSource.Create(databaseConnectionString);
        await using var blockerConnection = await dataSource.OpenConnectionAsync();
        await using var blockerTransaction = await blockerConnection.BeginTransactionAsync();
        await using (var blockConsent = blockerConnection.CreateCommand())
        {
            blockConsent.Transaction = blockerTransaction;
            blockConsent.CommandText = """
                select granted
                from public.student_project_cloud_consents
                where account_id = @account_id
                for update;
                """;
            blockConsent.Parameters.AddWithValue("account_id", AccountId);
            if (await blockConsent.ExecuteScalarAsync() is not true)
                throw new InvalidOperationException("The consent-race fixture is not explicitly granted.");
        }

        Task<HttpResponseMessage>? revokeTask = null;
        Task<HttpResponseMessage>? saveTask = null;
        HttpResponseMessage? revokeResponse = null;
        HttpResponseMessage? saveResponse = null;
        var blockerReleased = false;
        try
        {
            revokeTask = SendAsync(
                client,
                HttpMethod.Put,
                "/v1/projects/cloud-consent",
                AccountId,
                new
                {
                    schema = "evidrilo.project-cloud-consent-update",
                    version = "1",
                    policyVersion = "student-project-cloud.v1",
                    decision = "revoke",
                });
            await WaitForDatabaseLockWaitAsync(
                dataSource,
                "%student_project_cloud_consents%for update%",
                "consent revocation");

            saveTask = SendProjectAsync(
                client,
                HttpMethod.Put,
                $"/v1/projects/{projectId:D}",
                AccountId,
                "student-project-save-consent-race-001",
                new
                {
                    schema = "evidrilo.student-project-save",
                    version = "1",
                    expectedVersion,
                    project = CreateStudentProject("A write queued behind consent revocation."),
                });
            await WaitForDatabaseLockWaitAsync(
                dataSource,
                "%student_project_cloud_consents%for share%",
                "project save consent check");

            await blockerTransaction.CommitAsync();
            blockerReleased = true;
            revokeResponse = await revokeTask.WaitAsync(TimeSpan.FromSeconds(10));
            saveResponse = await saveTask.WaitAsync(TimeSpan.FromSeconds(10));
            RequireStatus(revokeResponse, HttpStatusCode.OK, "concurrent project cloud consent revoke");
            RequireStatus(saveResponse, HttpStatusCode.Forbidden, "project save queued behind consent revoke");
            RequireString(await ReadJsonAsync(saveResponse), "code", "PROJECT_CLOUD_CONSENT_REQUIRED");

            using var detail = await SendAsync(
                client,
                HttpMethod.Get,
                $"/v1/projects/{projectId:D}",
                AccountId,
                body: null);
            RequireStatus(detail, HttpStatusCode.OK, "project remains unchanged after consent-race rejection");
            var body = await ReadJsonAsync(detail);
            RequireNumber(body, "projectVersion", expectedVersion);
            RequireString(body.GetProperty("project"), "taskBrief", "Concurrent project revision.");
        }
        finally
        {
            if (!blockerReleased)
            {
                try
                {
                    await blockerTransaction.RollbackAsync();
                }
                catch (Exception exception) when (exception is InvalidOperationException or NpgsqlException)
                {
                    // The connection/transaction may already have been closed by a failed setup.
                }
            }

            await DisposeResponseAsync(revokeTask, revokeResponse);
            await DisposeResponseAsync(saveTask, saveResponse);
        }
    }

    private static async Task WaitForDatabaseLockWaitAsync(
        NpgsqlDataSource dataSource,
        string queryPattern,
        string operation)
    {
        await using var connection = await dataSource.OpenConnectionAsync();
        await using var command = connection.CreateCommand();
        command.CommandText = """
            select exists (
                select 1
                from pg_stat_activity
                where pid <> pg_backend_pid()
                  and datname = current_database()
                  and wait_event_type = 'Lock'
                  and query ilike @query_pattern
            );
            """;
        command.CommandTimeout = 2;
        command.Parameters.AddWithValue("query_pattern", queryPattern);
        var deadline = DateTimeOffset.UtcNow.AddSeconds(10);
        while (DateTimeOffset.UtcNow < deadline)
        {
            if (await command.ExecuteScalarAsync() is true) return;
            await Task.Delay(TimeSpan.FromMilliseconds(25));
        }

        throw new TimeoutException($"The {operation} request did not reach its expected PostgreSQL row-lock wait.");
    }

    private static async Task DisposeResponseAsync(
        Task<HttpResponseMessage>? responseTask,
        HttpResponseMessage? response)
    {
        if (response is not null)
        {
            response.Dispose();
            return;
        }

        if (responseTask is null) return;
        try
        {
            (await responseTask.WaitAsync(TimeSpan.FromSeconds(10))).Dispose();
        }
        catch (Exception exception) when (exception is TimeoutException or HttpRequestException or OperationCanceledException)
        {
            // Cleanup must not hide the original assertion/timeout failure.
        }
    }

    private static object CreateStudentProject(
        string taskBrief,
        string? claim = null,
        string? hypothesis = null) => new
    {
        title = "Local student inquiry",
        taskBrief,
        question = "How does this method relate to the observed outcome?",
        method = "Student-described method",
        hypothesis,
        criteria = Array.Empty<object>(),
        evidenceItems = Array.Empty<object>(),
        criterionEvidenceLinks = Array.Empty<object>(),
        analysis = (string?)null,
        claim,
        claimEvidenceIds = Array.Empty<string>(),
        limitations = Array.Empty<object>(),
        nextAction = (string?)null,
    };

    private static async Task<HttpResponseMessage> SendProjectAsync(
        HttpClient client,
        HttpMethod method,
        string path,
        Guid accountId,
        string idempotencyKey,
        object body)
    {
        using var request = new HttpRequestMessage(method, path);
        request.Headers.Add("X-Test-User", $"{accountId}|true");
        request.Headers.Add("Idempotency-Key", idempotencyKey);
        request.Content = JsonContent.Create(body);
        return await client.SendAsync(request);
    }

    private static async Task SeedLargeAccountExportFixtureAsync(
        string databaseConnectionString,
        int firstEvent,
        int eventCount)
    {
        await using var dataSource = NpgsqlDataSource.Create(databaseConnectionString);
        await using var connection = await dataSource.OpenConnectionAsync();
        await using var command = connection.CreateCommand();
        command.CommandText = """
            insert into public.analytics_events (
                account_id, client_event_id, event_name, event_version,
                occurred_at, source, consent_version, properties
            )
            select
                @account_id,
                md5('account-export-' || event_number::text)::uuid,
                'attempt_completed',
                1,
                clock_timestamp() + event_number * interval '1 second',
                'mobile',
                'analytics.v1',
                jsonb_build_object('ordinal', event_number, 'padding', repeat('x', 128))
            from generate_series(@first_event, @first_event + @event_count - 1) as event_number
            on conflict (account_id, client_event_id) do nothing;
            """;
        command.Parameters.AddWithValue("account_id", AccountId);
        command.Parameters.AddWithValue("first_event", firstEvent);
        command.Parameters.AddWithValue("event_count", eventCount);
        await command.ExecuteNonQueryAsync();
    }

    private static async Task AssertAccountExportQueueLimitAsync(
        HttpClient client,
        string databaseConnectionString,
        Guid accountId)
    {
        await SeedAccountExportQueueCapacityAsync(databaseConnectionString);
        try
        {
            using var limited = await SendAccountExportAsync(
                client,
                HttpMethod.Post,
                "/v2/account/exports",
                accountId,
                "export_queue_limit_0001");
            RequireStatus(limited, HttpStatusCode.TooManyRequests, "account export queue capacity");
            RequireString(await ReadJsonAsync(limited), "code", "EXPORT_CAPACITY_LIMITED");
        }
        finally
        {
            await ClearAccountExportQueueCapacityAsync(databaseConnectionString);
        }
    }

    private static async Task SeedAccountExportQueueCapacityAsync(string databaseConnectionString)
    {
        await using var dataSource = NpgsqlDataSource.Create(databaseConnectionString);
        await using var connection = await dataSource.OpenConnectionAsync();
        await using var command = connection.CreateCommand();
        command.CommandText = """
            insert into auth.users (id)
            select md5('account-export-queue-user-' || user_number::text)::uuid
              from generate_series(1, 100) as user_number
            on conflict (id) do nothing;

            insert into public.account_export_jobs (
                account_id, idempotency_key_hash, request_id, status
            )
            select
                md5('account-export-queue-user-' || user_number::text)::uuid,
                md5('account-export-queue-key-a-' || user_number::text)
                    || md5('account-export-queue-key-b-' || user_number::text),
                'queue-limit-smoke-' || lpad(user_number::text, 4, '0'),
                'queued'
              from generate_series(1, 100) as user_number;
            """;
        await command.ExecuteNonQueryAsync();
    }

    private static async Task ClearAccountExportQueueCapacityAsync(string databaseConnectionString)
    {
        await using var dataSource = NpgsqlDataSource.Create(databaseConnectionString);
        await using var connection = await dataSource.OpenConnectionAsync();
        await using var command = connection.CreateCommand();
        command.CommandText = """
            delete from public.account_export_jobs where request_id like 'queue-limit-smoke-%';
            delete from auth.users
             where id in (
                 select md5('account-export-queue-user-' || user_number::text)::uuid
                   from generate_series(1, 100) as user_number
             );
            """;
        await command.ExecuteNonQueryAsync();
    }

    private static async Task SeedProjectAiExportFixtureAsync(string databaseConnectionString)
    {
        await using var dataSource = NpgsqlDataSource.Create(databaseConnectionString);
        await using var connection = await dataSource.OpenConnectionAsync();
        await using var command = connection.CreateCommand();
        command.CommandText = """
            insert into public.project_ai_consents (
                account_id, policy_version, granted, granted_at, revoked_at,
                consent_generation, updated_at
            ) values (
                @account_id, 'project-ai-data.v1', true, now(), null, 1, now()
            );

            insert into public.project_ai_consent_events (
                event_id, account_id, policy_version, decision, consent_generation, decided_at
            ) values (
                '99999999-0000-0000-0000-000000000995',
                @account_id, 'project-ai-data.v1', 'grant', 1, now()
            );

            insert into public.project_ai_activity (
                activity_id, account_id, installation_id, request_id, mode, outcome
            ) values (
                '99999999-0000-0000-0000-000000000996',
                @account_id,
                '99999999-0000-0000-0000-000000000997',
                'export_activity_req_0001',
                'GENERAL',
                'PENDING'
            );

            insert into public.ai_conversation_sessions (
                session_id, account_id, creation_request_id, case_version_id,
                context_fingerprint, turn_count, created_at, updated_at, expires_at
            ) values (
                '99999999-0000-0000-0000-000000000998',
                @account_id,
                'export_session_req_0001',
                'M0_T2:1',
                repeat('a', 64),
                1,
                now(),
                now(),
                now() + interval '1 day'
            );

            insert into public.ai_conversation_turn_requests (
                account_id, session_id, request_id, request_hash, turn_index,
                status, started_at, lease_expires_at, completed_at
            ) values (
                @account_id,
                '99999999-0000-0000-0000-000000000998',
                'export_turn_req_0001',
                repeat('b', 64),
                1,
                'completed',
                now(),
                now() + interval '1 minute',
                now()
            );
            """;
        command.Parameters.AddWithValue("account_id", AccountId);
        await command.ExecuteNonQueryAsync();
    }

    private static async Task<Guid> CreateAccountExportAsync(
        HttpClient client,
        Guid accountId,
        string idempotencyKey)
    {
        using var created = await SendAccountExportAsync(
            client,
            HttpMethod.Post,
            "/v2/account/exports",
            accountId,
            idempotencyKey);
        RequireStatus(created, HttpStatusCode.Accepted, "account export creation");
        if (created.Headers.CacheControl?.NoStore != true)
            throw new InvalidOperationException("Account export job response was cacheable.");
        var body = await ReadJsonAsync(created);
        RequireString(body, "schema", "evidrilo.account-export-job");
        RequireString(body, "version", "2");
        if (!Guid.TryParse(body.GetProperty("exportId").GetString(), out var exportId))
            throw new InvalidOperationException("Account export job returned an invalid opaque ID.");
        return exportId;
    }

    private static async Task<HttpResponseMessage> SendAccountExportAsync(
        HttpClient client,
        HttpMethod method,
        string path,
        Guid accountId,
        string? idempotencyKey = null)
    {
        using var request = new HttpRequestMessage(method, path);
        request.Headers.Add("X-Test-User", $"{accountId}|true");
        if (idempotencyKey is not null)
            request.Headers.Add("Idempotency-Key", idempotencyKey);
        return await client.SendAsync(request);
    }

    private static bool ContainsForbiddenExportFields(JsonElement element)
    {
        if (element.ValueKind == JsonValueKind.Array)
            return element.EnumerateArray().Any(ContainsForbiddenExportFields);
        if (element.ValueKind != JsonValueKind.Object)
            return false;

        foreach (var property in element.EnumerateObject())
        {
            if (property.Name is "prompt" or "prompts" or "response" or "responses"
                or "requestHash" or "settlementHash" or "activeRequestHash")
                return true;
            if (ContainsForbiddenExportFields(property.Value))
                return true;
        }
        return false;
    }

    private static async Task<HttpResponseMessage> SendAiAsync(
        HttpClient client,
        object body,
        string idempotencyKey)
    {
        using var request = new HttpRequestMessage(HttpMethod.Post, "/v1/ai/assist")
        {
            Content = JsonContent.Create(body),
        };
        request.Headers.Add("X-Test-User", $"{AccountId}|true");
        request.Headers.Add("Idempotency-Key", idempotencyKey);
        return await client.SendAsync(request);
    }

    private static async Task<HttpResponseMessage> SendConversationAsync(
        HttpClient client,
        HttpMethod method,
        string path,
        object? body,
        string? idempotencyKey = null,
        Guid? accountId = null)
    {
        using var request = new HttpRequestMessage(method, path);
        request.Headers.Add("X-Test-User", $"{accountId ?? AccountId}|true");
        if (idempotencyKey is not null) request.Headers.Add("Idempotency-Key", idempotencyKey);
        if (body is not null) request.Content = JsonContent.Create(body);
        return await client.SendAsync(request);
    }

    private static async Task AssertAiConversationFlowAsync(string databaseConnectionString, string repositoryRoot)
    {
        using var factory = new E2eApiFactory(databaseConnectionString, repositoryRoot, useTestAiProvider: true);
        using var client = factory.CreateClient();
        var startBody = ConversationStartBody();
        using var startA = await SendConversationAsync(
            client,
            HttpMethod.Post,
            "/v1/ai/conversations",
            startBody,
            "conversation-start-race-001");
        using var startB = await SendConversationAsync(
            client,
            HttpMethod.Post,
            "/v1/ai/conversations",
            startBody,
            "conversation-start-race-001");
        RequireStatus(startA, HttpStatusCode.OK, "AI conversation start");
        RequireStatus(startB, HttpStatusCode.OK, "idempotent AI conversation start");
        var start = await ReadJsonAsync(startA);
        var replayStart = await ReadJsonAsync(startB);
        RequireString(start, "schema", "evidrilo.ai-conversation-session");
        RequireNumber(start, "turnLimit", 5);
        RequireString(replayStart, "sessionId", start.GetProperty("sessionId").GetString()!);
        var sessionId = start.GetProperty("sessionId").GetString()!;

        using (var reused = await SendConversationAsync(
                   client,
                   HttpMethod.Post,
                   "/v1/ai/conversations",
                   ConversationStartBody(claimText: "Changed claim must invalidate an idempotency key."),
                   "conversation-start-race-001"))
        {
            RequireStatus(reused, HttpStatusCode.Conflict, "AI conversation start key reuse");
            RequireString(await ReadJsonAsync(reused), "code", "AI_IDEMPOTENCY_KEY_REUSE");
        }

        using var clearRaceStart = await SendConversationAsync(
            client,
            HttpMethod.Post,
            "/v1/ai/conversations",
            startBody,
            "conversation-clear-race-001");
        RequireStatus(clearRaceStart, HttpStatusCode.OK, "AI conversation start before clear race");
        var clearRaceSessionBody = await ReadJsonAsync(clearRaceStart);
        var clearRaceSessionId = Guid.Parse(clearRaceSessionBody.GetProperty("sessionId").GetString()!);
        var clearRaceStore = factory.Services.GetRequiredService<IAiConversationStore>();
        var activeClearRace = await clearRaceStore.ReserveTurnAsync(
            AccountId,
            clearRaceSessionId,
            "clear_race_turn_001",
            new string('a', 64),
            clearRaceSessionBody.GetProperty("contextFingerprint").GetString()!,
            CancellationToken.None);
        using (var clearDuringTurn = await SendConversationAsync(
                   client,
                   HttpMethod.Delete,
                   $"/v1/ai/conversations/{clearRaceSessionId:D}",
                   body: null))
        {
            RequireStatus(clearDuringTurn, HttpStatusCode.Conflict, "clear during active AI turn");
            RequireString(await ReadJsonAsync(clearDuringTurn), "code", "AI_REQUEST_IN_PROGRESS");
        }
        if (!await clearRaceStore.CompleteTurnAsync(
                AccountId,
                activeClearRace,
                accepted: false,
                CancellationToken.None))
            throw new InvalidOperationException("A rejected in-flight conversation turn could not be released after clear was refused.");

        var concurrentSession = await StartConversationAsync(client, "conversation-concurrent-001");
        var concurrentTurnBody = ConversationTurnBody([]);
        var concurrentTurns = await Task.WhenAll(
            Enumerable.Range(0, 2).Select(_ => SendConversationAsync(
                client,
                HttpMethod.Post,
                $"/v1/ai/conversations/{concurrentSession}/turns",
                concurrentTurnBody,
                "conversation-turn-race-001")));
        try
        {
            var acceptedCount = 0;
            var conflictCount = 0;
            foreach (var response in concurrentTurns)
            {
                if (response.StatusCode == HttpStatusCode.OK)
                {
                    acceptedCount++;
                    var body = await ReadJsonAsync(response);
                    RequireString(body, "status", "success");
                    RequireNumber(body, "turnsUsed", 1);
                    if (body.GetProperty("autoApplied").GetBoolean())
                        throw new InvalidOperationException("AI proposal was applied automatically.");
                }
                else if (response.StatusCode == HttpStatusCode.Conflict)
                {
                    conflictCount++;
                    var body = await ReadJsonAsync(response);
                    if (body.GetProperty("code").GetString() is not ("AI_REQUEST_IN_PROGRESS" or "AI_IDEMPOTENCY_REPLAY"))
                        throw new InvalidOperationException("Concurrent conversation replay returned the wrong conflict.");
                }
                else
                {
                    throw new InvalidOperationException($"Unexpected concurrent turn status: {response.StatusCode}.");
                }
            }
            if (acceptedCount != 1 || conflictCount != 1)
                throw new InvalidOperationException("Concurrent duplicate conversation turn was not serialized.");
        }
        finally
        {
            foreach (var response in concurrentTurns) response.Dispose();
        }

        using (var foreign = await SendConversationAsync(
                   client,
                   HttpMethod.Post,
                   $"/v1/ai/conversations/{sessionId}/turns",
                   ConversationTurnBody([]),
                   "conversation-foreign-001",
                   OtherAccountId))
        {
            RequireStatus(foreign, HttpStatusCode.NotFound, "cross-account conversation access");
            RequireString(await ReadJsonAsync(foreign), "code", "AI_CONVERSATION_NOT_FOUND");
        }

        var history = new List<object>();
        for (var index = 1; index <= 5; index++)
        {
            var boundedHistory = history.TakeLast(AiConversationPrompt.MaxHistoryMessages).ToArray();
            using var turn = await SendConversationAsync(
                client,
                HttpMethod.Post,
                $"/v1/ai/conversations/{sessionId}/turns",
                ConversationTurnBody(boundedHistory),
                $"conversation-turn-{index:000}");
            RequireStatus(turn, HttpStatusCode.OK, $"AI conversation turn {index}");
            var body = await ReadJsonAsync(turn);
            RequireString(body, "status", "success");
            RequireString(body, "kind", "draft_proposal");
            RequireNumber(body, "turnsUsed", index);
            RequireNumber(body, "turnsRemaining", 5 - index);
            if (body.GetProperty("autoApplied").GetBoolean())
                throw new InvalidOperationException("A typed AI proposal mutated the project without user action.");
            var proposal = body.GetProperty("proposal");
            RequireString(proposal, "field", "claim_scope");
            RequireString(proposal, "beforeValue", "LIMITED_COMPARISON");
            RequireString(proposal, "suggestedValue", "OBSERVED_COMPARISON_ONLY");
            history.Add(new { role = "user", text = $"Help with revision step {index}.", groundedAnchorIds = Array.Empty<string>() });
            history.Add(new { role = "assistant", text = body.GetProperty("text").GetString(), groundedAnchorIds = new[] { "OBS-E2E-01" } });
        }

        using (var limit = await SendConversationAsync(
                   client,
                   HttpMethod.Post,
                   $"/v1/ai/conversations/{sessionId}/turns",
                   ConversationTurnBody(history.TakeLast(AiConversationPrompt.MaxHistoryMessages).ToArray()),
                   "conversation-turn-006"))
        {
            RequireStatus(limit, HttpStatusCode.Conflict, "bounded conversation turn limit");
            RequireString(await ReadJsonAsync(limit), "code", "AI_CONVERSATION_TURN_LIMIT");
        }

        using (var stale = await SendConversationAsync(
                   client,
                   HttpMethod.Post,
                   $"/v1/ai/conversations/{sessionId}/turns",
                   ConversationTurnBody([], claimScope: "CHANGED_DRAFT"),
                   "conversation-stale-001"))
        {
            RequireStatus(stale, HttpStatusCode.Conflict, "stale conversation context");
            RequireString(await ReadJsonAsync(stale), "code", "AI_CONTEXT_STALE");
        }

        await AssertConversationMetadataAsync(databaseConnectionString, sessionId, expectedTurns: 5);

        using (var cleared = await SendConversationAsync(
                   client,
                   HttpMethod.Delete,
                   $"/v1/ai/conversations/{sessionId}",
                   body: null))
        {
            RequireStatus(cleared, HttpStatusCode.OK, "clear AI conversation");
            RequireString(await ReadJsonAsync(cleared), "status", "cleared");
        }
        using (var clearConcurrent = await SendConversationAsync(
                   client,
                   HttpMethod.Delete,
                   $"/v1/ai/conversations/{concurrentSession}",
                   body: null))
        {
            RequireStatus(clearConcurrent, HttpStatusCode.OK, "clear concurrent AI conversation");
        }
        using (var clearRace = await SendConversationAsync(
                   client,
                   HttpMethod.Delete,
                   $"/v1/ai/conversations/{clearRaceSessionId:D}",
                   body: null))
        {
            RequireStatus(clearRace, HttpStatusCode.OK, "clear conversation after turn release");
        }

        await AssertConversationMetadataDeletedAsync(databaseConnectionString, sessionId);
        await AssertConversationMetadataDeletedAsync(databaseConnectionString, clearRaceSessionId.ToString("D"));
        Console.WriteLine("EVIDRILO_AI_CONTEXTUAL_CONVERSATION_PROPOSAL_E2E_PASS");
    }

    private static async Task<string> StartConversationAsync(HttpClient client, string idempotencyKey)
    {
        using var response = await SendConversationAsync(
            client,
            HttpMethod.Post,
            "/v1/ai/conversations",
            ConversationStartBody(),
            idempotencyKey);
        RequireStatus(response, HttpStatusCode.OK, "start isolated AI conversation");
        return (await ReadJsonAsync(response)).GetProperty("sessionId").GetString()!;
    }

    private static async Task AssertAiProviderSpendBudgetAsync(string databaseConnectionString)
    {
        using var store = new NpgsqlAiProviderSpendBudgetStore(databaseConnectionString);
        var requests = new[]
        {
            new AiProviderSpendReservation(AccountId, "spend_e2e_primary_001", "synthetic-pinned-model", 0.40m, 1.00m, TimeSpan.FromSeconds(30)),
            new AiProviderSpendReservation(OtherAccountId, "spend_e2e_primary_002", "synthetic-pinned-model", 0.40m, 1.00m, TimeSpan.FromSeconds(30)),
            new AiProviderSpendReservation(ReviewerAccountId, "spend_e2e_primary_003", "synthetic-pinned-model", 0.40m, 1.00m, TimeSpan.FromSeconds(30)),
        };
        var reservationStatuses = await Task.WhenAll(requests.Select(request =>
            store.TryReserveAsync(request, CancellationToken.None)));
        var acceptedRequests = requests
            .Zip(reservationStatuses)
            .Where(pair => pair.Second == AiProviderBudgetReservationStatus.Reserved)
            .Select(pair => pair.First)
            .ToArray();
        if (acceptedRequests.Length != 2
            || reservationStatuses.Count(status => status == AiProviderBudgetReservationStatus.BudgetExceeded) != 1)
            throw new InvalidOperationException("Concurrent provider reservations overspent the monthly budget.");

        foreach (var request in acceptedRequests)
        {
            if (!await store.CompleteAsync(
                    request.AccountId,
                    request.RequestId,
                    actualCostUsd: 0.10m,
                    inputTokens: 10,
                    cachedInputTokens: 2,
                    cacheWriteInputTokens: 3,
                    outputTokens: 20,
                    reasoningTokens: 5,
                    uncertain: false,
                    released: false,
                    CancellationToken.None))
                throw new InvalidOperationException("Provider spend usage could not be settled.");
        }

        await using (var connection = new NpgsqlConnection(databaseConnectionString))
        {
            await connection.OpenAsync();
            await using var usage = connection.CreateCommand();
            usage.CommandText = """
                select input_tokens, cached_input_tokens, cache_write_input_tokens,
                       output_tokens, reasoning_tokens
                  from public.ai_provider_spend_reservations
                 where provider = 'openai' and account_id = @account_id and request_id = @request_id;
                """;
            usage.Parameters.AddWithValue("account_id", acceptedRequests[0].AccountId);
            usage.Parameters.AddWithValue("request_id", acceptedRequests[0].RequestId);
            await using var reader = await usage.ExecuteReaderAsync();
            if (!await reader.ReadAsync()
                || reader.GetInt32(0) != 10
                || reader.GetInt32(1) != 2
                || reader.GetInt32(2) != 3
                || reader.GetInt32(3) != 20
                || reader.GetInt32(4) != 5)
                throw new InvalidOperationException("Provider token categories were not persisted exactly.");
        }

        var replay = await store.TryReserveAsync(acceptedRequests[0], CancellationToken.None);
        if (replay != AiProviderBudgetReservationStatus.Replay)
            throw new InvalidOperationException("A settled provider request key was not replay-safe.");

        const string sharedRequestId = "spend_e2e_shared_key_001";
        var sharedAccountRequest = new AiProviderSpendReservation(
            AccountId, sharedRequestId, "synthetic-pinned-model", 0.15m, 1.00m, TimeSpan.FromSeconds(30));
        var sharedOtherAccountRequest = sharedAccountRequest with { AccountId = OtherAccountId };
        var sharedStatuses = await Task.WhenAll(
            store.TryReserveAsync(sharedAccountRequest, CancellationToken.None),
            store.TryReserveAsync(sharedOtherAccountRequest, CancellationToken.None));
        if (sharedStatuses.Any(status => status != AiProviderBudgetReservationStatus.Reserved))
            throw new InvalidOperationException("Provider spend request identifiers were not account-scoped.");
        foreach (var request in new[] { sharedAccountRequest, sharedOtherAccountRequest })
        {
            if (!await store.CompleteAsync(
                    request.AccountId,
                    request.RequestId,
                    actualCostUsd: 0.05m,
                    inputTokens: 5,
                    cachedInputTokens: 0,
                    cacheWriteInputTokens: 0,
                    outputTokens: 5,
                    reasoningTokens: 0,
                    uncertain: false,
                    released: false,
                    CancellationToken.None))
                throw new InvalidOperationException("Account-scoped provider usage was not settled.");
        }

        var releasedRequest = new AiProviderSpendReservation(
            ReviewerAccountId, "spend_e2e_release_001", "synthetic-pinned-model", 0.20m, 1.00m, TimeSpan.FromSeconds(30));
        if (await store.TryReserveAsync(releasedRequest, CancellationToken.None)
            != AiProviderBudgetReservationStatus.Reserved
            || !await store.CompleteAsync(
                releasedRequest.AccountId,
                releasedRequest.RequestId,
                actualCostUsd: 0,
                inputTokens: null,
                cachedInputTokens: null,
                cacheWriteInputTokens: null,
                outputTokens: null,
                reasoningTokens: null,
                uncertain: false,
                released: true,
                CancellationToken.None))
            throw new InvalidOperationException("A pre-provider cancellation did not release its spend reservation.");

        var expiredRequest = new AiProviderSpendReservation(
            ReviewerAccountId, "spend_e2e_expired_001", "synthetic-pinned-model", 0.50m, 1.00m, TimeSpan.FromSeconds(30));
        if (await store.TryReserveAsync(expiredRequest, CancellationToken.None)
            != AiProviderBudgetReservationStatus.Reserved)
            throw new InvalidOperationException("Could not create the provider lease-recovery fixture.");

        await using (var connection = new NpgsqlConnection(databaseConnectionString))
        {
            await connection.OpenAsync();
            await using var expire = connection.CreateCommand();
            expire.CommandText = """
                update public.ai_provider_spend_reservations
                   set lease_expires_at = now() - interval '1 second'
                 where provider = 'openai' and account_id = @account_id and request_id = @request_id;
                """;
            expire.Parameters.AddWithValue("account_id", ReviewerAccountId);
            expire.Parameters.AddWithValue("request_id", expiredRequest.RequestId);
            if (await expire.ExecuteNonQueryAsync() != 1)
                throw new InvalidOperationException("Could not expire the provider spend lease fixture.");
        }

        var overBudget = new AiProviderSpendReservation(
            OtherAccountId, "spend_e2e_after_expiry_001", "synthetic-pinned-model", 0.21m, 1.00m, TimeSpan.FromSeconds(30));
        if (await store.TryReserveAsync(overBudget, CancellationToken.None)
            != AiProviderBudgetReservationStatus.BudgetExceeded)
            throw new InvalidOperationException("An abandoned provider lease was not charged conservatively before new spend.");

        await using (var connection = new NpgsqlConnection(databaseConnectionString))
        {
            await connection.OpenAsync();
            await using var verify = connection.CreateCommand();
            verify.CommandText = """
                select reservation.status, reservation.actual_cost_usd,
                       budget.reserved_usd, budget.spent_usd
                  from public.ai_provider_spend_reservations reservation
                  join public.ai_provider_monthly_spend budget
                    on budget.provider = reservation.provider
                   and budget.period_start = reservation.period_start
                 where reservation.provider = 'openai'
                   and reservation.account_id = @account_id
                   and reservation.request_id = @request_id;
                """;
            verify.Parameters.AddWithValue("account_id", ReviewerAccountId);
            verify.Parameters.AddWithValue("request_id", expiredRequest.RequestId);
            await using var reader = await verify.ExecuteReaderAsync();
            if (!await reader.ReadAsync()
                || reader.GetString(0) != "uncertain"
                || reader.GetDecimal(1) != 0.50m
                || reader.GetDecimal(2) != 0m
                || reader.GetDecimal(3) != 0.80m)
                throw new InvalidOperationException("Expired provider spend was not preserved as conservative monthly usage.");
        }

        Console.WriteLine("EVIDRILO_AI_PROVIDER_MONTHLY_SPEND_BUDGET_E2E_PASS");
    }

    private static object ConversationStartBody(string claimText = "The observed comparison supports a bounded claim.") => new
    {
        optedIn = true,
        locale = "en-US",
        learnerLimitation = "Each condition was measured once.",
        context = ConversationContext(claimText),
    };

    private static object ConversationTurnBody(
        IReadOnlyList<object> history,
        string claimScope = "LIMITED_COMPARISON") => new
    {
        purpose = "language_alternative",
        input = "Suggest a narrower way to express the claim without adding evidence.",
        locale = "en-US",
        optedIn = true,
        learnerLimitation = "Each condition was measured once.",
        context = ConversationContext("The observed comparison supports a bounded claim.", claimScope),
        history,
    };

    private static object ConversationContext(
        string claimText,
        string claimScope = "LIMITED_COMPARISON") => new
    {
        caseVersionId = CaseVersionId,
        feedbackCode = "MISSING_EVIDENCE",
        feedbackStatus = "ACTION_REQUIRED",
        anchorIds = new[] { "OBS-E2E-01" },
        limitationIds = new[] { "LIMIT-E2E-01" },
        claimText,
        claimScope,
        nextAction = "State the limitation before revising.",
    };

    private static async Task AssertConversationMetadataAsync(
        string connectionString,
        string sessionId,
        int expectedTurns)
    {
        await using var dataSource = NpgsqlDataSource.Create(connectionString);
        await using var connection = await dataSource.OpenConnectionAsync();
        await using var command = connection.CreateCommand();
        command.CommandText = """
            select session.turn_count,
                   count(turn.request_id),
                   exists (
                       select 1 from information_schema.columns
                       where table_schema = 'public'
                         and table_name in ('ai_conversation_sessions', 'ai_conversation_turn_requests')
                         and column_name ~ '(prompt|response|transcript|message)'
                   )
            from public.ai_conversation_sessions as session
            left join public.ai_conversation_turn_requests as turn
              on turn.account_id = session.account_id and turn.session_id = session.session_id
            where session.session_id = @session_id
            group by session.turn_count;
            """;
        command.Parameters.AddWithValue("session_id", Guid.Parse(sessionId));
        await using var reader = await command.ExecuteReaderAsync();
        if (!await reader.ReadAsync()
            || reader.GetInt16(0) != expectedTurns
            || reader.GetInt64(1) != expectedTurns
            || reader.GetBoolean(2))
            throw new InvalidOperationException("Conversation metadata is not bounded, complete, or transcript-free.");
    }

    private static async Task AssertConversationMetadataDeletedAsync(string connectionString, string sessionId)
    {
        await using var dataSource = NpgsqlDataSource.Create(connectionString);
        await using var connection = await dataSource.OpenConnectionAsync();
        await using var command = connection.CreateCommand();
        command.CommandText = """
            select
                (select count(*) from public.ai_conversation_sessions where session_id = @session_id),
                (select count(*) from public.ai_conversation_turn_requests where session_id = @session_id);
            """;
        command.Parameters.AddWithValue("session_id", Guid.Parse(sessionId));
        await using var reader = await command.ExecuteReaderAsync();
        if (!await reader.ReadAsync() || reader.GetInt64(0) != 0 || reader.GetInt64(1) != 0)
            throw new InvalidOperationException("Clearing a conversation did not delete its session metadata and turn records.");
    }

    private static async Task AssertPublishedCaseEvidenceFlowAsync(HttpClient client)
    {
        using var catalogue = await SendAsync(
            client,
            HttpMethod.Get,
            "/v1/cases",
            AccountId,
            body: null);
        RequireStatus(catalogue, HttpStatusCode.OK, "published case catalogue");
        var catalogueBody = await ReadJsonAsync(catalogue);
        RequireString(catalogueBody, "schema", "evidrilo.case-catalogue");
        RequireString(catalogueBody, "version", "1");
        var cases = catalogueBody.GetProperty("cases");
        if (cases.GetArrayLength() != 1)
        {
            throw new InvalidOperationException(
                "The published case catalogue exposed a draft or omitted the seeded published case.");
        }

        var publishedSummary = cases[0];
        RequireString(publishedSummary, "caseVersionId", CaseVersionId);
        RequireString(publishedSummary, "contentHash", CaseContentHash);
        RequireString(publishedSummary, "objective", "Connect evidence to a bounded claim");
        if (publishedSummary.GetProperty("difficulty").GetInt32() != 2)
        {
            throw new InvalidOperationException("The published case catalogue returned the wrong difficulty.");
        }

        using var summary = await SendAsync(
            client,
            HttpMethod.Get,
            $"/v1/cases/{CaseVersionId}",
            AccountId,
            body: null);
        RequireStatus(summary, HttpStatusCode.OK, "published case summary");
        var summaryBody = await ReadJsonAsync(summary);
        RequireString(summaryBody, "schema", "evidrilo.case-summary");
        RequireString(summaryBody, "caseVersionId", CaseVersionId);
        if (summaryBody.GetProperty("facts").GetArrayLength() != 2
            || summaryBody.GetProperty("rules").GetArrayLength() != 1
            || summaryBody.GetProperty("variants").GetArrayLength() != 1)
        {
            throw new InvalidOperationException("The published case summary did not return canonical learning content.");
        }

        using var graph = await SendAsync(
            client,
            HttpMethod.Get,
            $"/v1/cases/{CaseVersionId}/evidence-graph",
            AccountId,
            body: null);
        RequireStatus(graph, HttpStatusCode.OK, "published evidence graph");
        var graphBody = await ReadJsonAsync(graph);
        RequireString(graphBody, "schema", "evidrilo.evidence-graph");
        RequireString(graphBody, "caseVersionId", CaseVersionId);
        if (graphBody.GetProperty("nodes").GetArrayLength() != 5
            || graphBody.GetProperty("edges").GetArrayLength() != 6)
        {
            throw new InvalidOperationException("The published evidence graph did not materialize the seeded relationships.");
        }

        using var draftSummary = await SendAsync(
            client,
            HttpMethod.Get,
            $"/v1/cases/{DraftCaseVersionId}",
            AccountId,
            body: null);
        RequireStatus(draftSummary, HttpStatusCode.NotFound, "draft case protection");
        RequireString(await ReadJsonAsync(draftSummary), "code", "CASE_NOT_FOUND");

        using var draftGraph = await SendAsync(
            client,
            HttpMethod.Get,
            $"/v1/cases/{DraftCaseVersionId}/evidence-graph",
            AccountId,
            body: null);
        RequireStatus(draftGraph, HttpStatusCode.NotFound, "draft evidence graph protection");
        RequireString(await ReadJsonAsync(draftGraph), "code", "CASE_NOT_FOUND");
    }

    private static async Task AssertProjectTemplateCatalogFlowAsync(
        string databaseConnectionString,
        string repositoryRoot)
    {
        using var factory = new E2eApiFactory(databaseConnectionString, repositoryRoot);
        using var client = factory.CreateClient();
        const string templateId = "e2e-literature-review-template";
        const string family = "literature_review";

        async Task<HttpResponseMessage> CreateDraftAsync(
            int version,
            string id,
            string templateFamily,
            string title) => await SendAsync(
            client,
            HttpMethod.Post,
            "/v1/authoring/project-templates",
            AccountId,
            new
            {
                schema = "evidrilo.project-template-draft",
                version = "1",
                organizationId = OrganizationId,
                templateId = id,
                templateVersion = version,
                family = templateFamily,
                template = new
                {
                    title,
                    summary = "Compare a defined set of sources against one focused question.",
                    intendedOutput = "A source-to-claim outline with stated limits.",
                    inputFields = new[]
                    {
                        new { id = "question", kind = "research_question", label = "Research question", required = true },
                    },
                    steps = new[]
                    {
                        new { id = "frame-question", title = "Frame the question", inputFieldIds = new[] { "question" } },
                    },
                    methodSpecificLimitations = new[] { "A selected source set is not a complete census of the field." },
                    provenanceRequirements = new[] { "Record each source identifier and retrieval location." },
                    accessibilityExpectations = new[] { "Use descriptive labels and preserve a logical reading order." },
                    examples = new[]
                    {
                        new { id = "example-normal", summary = "Synthetic bounded source-to-claim comparison.", reviewed = false, kind = "normal" },
                        new { id = "example-edge", summary = "Synthetic conflicting-source boundary case.", reviewed = false, kind = "edge_or_conflicting" },
                    },
                },
            });

        async Task<HttpResponseMessage> TransitionAsync(
            Guid actorId,
            string id,
            int version,
            string targetState,
            string? reason,
            string[]? reviewedExampleIds = null) => await SendAsync(
            client,
            HttpMethod.Post,
            $"/v1/authoring/project-templates/{id}/versions/{version}/transition",
            actorId,
            new
            {
                schema = "evidrilo.project-template-transition",
                version = "1",
                targetState,
                reason,
                reviewedExampleIds,
            });

        using (var created = await CreateDraftAsync(1, templateId, family, "Bounded literature review"))
        {
            RequireStatus(created, HttpStatusCode.Created, "project-template draft creation");
            var body = await ReadJsonAsync(created);
            RequireString(body, "schema", "evidrilo.project-template-operation");
            RequireString(body, "templateId", templateId);
            RequireString(body, "state", "draft");
        }

        using (var hiddenDraft = await client.GetAsync($"/v1/project-templates/{templateId}/versions/1"))
        {
            RequireStatus(hiddenDraft, HttpStatusCode.NotFound, "project-template draft protection");
            RequireString(await ReadJsonAsync(hiddenDraft), "code", "PROJECT_TEMPLATE_NOT_FOUND");
        }

        using (var sentToReview = await TransitionAsync(
            AccountId,
            templateId,
            1,
            "review",
            "Ready for independent method review."))
        {
            RequireStatus(sentToReview, HttpStatusCode.OK, "project-template submission for review");
            RequireString(await ReadJsonAsync(sentToReview), "state", "review");
        }

        using (var selfApproval = await TransitionAsync(
            AccountId,
            templateId,
            1,
            "approved",
            "The author cannot review their own template.",
            ["example-normal", "example-edge"]))
        {
            RequireStatus(selfApproval, HttpStatusCode.Conflict, "project-template self-review protection");
            RequireString(await ReadJsonAsync(selfApproval), "code", "TEMPLATE_AUTHOR_CANNOT_REVIEW_OWN");
        }

        using (var approved = await TransitionAsync(
            ReviewerAccountId,
            templateId,
            1,
            "approved",
            "The structure and normal and edge example anchors were independently reviewed.",
            ["example-normal", "example-edge"]))
        {
            RequireStatus(approved, HttpStatusCode.OK, "independent project-template approval");
            RequireString(await ReadJsonAsync(approved), "state", "approved");
        }

        using (var published = await TransitionAsync(
            MaintainerAccountId,
            templateId,
            1,
            "published",
            "Approved synthetic template published for local catalog verification."))
        {
            RequireStatus(published, HttpStatusCode.OK, "project-template publication");
            RequireString(await ReadJsonAsync(published), "state", "published");
        }

        using (var publicDetail = await client.GetAsync($"/v1/project-templates/{templateId}/versions/1"))
        {
            RequireStatus(publicDetail, HttpStatusCode.OK, "published project-template detail");
            var body = await ReadJsonAsync(publicDetail);
            var template = body.GetProperty("template");
            RequireString(template, "publication", "published");
            if (!template.GetProperty("examples")[0].GetProperty("reviewed").GetBoolean()
                || template.TryGetProperty("organizationId", out _)
                || template.TryGetProperty("authorId", out _)
                || template.TryGetProperty("reviewerId", out _))
            {
                throw new InvalidOperationException(
                    "The public project-template detail leaked private identity or omitted server-owned review metadata.");
            }
        }

        using (var secondVersion = await CreateDraftAsync(2, templateId, family, "Bounded literature review"))
        {
            RequireStatus(secondVersion, HttpStatusCode.Created, "sequential project-template version creation");
            if ((await ReadJsonAsync(secondVersion)).GetProperty("templateVersion").GetInt32() != 2)
                throw new InvalidOperationException("The project-template catalog did not create the next sequential version.");
        }

        using (var secondReview = await TransitionAsync(AccountId, templateId, 2, "review", "Submit revised template."))
            RequireStatus(secondReview, HttpStatusCode.OK, "second project-template review submission");
        using (var secondApproval = await TransitionAsync(
            ReviewerAccountId,
            templateId,
            2,
            "approved",
            "The revised structure and both scenario kinds were independently reviewed.",
            ["example-normal", "example-edge"]))
            RequireStatus(secondApproval, HttpStatusCode.OK, "second project-template approval");
        using (var secondPublication = await TransitionAsync(
            MaintainerAccountId,
            templateId,
            2,
            "published",
            "Supersede the prior synthetic catalog version."))
            RequireStatus(secondPublication, HttpStatusCode.OK, "second project-template publication");

        using (var retiredVersion = await client.GetAsync($"/v1/project-templates/{templateId}/versions/1"))
            RequireStatus(retiredVersion, HttpStatusCode.NotFound, "superseded project-template version is not current-public");

        const string secondTemplateId = "e2e-observational-survey-template";
        using (var secondTemplate = await CreateDraftAsync(
            1,
            secondTemplateId,
            "observational_survey",
            "Bounded observational survey"))
            RequireStatus(secondTemplate, HttpStatusCode.Created, "second catalog template draft creation");
        using (var secondTemplateReview = await TransitionAsync(
            AccountId,
            secondTemplateId,
            1,
            "review",
            "Ready for independent survey-method review."))
            RequireStatus(secondTemplateReview, HttpStatusCode.OK, "second catalog template review submission");
        using (var secondTemplateApproval = await TransitionAsync(
            ReviewerAccountId,
            secondTemplateId,
            1,
            "approved",
            "The survey structure and both scenario kinds were reviewed.",
            ["example-normal", "example-edge"]))
            RequireStatus(secondTemplateApproval, HttpStatusCode.OK, "second catalog template approval");
        using (var secondTemplatePublication = await TransitionAsync(
            MaintainerAccountId,
            secondTemplateId,
            1,
            "published",
            "Publish synthetic survey template for pagination verification."))
            RequireStatus(secondTemplatePublication, HttpStatusCode.OK, "second catalog template publication");

        using (var firstPage = await client.GetAsync("/v1/project-templates?limit=1"))
        {
            RequireStatus(firstPage, HttpStatusCode.OK, "first project-template catalog page");
            var body = await ReadJsonAsync(firstPage);
            var templates = body.GetProperty("templates");
            if (templates.GetArrayLength() != 1
                || templates[0].GetProperty("id").GetString() != templateId
                || body.GetProperty("nextAfterTemplateId").GetString() != templateId)
                throw new InvalidOperationException("The project-template catalog did not return an after-cursor for the next page.");
        }

        using (var secondPage = await client.GetAsync(
            $"/v1/project-templates?limit=1&afterTemplateId={templateId}"))
        {
            RequireStatus(secondPage, HttpStatusCode.OK, "next project-template catalog page");
            var body = await ReadJsonAsync(secondPage);
            var templates = body.GetProperty("templates");
            if (templates.GetArrayLength() != 1
                || templates[0].GetProperty("id").GetString() != secondTemplateId
                || body.GetProperty("nextAfterTemplateId").ValueKind != JsonValueKind.Null)
                throw new InvalidOperationException("The project-template catalog after-cursor skipped or repeated a template.");
        }

        using (var catalog = await client.GetAsync($"/v1/project-templates?family={family}"))
        {
            RequireStatus(catalog, HttpStatusCode.OK, "anonymous project-template catalog browse");
            var templates = (await ReadJsonAsync(catalog)).GetProperty("templates");
            if (templates.GetArrayLength() != 1
                || templates[0].GetProperty("version").GetInt32() != 2
                || templates[0].GetProperty("id").GetString() != templateId
                || templates[0].TryGetProperty("authorId", out _))
            {
                throw new InvalidOperationException(
                    "The public project-template catalog did not expose only the current version and safe fields.");
            }
        }

        Console.WriteLine("EVIDRILO_PROJECT_TEMPLATE_HTTP_POSTGRES_LIFECYCLE_E2E_PASS");
    }

    private static async Task AssertContentLifecycleFlowAsync(
        HttpClient client,
        string databaseConnectionString)
    {
        var authoringRequest = new
        {
            schema = "evidrilo.case-authoring",
            version = "1",
            organizationId = OrganizationId,
            document = new
            {
                content = new
                {
                    caseId = "case-e2e",
                    caseVersionId = CaseVersionId,
                    title = "Evidence graph integration case",
                    contentHash = CaseContentHash,
                    evaluatorVersion = "evidrilo.v1",
                    factAnchors = new[] { "OBS-E2E-01", "LIMIT-E2E-01" },
                    skillTags = new[] { "evidence" },
                },
                objective = "Connect evidence to a bounded claim",
                difficulty = 2,
                evidenceReferences = new[] { "OBS-E2E-01", "LIMIT-E2E-01" },
                facts = new[]
                {
                    new { id = "OBS-E2E-01", type = "observation", text = "Warm water reached the mark in 32 seconds." },
                    new { id = "LIMIT-E2E-01", type = "limitation", text = "Each condition was measured once." },
                },
                rules = new[]
                {
                    new { id = "RULE-E2E-01", outcome = "PASS", anchorIds = new[] { "OBS-E2E-01", "LIMIT-E2E-01" } },
                },
                variants = new[]
                {
                    new { id = "CHALLENGE-E2E-01", removedFactIds = new[] { "LIMIT-E2E-01" } },
                },
            },
        };

        using var created = await SendAsync(
            client,
            HttpMethod.Post,
            "/v1/authoring/cases",
            AccountId,
            authoringRequest);
        RequireStatus(created, HttpStatusCode.OK, "case draft creation");
        var createdBody = await ReadJsonAsync(created);
        RequireString(createdBody, "schema", "evidrilo.case-authoring-result");
        RequireString(createdBody, "caseVersionId", CaseVersionId);
        RequireString(createdBody, "state", "draft");

        using var duplicateCreate = await SendAsync(
            client,
            HttpMethod.Post,
            "/v1/authoring/cases",
            AccountId,
            authoringRequest);
        RequireStatus(duplicateCreate, HttpStatusCode.Conflict, "duplicate case draft creation");
        RequireString(await ReadJsonAsync(duplicateCreate), "code", "CASE_VERSION_EXISTS");

        using var hiddenDraft = await SendAsync(
            client,
            HttpMethod.Get,
            $"/v1/cases/{CaseVersionId}",
            AccountId,
            body: null);
        RequireStatus(hiddenDraft, HttpStatusCode.NotFound, "unpublished case protection");
        RequireString(await ReadJsonAsync(hiddenDraft), "code", "CASE_NOT_FOUND");

        using var sentToReview = await SendTransitionAsync(
            client,
            AccountId,
            CaseVersionId,
            "review",
            "Ready for independent review.");
        RequireStatus(sentToReview, HttpStatusCode.OK, "draft to review transition");
        RequireString(await ReadJsonAsync(sentToReview), "state", "review");

        using var selfApproval = await SendTransitionAsync(
            client,
            AccountId,
            CaseVersionId,
            "approved",
            "Author cannot approve own case.");
        RequireStatus(selfApproval, HttpStatusCode.Conflict, "self approval rejection");
        RequireString(await ReadJsonAsync(selfApproval), "code", "INVALID_CASE_TRANSITION");

        var concurrentApprovals = await Task.WhenAll(
            Enumerable.Range(0, 2).Select(_ => SendTransitionAsync(
                client,
                ReviewerAccountId,
                CaseVersionId,
                "approved",
                "Evidence and challenge content reviewed.")));
        try
        {
            if (concurrentApprovals.Count(response => response.StatusCode == HttpStatusCode.OK) != 1
                || concurrentApprovals.Count(response => response.StatusCode == HttpStatusCode.Conflict) != 1)
            {
                throw new InvalidOperationException(
                    "Concurrent case approvals did not produce exactly one commit and one fenced conflict.");
            }

            foreach (var approval in concurrentApprovals)
            {
                if (approval.StatusCode == HttpStatusCode.OK)
                {
                    RequireString(await ReadJsonAsync(approval), "state", "approved");
                }
                else
                {
                    RequireString(await ReadJsonAsync(approval), "code", "INVALID_CASE_TRANSITION");
                }
            }
        }
        finally
        {
            foreach (var approval in concurrentApprovals) approval.Dispose();
        }

        using var duplicateApproval = await SendTransitionAsync(
            client,
            ReviewerAccountId,
            CaseVersionId,
            "approved",
            "Duplicate review retry.");
        RequireStatus(duplicateApproval, HttpStatusCode.Conflict, "duplicate review transition");
        RequireString(await ReadJsonAsync(duplicateApproval), "code", "INVALID_CASE_TRANSITION");

        using var published = await SendTransitionAsync(
            client,
            MaintainerAccountId,
            CaseVersionId,
            "published",
            "Approved for the published catalogue.");
        RequireStatus(published, HttpStatusCode.OK, "approved to published transition");
        RequireString(await ReadJsonAsync(published), "state", "published");

        using var mutatePublished = await SendTransitionAsync(
            client,
            MaintainerAccountId,
            CaseVersionId,
            "draft",
            "Published versions cannot be rewritten.");
        RequireStatus(mutatePublished, HttpStatusCode.Conflict, "published immutability guard");
        RequireString(await ReadJsonAsync(mutatePublished), "code", "PUBLISHED_VERSION_IMMUTABLE");

        using var unauthorizedAudit = await SendAsync(
            client,
            HttpMethod.Get,
            $"/v1/authoring/cases/{CaseVersionId}/audit",
            OtherAccountId,
            body: null);
        RequireStatus(unauthorizedAudit, HttpStatusCode.Forbidden, "cross-account audit protection");
        RequireString(await ReadJsonAsync(unauthorizedAudit), "code", "MEMBERSHIP_REQUIRED");

        using var audit = await SendAsync(
            client,
            HttpMethod.Get,
            $"/v1/authoring/cases/{CaseVersionId}/audit",
            MaintainerAccountId,
            body: null);
        RequireStatus(audit, HttpStatusCode.OK, "case lifecycle audit read");
        var auditBody = await ReadJsonAsync(audit);
        RequireString(auditBody, "schema", "evidrilo.case-lifecycle-audit");
        var events = auditBody.GetProperty("events");
        if (events.GetArrayLength() != 4)
        {
            throw new InvalidOperationException("The lifecycle audit did not contain exactly four state events.");
        }

        RequireString(events[0], "eventType", "created");
        RequireString(events[0], "toState", "draft");
        RequireString(events[0], "reason", "draft_created");
        RequireActor(events[0], AccountId);
        if (events[0].GetProperty("fromState").ValueKind != JsonValueKind.Null)
            throw new InvalidOperationException("Draft creation unexpectedly had a previous state.");

        RequireTransitionEvent(events[1], AccountId, "draft", "review", "Ready for independent review.");
        RequireTransitionEvent(events[2], ReviewerAccountId, "review", "approved", "Evidence and challenge content reviewed.");
        RequireTransitionEvent(events[3], MaintainerAccountId, "approved", "published", "Approved for the published catalogue.");
        await AssertLifecycleDatabaseStateAsync(databaseConnectionString);
    }

    private static async Task<HttpResponseMessage> SendTransitionAsync(
        HttpClient client,
        Guid accountId,
        string caseVersionId,
        string targetState,
        string reason) => await SendAsync(
        client,
        HttpMethod.Post,
        $"/v1/authoring/cases/{caseVersionId}/transition",
        accountId,
        new
        {
            schema = "evidrilo.case-transition",
            version = "1",
            targetState,
            reason,
        });

    private static void RequireTransitionEvent(
        JsonElement auditEvent,
        Guid actorAccountId,
        string fromState,
        string toState,
        string reason)
    {
        RequireString(auditEvent, "eventType", "transitioned");
        RequireString(auditEvent, "fromState", fromState);
        RequireString(auditEvent, "toState", toState);
        RequireString(auditEvent, "reason", reason);
        RequireActor(auditEvent, actorAccountId);
    }

    private static void RequireActor(JsonElement auditEvent, Guid expected)
    {
        if (!auditEvent.TryGetProperty("actorAccountId", out var actor)
            || actor.GetGuid() != expected)
        {
            throw new InvalidOperationException("The lifecycle audit actor did not match the authorized transition actor.");
        }
    }

    private static async Task AssertLifecycleDatabaseStateAsync(string databaseConnectionString)
    {
        await using var dataSource = NpgsqlDataSource.Create(databaseConnectionString);
        await using var connection = await dataSource.OpenConnectionAsync();
        await using var versionCommand = connection.CreateCommand();
        versionCommand.CommandText = """
            select status, author_id, reviewer_id, organization_id,
                   (select count(*) from public.case_review_decisions
                    where case_version_id = @case_version_id),
                   (select count(*) from public.case_lifecycle_audit_events
                    where case_version_id = @case_version_id)
            from public.case_versions
            where case_version_id = @case_version_id;
            """;
        versionCommand.Parameters.AddWithValue("case_version_id", CaseVersionId);
        await using (var reader = await versionCommand.ExecuteReaderAsync())
        {
            if (!await reader.ReadAsync()
                || reader.GetString(0) != "published"
                || reader.GetGuid(1) != AccountId
                || reader.GetGuid(2) != ReviewerAccountId
                || reader.GetGuid(3) != OrganizationId
                || reader.GetInt64(4) != 1
                || reader.GetInt64(5) != 4)
            {
                throw new InvalidOperationException("The database lifecycle state did not match the server-owned flow.");
            }
        }

        await using var decisionCommand = connection.CreateCommand();
        decisionCommand.CommandText = """
            select decision, reviewer_id, reason
            from public.case_review_decisions
            where case_version_id = @case_version_id
            order by created_at, decision_id;
            """;
        decisionCommand.Parameters.AddWithValue("case_version_id", CaseVersionId);
        await using var decisionReader = await decisionCommand.ExecuteReaderAsync();
        if (!await decisionReader.ReadAsync()
            || decisionReader.GetString(0) != "approved"
            || decisionReader.GetGuid(1) != ReviewerAccountId
            || decisionReader.GetString(2) != "Evidence and challenge content reviewed."
            || await decisionReader.ReadAsync())
        {
            throw new InvalidOperationException("The database review decision was missing, duplicated, or misattributed.");
        }
    }

    private static async Task AssertSyncAndProjectionFlowAsync(HttpClient client)
    {
        var syncRequest = new
        {
            schema = "evidrilo.sync-push-request",
            version = "1",
            commands = new[]
            {
                new
                {
                    commandId = CommandId,
                    attemptId = AttemptId,
                    caseVersionId = CaseVersionId,
                    commandType = "attempt_submitted",
                    revisionNumber = 0,
                    clientOccurredAt = "2026-09-16T10:00:00Z",
                    snapshotDigest = SnapshotDigest,
                },
            },
        };

        using var accepted = await SendAsync(
            client,
            HttpMethod.Post,
            "/v1/sync/commands",
            AccountId,
            syncRequest);
        RequireStatus(accepted, HttpStatusCode.OK, "initial sync push");
        var acceptedBody = await ReadJsonAsync(accepted);
        RequireString(acceptedBody.GetProperty("results")[0], "outcome", "accepted");

        var concurrentSyncReplays = await Task.WhenAll(
            Enumerable.Range(0, 4).Select(_ => SendAsync(
                client,
                HttpMethod.Post,
                "/v1/sync/commands",
                AccountId,
                syncRequest)));
        try
        {
            foreach (var replay in concurrentSyncReplays)
            {
                RequireStatus(replay, HttpStatusCode.OK, "concurrent sync replay");
                RequireString(
                    (await ReadJsonAsync(replay)).GetProperty("results")[0],
                    "outcome",
                    "duplicate");
            }
        }
        finally
        {
            foreach (var replay in concurrentSyncReplays) replay.Dispose();
        }

        var analyticsRequest = new
        {
            schema = "evidrilo.analytics-event",
            version = "1",
            clientEventId = ClientEventId,
            eventName = "attempt_completed",
            eventVersion = 1,
            occurredAt = "2026-09-16T10:01:00Z",
            source = "mobile",
            consent = "granted",
            properties = new
            {
                attemptId = AttemptId,
                caseVersionId = CaseVersionId,
                outcome = "PASS",
            },
        };

        using var analytics = await SendAsync(
            client,
            HttpMethod.Post,
            "/v1/analytics/events",
            AccountId,
            analyticsRequest);
        RequireStatus(analytics, HttpStatusCode.OK, "analytics append");
        RequireString(await ReadJsonAsync(analytics), "outcome", "accepted");

        var concurrentAnalyticsReplays = await Task.WhenAll(
            Enumerable.Range(0, 4).Select(_ => SendAsync(
                client,
                HttpMethod.Post,
                "/v1/analytics/events",
                AccountId,
                analyticsRequest)));
        try
        {
            foreach (var replay in concurrentAnalyticsReplays)
            {
                RequireStatus(replay, HttpStatusCode.OK, "concurrent analytics replay");
                RequireString(await ReadJsonAsync(replay), "outcome", "duplicate");
            }
        }
        finally
        {
            foreach (var replay in concurrentAnalyticsReplays) replay.Dispose();
        }

        using var analyticsReplay = await SendAsync(
            client,
            HttpMethod.Post,
            "/v1/analytics/events",
            AccountId,
            analyticsRequest);
        RequireStatus(analyticsReplay, HttpStatusCode.OK, "analytics idempotent replay");
        RequireString(await ReadJsonAsync(analyticsReplay), "outcome", "duplicate");

        var projection = await WaitForProjectionAsync(client);
        RequireNumber(projection, "attemptsObserved", 1);
        RequireNumber(projection, "completedAttempts", 1);
        RequireNumber(projection, "passCount", 1);

        using var pull = await SendAsync(
            client,
            HttpMethod.Get,
            "/v1/sync/pull?cursor=0&limit=10",
            AccountId,
            body: null);
        if (pull.StatusCode != HttpStatusCode.OK)
        {
            throw new InvalidOperationException(
                $"sync pull returned {(int)pull.StatusCode}; expected 200; body={await pull.Content.ReadAsStringAsync()}");
        }
        var pullBody = await ReadJsonAsync(pull);
        RequireNumber(pullBody, "nextCursor", 1);
        if (pullBody.GetProperty("changes").GetArrayLength() != 1)
        {
            throw new InvalidOperationException(
                "The API sync pull did not return the accepted command.");
        }

        using var syncReplay = await SendAsync(
            client,
            HttpMethod.Post,
            "/v1/sync/commands",
            AccountId,
            syncRequest);
        RequireStatus(syncReplay, HttpStatusCode.OK, "sync idempotent replay");
        RequireString(
            (await ReadJsonAsync(syncReplay)).GetProperty("results")[0],
            "outcome",
            "duplicate");

        var reusedCommand = new
        {
            schema = "evidrilo.sync-push-request",
            version = "1",
            commands = new[]
            {
                new
                {
                    commandId = CommandId,
                    attemptId = AttemptId,
                    caseVersionId = CaseVersionId,
                    commandType = "attempt_submitted",
                    revisionNumber = 0,
                    clientOccurredAt = "2026-09-16T10:00:00Z",
                    snapshotDigest =
                        "fedcba9876543210fedcba9876543210fedcba9876543210fedcba9876543210",
                },
            },
        };
        using var reused = await SendAsync(
            client,
            HttpMethod.Post,
            "/v1/sync/commands",
            AccountId,
            reusedCommand);
        RequireStatus(reused, HttpStatusCode.OK, "sync idempotency-key reuse");
        var reusedBody = await ReadJsonAsync(reused);
        RequireString(reusedBody.GetProperty("results")[0], "outcome", "rejected");
        RequireString(
            reusedBody.GetProperty("results")[0],
            "reasonCode",
            "IDEMPOTENCY_KEY_REUSE");
    }

    private static async Task AssertIsolationAsync(HttpClient client)
    {
        using var isolatedPull = await SendAsync(
            client,
            HttpMethod.Get,
            "/v1/sync/pull?cursor=0&limit=10",
            OtherAccountId,
            body: null);
        RequireStatus(isolatedPull, HttpStatusCode.OK, "cross-account sync isolation");
        var body = await ReadJsonAsync(isolatedPull);
        if (body.GetProperty("changes").GetArrayLength() != 0)
        {
            throw new InvalidOperationException(
                "Cross-account sync isolation returned another account's change.");
        }
    }

    private static async Task AssertAccountDeletionOutboxFlowAsync(
        HttpClient client,
        string databaseConnectionString)
    {
        using (var missingConfirmation = await SendAccountDeletionAsync(
            client,
            confirmed: false,
            DeletionTestAccountId))
        {
            RequireStatus(
                missingConfirmation,
                HttpStatusCode.BadRequest,
                "account deletion requires explicit confirmation");
            RequireString(
                await ReadJsonAsync(missingConfirmation),
                "code",
                "ACCOUNT_DELETION_CONFIRMATION_REQUIRED");
        }

        using (var deletion = await SendAccountDeletionAsync(
            client,
            confirmed: true,
            DeletionTestAccountId))
        {
            RequireStatus(deletion, HttpStatusCode.OK, "account deletion transaction");
            RequireString(await ReadJsonAsync(deletion), "outcome", "accepted");
        }

        await using (var dataSource = NpgsqlDataSource.Create(databaseConnectionString))
        await using (var connection = await dataSource.OpenConnectionAsync())
        await using (var verify = connection.CreateCommand())
        {
            verify.CommandText = """
                select
                    (select count(*) from public.account_deletion_tombstones where account_id = @account_id),
                    (select count(*) from public.account_auth_deletion_outbox
                      where account_id = @account_id and status = 'queued'),
                    (select count(*) from public.student_projects where account_id = @account_id),
                    (select count(*) from public.student_project_cloud_consent_events where account_id = @account_id);
                """;
            verify.Parameters.AddWithValue("account_id", DeletionTestAccountId);
            await using var reader = await verify.ExecuteReaderAsync();
            if (!await reader.ReadAsync()
                || reader.GetInt64(0) != 1
                || reader.GetInt64(1) != 1
                || reader.GetInt64(2) != 0
                || reader.GetInt64(3) == 0)
            {
                throw new InvalidOperationException(
                    "Account deletion did not atomically tombstone, enqueue Auth cleanup, delete project content, and preserve minimal consent history.");
            }
        }

        using (var blockedRead = await SendAsync(
            client,
            HttpMethod.Get,
            "/v1/account/me",
            DeletionTestAccountId,
            body: null))
        {
            RequireStatus(blockedRead, HttpStatusCode.Gone, "deleted account access guard");
            RequireString(await ReadJsonAsync(blockedRead), "code", "ACCOUNT_DELETED");
        }

        // Simulate the Auth provider completing its deletion. The durable
        // tombstone/outbox deliberately have no auth.users foreign key.
        await using (var dataSource = NpgsqlDataSource.Create(databaseConnectionString))
        await using (var connection = await dataSource.OpenConnectionAsync())
        await using (var deleteIdentity = connection.CreateCommand())
        {
            deleteIdentity.CommandText = "delete from auth.users where id = @account_id;";
            deleteIdentity.Parameters.AddWithValue("account_id", DeletionTestAccountId);
            if (await deleteIdentity.ExecuteNonQueryAsync() != 1)
                throw new InvalidOperationException("The synthetic Auth identity fixture was not present.");
        }

        using (var deletionReplay = await SendAccountDeletionAsync(
            client,
            confirmed: true,
            DeletionTestAccountId))
        {
            RequireStatus(deletionReplay, HttpStatusCode.OK, "account deletion after Auth identity removal");
            RequireString(await ReadJsonAsync(deletionReplay), "outcome", "already_completed");
        }

        using var outbox = new NpgsqlAccountAuthDeletionOutboxStore(databaseConnectionString);
        var claims = await Task.WhenAll(
            outbox.ClaimAsync(1, CancellationToken.None),
            outbox.ClaimAsync(1, CancellationToken.None));
        var firstJob = claims.SelectMany(batch => batch).Single();
        if (firstJob.AccountId != DeletionTestAccountId || firstJob.Attempts != 1)
            throw new InvalidOperationException("Concurrent Auth deletion claims did not lease exactly one synthetic account job.");

        await outbox.FailAsync(firstJob, "AUTH_ADMIN_TRANSIENT", retryable: true, CancellationToken.None);
        await AssertOutboxStateAsync(
            databaseConnectionString,
            firstJob.OutboxId,
            expectedStatus: "queued",
            expectedAttempts: 1,
            expectedError: "AUTH_ADMIN_TRANSIENT");

        await using (var dataSource = NpgsqlDataSource.Create(databaseConnectionString))
        await using (var connection = await dataSource.OpenConnectionAsync())
        await using (var makeRetryReady = connection.CreateCommand())
        {
            makeRetryReady.CommandText = """
                update public.account_auth_deletion_outbox
                   set available_at = now()
                 where outbox_id = @outbox_id and status = 'queued';
                """;
            makeRetryReady.Parameters.AddWithValue("outbox_id", firstJob.OutboxId);
            if (await makeRetryReady.ExecuteNonQueryAsync() != 1)
                throw new InvalidOperationException("The synthetic Auth deletion retry fixture could not be advanced.");
        }

        var retryClaims = await outbox.ClaimAsync(1, CancellationToken.None);
        if (retryClaims.Count != 1)
            throw new InvalidOperationException("A transient Auth deletion failure was not returned after its bounded delay fixture.");
        var secondJob = retryClaims[0];
        if (secondJob.Attempts != 2 || secondJob.LeaseToken == firstJob.LeaseToken)
            throw new InvalidOperationException("Auth deletion retry did not receive a fresh attempt and fencing token.");

        try
        {
            await outbox.CompleteAsync(firstJob, CancellationToken.None);
            throw new InvalidOperationException("A stale Auth deletion lease unexpectedly completed the newer job.");
        }
        catch (AccountAuthDeletionLeaseLostException)
        {
            // Expected: the newer lease token fences this stale worker.
        }
        await outbox.FailAsync(firstJob, "AUTH_ADMIN_REJECTED", retryable: false, CancellationToken.None);
        await AssertOutboxStateAsync(
            databaseConnectionString,
            secondJob.OutboxId,
            expectedStatus: "running",
            expectedAttempts: 2,
            expectedError: null);
        await outbox.CompleteAsync(secondJob, CancellationToken.None);
        await AssertOutboxStateAsync(
            databaseConnectionString,
            secondJob.OutboxId,
            expectedStatus: "completed",
            expectedAttempts: 2,
            expectedError: null);
        await using (var dataSource = NpgsqlDataSource.Create(databaseConnectionString))
        await using (var connection = await dataSource.OpenConnectionAsync())
        await using (var verifyScrub = connection.CreateCommand())
        {
            verifyScrub.CommandText = """
                select account_id is null
                  from public.account_auth_deletion_outbox
                 where outbox_id = @outbox_id;
                """;
            verifyScrub.Parameters.AddWithValue("outbox_id", secondJob.OutboxId);
            if (await verifyScrub.ExecuteScalarAsync() is not true)
                throw new InvalidOperationException("A completed Auth deletion outbox row retained its account identifier.");
        }

        var exhaustedOutboxId = Guid.Parse("323e4567-e89b-42d3-a456-426614174000");
        await using (var dataSource = NpgsqlDataSource.Create(databaseConnectionString))
        await using (var connection = await dataSource.OpenConnectionAsync())
        await using (var seedExhausted = connection.CreateCommand())
        {
            seedExhausted.CommandText = """
                insert into public.account_auth_deletion_outbox (
                    outbox_id, account_id, status, attempts, available_at, lease_token, leased_until
                ) values (
                    @outbox_id, @account_id, 'running', 8, now() - interval '1 minute',
                    @lease_token, now() - interval '1 second'
                );
                """;
            seedExhausted.Parameters.AddWithValue("outbox_id", exhaustedOutboxId);
            seedExhausted.Parameters.AddWithValue("account_id", OtherAccountId);
            seedExhausted.Parameters.AddWithValue("lease_token", Guid.NewGuid());
            await seedExhausted.ExecuteNonQueryAsync();
        }

        if ((await outbox.ClaimAsync(1, CancellationToken.None)).Count != 0)
            throw new InvalidOperationException("An exhausted Auth deletion job was claimed again.");
        await AssertOutboxStateAsync(
            databaseConnectionString,
            exhaustedOutboxId,
            expectedStatus: "dead_letter",
            expectedAttempts: 8,
            expectedError: "AUTH_ADMIN_RETRY_EXHAUSTED");

        Console.WriteLine("EVIDRILO_ACCOUNT_TOMBSTONE_AUTH_OUTBOX_RETRY_FENCING_E2E_PASS");
    }

    private static async Task<HttpResponseMessage> SendAccountDeletionAsync(
        HttpClient client,
        bool confirmed,
        Guid? accountId = null)
    {
        using var request = new HttpRequestMessage(HttpMethod.Delete, "/v1/account/me");
        request.Headers.Add("X-Test-User", $"{accountId ?? AccountId}|true");
        if (confirmed) request.Headers.Add("X-Account-Deletion-Confirm", "delete-my-account");
        return await client.SendAsync(request);
    }

    private static async Task AssertOutboxStateAsync(
        string databaseConnectionString,
        Guid outboxId,
        string expectedStatus,
        int expectedAttempts,
        string? expectedError)
    {
        await using var dataSource = NpgsqlDataSource.Create(databaseConnectionString);
        await using var connection = await dataSource.OpenConnectionAsync();
        await using var command = connection.CreateCommand();
        command.CommandText = """
            select status, attempts, last_error_code
              from public.account_auth_deletion_outbox
             where outbox_id = @outbox_id;
            """;
        command.Parameters.AddWithValue("outbox_id", outboxId);
        await using var reader = await command.ExecuteReaderAsync();
        if (!await reader.ReadAsync()
            || reader.GetString(0) != expectedStatus
            || reader.GetInt32(1) != expectedAttempts
            || (reader.IsDBNull(2) ? null : reader.GetString(2)) != expectedError)
        {
            throw new InvalidOperationException("The account Auth deletion outbox state did not match the expected fenced transition.");
        }
    }

    private static async Task SeedAsync(string connectionString)
    {
        await using var dataSource = NpgsqlDataSource.Create(connectionString);
        await using var connection = await dataSource.OpenConnectionAsync();
        await using var command = connection.CreateCommand();
        command.CommandText = """
            select set_config('request.jwt.claim.sub', @account_id::text, false);

            delete from auth.users
             where id in (
                @account_id,
                @other_account_id,
                @reviewer_account_id,
                @maintainer_account_id,
                @deletion_test_account_id
             );

            insert into auth.users (id) values
                (@account_id),
                (@other_account_id),
                (@reviewer_account_id),
                (@maintainer_account_id),
                (@deletion_test_account_id);

            select set_config('request.jwt.claim.sub', @account_id::text, false);

            insert into public.organizations (organization_id, name)
            values (@organization_id, 'Evidrilo lifecycle integration organization')
            on conflict (organization_id) do nothing;

            insert into public.organization_memberships (organization_id, account_id, role, active)
            values
                (@organization_id, @account_id, 'author', true),
                (@organization_id, @reviewer_account_id, 'reviewer', true),
                (@organization_id, @maintainer_account_id, 'maintainer', true)
            on conflict (organization_id, account_id) do update
                set role = excluded.role,
                    active = excluded.active;

            insert into public.student_project_cloud_consent_events (
                event_id, account_id, policy_version, decision
            ) values (
                '99999999-0000-0000-0000-000000000994',
                @deletion_test_account_id,
                'student-project-cloud.v1',
                'grant'
            ) on conflict (event_id) do nothing;

            insert into public.case_versions (
                case_version_id, content_hash, status, published_at,
                case_id, title, evaluator_version, skill_tags
            ) values (
                'M0_T2:draft', @draft_content_hash, 'draft', null,
                'case-e2e-draft', 'Draft evidence case', 'evidrilo.v1',
                array['evidence']
            ) on conflict (case_version_id) do nothing;
            """;
        command.Parameters.AddWithValue("account_id", AccountId);
        command.Parameters.AddWithValue("other_account_id", OtherAccountId);
        command.Parameters.AddWithValue("reviewer_account_id", ReviewerAccountId);
        command.Parameters.AddWithValue("maintainer_account_id", MaintainerAccountId);
        command.Parameters.AddWithValue("deletion_test_account_id", DeletionTestAccountId);
        command.Parameters.AddWithValue("organization_id", OrganizationId);
        command.Parameters.AddWithValue(
            "draft_content_hash",
            "abcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefabcd");
        await command.ExecuteNonQueryAsync();
    }

    private static Process StartWorker(
        string dotnetRoot,
        string workerAssembly,
        string connectionString)
    {
        var startInfo = new ProcessStartInfo
        {
            FileName = Path.Combine(dotnetRoot, "dotnet"),
            Arguments = $"\"{workerAssembly}\"",
            WorkingDirectory = Path.GetDirectoryName(workerAssembly)!,
            UseShellExecute = false,
            RedirectStandardOutput = true,
            RedirectStandardError = true,
            CreateNoWindow = true,
        };
        startInfo.Environment["DOTNET_ROOT"] = dotnetRoot;
        startInfo.Environment["DOTNET_ENVIRONMENT"] = "Development";
        startInfo.Environment["DATABASE_URL"] = connectionString;
        startInfo.Environment["WORKER_POLL_INTERVAL_SECONDS"] = "1";
        startInfo.Environment["WORKER_BATCH_SIZE"] = "1";
        var process = Process.Start(startInfo)
            ?? throw new InvalidOperationException(
                "The local projection worker could not be started.");
        process.OutputDataReceived += (_, eventArgs) =>
        {
            if (eventArgs.Data is not null) Console.WriteLine($"E2E_WORKER {eventArgs.Data}");
        };
        process.ErrorDataReceived += (_, eventArgs) =>
        {
            if (eventArgs.Data is not null) Console.Error.WriteLine($"E2E_WORKER {eventArgs.Data}");
        };
        process.BeginOutputReadLine();
        process.BeginErrorReadLine();
        return process;
    }

    private static async Task<JsonElement> WaitForProjectionAsync(HttpClient client)
    {
        for (var attempt = 0; attempt < 30; attempt++)
        {
            using var response = await SendAsync(
                client,
                HttpMethod.Get,
                "/v1/progress/me",
                AccountId,
                body: null);
            RequireStatus(response, HttpStatusCode.OK, "progress projection read");
            var body = await ReadJsonAsync(response);
            if (body.GetProperty("attemptsObserved").GetInt32() == 1) return body;
            await Task.Delay(TimeSpan.FromSeconds(1));
        }

        throw new TimeoutException(
            "The local projection worker did not materialize progress within 30 seconds.");
    }

    private static async Task<HttpResponseMessage> SendAsync(
        HttpClient client,
        HttpMethod method,
        string path,
        Guid accountId,
        object? body)
    {
        using var request = new HttpRequestMessage(method, path);
        request.Headers.Add("X-Test-User", $"{accountId}|true");
        if (body is not null) request.Content = JsonContent.Create(body);
        return await client.SendAsync(request);
    }

    private static async Task AssertReadyAsync(HttpClient client)
    {
        using var response = await client.GetAsync("/health/ready");
        RequireStatus(response, HttpStatusCode.ServiceUnavailable, "degraded API readiness");
        var body = await ReadJsonAsync(response);
        RequireString(body, "check", "ready");
        RequireString(body, "status", "degraded");
        RequireString(body.GetProperty("dependencies"), "config", "missing");
        RequireString(body.GetProperty("dependencies"), "auth", "missing");
        RequireString(body.GetProperty("dependencies"), "database", "ready");
    }

    private static async Task<JsonElement> ReadJsonAsync(HttpResponseMessage response) =>
        await response.Content.ReadFromJsonAsync<JsonElement>();

    private static void RequireStatus(
        HttpResponseMessage response,
        HttpStatusCode expected,
        string operation)
    {
        if (response.StatusCode != expected)
        {
            var responseBody = response.Content.ReadAsStringAsync().GetAwaiter().GetResult();
            throw new InvalidOperationException(
                $"{operation} returned {(int)response.StatusCode}; expected {(int)expected}; body={responseBody}");
        }
    }

    private static void RequireString(JsonElement body, string name, string expected)
    {
        if (!body.TryGetProperty(name, out var value) || value.GetString() != expected)
        {
            throw new InvalidOperationException(
                $"E2E response field {name} did not match the expected value; actual={value}");
        }
    }

    private static void RequireNumber(JsonElement body, string name, int expected)
    {
        if (!body.TryGetProperty(name, out var value) || value.GetInt32() != expected)
        {
            throw new InvalidOperationException(
                $"E2E response field {name} did not match the expected value.");
        }
    }

    private static void StopWorker(Process process)
    {
        if (!process.HasExited)
        {
            process.Kill(entireProcessTree: true);
            process.WaitForExit(5000);
        }
        process.Dispose();
    }
}

internal sealed class E2eApiFactory : WebApplicationFactory<global::Program>
{
    private readonly string databaseConnectionString;
    private readonly string repositoryRoot;
    private readonly bool useTestAiProvider;

    public E2eApiFactory(
        string databaseConnectionString,
        string repositoryRoot,
        bool useTestAiProvider = false)
    {
        this.databaseConnectionString = Environment.GetEnvironmentVariable("EVIDRILO_E2E_API_DATABASE_URL") ?? databaseConnectionString;
        this.repositoryRoot = repositoryRoot;
        this.useTestAiProvider = useTestAiProvider;
    }

    protected override void ConfigureWebHost(IWebHostBuilder builder)
    {
        builder.UseContentRoot(repositoryRoot);
        builder.UseEnvironment("Testing");
        builder.UseSetting("Platform:DatabaseConnectionString", databaseConnectionString);
        builder.UseSetting("Platform:RevenueCatWebhookSecret", EntryPoint.BillingWebhookSecret);
        builder.UseSetting("Platform:RevenueCatEntitlementId", EntryPoint.BillingEntitlement);
        builder.ConfigureAppConfiguration((_, configuration) =>
        {
            configuration.AddInMemoryCollection(new Dictionary<string, string?>
            {
                ["Platform:Environment"] = "Testing",
                ["AI_PROVIDER_ENABLED"] = "false",
                ["Platform:SupabaseUrl"] = "",
                ["Platform:SupabasePublishableKey"] = "",
                ["Platform:DatabaseConnectionString"] = databaseConnectionString,
                ["Platform:CorsAllowedOrigins"] = "http://localhost:3000",
            });
        });
        builder.ConfigureServices(services =>
        {
            if (useTestAiProvider)
            {
                services.RemoveAll<IAiProvider>();
                services.AddSingleton<IAiProvider, E2eConversationProvider>();
            }
            services.AddAuthentication(options =>
            {
                options.DefaultAuthenticateScheme = E2eAuthenticationHandler.SchemeName;
                options.DefaultChallengeScheme = E2eAuthenticationHandler.SchemeName;
            }).AddScheme<AuthenticationSchemeOptions, E2eAuthenticationHandler>(
                E2eAuthenticationHandler.SchemeName,
                _ => { });
        });
    }

    protected override IEnumerable<Assembly> GetTestAssemblies() =>
        new[] { typeof(E2eApiFactory).Assembly };
}

internal sealed class E2eConversationProvider : IAiProvider
{
    public int EstimateMaximumCreditCost(AiProviderRequest request) => 10;

    public async Task<AiProviderResponse?> CompleteAsync(
        AiProviderRequest request,
        CancellationToken cancellationToken)
    {
        await Task.Delay(TimeSpan.FromMilliseconds(80), cancellationToken);
        if (request.Purpose != AiAssistPurpose.LanguageAlternative)
            return new AiProviderResponse("language_alternative", "Use wording bounded to this observed comparison.", ["OBS-E2E-01"])
            {
                Usage = new AiProviderTokenUsage(10_000, 0, 0, 1_000, 0),
            };

        return new AiProviderResponse(
            "draft_proposal",
            "Narrow the scope to the observed comparison only.",
            ["OBS-E2E-01"])
        {
            Usage = new AiProviderTokenUsage(10_000, 0, 0, 1_000, 0),
            Proposal = new AiDraftProposal(
                "claim_scope",
                "LIMITED_COMPARISON",
                "OBSERVED_COMPARISON_ONLY",
                ["OBS-E2E-01"]),
        };
    }
}

internal sealed class E2eAuthenticationHandler : AuthenticationHandler<AuthenticationSchemeOptions>
{
    public const string SchemeName = "LocalE2e";

    public E2eAuthenticationHandler(
        IOptionsMonitor<AuthenticationSchemeOptions> options,
        ILoggerFactory logger,
        System.Text.Encodings.Web.UrlEncoder encoder)
        : base(options, logger, encoder)
    {
    }

    protected override Task<AuthenticateResult> HandleAuthenticateAsync()
    {
        var value = Request.Headers["X-Test-User"].FirstOrDefault();
        if (string.IsNullOrWhiteSpace(value)) return Task.FromResult(AuthenticateResult.NoResult());

        var segments = value.Split('|', StringSplitOptions.TrimEntries);
        if (segments.Length != 2 || !Guid.TryParse(segments[0], out var id) || segments[1] != "true")
        {
            return Task.FromResult(AuthenticateResult.Fail("Invalid local E2E identity."));
        }

        var claims = new[]
        {
            new Claim("sub", id.ToString()),
            new Claim("email_verified", "true"),
        };
        var identity = new ClaimsIdentity(claims, SchemeName);
        return Task.FromResult(AuthenticateResult.Success(
            new AuthenticationTicket(new ClaimsPrincipal(identity), SchemeName)));
    }
}
