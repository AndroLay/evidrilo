using System.Net;
using System.Net.Http.Json;
using System.Text.Json;
using Evidrilo.Api.Content;
using Microsoft.AspNetCore.Hosting;
using Microsoft.AspNetCore.TestHost;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.DependencyInjection.Extensions;

namespace Evidrilo.Api.Tests;

public sealed class CaseAuthoringEndpointTests : IClassFixture<ApiFactory>
{
    private static readonly Guid UserId = Guid.Parse("123e4567-e89b-42d3-a456-426614174000");
    private readonly HttpClient client;

    public CaseAuthoringEndpointTests(ApiFactory factory)
    {
        client = factory.CreateClient();
    }

    [Fact]
    public async Task Case_authoring_requires_authenticated_identity()
    {
        using var response = await client.PostAsJsonAsync("/v1/authoring/cases", ValidRequest());
        var body = await response.Content.ReadFromJsonAsync<JsonElement>();

        Assert.Equal(HttpStatusCode.Unauthorized, response.StatusCode);
        Assert.Equal("AUTH_REQUIRED", body.GetProperty("code").GetString());
    }

    [Fact]
    public async Task Case_authoring_requires_verified_identity()
    {
        using var request = new HttpRequestMessage(HttpMethod.Post, "/v1/authoring/cases")
        {
            Content = JsonContent.Create(ValidRequest()),
        };
        request.Headers.Add("X-Test-User", $"{UserId}|false");

        using var response = await client.SendAsync(request);
        var body = await response.Content.ReadFromJsonAsync<JsonElement>();

        Assert.Equal(HttpStatusCode.Forbidden, response.StatusCode);
        Assert.Equal("FORBIDDEN", body.GetProperty("code").GetString());
    }

    [Fact]
    public async Task Case_transition_rejects_a_request_without_a_target_state()
    {
        using var request = new HttpRequestMessage(
            HttpMethod.Post,
            "/v1/authoring/cases/M0_T2:1/transition")
        {
            Content = JsonContent.Create(new
            {
                schema = "evidrilo.case-transition",
                version = "1",
                reason = "Synthetic transition",
            }),
        };
        request.Headers.Add("X-Test-User", $"{UserId}|true");

        using var response = await client.SendAsync(request);
        var body = await response.Content.ReadFromJsonAsync<JsonElement>();

        Assert.Equal(HttpStatusCode.BadRequest, response.StatusCode);
        Assert.Equal("INVALID_CASE_TRANSITION", body.GetProperty("code").GetString());
    }

    [Fact]
    public async Task Case_transition_rejects_an_unknown_contract_version_before_store_access()
    {
        using var request = new HttpRequestMessage(
            HttpMethod.Post,
            "/v1/authoring/cases/M0_T2:1/transition")
        {
            Content = JsonContent.Create(new
            {
                schema = "evidrilo.case-transition",
                version = "999",
                targetState = "review",
                reason = "Synthetic transition",
            }),
        };
        request.Headers.Add("X-Test-User", $"{UserId}|true");

        using var response = await client.SendAsync(request);
        var body = await response.Content.ReadFromJsonAsync<JsonElement>();

        Assert.Equal(HttpStatusCode.BadRequest, response.StatusCode);
        Assert.Equal("INVALID_CASE_TRANSITION", body.GetProperty("code").GetString());
    }

    [Fact]
    public async Task Case_authoring_fails_closed_when_database_is_not_configured()
    {
        using var request = new HttpRequestMessage(HttpMethod.Post, "/v1/authoring/cases")
        {
            Content = JsonContent.Create(ValidRequest()),
        };
        request.Headers.Add("X-Test-User", $"{UserId}|true");

        using var response = await client.SendAsync(request);
        var body = await response.Content.ReadFromJsonAsync<JsonElement>();

        Assert.Equal(HttpStatusCode.ServiceUnavailable, response.StatusCode);
        Assert.Equal("DATABASE_NOT_CONFIGURED", body.GetProperty("code").GetString());
    }

    [Fact]
    public async Task Case_audit_requires_authenticated_identity()
    {
        using var response = await client.GetAsync("/v1/authoring/cases/M0_T2:1/audit");
        var body = await response.Content.ReadFromJsonAsync<JsonElement>();

        Assert.Equal(HttpStatusCode.Unauthorized, response.StatusCode);
        Assert.Equal("AUTH_REQUIRED", body.GetProperty("code").GetString());
    }

    [Fact]
    public async Task Case_audit_requires_verified_identity()
    {
        using var request = new HttpRequestMessage(HttpMethod.Get, "/v1/authoring/cases/M0_T2:1/audit");
        request.Headers.Add("X-Test-User", $"{UserId}|false");

        using var response = await client.SendAsync(request);
        var body = await response.Content.ReadFromJsonAsync<JsonElement>();

        Assert.Equal(HttpStatusCode.Forbidden, response.StatusCode);
        Assert.Equal("FORBIDDEN", body.GetProperty("code").GetString());
    }

    [Fact]
    public async Task Case_audit_fails_closed_when_database_is_not_configured()
    {
        using var request = new HttpRequestMessage(HttpMethod.Get, "/v1/authoring/cases/M0_T2:1/audit");
        request.Headers.Add("X-Test-User", $"{UserId}|true");

        using var response = await client.SendAsync(request);
        var body = await response.Content.ReadFromJsonAsync<JsonElement>();

        Assert.Equal(HttpStatusCode.ServiceUnavailable, response.StatusCode);
        Assert.Equal("DATABASE_NOT_CONFIGURED", body.GetProperty("code").GetString());
    }

