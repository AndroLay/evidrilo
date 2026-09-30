using System.Net;
using System.Net.Http.Json;
using System.Text;
using System.Text.Json;
using Evidrilo.Api.Account;
using Microsoft.AspNetCore.RateLimiting;
using Microsoft.AspNetCore.Routing;
using Microsoft.Extensions.DependencyInjection;

namespace Evidrilo.Api.Tests;

public sealed class AccountExportJobEndpointTests : IDisposable
{
    private static readonly Guid OwnerId = Guid.Parse("123e4567-e89b-42d3-a456-426614174000");
    private static readonly Guid OtherId = Guid.Parse("223e4567-e89b-42d3-a456-426614174000");
    private readonly RecordingAccountExportJobStore store = new();
    private readonly ApiFactory factory;
    private readonly HttpClient client;

    public AccountExportJobEndpointTests()
    {
        factory = ApiFactory.WithAdditionalServices(services =>
            services.AddSingleton<IAccountExportJobStore>(store));
        client = factory.CreateClient();
    }

    [Theory]
    [InlineData("POST", "/v2/account/exports", "account-export-create")]
    [InlineData("GET", "/v2/account/exports/{exportId:guid}", "account-export-status")]
    [InlineData("GET", "/v2/account/exports/{exportId:guid}/download", "account-export-download")]
    [InlineData("DELETE", "/v2/account/exports/{exportId:guid}", "account-export-status")]
    public void Export_routes_use_their_account_rate_limit_policy(string method, string path, string expectedPolicy)
    {
        var dataSource = factory.Services.GetRequiredService<EndpointDataSource>();
        var endpoint = Assert.Single(
            dataSource.Endpoints,
            candidate => candidate is RouteEndpoint route
                && route.RoutePattern.RawText == path
                && candidate.Metadata.GetMetadata<IHttpMethodMetadata>()?.HttpMethods.Contains(method) == true);

        Assert.Equal(expectedPolicy, endpoint.Metadata.GetMetadata<EnableRateLimitingAttribute>()?.PolicyName);
    }

    [Fact]
    public async Task Export_jobs_require_authenticated_and_verified_accounts()
    {
        using var anonymous = await client.GetAsync(
            "/v2/account/exports/123e4567-e89b-42d3-a456-426614174000",
            cancellationToken: TestContext.Current.CancellationToken);
        using var unverifiedRequest = AuthenticatedRequest(
            HttpMethod.Get,
            "/v2/account/exports/123e4567-e89b-42d3-a456-426614174000",
            verified: false);
        using var unverified = await client.SendAsync(unverifiedRequest, cancellationToken: TestContext.Current.CancellationToken);

        Assert.Equal(HttpStatusCode.Unauthorized, anonymous.StatusCode);
        Assert.Equal(HttpStatusCode.Forbidden, unverified.StatusCode);
        Assert.Equal("no-store", unverified.Headers.CacheControl?.ToString());
    }

    [Fact]
    public async Task Export_creation_requires_one_valid_idempotency_key_before_store_access()
    {
        using var missingRequest = AuthenticatedRequest(HttpMethod.Post, "/v2/account/exports");
        using var missing = await client.SendAsync(missingRequest, cancellationToken: TestContext.Current.CancellationToken);
        using var malformedRequest = AuthenticatedRequest(HttpMethod.Post, "/v2/account/exports", key: "contains spaces");
        using var malformed = await client.SendAsync(malformedRequest, cancellationToken: TestContext.Current.CancellationToken);

        Assert.Equal(HttpStatusCode.BadRequest, missing.StatusCode);
        Assert.Equal(HttpStatusCode.BadRequest, malformed.StatusCode);
        Assert.Equal(0, store.CreateCalls);
    }

