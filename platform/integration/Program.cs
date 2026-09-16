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
            await AssertSyncAndProjectionFlowAsync(client);
            await AssertIsolationAsync(client);
            Console.WriteLine("EVIDRILO_API_DATABASE_WORKER_SYNC_E2E_PASS");
        }
        finally
        {
            StopWorker(worker);
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

            delete from auth.users where id in (@account_id, @other_account_id);

            insert into auth.users (id) values (@account_id), (@other_account_id);

            select set_config('request.jwt.claim.sub', @account_id::text, false);

            insert into public.case_versions (
                case_version_id, content_hash, status, published_at
            ) values (
                'M0_T2:1', 'local-e2e-content', 'published', now()
            ) on conflict (case_version_id) do update
                set content_hash = excluded.content_hash,
                    status = excluded.status,
                    published_at = excluded.published_at;
            """;
        command.Parameters.AddWithValue("account_id", AccountId);
        command.Parameters.AddWithValue("other_account_id", OtherAccountId);
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