    [Fact]
    public async Task Case_audit_rejects_an_invalid_case_version()
    {
        using var request = new HttpRequestMessage(HttpMethod.Get, "/v1/authoring/cases/not valid/audit");
        request.Headers.Add("X-Test-User", $"{UserId}|true");

        using var response = await client.SendAsync(request);
        var body = await response.Content.ReadFromJsonAsync<JsonElement>();

        Assert.Equal(HttpStatusCode.BadRequest, response.StatusCode);
        Assert.Equal("INVALID_CASE_VERSION", body.GetProperty("code").GetString());
    }

    [Fact]
    public async Task Case_audit_returns_a_closed_deletion_safe_response()
    {
        using var testFactory = new ApiFactory().WithWebHostBuilder(builder =>
            builder.ConfigureTestServices(services =>
            {
                services.RemoveAll<ICaseLifecycleAuditStore>();
                services.AddSingleton<ICaseLifecycleAuditStore, PublishedAuditStore>();
            }));
        using var testClient = testFactory.CreateClient();
        using var request = new HttpRequestMessage(HttpMethod.Get, "/v1/authoring/cases/M0_T2:1/audit");
        request.Headers.Add("X-Test-User", $"{UserId}|true");

        using var response = await testClient.SendAsync(request);
        var body = await response.Content.ReadFromJsonAsync<JsonElement>();

        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        Assert.Equal("evidrilo.case-lifecycle-audit", body.GetProperty("schema").GetString());
        Assert.Equal("M0_T2:1", body.GetProperty("caseVersionId").GetString());
        Assert.False(body.GetProperty("truncated").GetBoolean());
        var auditEvent = body.GetProperty("events")[0];
        Assert.True(auditEvent.TryGetProperty("actorAccountId", out var actor));
        Assert.Equal(JsonValueKind.Null, actor.ValueKind);
        Assert.Equal("created", auditEvent.GetProperty("eventType").GetString());
        Assert.True(auditEvent.TryGetProperty("fromState", out var fromState));
        Assert.Equal(JsonValueKind.Null, fromState.ValueKind);
        Assert.Equal("draft", auditEvent.GetProperty("toState").GetString());
        Assert.True(auditEvent.TryGetProperty("reason", out var reason));
        Assert.Equal(JsonValueKind.Null, reason.ValueKind);
    }

    [Fact]
    public async Task Case_audit_defensively_caps_an_overlong_store_result()
    {
        using var testFactory = new ApiFactory().WithWebHostBuilder(builder =>
            builder.ConfigureTestServices(services =>
            {
                services.RemoveAll<ICaseLifecycleAuditStore>();
                services.AddSingleton<ICaseLifecycleAuditStore, OverlongAuditStore>();
            }));
        using var testClient = testFactory.CreateClient();
        using var request = new HttpRequestMessage(HttpMethod.Get, "/v1/authoring/cases/M0_T2:1/audit");
        request.Headers.Add("X-Test-User", $"{UserId}|true");

        using var response = await testClient.SendAsync(request);
        var body = await response.Content.ReadFromJsonAsync<JsonElement>();

        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        Assert.Equal(CaseLifecycleAuditLimits.MaxEvents, body.GetProperty("events").GetArrayLength());
        Assert.True(body.GetProperty("truncated").GetBoolean());
    }

    private sealed class PublishedAuditStore : ICaseLifecycleAuditStore
    {
        public Task<CaseLifecycleAuditRead?> GetAsync(
            Guid accountId,
            string caseVersionId,
            CancellationToken cancellationToken) => Task.FromResult<CaseLifecycleAuditRead?>(
            new CaseLifecycleAuditRead(
                caseVersionId,
                [new CaseLifecycleAuditEvent(
                    Guid.Parse("123e4567-e89b-42d3-a456-426614174020"),
                    null,
                    "created",
                    null,
                    CaseLifecycleState.Draft,
                    null,
                    DateTimeOffset.UnixEpoch)],
                false));
    }

    private sealed class OverlongAuditStore : ICaseLifecycleAuditStore
    {
        public Task<CaseLifecycleAuditRead?> GetAsync(
            Guid accountId,
            string caseVersionId,
            CancellationToken cancellationToken) => Task.FromResult<CaseLifecycleAuditRead?>(
            new CaseLifecycleAuditRead(
                caseVersionId,
                Enumerable.Range(0, CaseLifecycleAuditLimits.MaxEvents + 1)
                    .Select(index => new CaseLifecycleAuditEvent(
                        Guid.Parse("123e4567-e89b-42d3-a456-426614174020"),
                        null,
                        "created",
                        null,
                        CaseLifecycleState.Draft,
                        null,
                        DateTimeOffset.UnixEpoch.AddMinutes(index)))
                    .ToArray(),
                false));
    }

    private static object ValidRequest() => new
    {
        schema = "evidrilo.case-authoring",
        version = "1",
        organizationId = "123e4567-e89b-42d3-a456-426614174001",
        document = new
        {
            content = new
            {
                caseId = "M0_T2",
                caseVersionId = "M0_T2:1",
                title = "Synthetic tablet case",
                contentHash = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                evaluatorVersion = "evaluator.v1",
                factAnchors = new[] { "fact-1", "fact-2" },
                skillTags = new[] { "evidence-linking" },
            },
            objective = "Connect evidence to a bounded claim",
            difficulty = 2,
            evidenceReferences = new[] { "fact-1" },
            facts = new[]
            {
                new { id = "fact-1", type = "observation", text = "The tablet was cold." },
                new { id = "fact-2", type = "limitation", text = "Temperature was not controlled." },
            },
            rules = new[] { new { id = "rule-1", outcome = "PASS", anchorIds = new[] { "fact-1" } } },
            variants = new[]
            {
                new { id = "challenge-1", removedFactIds = new[] { "fact-1" } },
            },
        },
    };
}
