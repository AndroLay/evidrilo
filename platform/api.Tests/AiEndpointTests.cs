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
    public async Task Ai_credits_require_authenticated_identity()
    {
        using var response = await client.GetAsync("/v1/ai/credits");
        var body = await response.Content.ReadFromJsonAsync<JsonElement>();

        Assert.Equal(HttpStatusCode.Unauthorized, response.StatusCode);
        Assert.Equal("AUTH_REQUIRED", body.GetProperty("code").GetString());
    }

    [Fact]
    public async Task Ai_credits_fail_closed_when_database_is_not_configured()
    {
        using var request = new HttpRequestMessage(HttpMethod.Get, "/v1/ai/credits");
        request.Headers.Add("X-Test-User", $"{UserId}|true");

        using var response = await client.SendAsync(request);
        var body = await response.Content.ReadFromJsonAsync<JsonElement>();

        Assert.Equal(HttpStatusCode.ServiceUnavailable, response.StatusCode);
        Assert.Equal("DATABASE_NOT_CONFIGURED", body.GetProperty("code").GetString());
    }

    [Fact]
    public async Task Ai_conversation_creation_requires_authenticated_identity()
    {
        using var response = await client.PostAsJsonAsync("/v1/ai/conversations", ValidConversationStartRequest());
        var body = await response.Content.ReadFromJsonAsync<JsonElement>();

        Assert.Equal(HttpStatusCode.Unauthorized, response.StatusCode);
        Assert.Equal("AUTH_REQUIRED", body.GetProperty("code").GetString());
    }

    [Fact]
    public async Task Ai_conversation_creation_requires_an_idempotency_key()
    {
        using var request = new HttpRequestMessage(HttpMethod.Post, "/v1/ai/conversations")
        {
            Content = JsonContent.Create(ValidConversationStartRequest()),
        };
        request.Headers.Add("X-Test-User", $"{UserId}|true");

        using var response = await client.SendAsync(request);
        var body = await response.Content.ReadFromJsonAsync<JsonElement>();

        Assert.Equal(HttpStatusCode.BadRequest, response.StatusCode);
        Assert.Equal("INVALID_IDEMPOTENCY_KEY", body.GetProperty("code").GetString());
    }

    [Fact]
    public async Task Ai_conversation_creation_fails_closed_without_database()
    {
        using var request = new HttpRequestMessage(HttpMethod.Post, "/v1/ai/conversations")
        {
            Content = JsonContent.Create(ValidConversationStartRequest()),
        };
        request.Headers.Add("X-Test-User", $"{UserId}|true");
        request.Headers.Add("Idempotency-Key", "start_chat_0001");

        using var response = await client.SendAsync(request);
        var body = await response.Content.ReadFromJsonAsync<JsonElement>();

        Assert.Equal(HttpStatusCode.ServiceUnavailable, response.StatusCode);
        Assert.Equal("DATABASE_NOT_CONFIGURED", body.GetProperty("code").GetString());
    }

    [Fact]
    public async Task Ai_conversation_clear_requires_authenticated_identity()
    {
        using var response = await client.DeleteAsync("/v1/ai/conversations/2c31ca7a-b10e-4f22-9409-b817aaf875f4");
        var body = await response.Content.ReadFromJsonAsync<JsonElement>();

        Assert.Equal(HttpStatusCode.Unauthorized, response.StatusCode);
        Assert.Equal("AUTH_REQUIRED", body.GetProperty("code").GetString());
    }

    [Fact]
    public async Task Ai_conversation_turn_requires_authenticated_identity()
    {
        using var response = await client.PostAsJsonAsync(
            "/v1/ai/conversations/2c31ca7a-b10e-4f22-9409-b817aaf875f4/turns",
            ValidConversationTurnRequest());
        var body = await response.Content.ReadFromJsonAsync<JsonElement>();

        Assert.Equal(HttpStatusCode.Unauthorized, response.StatusCode);
        Assert.Equal("AUTH_REQUIRED", body.GetProperty("code").GetString());
    }

    [Fact]
    public async Task Ai_conversation_turn_requires_an_idempotency_key()
    {
        using var request = new HttpRequestMessage(
            HttpMethod.Post,
            "/v1/ai/conversations/2c31ca7a-b10e-4f22-9409-b817aaf875f4/turns")
        {
            Content = JsonContent.Create(ValidConversationTurnRequest()),
        };
        request.Headers.Add("X-Test-User", $"{UserId}|true");

        using var response = await client.SendAsync(request);
        var body = await response.Content.ReadFromJsonAsync<JsonElement>();

        Assert.Equal(HttpStatusCode.BadRequest, response.StatusCode);
        Assert.Equal("INVALID_IDEMPOTENCY_KEY", body.GetProperty("code").GetString());
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
    public async Task Ai_assistance_rejects_an_invalid_idempotency_key_at_the_api_boundary()
    {
        using var request = new HttpRequestMessage(HttpMethod.Post, "/v1/ai/assist")
        {
            Content = JsonContent.Create(ValidRequest()),
        };
        request.Headers.Add("X-Test-User", $"{UserId}|true");
        request.Headers.Add("Idempotency-Key", "contains spaces");

        using var response = await client.SendAsync(request);
        var body = await response.Content.ReadFromJsonAsync<JsonElement>();

        Assert.Equal(HttpStatusCode.BadRequest, response.StatusCode);
        Assert.Equal("INVALID_IDEMPOTENCY_KEY", body.GetProperty("code").GetString());
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
        context = new
        {
            caseVersionId = "M0_T2:1",
            feedbackCode = "MISSING_EVIDENCE",
            feedbackStatus = "ACTION_REQUIRED",
            anchorIds = new[] { "OBS-E2E-01" },
            limitationIds = new[] { "LIMIT-E2E-01" },
            claimText = "The supplied observation supports a bounded comparison.",
            claimScope = "LIMITED_COMPARISON",
            nextAction = "State the limitation before revising.",
        },
    };

    private static object ValidConversationStartRequest() => new
    {
        optedIn = true,
        locale = "en-US",
        learnerLimitation = "Each condition was measured once.",
        context = new
        {
            caseVersionId = "M0_T2:1",
            feedbackCode = "MISSING_EVIDENCE",
            feedbackStatus = "ACTION_REQUIRED",
            anchorIds = new[] { "OBS-E2E-01" },
            limitationIds = new[] { "LIMIT-E2E-01" },
            claimText = "The supplied observation supports a bounded comparison.",
            claimScope = "LIMITED_COMPARISON",
            nextAction = "State the limitation before revising.",
        },
    };

    private static object ValidConversationTurnRequest() => new
    {
        purpose = "explain_feedback",
        input = "Explain this feedback.",
        locale = "en-US",
        optedIn = true,
        history = Array.Empty<object>(),
        learnerLimitation = "Each condition was measured once.",
        context = new
        {
            caseVersionId = "M0_T2:1",
            feedbackCode = "MISSING_EVIDENCE",
            feedbackStatus = "ACTION_REQUIRED",
            anchorIds = new[] { "OBS-E2E-01" },
            limitationIds = new[] { "LIMIT-E2E-01" },
            claimText = "The supplied observation supports a bounded comparison.",
            claimScope = "LIMITED_COMPARISON",
            nextAction = "State the limitation before revising.",
        },
    };
}
