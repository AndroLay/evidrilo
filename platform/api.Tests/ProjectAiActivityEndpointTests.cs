using Evidrilo.Api;
using Microsoft.AspNetCore.RateLimiting;
using Microsoft.AspNetCore.Routing;
using Microsoft.Extensions.DependencyInjection;

namespace Evidrilo.Api.Tests;

public sealed class ProjectAiActivityEndpointTests(ApiFactory factory) : IClassFixture<ApiFactory>
{
    [Theory]
    [InlineData("GET", "/v1/project-ai/activity")]
    [InlineData("DELETE", "/v1/project-ai/activity/general")]
    public void Project_ai_activity_routes_use_the_api_rate_limit_policy(string method, string path)
    {
        var dataSource = factory.Services.GetRequiredService<EndpointDataSource>();
        var endpoint = Assert.Single(
            dataSource.Endpoints,
            candidate => candidate is RouteEndpoint route
                && route.RoutePattern.RawText == path
                && candidate.Metadata.GetMetadata<IHttpMethodMetadata>()?.HttpMethods.Contains(method) == true);

        Assert.Equal("api", endpoint.Metadata.GetMetadata<EnableRateLimitingAttribute>()?.PolicyName);
    }
}
