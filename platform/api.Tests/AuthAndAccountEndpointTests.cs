using System.Net;
using System.Net.Http.Json;
using Microsoft.AspNetCore.RateLimiting;
using Microsoft.AspNetCore.Routing;
using Microsoft.Extensions.DependencyInjection;

namespace Evidrilo.Api.Tests;

public sealed class AuthAndAccountEndpointTests : IClassFixture<ApiFactory>
{
    private static readonly Guid UserId = Guid.Parse("123e4567-e89b-42d3-a456-426614174000");
    private readonly HttpClient client;
    private readonly ApiFactory factory;

    public AuthAndAccountEndpointTests(ApiFactory factory)
    {
        this.factory = factory;
        client = factory.CreateClient();
    }

    [Fact]
    public void Account_summary_uses_the_api_rate_limit_policy()
    {
        var dataSource = factory.Services.GetRequiredService<EndpointDataSource>();
        var endpoint = Assert.Single(
            dataSource.Endpoints,
            candidate => candidate is RouteEndpoint route
                && route.RoutePattern.RawText == "/v1/account/me"
                && candidate.Metadata.GetMetadata<IHttpMethodMetadata>()
                ?.HttpMethods.Contains("GET") == true
            );

        var policy = endpoint.Metadata.GetMetadata<EnableRateLimitingAttribute>();

        Assert.NotNull(policy);
        Assert.Equal("api", policy!.PolicyName);
    }

    [Fact]
    public async Task Account_rate_limit_partitions_authenticated_requests_by_account()
    {
        using var isolatedFactory = new ApiFactory();
        using var isolatedClient = isolatedFactory.CreateClient();
        var firstAccount = Guid.Parse("123e4567-e89b-42d3-a456-426614174000");
        var secondAccount = Guid.Parse("223e4567-e89b-42d3-a456-426614174000");

        for (var index = 0; index < 60; index++)
        {
            using var request = new HttpRequestMessage(HttpMethod.Get, "/v1/account/me");
            request.Headers.Add("X-Test-User", $"{firstAccount}|true");
            using var response = await isolatedClient.SendAsync(request);

            Assert.NotEqual(HttpStatusCode.TooManyRequests, response.StatusCode);
        }

        using var secondRequest = new HttpRequestMessage(HttpMethod.Get, "/v1/account/me");
        secondRequest.Headers.Add("X-Test-User", $"{secondAccount}|true");
        using var secondResponse = await isolatedClient.SendAsync(secondRequest);

        Assert.NotEqual(HttpStatusCode.TooManyRequests, secondResponse.StatusCode);
    }

    [Fact]
    public async Task Account_endpoint_rejects_anonymous_requests_with_safe_error_contract()
    {
        using var response = await client.GetAsync("/v1/account/me");
        var body = await response.Content.ReadFromJsonAsync<JsonElement>();

        Assert.Equal(HttpStatusCode.Unauthorized, response.StatusCode);
        Assert.Equal("AUTH_REQUIRED", body.GetProperty("code").GetString());
        Assert.Equal("evidrilo.http-error", body.GetProperty("schema").GetString());
        Assert.DoesNotContain("token", body.ToString(), StringComparison.OrdinalIgnoreCase);
    }

    [Fact]
    public async Task Account_endpoint_uses_verified_subject_and_returns_only_public_summary()
    {
        using var request = new HttpRequestMessage(HttpMethod.Get, "/v1/account/me");
        request.Headers.Add("X-Test-User", $"{UserId}|true");

        using var response = await client.SendAsync(request);
        var body = await response.Content.ReadFromJsonAsync<JsonElement>();

        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        Assert.Equal(UserId.ToString(), body.GetProperty("accountId").GetString());
        Assert.True(body.GetProperty("emailVerified").GetBoolean());
        Assert.True(body.TryGetProperty("requestId", out _));
        Assert.False(body.TryGetProperty("accessToken", out _));
        Assert.False(body.TryGetProperty("refreshToken", out _));
        Assert.False(body.TryGetProperty("email", out _));
    }

    [Fact]
    public async Task Account_endpoint_rejects_unverified_identity_without_account_discovery()
    {
        using var request = new HttpRequestMessage(HttpMethod.Get, "/v1/account/me");
        request.Headers.Add("X-Test-User", $"{UserId}|false");

        using var response = await client.SendAsync(request);
        var body = await response.Content.ReadFromJsonAsync<JsonElement>();

        Assert.Equal(HttpStatusCode.Forbidden, response.StatusCode);
        Assert.Equal("FORBIDDEN", body.GetProperty("code").GetString());
        Assert.DoesNotContain(UserId.ToString(), body.ToString(), StringComparison.Ordinal);
    }

    [Fact]
    public async Task Account_export_requires_verified_identity()
    {
        using var request = new HttpRequestMessage(HttpMethod.Get, "/v1/account/me/export");
        request.Headers.Add("X-Test-User", $"{UserId}|false");

        using var response = await client.SendAsync(request);
        var body = await response.Content.ReadFromJsonAsync<JsonElement>();

        Assert.Equal(HttpStatusCode.Forbidden, response.StatusCode);
        Assert.Equal("FORBIDDEN", body.GetProperty("code").GetString());
    }

    [Fact]
    public async Task Account_export_rejects_anonymous_requests_without_account_discovery()
    {
        using var response = await client.GetAsync("/v1/account/me/export");
        var body = await response.Content.ReadFromJsonAsync<JsonElement>();

        Assert.Equal(HttpStatusCode.Unauthorized, response.StatusCode);
        Assert.Equal("AUTH_REQUIRED", body.GetProperty("code").GetString());
        Assert.DoesNotContain(UserId.ToString(), body.ToString(), StringComparison.Ordinal);
    }

    [Fact]
    public async Task Account_export_fails_closed_without_database_configuration()
    {
        using var request = new HttpRequestMessage(HttpMethod.Get, "/v1/account/me/export");
        request.Headers.Add("X-Test-User", $"{UserId}|true");

        using var response = await client.SendAsync(request);
        var body = await response.Content.ReadFromJsonAsync<JsonElement>();

        Assert.Equal(HttpStatusCode.ServiceUnavailable, response.StatusCode);
        Assert.Equal("DATABASE_NOT_CONFIGURED", body.GetProperty("code").GetString());
    }
}