    [Fact]
    public async Task Export_creation_is_idempotent_and_returns_no_store_job_metadata()
    {
        using var firstRequest = AuthenticatedRequest(HttpMethod.Post, "/v2/account/exports", key: "export_req_0001");
        using var first = await client.SendAsync(firstRequest, cancellationToken: TestContext.Current.CancellationToken);
        using var retryRequest = AuthenticatedRequest(HttpMethod.Post, "/v2/account/exports", key: "export_req_0001");
        using var retry = await client.SendAsync(retryRequest, cancellationToken: TestContext.Current.CancellationToken);
        var firstBody = await first.Content.ReadFromJsonAsync<JsonElement>(cancellationToken: TestContext.Current.CancellationToken);
        var retryBody = await retry.Content.ReadFromJsonAsync<JsonElement>(cancellationToken: TestContext.Current.CancellationToken);

        Assert.Equal(HttpStatusCode.Accepted, first.StatusCode);
        Assert.Equal(HttpStatusCode.Accepted, retry.StatusCode);
        Assert.Equal(firstBody.GetProperty("exportId").GetString(), retryBody.GetProperty("exportId").GetString());
        Assert.Equal("evidrilo.account-export-job", firstBody.GetProperty("schema").GetString());
        Assert.Equal("2", firstBody.GetProperty("version").GetString());
        Assert.Equal(AccountExportJobResponse.MediaType, first.Content.Headers.ContentType?.MediaType);
        Assert.Equal("no-store", first.Headers.CacheControl?.ToString());
        Assert.Equal(1, store.CreatedJobs);
    }

    [Fact]
    public async Task Export_status_and_download_hide_jobs_from_other_accounts()
    {
        var job = await store.CreateOwnAsync(OwnerId, "a6".PadRight(64, 'a'), "export_req_0002", TestContext.Current.CancellationToken);
        var otherStatus = await SendAuthenticatedAsync(
            HttpMethod.Get,
            $"/v2/account/exports/{job.ExportId:D}",
            OtherId);
        var otherDownload = await SendAuthenticatedAsync(
            HttpMethod.Get,
            $"/v2/account/exports/{job.ExportId:D}/download",
            OtherId);
        var otherDelete = await SendAuthenticatedAsync(
            HttpMethod.Delete,
            $"/v2/account/exports/{job.ExportId:D}",
            OtherId);
        using (otherStatus)
        using (otherDownload)
        using (otherDelete)
        {
            Assert.Equal(HttpStatusCode.NotFound, otherStatus.StatusCode);
            Assert.Equal(HttpStatusCode.NotFound, otherDownload.StatusCode);
            Assert.Equal(HttpStatusCode.NotFound, otherDelete.StatusCode);
            Assert.Equal("no-store", otherStatus.Headers.CacheControl?.ToString());
        }
    }

    [Fact]
    public async Task Pending_exports_conflict_and_cancel_fences_the_job()
    {
        var job = await store.CreateOwnAsync(OwnerId, "a7".PadRight(64, 'a'), "export_req_0003", TestContext.Current.CancellationToken);
        using var pending = await SendAuthenticatedAsync(
            HttpMethod.Get,
            $"/v2/account/exports/{job.ExportId:D}/download",
            OwnerId);
        using var cancel = await SendAuthenticatedAsync(
            HttpMethod.Delete,
            $"/v2/account/exports/{job.ExportId:D}",
            OwnerId);
        using var cancelledDownload = await SendAuthenticatedAsync(
            HttpMethod.Get,
            $"/v2/account/exports/{job.ExportId:D}/download",
            OwnerId);
        var body = await cancel.Content.ReadFromJsonAsync<JsonElement>(cancellationToken: TestContext.Current.CancellationToken);

        Assert.Equal(HttpStatusCode.Conflict, pending.StatusCode);
        Assert.Equal("no-store", pending.Headers.CacheControl?.ToString());
        Assert.Equal(HttpStatusCode.OK, cancel.StatusCode);
        Assert.Equal("cancelled", body.GetProperty("status").GetString());
        Assert.Equal("no-store", cancel.Headers.CacheControl?.ToString());
        Assert.Equal(HttpStatusCode.NotFound, cancelledDownload.StatusCode);
    }

    [Fact]
    public async Task Ready_export_download_streams_fixed_attachment_without_caching()
    {
        var job = await store.CreateOwnAsync(OwnerId, "a8".PadRight(64, 'a'), "export_req_0004", TestContext.Current.CancellationToken);
        store.MarkReady(job.ExportId, Encoding.UTF8.GetBytes("{\"version\":\"2\"}"));
        using var response = await SendAuthenticatedAsync(
            HttpMethod.Get,
            $"/v2/account/exports/{job.ExportId:D}/download",
            OwnerId);
        var content = await response.Content.ReadAsStringAsync(TestContext.Current.CancellationToken);

        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        Assert.Equal("no-store", response.Headers.CacheControl?.ToString());
        Assert.Equal("application/vnd.evidrilo.account-export.v2+json", response.Content.Headers.ContentType?.MediaType);
        Assert.Contains(
            "evidrilo-account-export-v2.json",
            response.Content.Headers.ContentDisposition?.FileName ?? string.Empty);
        Assert.Equal("{\"version\":\"2\"}", content);
    }

