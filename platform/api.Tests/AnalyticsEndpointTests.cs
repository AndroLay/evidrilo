using System.Net;
using System.Net.Http.Json;
using System.Text.Json;

namespace Evidrilo.Api.Tests;

public sealed class AnalyticsEndpointTests : IClassFixture<ApiFactory>
{
    private static readonly Guid UserId = Guid.Parse("123e4567-e89b-42d3-a456-426614174000");
    private readonly HttpClient client;

    public AnalyticsEndpointTests(ApiFactory factory)
    {
        client = factory.CreateClient();
    }

    [Fact]
    public async Task Analytics_requires_verified_identity()
    {
        using var response = await client.PostAsJsonAsync("/v1/analytics/events", ValidEvent());
        var body = await response.Content.ReadFromJsonAsync<JsonElement>();

        Assert.Equal(HttpStatusCode.Unauthorized, response.StatusCode);
        Assert.Equal("AUTH_REQUIRED", body.GetProperty("code").GetString());
    }

    [Fact]
    public async Task Analytics_rejects_unconsented_event_before_storage()
    {
        using var request = new HttpRequestMessage(HttpMethod.Post, "/v1/analytics/events")
        {
            Content = JsonContent.Create(ValidEvent("denied")),
        };
        request.Headers.Add("X-Test-User", $"{UserId}|true");

        using var response = await client.SendAsync(request);
        var body = await response.Content.ReadFromJsonAsync<JsonElement>();

        Assert.Equal(HttpStatusCode.Forbidden, response.StatusCode);
        Assert.Equal("ANALYTICS_CONSENT_REQUIRED", body.GetProperty("code").GetString());
    }

    [Fact]
    public async Task Analytics_rejects_an_event_without_an_event_name()
    {
        using var request = new HttpRequestMessage(HttpMethod.Post, "/v1/analytics/events")
        {
            Content = JsonContent.Create(new
            {
                schema = "evidrilo.analytics-event",
                version = "1",
                clientEventId = "123e4567-e89b-42d3-a456-426614174010",
                eventVersion = 1,
                occurredAt = "2026-09-10T09:00:00Z",
                source = "mobile",
                consent = "granted",
                properties = new
                {
                    attemptId = "123e4567-e89b-42d3-a456-426614174011",
                    caseVersionId = "M0_T2:1",
                    outcome = "PASS",
                    skillId = "evidence-linking",
                    revisionChanged = true,
                },
            }),
        };
        request.Headers.Add("X-Test-User", $"{UserId}|true");

        using var response = await client.SendAsync(request);
        var body = await response.Content.ReadFromJsonAsync<JsonElement>();

        Assert.Equal(HttpStatusCode.BadRequest, response.StatusCode);
        Assert.Equal("INVALID_ANALYTICS_EVENT", body.GetProperty("code").GetString());
    }

    [Fact]
    public async Task Analytics_rejects_an_event_without_a_source()
    {
        using var request = new HttpRequestMessage(HttpMethod.Post, "/v1/analytics/events")
        {
            Content = JsonContent.Create(new
            {
                schema = "evidrilo.analytics-event",
                version = "1",
                clientEventId = "123e4567-e89b-42d3-a456-426614174010",
                eventName = "attempt_completed",
                eventVersion = 1,
                occurredAt = "2026-09-10T09:00:00Z",
                consent = "granted",
                properties = new
                {
                    attemptId = "123e4567-e89b-42d3-a456-426614174011",
                    caseVersionId = "M0_T2:1",
                    outcome = "PASS",
                    skillId = "evidence-linking",
                    revisionChanged = true,
                },
            }),
        };
        request.Headers.Add("X-Test-User", $"{UserId}|true");

        using var response = await client.SendAsync(request);
        var body = await response.Content.ReadFromJsonAsync<JsonElement>();

        Assert.Equal(HttpStatusCode.BadRequest, response.StatusCode);
        Assert.Equal("INVALID_ANALYTICS_EVENT", body.GetProperty("code").GetString());
    }

    [Fact]
    public async Task Analytics_fails_closed_when_database_is_not_configured()
    {
        using var request = new HttpRequestMessage(HttpMethod.Post, "/v1/analytics/events")
        {
            Content = JsonContent.Create(ValidEvent()),
        };
        request.Headers.Add("X-Test-User", $"{UserId}|true");

        using var response = await client.SendAsync(request);
        var body = await response.Content.ReadFromJsonAsync<JsonElement>();

        Assert.Equal(HttpStatusCode.ServiceUnavailable, response.StatusCode);
        Assert.Equal("DATABASE_NOT_CONFIGURED", body.GetProperty("code").GetString());
    }

    private static object ValidEvent(string consent = "granted") => new
    {
        schema = "evidrilo.analytics-event",
        version = "1",
        clientEventId = "123e4567-e89b-42d3-a456-426614174010",
        eventName = "attempt_completed",
        eventVersion = 1,
        occurredAt = "2026-09-10T09:00:00Z",
        source = "mobile",
        consent,
        properties = new
        {
            attemptId = "123e4567-e89b-42d3-a456-426614174011",
            caseVersionId = "M0_T2:1",
            outcome = "PASS",
            skillId = "evidence-linking",
            revisionChanged = true,
        },
    };
}
