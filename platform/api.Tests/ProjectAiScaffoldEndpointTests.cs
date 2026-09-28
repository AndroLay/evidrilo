using System.Net;
using System.Text;
using System.Text.Json;
using Evidrilo.Api.ProjectAi;
using Microsoft.AspNetCore.Http.Json;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Options;

namespace Evidrilo.Api.Tests;

public sealed class ProjectAiScaffoldEndpointTests : IClassFixture<ApiFactory>
{
    private static readonly Guid AccountId = Guid.Parse("b72a3ddd-fec0-4b6a-9b63-12724da76fa6");
    private readonly HttpClient client;
    private readonly JsonSerializerOptions httpJsonSerializerOptions;

    public ProjectAiScaffoldEndpointTests(ApiFactory factory)
    {
        client = factory.CreateClient();
        httpJsonSerializerOptions = factory.Services
            .GetRequiredService<IOptions<JsonOptions>>()
            .Value
            .SerializerOptions;
    }

    [Fact]
    public void Scaffold_response_keeps_nullable_keys_under_the_api_http_serializer()
    {
        var scaffold = new ProjectAiScaffoldOutput(
            "reviewed-template",
            1,
            "project-scaffold.v1",
            "Review the framing against the assignment instructions.",
            [],
            [],
            ["What evidence would answer this question?"]);
        var response = new ProjectAiScaffoldResponse(
            "evidrilo.project-ai-scaffold",
            "1",
            "preview",
            scaffold,
            BaseProjectRevision: null,
            ReasonCode: null,
            RequestId: "project-ai-request-0001",
            CreditCost: 3,
            Operation: ProjectAiScaffoldValidator.CreateOperation,
            ProjectId: null);

        using var body = JsonDocument.Parse(JsonSerializer.Serialize(response, httpJsonSerializerOptions));

        AssertExactKeys(body.RootElement,
            "schema", "version", "status", "scaffold", "baseProjectRevision", "reasonCode", "requestId", "creditCost", "operation", "projectId");
        Assert.Equal(JsonValueKind.Null, body.RootElement.GetProperty("baseProjectRevision").ValueKind);
        Assert.Equal(ProjectAiScaffoldValidator.CreateOperation, body.RootElement.GetProperty("operation").GetString());
        Assert.Equal(JsonValueKind.Null, body.RootElement.GetProperty("projectId").ValueKind);
        Assert.Equal(JsonValueKind.Null, body.RootElement.GetProperty("reasonCode").ValueKind);
        Assert.Equal(3, body.RootElement.GetProperty("creditCost").GetInt32());

        using var unrelated = JsonDocument.Parse(JsonSerializer.Serialize(
            new { optional = (string?)null },
            httpJsonSerializerOptions));
        Assert.False(unrelated.RootElement.TryGetProperty("optional", out _));
    }

    [Fact]
    public async Task Scaffold_request_requires_authentication_and_verified_email()
    {
        using var anonymous = await client.PostAsync("/v1/project-ai/scaffold", Json(ValidRequest()));
        using var unverifiedRequest = Request(ValidRequest());
        unverifiedRequest.Headers.Add("X-Test-User", $"{AccountId}|false");
        using var unverified = await client.SendAsync(unverifiedRequest);

        Assert.Equal(HttpStatusCode.Unauthorized, anonymous.StatusCode);
        Assert.Equal(HttpStatusCode.Forbidden, unverified.StatusCode);
    }

    [Fact]
    public async Task Scaffold_request_fails_closed_when_server_consent_storage_is_unavailable()
    {
        using var request = Request(ValidRequest(projectDataConsent: false));
        request.Headers.Add("X-Test-User", $"{AccountId}|true");
        using var response = await client.SendAsync(request);
        using var body = JsonDocument.Parse(await response.Content.ReadAsStringAsync());

        Assert.Equal(HttpStatusCode.ServiceUnavailable, response.StatusCode);
        Assert.Equal("DATABASE_NOT_CONFIGURED", body.RootElement.GetProperty("code").GetString());
    }

    [Fact]
    public async Task Valid_scaffold_request_fails_closed_while_provider_is_not_ready()
    {
        using var request = Request(ValidRequest());
        request.Headers.Add("X-Test-User", $"{AccountId}|true");
        using var response = await client.SendAsync(request);
        using var body = JsonDocument.Parse(await response.Content.ReadAsStringAsync());

        Assert.Equal(HttpStatusCode.ServiceUnavailable, response.StatusCode);
        Assert.Equal("DATABASE_NOT_CONFIGURED", body.RootElement.GetProperty("code").GetString());
    }

    private static HttpRequestMessage Request(string json)
    {
        var request = new HttpRequestMessage(HttpMethod.Post, "/v1/project-ai/scaffold")
        {
            Content = new StringContent(json, Encoding.UTF8, "application/json"),
        };
        request.Headers.Add("Idempotency-Key", "project-ai-request-0001");
        return request;
    }

    private static StringContent Json(string json) => new(json, Encoding.UTF8, "application/json");

    private static string ValidRequest(bool projectDataConsent = true) => JsonSerializer.Serialize(new
    {
        schema = "evidrilo.project-ai-scaffold",
        version = "1",
        operation = ProjectAiScaffoldValidator.CreateOperation,
        projectId = (string?)null,
        templateId = "reviewed-template",
        templateVersion = 1,
        baseProjectRevision = (int?)null,
        assignmentBrief = "Compare two measurements and explain the reasoning.",
        researchQuestion = "How does the measured outcome vary?",
        studentQuestion = "Help me define a manageable first step.",
        currentFields = new Dictionary<string, string>(),
        constraints = new[] { "Limited time" },
        locale = "en",
        optedIn = true,
        projectDataConsent,
        projectDataConsentVersion = "project-ai-data.v1",
    });

    private static void AssertExactKeys(JsonElement element, params string[] expectedKeys)
    {
        var actualKeys = element.EnumerateObject()
            .Select(property => property.Name)
            .OrderBy(name => name, StringComparer.Ordinal)
            .ToArray();
        Assert.Equal(expectedKeys.OrderBy(name => name, StringComparer.Ordinal), actualKeys);
    }
}