    private Task<HttpResponseMessage> SendAuthenticatedAsync(HttpMethod method, string path, Guid accountId) =>
        client.SendAsync(
            AuthenticatedRequest(method, path, accountId.ToString("D")),
            cancellationToken: TestContext.Current.CancellationToken);

    private static HttpRequestMessage AuthenticatedRequest(
        HttpMethod method,
        string path,
        string accountId = "123e4567-e89b-42d3-a456-426614174000",
        bool verified = true,
        string? key = null)
    {
        var request = new HttpRequestMessage(method, path);
        request.Headers.Add("X-Test-User", $"{accountId}|{verified.ToString().ToLowerInvariant()}");
        if (key is not null)
            request.Headers.Add("Idempotency-Key", key);
        return request;
    }

    public void Dispose()
    {
        client.Dispose();
        factory.Dispose();
    }

    private sealed class RecordingAccountExportJobStore : IAccountExportJobStore
    {
        private readonly Dictionary<Guid, (Guid AccountId, AccountExportJob Job, byte[]? Payload)> jobs = new();
        private readonly Dictionary<(Guid AccountId, string KeyHash), Guid> keys = new();
        public int CreateCalls { get; private set; }
        public int CreatedJobs => jobs.Count;

        public Task<AccountExportJob> CreateOwnAsync(
            Guid accountId,
            string idempotencyKeyHash,
            string requestId,
            CancellationToken cancellationToken)
        {
            CreateCalls++;
            if (keys.TryGetValue((accountId, idempotencyKeyHash), out var existingId))
                return Task.FromResult(jobs[existingId].Job);

            var now = DateTimeOffset.UtcNow;
            var job = new AccountExportJob(
                Guid.NewGuid(),
                "queued",
                requestId,
                now,
                null,
                now.AddHours(24),
                null,
                null);
            jobs.Add(job.ExportId, (accountId, job, null));
            keys.Add((accountId, idempotencyKeyHash), job.ExportId);
            return Task.FromResult(job);
        }

        public Task<AccountExportJob?> ReadOwnAsync(Guid accountId, Guid exportId, CancellationToken cancellationToken) =>
            Task.FromResult(jobs.TryGetValue(exportId, out var value) && value.AccountId == accountId
                ? value.Job
                : null);

        public Task<IAccountExportArtifactLease?> OpenReadyArtifactOwnAsync(
            Guid accountId,
            Guid exportId,
            CancellationToken cancellationToken)
        {
            if (!jobs.TryGetValue(exportId, out var value)
                || value.AccountId != accountId
                || value.Job.Status != "ready"
                || value.Payload is null)
                return Task.FromResult<IAccountExportArtifactLease?>(null);
            return Task.FromResult<IAccountExportArtifactLease?>(new MemoryArtifactLease(value.Payload));
        }

        public Task<AccountExportJob?> CancelOwnAsync(Guid accountId, Guid exportId, CancellationToken cancellationToken)
        {
            if (!jobs.TryGetValue(exportId, out var value) || value.AccountId != accountId)
                return Task.FromResult<AccountExportJob?>(null);
            var cancelled = value.Job with
            {
                Status = "cancelled",
                CompletedAt = DateTimeOffset.UtcNow,
                ArtifactBytes = null,
                ErrorCode = null,
            };
            jobs[exportId] = (accountId, cancelled, null);
            return Task.FromResult<AccountExportJob?>(cancelled);
        }

        public void MarkReady(Guid exportId, byte[] payload)
        {
            var value = jobs[exportId];
            var ready = value.Job with
            {
                Status = "ready",
                CompletedAt = DateTimeOffset.UtcNow,
                ExpiresAt = DateTimeOffset.UtcNow.AddHours(24),
                ArtifactBytes = payload.LongLength,
            };
            jobs[exportId] = (value.AccountId, ready, payload);
        }

        private sealed class MemoryArtifactLease(byte[] payload) : IAccountExportArtifactLease
        {
            public long Length => payload.LongLength;

            public async Task CopyToAsync(Stream destination, CancellationToken cancellationToken) =>
                await destination.WriteAsync(payload, cancellationToken);

            public ValueTask DisposeAsync() => ValueTask.CompletedTask;
        }
    }
}
