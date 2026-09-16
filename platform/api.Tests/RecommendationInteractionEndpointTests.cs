using System.Net;
using System.Net.Http.Json;
using System.Text.Json;

namespace Evidrilo.Api.Tests;

public sealed class RecommendationInteractionEndpointTests : IClassFixture<ApiFactory>
{
    private static readonly Guid UserId = Guid.Parse("123e4567-e89b-42d3-a456-426614174000");
    private readonly HttpClient client;

    public RecommendationInteractionEndpointTests(ApiFactory factory)
    {
        client = factory.CreateClient();
    }

    [Fact]
    public async Task Recommendation_interaction_requires_authenticated_identity()
    {
        using var response = await client.PostAsJsonAsync("/v1/recommendations/interactions", ValidRequest());
        var body = await response.Content.ReadFromJsonAsync<JsonElement>();

        Assert.Equal(HttpStatusCode.Unauthorized, response.StatusCode);
        Assert.Equal("AUTH_REQUIRED", body.GetProperty("code").GetString());
    }

    [Fact]
    public async Task Recommendation_interaction_rejects_missing_consent_before_storage()
    {
        using var request = new HttpRequestMessage(HttpMethod.Post, "/v1/recommendations/interactions")
        {
            Content = JsonContent.Create(ValidRequest(consent: "denied")),
        };
        request.Headers.Add("X-Test-User", $"{UserId}|true");

        using var response = await client.SendAsync(request);
        var body = await response.Content.ReadFromJsonAsync<JsonElement>();

        Assert.Equal(HttpStatusCode.Forbidden, response.StatusCode);
        Assert.Equal("RECOMMENDATION_CONSENT_REQUIRED", body.GetProperty("code").GetString());
    }

    [Fact]
    public async Task Recommendation_interaction_rejects_a_request_without_an_interaction()
    {
        using var request = new HttpRequestMessage(HttpMethod.Post, "/v1/recommendations/interactions")
        {
            Content = JsonContent.Create(new
            {
                schema = "evidrilo.recommendation-interaction",
                version = "1",
                clientEventId = "123e4567-e89b-42d3-a456-426614174010",
                caseVersionId = "M0_T2:1",
                calculationVersion = "recommendation.v1",
                reasonCode = "PRACTICE_ACTION_REQUIRED",
                consent = "granted",
            }),
        };
        request.Headers.Add("X-Test-User", $"{UserId}|true");

        using var response = await client.SendAsync(request);
        var body = await response.Content.ReadFromJsonAsync<JsonElement>();

        Assert.Equal(HttpStatusCode.BadRequest, response.StatusCode);
        Assert.Equal("INVALID_RECOMMENDATION_INTERACTION", body.GetProperty("code").GetString());
    }

    [Fact]
    public async Task Recommendation_interaction_fails_closed_when_database_is_not_configured()
    {
        using var request = new HttpRequestMessage(HttpMethod.Post, "/v1/recommendations/interactions")
        {
            Content = JsonContent.Create(ValidRequest()),
        };
        request.Headers.Add("X-Test-User", $"{UserId}|true");

        using var response = await client.SendAsync(request);
        var body = await response.Content.ReadFromJsonAsync<JsonElement>();

        Assert.Equal(HttpStatusCode.ServiceUnavailable, response.StatusCode);
        Assert.Equal("DATABASE_NOT_CONFIGURED", body.GetProperty("code").GetString());
    }

    private static object ValidRequest(string consent = "granted") => new
    {
        schema = "evidrilo.recommendation-interaction",
        version = "1",
        clientEventId = "123e4567-e89b-42d3-a456-426614174010",
        caseVersionId = "M0_T2:1",
        interaction = "accepted",
        calculationVersion = "recommendation.v1",
        reasonCode = "PRACTICE_ACTION_REQUIRED",
        consent,
    };
}
