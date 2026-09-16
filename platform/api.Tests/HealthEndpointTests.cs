using System.Net;
using System.Net.Http.Json;

namespace Evidrilo.Api.Tests;

public sealed class HealthEndpointTests : IClassFixture<ApiFactory>
{
    private readonly HttpClient client;

    public HealthEndpointTests(ApiFactory factory)
    {
        client = factory.CreateClient();
    }

    [Fact]
    public async Task Live_endpoint_is_dependency_free_and_propagates_request_id()
    {
        using var request = new HttpRequestMessage(HttpMethod.Get, "/health/live");
        request.Headers.Add("X-Request-Id", "req-health-001");

        using var response = await client.SendAsync(request);
        var body = await response.Content.ReadFromJsonAsync<JsonElement>();

        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        Assert.Equal("req-health-001", response.Headers.GetValues("X-Request-Id").Single());
        Assert.Equal("evidrilo.health", body.GetProperty("schema").GetString());
        Assert.Equal("live", body.GetProperty("check").GetString());
        Assert.Equal("ok", body.GetProperty("status").GetString());
        Assert.False(body.TryGetProperty("dependencies", out _));
    }

    [Fact]
    public async Task Readiness_is_degraded_without_external_configuration()
    {
        using var response = await client.GetAsync("/health/ready");
        var body = await response.Content.ReadFromJsonAsync<JsonElement>();

        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        Assert.Equal("ready", body.GetProperty("check").GetString());
        Assert.Equal("degraded", body.GetProperty("status").GetString());
        Assert.Equal("missing", body.GetProperty("dependencies").GetProperty("config").GetString());
        Assert.Equal("missing", body.GetProperty("dependencies").GetProperty("auth").GetString());
        Assert.Equal("missing", body.GetProperty("dependencies").GetProperty("database").GetString());
        Assert.False(body.ToString().Contains("supabase", StringComparison.OrdinalIgnoreCase));
    }
}
