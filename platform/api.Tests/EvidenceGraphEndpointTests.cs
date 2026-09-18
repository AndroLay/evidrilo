using System.Net;
using System.Net.Http.Json;
using System.Text.Json;
using Evidrilo.Api.Content;
using Microsoft.AspNetCore.RateLimiting;
using Microsoft.AspNetCore.Hosting;
using Microsoft.AspNetCore.Routing;
using Microsoft.AspNetCore.TestHost;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.DependencyInjection.Extensions;

namespace Evidrilo.Api.Tests;

public sealed class EvidenceGraphEndpointTests : IClassFixture<ApiFactory>
{
    private static readonly Guid UserId = Guid.Parse("123e4567-e89b-42d3-a456-426614174000");
    private readonly HttpClient client;
    private readonly ApiFactory factory;

    public EvidenceGraphEndpointTests(ApiFactory factory)
    {
        this.factory = factory;
        client = factory.CreateClient();
    }

    [Fact]
    public void Evidence_graph_uses_the_api_rate_limit_policy()
    {
        var dataSource = factory.Services.GetRequiredService<EndpointDataSource>();
        var endpoint = Assert.Single(
            dataSource.Endpoints,
            candidate => candidate.Metadata.GetMetadata<IHttpMethodMetadata>()
                ?.HttpMethods.Contains("GET") == true
                && candidate.DisplayName?.EndsWith(
                    "/v1/cases/{caseVersionId}/evidence-graph",
                    StringComparison.Ordinal) == true);

        var policy = endpoint.Metadata.GetMetadata<EnableRateLimitingAttribute>();

        Assert.NotNull(policy);
        Assert.Equal("api", policy!.PolicyName);
    }

    [Fact]
    public async Task Evidence_graph_requires_authenticated_identity()
    {
        using var response = await client.GetAsync("/v1/cases/case-1:v1/evidence-graph");
        var body = await response.Content.ReadFromJsonAsync<JsonElement>();

        Assert.Equal(HttpStatusCode.Unauthorized, response.StatusCode);
        Assert.Equal("AUTH_REQUIRED", body.GetProperty("code").GetString());
    }

    [Fact]
    public async Task Evidence_graph_requires_verified_identity()
    {
        using var request = new HttpRequestMessage(
            HttpMethod.Get,
            "/v1/cases/case-1:v1/evidence-graph");
        request.Headers.Add("X-Test-User", $"{UserId}|false");

        using var response = await client.SendAsync(request);
        var body = await response.Content.ReadFromJsonAsync<JsonElement>();

        Assert.Equal(HttpStatusCode.Forbidden, response.StatusCode);
        Assert.Equal("FORBIDDEN", body.GetProperty("code").GetString());
    }

    [Fact]
    public async Task Evidence_graph_rejects_an_invalid_case_version()
    {
        using var request = new HttpRequestMessage(
            HttpMethod.Get,
            "/v1/cases/not%20valid/evidence-graph");
        request.Headers.Add("X-Test-User", $"{UserId}|true");

        using var response = await client.SendAsync(request);
        var body = await response.Content.ReadFromJsonAsync<JsonElement>();

        Assert.Equal(HttpStatusCode.BadRequest, response.StatusCode);
        Assert.Equal("INVALID_CASE_VERSION", body.GetProperty("code").GetString());
    }

    [Fact]
    public async Task Evidence_graph_fails_closed_when_database_is_not_configured()
    {
        using var request = new HttpRequestMessage(
            HttpMethod.Get,
            "/v1/cases/case-1:v1/evidence-graph");
        request.Headers.Add("X-Test-User", $"{UserId}|true");

        using var response = await client.SendAsync(request);
        var body = await response.Content.ReadFromJsonAsync<JsonElement>();

        Assert.Equal(HttpStatusCode.ServiceUnavailable, response.StatusCode);
        Assert.Equal("DATABASE_NOT_CONFIGURED", body.GetProperty("code").GetString());
    }

    [Fact]
    public async Task Evidence_graph_returns_the_versioned_relationship_contract()
    {
        using var testFactory = new ApiFactory().WithWebHostBuilder(builder =>
            builder.ConfigureTestServices(services =>
            {
                services.RemoveAll<ICaseStore>();
                services.AddSingleton<ICaseStore, PublishedCaseStore>();
            }));
        using var testClient = testFactory.CreateClient();
        using var request = new HttpRequestMessage(
            HttpMethod.Get,
            "/v1/cases/case-1:v1/evidence-graph");
        request.Headers.Add("X-Test-User", $"{UserId}|true");

        using var response = await testClient.SendAsync(request);
        var body = await response.Content.ReadFromJsonAsync<JsonElement>();

        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        Assert.Equal("evidrilo.evidence-graph", body.GetProperty("schema").GetString());
        Assert.Equal("1", body.GetProperty("version").GetString());
        Assert.Equal("case-1:v1", body.GetProperty("caseVersionId").GetString());
        Assert.Equal(5, body.GetProperty("nodes").GetArrayLength());
        Assert.Equal(5, body.GetProperty("edges").GetArrayLength());
        Assert.Equal(
            "verified_by",
            body.GetProperty("edges")[4].GetProperty("relation").GetString());
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

        public Task<IReadOnlyList<PublishedCaseListItem>> ListPublishedAsync(
            Guid accountId,
            CancellationToken cancellationToken) => Task.FromResult<IReadOnlyList<PublishedCaseListItem>>(
            Array.Empty<PublishedCaseListItem>());
    }
}
