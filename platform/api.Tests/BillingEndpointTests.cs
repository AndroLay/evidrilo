using System.Net;
using System.Net.Http.Json;
using System.Text;
using System.Text.Json;
using Microsoft.AspNetCore.Routing;
using Microsoft.AspNetCore.RateLimiting;
using Microsoft.Extensions.DependencyInjection;

namespace Evidrilo.Api.Tests;

public sealed class BillingEndpointTests : IClassFixture<ApiFactory>
{
    private readonly HttpClient client;
    private readonly ApiFactory factory;

    public BillingEndpointTests(ApiFactory factory)
    {
        this.factory = factory;
        client = factory.CreateClient();
    }

    [Fact]
    public async Task Billing_webhook_fails_closed_without_secret_configuration()
    {
        using var request = new HttpRequestMessage(HttpMethod.Post, "/v1/billing/webhook")
        {
            Content = new StringContent("{}", Encoding.UTF8, "application/json"),
        };
        request.Headers.Add("X-RevenueCat-Webhook-Signature", "t=0,v1=synthetic");

        using var response = await client.SendAsync(request);
        var body = await response.Content.ReadFromJsonAsync<JsonElement>();

        Assert.Equal(HttpStatusCode.ServiceUnavailable, response.StatusCode);
        Assert.Equal("BILLING_NOT_CONFIGURED", body.GetProperty("code").GetString());
    }

    [Fact]
    public void Billing_webhook_uses_a_dedicated_rate_limit_policy()
    {
        var dataSource = factory.Services.GetRequiredService<EndpointDataSource>();
        var endpoint = Assert.Single(
            dataSource.Endpoints,
            candidate => candidate.Metadata.GetMetadata<IHttpMethodMetadata>()
                ?.HttpMethods.Contains("POST") == true
                && candidate.DisplayName?.Contains("/v1/billing/webhook", StringComparison.Ordinal) == true);

        var policy = endpoint.Metadata.GetMetadata<EnableRateLimitingAttribute>();

        Assert.NotNull(policy);
        Assert.Equal("billing-webhook", policy!.PolicyName);
    }
}
