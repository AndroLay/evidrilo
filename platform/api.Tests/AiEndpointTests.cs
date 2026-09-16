using System.Net;
using System.Net.Http.Json;
using System.Text.Json;

namespace Evidrilo.Api.Tests;

public sealed class AiEndpointTests : IClassFixture<ApiFactory>
{
    private static readonly Guid UserId = Guid.Parse("123e4567-e89b-42d3-a456-426614174000");
    private readonly HttpClient client;

    public AiEndpointTests(ApiFactory factory)
    {
        client = factory.CreateClient();
    }

    [Fact]
    public async Task Ai_assistance_requires_authenticated_identity()
    {
        using var response = await client.PostAsJsonAsync("/v1/ai/assist", ValidRequest());
        var body = await response.Content.ReadFromJsonAsync<JsonElement>();

        Assert.Equal(HttpStatusCode.Unauthorized, response.StatusCode);
        Assert.Equal("AUTH_REQUIRED", body.GetProperty("code").GetString());
    }

    [Fact]
    public async Task Ai_assistance_requires_verified_identity()
    {
        using var request = new HttpRequestMessage(HttpMethod.Post, "/v1/ai/assist")
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
    public async Task Ai_assistance_defaults_to_explicit_opt_in_fallback()
    {
        using var request = new HttpRequestMessage(HttpMethod.Post, "/v1/ai/assist")
        {
            Content = JsonContent.Create(ValidRequest(optedIn: false)),
        };
        request.Headers.Add("X-Test-User", $"{UserId}|true");

        using var response = await client.SendAsync(request);
        var body = await response.Content.ReadFromJsonAsync<JsonElement>();

        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        Assert.Equal("fallback", body.GetProperty("status").GetString());
        Assert.Equal("AI_OPT_IN_REQUIRED", body.GetProperty("reasonCode").GetString());
        Assert.Null(body.GetProperty("text").GetString());
    }

    [Fact]
    public async Task Ai_assistance_rejects_a_request_without_a_purpose()
    {
        using var request = new HttpRequestMessage(HttpMethod.Post, "/v1/ai/assist")
        {
            Content = JsonContent.Create(new
            {
                input = "Explain the evidence anchor.",
                locale = "en-US",
                optedIn = true,
            }),
        };
        request.Headers.Add("X-Test-User", $"{UserId}|true");

        using var response = await client.SendAsync(request);
        var body = await response.Content.ReadFromJsonAsync<JsonElement>();

        Assert.Equal(HttpStatusCode.BadRequest, response.StatusCode);
        Assert.Equal("INVALID_AI_REQUEST", body.GetProperty("code").GetString());
    }

    [Fact]
    public async Task Ai_assistance_fails_closed_when_audit_database_is_not_configured()
    {
        using var request = new HttpRequestMessage(HttpMethod.Post, "/v1/ai/assist")
        {
            Content = JsonContent.Create(ValidRequest(optedIn: true)),
        };
        request.Headers.Add("X-Test-User", $"{UserId}|true");

        using var response = await client.SendAsync(request);
        var body = await response.Content.ReadFromJsonAsync<JsonElement>();

        Assert.Equal(HttpStatusCode.ServiceUnavailable, response.StatusCode);
        Assert.Equal("DATABASE_NOT_CONFIGURED", body.GetProperty("code").GetString());
    }

    private static object ValidRequest(bool optedIn = true) => new
    {
        purpose = "explain_feedback",
        input = "Explain the evidence anchor.",
        locale = "en-US",
        optedIn,
    };
}
