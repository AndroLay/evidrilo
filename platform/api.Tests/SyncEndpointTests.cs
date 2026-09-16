using System.Net;
using System.Net.Http.Json;
using System.Text;
using System.Text.Json;

namespace Evidrilo.Api.Tests;

public sealed class SyncEndpointTests : IClassFixture<ApiFactory>
{
    private static readonly Guid UserId = Guid.Parse("123e4567-e89b-42d3-a456-426614174000");
    private readonly HttpClient client;

    public SyncEndpointTests(ApiFactory factory)
    {
        client = factory.CreateClient();
    }

    [Fact]
    public async Task Sync_push_requires_authenticated_identity()
    {
        using var response = await client.PostAsJsonAsync(
            "/v1/sync/commands",
            ValidRequest());
        var body = await response.Content.ReadFromJsonAsync<JsonElement>();

        Assert.Equal(HttpStatusCode.Unauthorized, response.StatusCode);
        Assert.Equal("AUTH_REQUIRED", body.GetProperty("code").GetString());
    }

    [Fact]
    public async Task Sync_push_fails_closed_when_database_is_not_configured()
    {
        using var request = new HttpRequestMessage(HttpMethod.Post, "/v1/sync/commands")
        {
            Content = JsonContent.Create(ValidRequest()),
        };
        request.Headers.Add("X-Test-User", $"{UserId}|true");

        using var response = await client.SendAsync(request);
        var body = await response.Content.ReadFromJsonAsync<JsonElement>();

        Assert.Equal(HttpStatusCode.ServiceUnavailable, response.StatusCode);
        Assert.Equal("DATABASE_NOT_CONFIGURED", body.GetProperty("code").GetString());
        Assert.DoesNotContain("connection", body.ToString(), StringComparison.OrdinalIgnoreCase);
    }

    [Fact]
    public async Task Sync_push_rejects_malformed_command_before_database_access()
    {
        using var request = new HttpRequestMessage(HttpMethod.Post, "/v1/sync/commands")
        {
            Content = JsonContent.Create(new
            {
                schema = "evidrilo.sync-push-request",
                version = "1",
                commands = new[]
                {
                    new
                    {
                        commandId = "123e4567-e89b-42d3-a456-426614174000",
                        attemptId = "123e4567-e89b-42d3-a456-426614174001",
                        caseVersionId = "M0 T2",
                        commandType = "attempt_submitted",
                        revisionNumber = 0,
                        clientOccurredAt = "2026-09-10T09:00:00Z",
                        snapshotDigest = "bad",
                    },
                },
            }),
        };
        request.Headers.Add("X-Test-User", $"{UserId}|true");

        using var response = await client.SendAsync(request);
        var body = await response.Content.ReadFromJsonAsync<JsonElement>();

        Assert.Equal(HttpStatusCode.BadRequest, response.StatusCode);
        Assert.Equal("INVALID_SYNC_COMMAND", body.GetProperty("code").GetString());
    }

    [Fact]
    public async Task Sync_push_rejects_unknown_contract_fields()
    {
        using var request = new HttpRequestMessage(HttpMethod.Post, "/v1/sync/commands")
        {
            Content = JsonContent.Create(new
            {
                schema = "evidrilo.sync-push-request",
                version = "1",
                commands = new[]
                {
                    new
                    {
                        commandId = "123e4567-e89b-42d3-a456-426614174000",
                        attemptId = "123e4567-e89b-42d3-a456-426614174001",
                        caseVersionId = "M0_T2:1",
                        commandType = "attempt_submitted",
                        revisionNumber = 0,
                        clientOccurredAt = "2026-09-10T09:00:00Z",
                        snapshotDigest = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                        rawDraftText = "must-not-cross-boundary",
                    },
                },
            }),
        };
        request.Headers.Add("X-Test-User", $"{UserId}|true");

        using var response = await client.SendAsync(request);
        var body = await response.Content.ReadFromJsonAsync<JsonElement>();

        Assert.Equal(HttpStatusCode.BadRequest, response.StatusCode);
        Assert.Equal("INVALID_SYNC_REQUEST", body.GetProperty("code").GetString());
        Assert.DoesNotContain("must-not-cross-boundary", body.ToString(), StringComparison.Ordinal);
    }

