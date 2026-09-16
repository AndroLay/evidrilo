using System.Net;
using System.Net.Http.Json;
using System.Text.Json;

namespace Evidrilo.Api.Tests;

public sealed class ProgressEndpointTests : IClassFixture<ApiFactory>
{
    private static readonly Guid UserId = Guid.Parse("123e4567-e89b-42d3-a456-426614174000");
    private readonly HttpClient client;

    public ProgressEndpointTests(ApiFactory factory)
    {
        client = factory.CreateClient();
    }

    [Fact]
    public async Task Progress_requires_authenticated_identity()
    {
        using var response = await client.GetAsync("/v1/progress/me");
        var body = await response.Content.ReadFromJsonAsync<JsonElement>();

        Assert.Equal(HttpStatusCode.Unauthorized, response.StatusCode);
        Assert.Equal("AUTH_REQUIRED", body.GetProperty("code").GetString());
    }

    [Fact]
    public async Task Progress_requires_verified_identity()
    {
        using var request = new HttpRequestMessage(HttpMethod.Get, "/v1/progress/me");
        request.Headers.Add("X-Test-User", $"{UserId}|false");

        using var response = await client.SendAsync(request);
        var body = await response.Content.ReadFromJsonAsync<JsonElement>();

        Assert.Equal(HttpStatusCode.Forbidden, response.StatusCode);
        Assert.Equal("FORBIDDEN", body.GetProperty("code").GetString());
    }

    [Fact]
    public async Task Progress_fails_closed_when_database_is_not_configured()
    {
        using var request = new HttpRequestMessage(HttpMethod.Get, "/v1/progress/me");
        request.Headers.Add("X-Test-User", $"{UserId}|true");

        using var response = await client.SendAsync(request);
        var body = await response.Content.ReadFromJsonAsync<JsonElement>();

        Assert.Equal(HttpStatusCode.ServiceUnavailable, response.StatusCode);
        Assert.Equal("DATABASE_NOT_CONFIGURED", body.GetProperty("code").GetString());
    }

    [Fact]
    public async Task Daily_progress_rejects_invalid_date_range_before_database_access()
    {
        using var request = new HttpRequestMessage(
            HttpMethod.Get,
            "/v1/progress/daily?from=not-a-date&to=2026-09-10");
        request.Headers.Add("X-Test-User", $"{UserId}|true");

        using var response = await client.SendAsync(request);
        var body = await response.Content.ReadFromJsonAsync<JsonElement>();

        Assert.Equal(HttpStatusCode.BadRequest, response.StatusCode);
        Assert.Equal("INVALID_PROGRESS_RANGE", body.GetProperty("code").GetString());
    }

    [Fact]
    public async Task Daily_progress_requires_authenticated_identity()
    {
        using var response = await client.GetAsync("/v1/progress/daily");
        var body = await response.Content.ReadFromJsonAsync<JsonElement>();

        Assert.Equal(HttpStatusCode.Unauthorized, response.StatusCode);
        Assert.Equal("AUTH_REQUIRED", body.GetProperty("code").GetString());
    }

    [Fact]
    public async Task Daily_progress_fails_closed_when_database_is_not_configured()
    {
        using var request = new HttpRequestMessage(HttpMethod.Get, "/v1/progress/daily");
        request.Headers.Add("X-Test-User", $"{UserId}|true");

        using var response = await client.SendAsync(request);
        var body = await response.Content.ReadFromJsonAsync<JsonElement>();

        Assert.Equal(HttpStatusCode.ServiceUnavailable, response.StatusCode);
        Assert.Equal("DATABASE_NOT_CONFIGURED", body.GetProperty("code").GetString());
    }
}
