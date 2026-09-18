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
using Evidrilo.Api.Billing;
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
    private static readonly Guid OrganizationId =
        Guid.Parse("99999999-9999-4999-8999-999999999990");
    private static readonly Guid AttemptId =
        Guid.Parse("99999999-0000-0000-0000-000000000990");
    private static readonly Guid CommandId =
        Guid.Parse("99999999-0000-0000-0000-000000000991");
    private static readonly Guid ClientEventId =
        Guid.Parse("99999999-0000-0000-0000-000000000992");
    private const string CaseVersionId = "M0_T2:1";
    private const string DraftCaseVersionId = "M0_T2:draft";
    private const string CaseContentHash =
        "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    private const string SnapshotDigest =
        "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    private const string BillingWebhookSecret = "synthetic-billing-secret";
    private const string BillingEntitlement = "evidrilo_pro";

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

        var workerAssembly = Path.Combine(
            repositoryRoot,
            "platform",
            "worker",
            "bin",
            "Release",
            "net10.0",
            "Evidrilo.Worker.dll");
        if (!File.Exists(workerAssembly))
        {
            throw new FileNotFoundException(
                "The Release worker artifact is required for the local E2E smoke.",
                workerAssembly);
        }

        await SeedAsync(databaseConnectionString);
        using var apiFactory = new E2eApiFactory(databaseConnectionString, repositoryRoot);
        using var client = apiFactory.CreateClient();
        var worker = StartWorker(dotnetRoot, workerAssembly, databaseConnectionString);
        try
        {
            await AssertReadyAsync(client);
            await AssertBillingLifecycleFlowAsync(client, databaseConnectionString);
            await AssertContentLifecycleFlowAsync(client, databaseConnectionString);
            await AssertPublishedCaseEvidenceFlowAsync(client);
            await AssertSyncAndProjectionFlowAsync(client);
            await AssertIsolationAsync(client);
            Console.WriteLine("EVIDRILO_API_DATABASE_WORKER_PUBLISHED_CASE_EVIDENCE_GRAPH_E2E_PASS");
        }
        finally
        {
            StopWorker(worker);
        }
    }

    private static async Task AssertBillingLifecycleFlowAsync(
        HttpClient client,
        string databaseConnectionString)
    {
        var baseEventTimestampMs = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds();
        var purchase = CreateRevenueCatEventBody(
            "rc-e2e-purchase",
            "INITIAL_PURCHASE",
            "monthly",
            AccountId,
            baseEventTimestampMs);

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
            baseEventTimestampMs + 4_000);
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
        string? cancellationReason = null)
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

        return JsonSerializer.SerializeToUtf8Bytes(new Dictionary<string, object?>
        {
            ["api_version"] = "1.0",
            ["event"] = providerEvent,
        });
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

        using var approved = await SendTransitionAsync(
            client,
            ReviewerAccountId,
            CaseVersionId,
            "approved",
            "Evidence and challenge content reviewed.");
        RequireStatus(approved, HttpStatusCode.OK, "review approval transition");
        RequireString(await ReadJsonAsync(approved), "state", "approved");

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

    private static async Task SeedAsync(string connectionString)
    {
        await using var dataSource = NpgsqlDataSource.Create(connectionString);
        await using var connection = await dataSource.OpenConnectionAsync();
        await using var command = connection.CreateCommand();
        command.CommandText = """
            select set_config('request.jwt.claim.sub', @account_id::text, false);

            delete from auth.users
             where id in (@account_id, @other_account_id, @reviewer_account_id, @maintainer_account_id);

            insert into auth.users (id) values
                (@account_id),
                (@other_account_id),
                (@reviewer_account_id),
                (@maintainer_account_id);

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
        startInfo.Environment["DATABASE_URL"] = connectionString;
        startInfo.Environment["WORKER_POLL_INTERVAL_SECONDS"] = "1";
        startInfo.Environment["WORKER_BATCH_SIZE"] = "1";
        var process = Process.Start(startInfo)
            ?? throw new InvalidOperationException(
                "The local projection worker could not be started.");
        _ = process.StandardOutput.ReadToEndAsync();
        _ = process.StandardError.ReadToEndAsync();
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
        RequireStatus(response, HttpStatusCode.OK, "API readiness");
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
            throw new InvalidOperationException(
                $"{operation} returned {(int)response.StatusCode}; expected {(int)expected}.");
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

    public E2eApiFactory(string databaseConnectionString, string repositoryRoot)
    {
        this.databaseConnectionString = databaseConnectionString;
        this.repositoryRoot = repositoryRoot;
    }

    protected override void ConfigureWebHost(IWebHostBuilder builder)
    {
        builder.UseContentRoot(repositoryRoot);
        builder.UseEnvironment("Testing");
        builder.UseSetting("Platform:DatabaseConnectionString", databaseConnectionString);
        builder.ConfigureAppConfiguration((_, configuration) =>
        {
            configuration.AddInMemoryCollection(new Dictionary<string, string?>
            {
                ["Platform:Environment"] = "Testing",
                ["Platform:SupabaseUrl"] = "",
                ["Platform:SupabasePublishableKey"] = "",
                ["Platform:DatabaseConnectionString"] = databaseConnectionString,
                ["Platform:CorsAllowedOrigins"] = "http://localhost:3000",
                ["Platform:RevenueCatWebhookSecret"] = BillingWebhookSecret,
                ["Platform:RevenueCatEntitlementId"] = BillingEntitlement,
            });
        });
        builder.ConfigureServices(services =>
        {
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