    [Fact]
    public async Task Sync_push_rejects_malformed_json_with_the_stable_error_contract()
    {
        using var request = new HttpRequestMessage(HttpMethod.Post, "/v1/sync/commands")
        {
            Content = new StringContent("{", Encoding.UTF8, "application/json"),
        };
        request.Headers.Add("X-Test-User", $"{UserId}|true");

        using var response = await client.SendAsync(request);
        var body = await response.Content.ReadFromJsonAsync<JsonElement>();

        Assert.Equal(HttpStatusCode.BadRequest, response.StatusCode);
        Assert.Equal("INVALID_SYNC_REQUEST", body.GetProperty("code").GetString());
    }

    [Fact]
    public async Task Sync_push_rejects_a_command_without_a_command_type()
    {
        using var request = new HttpRequestMessage(HttpMethod.Post, "/v1/sync/commands")
        {
            Content = JsonContent.Create(new
            {
                schema = "evidrilo.sync-push-request",
                version = "1",
                commands = new[]
                {
                    new
                    {
                        commandId = "123e4567-e89b-42d3-a456-426614174000",
                        attemptId = "123e4567-e89b-42d3-a456-426614174001",
                        caseVersionId = "M0_T2:1",
                        revisionNumber = 0,
                        clientOccurredAt = "2026-09-10T09:00:00Z",
                        snapshotDigest = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                    },
                },
            }),
        };
        request.Headers.Add("X-Test-User", $"{UserId}|true");

        using var response = await client.SendAsync(request);
        var body = await response.Content.ReadFromJsonAsync<JsonElement>();

        Assert.Equal(HttpStatusCode.BadRequest, response.StatusCode);
        Assert.Equal("INVALID_SYNC_REQUEST", body.GetProperty("code").GetString());
    }

    [Fact]
    public async Task Sync_push_rejects_duplicate_command_ids_before_database_access()
    {
        var command = new
        {
            commandId = "123e4567-e89b-42d3-a456-426614174000",
            attemptId = "123e4567-e89b-42d3-a456-426614174001",
            caseVersionId = "M0_T2:1",
            commandType = "attempt_submitted",
            revisionNumber = 0,
            clientOccurredAt = "2026-09-10T09:00:00Z",
            snapshotDigest = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
        };
        using var request = new HttpRequestMessage(HttpMethod.Post, "/v1/sync/commands")
        {
            Content = JsonContent.Create(new
            {
                schema = "evidrilo.sync-push-request",
                version = "1",
                commands = new[] { command, command },
            }),
        };
        request.Headers.Add("X-Test-User", $"{UserId}|true");

        using var response = await client.SendAsync(request);
        var body = await response.Content.ReadFromJsonAsync<JsonElement>();

        Assert.Equal(HttpStatusCode.BadRequest, response.StatusCode);
        Assert.Equal("INVALID_SYNC_REQUEST", body.GetProperty("code").GetString());
    }

    [Fact]
    public async Task Sync_pull_rejects_a_cursor_above_the_mobile_contract_bound_before_database_access()
    {
        using var request = new HttpRequestMessage(
            HttpMethod.Get,
            "/v1/sync/pull?cursor=1000000000000001&limit=1");
        request.Headers.Add("X-Test-User", $"{UserId}|true");

        using var response = await client.SendAsync(request);
        var body = await response.Content.ReadFromJsonAsync<JsonElement>();

        Assert.Equal(HttpStatusCode.BadRequest, response.StatusCode);
        Assert.Equal("INVALID_SYNC_CURSOR", body.GetProperty("code").GetString());
    }

    private static object ValidRequest() => new
    {
        schema = "evidrilo.sync-push-request",
        version = "1",
        commands = new[]
        {
            new
            {
                commandId = "123e4567-e89b-42d3-a456-426614174000",
                attemptId = "123e4567-e89b-42d3-a456-426614174001",
                caseVersionId = "M0_T2:1",
                commandType = "attempt_submitted",
                revisionNumber = 0,
                clientOccurredAt = "2026-09-10T09:00:00Z",
                snapshotDigest = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
            },
        },
    };
}
