using System.Diagnostics;
using System.Net;
using System.Net.Http.Json;
using System.Reflection;
using System.Security.Claims;
using System.Text.Json;
using Microsoft.AspNetCore.Authentication;
using Microsoft.AspNetCore.Hosting;
using Microsoft.AspNetCore.Mvc.Testing;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Logging;
using Microsoft.Extensions.Options;
using Npgsql;

namespace Evidrilo.LocalE2e;

public static class EntryPoint
{
    private static readonly Guid AccountId =
        Guid.Parse("99999999-9999-9999-9999-999999999990");
    private static readonly Guid OtherAccountId =
        Guid.Parse("99999999-9999-9999-9999-999999999991");
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

            delete from auth.users where id in (@account_id, @other_account_id);

            insert into auth.users (id) values (@account_id), (@other_account_id);

            select set_config('request.jwt.claim.sub', @account_id::text, false);

            insert into public.case_versions (
                case_version_id, content_hash, status, published_at,
                case_id, title, evaluator_version, skill_tags,
                objective, difficulty, evidence_references, content
            ) values (
                'M0_T2:1', @content_hash, 'published', now(),
                'case-e2e', 'Evidence graph integration case', 'evidrilo.v1',
                array['evidence'],
                'Connect evidence to a bounded claim', 2,
                array['OBS-E2E-01', 'LIMIT-E2E-01'],
                $case$
                {
                  "content": {
                    "caseId": "case-e2e",
                    "caseVersionId": "M0_T2:1",
                    "title": "Evidence graph integration case",
                    "contentHash": "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                    "evaluatorVersion": "evidrilo.v1",
                    "factAnchors": ["OBS-E2E-01", "LIMIT-E2E-01"],
                    "skillTags": ["evidence"]
                  },
                  "objective": "Connect evidence to a bounded claim",
                  "difficulty": 2,
                  "evidenceReferences": ["OBS-E2E-01", "LIMIT-E2E-01"],
                  "facts": [
                    { "id": "OBS-E2E-01", "type": "observation", "text": "Warm water reached the mark in 32 seconds." },
                    { "id": "LIMIT-E2E-01", "type": "limitation", "text": "Each condition was measured once." }
                  ],
                  "rules": [
                    { "id": "RULE-E2E-01", "outcome": "PASS", "anchorIds": ["OBS-E2E-01", "LIMIT-E2E-01"] }
                  ],
                  "variants": [
                    { "id": "CHALLENGE-E2E-01", "removedFactIds": ["LIMIT-E2E-01"] }
                  ]
                }
                $case$::jsonb
            ) on conflict (case_version_id) do nothing;

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
        command.Parameters.AddWithValue("content_hash", CaseContentHash);
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
