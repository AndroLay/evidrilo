using System.Net;
using System.Net.Http.Json;
using System.Text.Json;
using Evidrilo.Api.Content;
using Microsoft.AspNetCore.RateLimiting;
using Microsoft.AspNetCore.Routing;
using Microsoft.AspNetCore.TestHost;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.DependencyInjection.Extensions;

namespace Evidrilo.Api.Tests;

public sealed class CaseEndpointTests : IClassFixture<ApiFactory>
{
    private readonly HttpClient client;
    private readonly ApiFactory factory;

    public CaseEndpointTests(ApiFactory factory)
    {
        this.factory = factory;
        client = factory.CreateClient();
    }

    [Fact]
    public void Case_read_uses_the_api_rate_limit_policy()
    {
        var dataSource = factory.Services.GetRequiredService<EndpointDataSource>();
        var endpoint = Assert.Single(
            dataSource.Endpoints,
            candidate => candidate.Metadata.GetMetadata<IHttpMethodMetadata>()
                ?.HttpMethods.Contains("GET") == true
                && candidate.DisplayName?.Contains("/v1/cases/{caseVersionId}", StringComparison.Ordinal) == true);

        var policy = endpoint.Metadata.GetMetadata<EnableRateLimitingAttribute>();

        Assert.NotNull(policy);
        Assert.Equal("api", policy!.PolicyName);
    }

    [Fact]
    public async Task Case_read_requires_verified_identity()
    {
        using var response = await client.GetAsync("/v1/cases/M0_T2:1");
        var body = await response.Content.ReadFromJsonAsync<JsonElement>();

        Assert.Equal(HttpStatusCode.Unauthorized, response.StatusCode);
        Assert.Equal("AUTH_REQUIRED", body.GetProperty("code").GetString());
    }

    [Fact]
    public async Task Case_read_fails_closed_without_database()
    {
        using var request = new HttpRequestMessage(HttpMethod.Get, "/v1/cases/M0_T2:1");
        request.Headers.Add("X-Test-User", "123e4567-e89b-42d3-a456-426614174000|true");

        using var response = await client.SendAsync(request);
        var body = await response.Content.ReadFromJsonAsync<JsonElement>();

        Assert.Equal(HttpStatusCode.ServiceUnavailable, response.StatusCode);
        Assert.Equal("DATABASE_NOT_CONFIGURED", body.GetProperty("code").GetString());
    }

    [Fact]
    public async Task Case_read_rejects_unverified_identity()
    {
        using var request = new HttpRequestMessage(HttpMethod.Get, "/v1/cases/M0_T2:1");
        request.Headers.Add("X-Test-User", "123e4567-e89b-42d3-a456-426614174000|false");

        using var response = await client.SendAsync(request);
        var body = await response.Content.ReadFromJsonAsync<JsonElement>();

        Assert.Equal(HttpStatusCode.Forbidden, response.StatusCode);
        Assert.Equal("FORBIDDEN", body.GetProperty("code").GetString());
    }

    [Fact]
    public async Task Case_read_returns_the_canonical_learning_content()
    {
        using var testFactory = new ApiFactory().WithWebHostBuilder(builder =>
            builder.ConfigureTestServices(services =>
            {
                services.RemoveAll<ICaseStore>();
                services.AddSingleton<ICaseStore, PublishedCaseStore>();
            }));
        using var testClient = testFactory.CreateClient();
        using var request = new HttpRequestMessage(HttpMethod.Get, "/v1/cases/case-1:v1");
        request.Headers.Add("X-Test-User", "123e4567-e89b-42d3-a456-426614174000|true");

        using var response = await testClient.SendAsync(request);
        var body = await response.Content.ReadFromJsonAsync<JsonElement>();

        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        Assert.Equal("Connect evidence to a bounded claim", body.GetProperty("objective").GetString());
        Assert.Equal("observation", body.GetProperty("facts")[0].GetProperty("type").GetString());
        Assert.Equal("limitation", body.GetProperty("facts")[1].GetProperty("type").GetString());
        Assert.Equal("PASS", body.GetProperty("rules")[0].GetProperty("outcome").GetString());
        Assert.Equal("challenge-1", body.GetProperty("variants")[0].GetProperty("id").GetString());
    }

    private sealed class PublishedCaseStore : ICaseStore
    {
        public Task<PublishedCaseSummary?> GetPublishedAsync(
            Guid accountId,
            string caseVersionId,
            CancellationToken cancellationToken) => Task.FromResult<PublishedCaseSummary?>(
            new PublishedCaseSummary(
                "case-1",
                "case-1:v1",
                "A bounded case",
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                "evaluator.v1",
                ["evidence"],
                new PublishedCaseContent(
                    "Connect evidence to a bounded claim",
                    2,
                    ["fact-observation"],
                    [
                        new AuthoringFact("fact-observation", "observation", "The tablet was cold."),
                        new AuthoringFact("fact-limitation", "limitation", "Temperature was not controlled."),
                    ],
                    [new AuthoringRule("rule-1", "PASS", ["fact-observation"])],
                    [new AuthoringVariant("challenge-1", ["fact-observation"])])
            ));
    }
}
