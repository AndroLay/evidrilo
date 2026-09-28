using System.Net;
using System.Text;
using System.Text.Json;
using Evidrilo.Api.Ai;
using Microsoft.Extensions.Configuration;

namespace Evidrilo.Api.Tests;

public sealed class OpenAiResponsesProviderTests
{
    [Fact]
    public async Task Sends_stateless_strict_schema_request_and_settles_actual_usage()
    {
        var handler = new RecordingHandler(_ => JsonResponse(CompletedResponse(
            """{"kind":"explanation","text":"The claim needs a narrower scope.","referencedAnchorIds":["OBS-01"],"proposal":null}""",
            inputTokens: 40,
            outputTokens: 20)));
        var budget = new RecordingSpendBudget();
        using var client = new HttpClient(handler);
        var provider = CreateProvider(client, budget);

        var response = await provider.CompleteAsync(Request(), CancellationToken.None);

        Assert.NotNull(response);
        Assert.Equal("explanation", response.Kind);
        Assert.Equal(["OBS-01"], response.ReferencedAnchorIds);
        Assert.Equal(HttpMethod.Post, handler.Request!.Method);
        Assert.Equal("https://api.openai.com/v1/responses", handler.Request.RequestUri!.AbsoluteUri);
        Assert.Equal("Bearer", handler.Request.Headers.Authorization!.Scheme);
        Assert.Equal("synthetic-secret", handler.Request.Headers.Authorization.Parameter);
        using var body = JsonDocument.Parse(handler.Body!);
        var root = body.RootElement;
        Assert.Equal("gpt-test-snapshot", root.GetProperty("model").GetString());
        Assert.False(root.GetProperty("store").GetBoolean());
        Assert.Equal(512, root.GetProperty("max_output_tokens").GetInt32());
        Assert.Equal("json_schema", root.GetProperty("text").GetProperty("format").GetProperty("type").GetString());
        Assert.True(root.GetProperty("text").GetProperty("format").GetProperty("strict").GetBoolean());
        Assert.False(root.TryGetProperty("conversation", out _));
        Assert.False(root.TryGetProperty("tools", out _));
        Assert.DoesNotContain("synthetic-secret", handler.Body!, StringComparison.Ordinal);
        Assert.DoesNotContain(AccountId.ToString(), handler.Body!, StringComparison.Ordinal);
        Assert.Equal(1, budget.ReserveCount);
        Assert.Equal(1, budget.SettleCount);
        Assert.Equal(0.00008m, budget.ActualCostUsd);
        Assert.Equal(40, budget.InputTokens);
        Assert.Equal(20, budget.OutputTokens);
    }

    [Fact]
    public async Task Structured_schema_limits_returned_anchor_ids_to_server_allowlist()
    {
        var handler = new RecordingHandler(_ => JsonResponse(CompletedResponse(
            """{"kind":"reflection_question","text":"Which fact supports that?","referencedAnchorIds":["OBS-01"],"proposal":null}""",
            40,
            20)));
        using var client = new HttpClient(handler);
        var provider = CreateProvider(client, new RecordingSpendBudget());

        _ = await provider.CompleteAsync(
            Request(allowedAnchors: new HashSet<string>(["OBS-01", "LIM-01"], StringComparer.Ordinal)),
            CancellationToken.None);

        using var body = JsonDocument.Parse(handler.Body!);
        var schema = body.RootElement.GetProperty("text").GetProperty("format").GetProperty("schema");
        Assert.Equal("object", schema.GetProperty("type").GetString());
        Assert.False(schema.GetProperty("additionalProperties").GetBoolean());
        Assert.Equal(
            2,
            schema.GetProperty("properties").GetProperty("proposal").GetProperty("anyOf").GetArrayLength());
        var anchorEnum = schema.GetProperty("properties").GetProperty("referencedAnchorIds")
            .GetProperty("items").GetProperty("enum").EnumerateArray().Select(item => item.GetString()!).ToArray();
        Assert.Equal(["LIM-01", "OBS-01"], anchorEnum);
    }

    [Fact]
    public async Task Language_alternative_maps_a_typed_draft_proposal()
    {
        var handler = new RecordingHandler(_ => JsonResponse(CompletedResponse(
            """{"kind":"draft_proposal","text":"Consider a bounded alternative.","referencedAnchorIds":["OBS-01"],"proposal":{"field":"claim_text","beforeValue":"The tablet dissolved faster because of heat.","suggestedValue":"In this comparison, the tablet dissolved faster in warm water.","anchorIds":["OBS-01"]}}""",
            40,
            20)));
        using var client = new HttpClient(handler);
        var provider = CreateProvider(client, new RecordingSpendBudget());

        var response = await provider.CompleteAsync(
            Request(AiAssistPurpose.LanguageAlternative),
            CancellationToken.None);

        Assert.NotNull(response);
        Assert.Equal("draft_proposal", response.Kind);
        Assert.Equal("claim_text", response.Proposal!.Field);
        Assert.Equal("The tablet dissolved faster because of heat.", response.Proposal.BeforeValue);
        Assert.Equal("In this comparison, the tablet dissolved faster in warm water.", response.Proposal.SuggestedValue);
    }

    [Theory]
    [InlineData("refusal", "AI_PROVIDER_REFUSED")]
    [InlineData("incomplete", "AI_PROVIDER_INCOMPLETE")]
    public async Task Refusal_and_incomplete_responses_are_not_returned_but_their_usage_is_settled(
        string status,
        string reason)
    {
        var handler = new RecordingHandler(_ => JsonResponse(CompletedResponse(
            "",
            40,
            20,
            status == "refusal" ? "completed" : status,
            refusal: status == "refusal" ? "I cannot help with this request." : null)));
        var budget = new RecordingSpendBudget();
        using var client = new HttpClient(handler);
        var provider = CreateProvider(client, budget);

        var exception = await Assert.ThrowsAsync<AiProviderFailureException>(
            () => provider.CompleteAsync(Request(), CancellationToken.None));

        Assert.Equal(reason, exception.ReasonCode);
        Assert.Equal(1, budget.SettleCount);
        Assert.Equal(0.00008m, budget.ActualCostUsd);
        Assert.DoesNotContain("I cannot help", exception.Message, StringComparison.Ordinal);
    }

    [Fact]
    public async Task Per_request_cost_ceiling_rejects_before_budget_reservation_or_http()
    {
        var handler = new RecordingHandler(_ => throw new Xunit.Sdk.XunitException("HTTP must not be called"));
        var budget = new RecordingSpendBudget();
        using var client = new HttpClient(handler);
        var provider = CreateProvider(client, budget, maxRequestCostUsd: 0.000001m, inputRate: 1000m);

        var exception = await Assert.ThrowsAsync<AiProviderFailureException>(
            () => provider.CompleteAsync(Request(prompt: "a longer bounded prompt"), CancellationToken.None));

        Assert.Equal("AI_PROVIDER_COST_LIMIT", exception.ReasonCode);
        Assert.Equal(0, budget.ReserveCount);
        Assert.Null(handler.Request);
    }

    [Fact]
    public async Task Monthly_spend_limit_fails_before_any_provider_request()
    {
        var handler = new RecordingHandler(_ => throw new Xunit.Sdk.XunitException("HTTP must not be called"));
        var budget = new RecordingSpendBudget(AiProviderBudgetReservationStatus.BudgetExceeded);
        using var client = new HttpClient(handler);
        var provider = CreateProvider(client, budget);

        var exception = await Assert.ThrowsAsync<AiProviderFailureException>(
            () => provider.CompleteAsync(Request(), CancellationToken.None));

        Assert.Equal("AI_PROVIDER_MONTHLY_BUDGET", exception.ReasonCode);
        Assert.Equal(1, budget.ReserveCount);
        Assert.Null(handler.Request);
    }

    [Fact]
    public async Task Provider_error_body_and_credentials_are_not_exposed_and_reserve_is_counted_uncertain()
    {
        const string privateError = "secret payload from provider synthetic-secret";
        var handler = new RecordingHandler(_ => new HttpResponseMessage(HttpStatusCode.TooManyRequests)
        {
            Content = new StringContent(privateError),
        });
        var budget = new RecordingSpendBudget();
        using var client = new HttpClient(handler);
        var provider = CreateProvider(client, budget);

        var exception = await Assert.ThrowsAsync<AiProviderFailureException>(
            () => provider.CompleteAsync(Request(), CancellationToken.None));

        Assert.Equal("AI_PROVIDER_RATE_LIMITED", exception.ReasonCode);
        Assert.DoesNotContain("synthetic-secret", exception.Message, StringComparison.Ordinal);
        Assert.DoesNotContain(privateError, exception.Message, StringComparison.Ordinal);
        Assert.Equal(1, budget.SettleCount);
        Assert.Null(budget.ActualCostUsd);
        Assert.True(budget.Uncertain);
    }

    [Fact]
    public async Task Malformed_provider_output_is_fail_closed_and_actual_usage_is_recorded()
    {
        var handler = new RecordingHandler(_ => JsonResponse(CompletedResponse(
            "not json",
            40,
            20)));
        var budget = new RecordingSpendBudget();
        using var client = new HttpClient(handler);
        var provider = CreateProvider(client, budget);

        var exception = await Assert.ThrowsAsync<AiProviderFailureException>(
            () => provider.CompleteAsync(Request(), CancellationToken.None));

        Assert.Equal("AI_PROVIDER_INVALID_OUTPUT", exception.ReasonCode);
        Assert.Equal(0.00008m, budget.ActualCostUsd);
        Assert.Equal(1, budget.SettleCount);
    }

    private static readonly Guid AccountId = Guid.Parse("123e4567-e89b-42d3-a456-426614174000");

    private static AiProviderRequest Request(
        AiAssistPurpose purpose = AiAssistPurpose.ExplainFeedback,
        string prompt = "Current claim and supplied observation.",
        IReadOnlySet<string>? allowedAnchors = null) => new(
        AccountId,
        "request_12345678",
        purpose,
        prompt,
        "en",
        "conversation.v1",
        allowedAnchors ?? new HashSet<string>(["OBS-01"], StringComparer.Ordinal));

    private static OpenAiResponsesProvider CreateProvider(
        HttpClient client,
        RecordingSpendBudget budget,
        decimal maxRequestCostUsd = 0.01m,
        decimal inputRate = 1m,
        decimal outputRate = 2m)
    {
        var values = new Dictionary<string, string?>
        {
            ["AI_PROVIDER_ENABLED"] = "true",
            ["AI_PROVIDER_ACTIVATION_APPROVED"] = "true",
            ["OPENAI_API_KEY"] = "synthetic-secret",
            ["AI_OPENAI_MODEL"] = "gpt-test-snapshot",
            ["AI_OPENAI_INPUT_USD_PER_MILLION_TOKENS"] = inputRate.ToString(System.Globalization.CultureInfo.InvariantCulture),
            ["AI_OPENAI_OUTPUT_USD_PER_MILLION_TOKENS"] = outputRate.ToString(System.Globalization.CultureInfo.InvariantCulture),
            ["AI_MAX_REQUEST_COST_USD"] = maxRequestCostUsd.ToString(System.Globalization.CultureInfo.InvariantCulture),
            ["AI_MONTHLY_SPEND_LIMIT_USD"] = "2",
        };
        return new OpenAiResponsesProvider(
            AiProviderOptions.From(new ConfigurationBuilder().AddInMemoryCollection(values).Build()),
            client,
            budget);
    }

    private static HttpResponseMessage JsonResponse(string json) => new(HttpStatusCode.OK)
    {
        Content = new StringContent(json, Encoding.UTF8, "application/json"),
    };

    private static string CompletedResponse(
        string outputText,
        int inputTokens,
        int outputTokens,
        string status = "completed",
        string? refusal = null)
    {
        var content = refusal is null
            ? JsonSerializer.Serialize(new[] { new { type = "output_text", text = outputText } })
            : JsonSerializer.Serialize(new[] { new { type = "refusal", refusal } });
        return $$"""
        {
          "id": "resp_synthetic",
          "status": "{{status}}",
          "model": "gpt-test-snapshot",
          "output": [
            { "type": "message", "role": "assistant", "content": {{content}} }
          ],
          "usage": { "input_tokens": {{inputTokens}}, "output_tokens": {{outputTokens}} }
        }
        """;
    }

    private sealed class RecordingHandler(Func<HttpRequestMessage, HttpResponseMessage> respond) : HttpMessageHandler
    {
        public HttpRequestMessage? Request { get; private set; }
        public string? Body { get; private set; }

        protected override async Task<HttpResponseMessage> SendAsync(
            HttpRequestMessage request,
            CancellationToken cancellationToken)
        {
            Request = request;
            Body = await request.Content!.ReadAsStringAsync(cancellationToken);
            return respond(request);
        }
    }

    private sealed class RecordingSpendBudget(
        AiProviderBudgetReservationStatus reservationStatus = AiProviderBudgetReservationStatus.Reserved)
        : IAiProviderSpendBudgetStore
    {
        public int ReserveCount { get; private set; }
        public int SettleCount { get; private set; }
        public decimal? ActualCostUsd { get; private set; }
        public int? InputTokens { get; private set; }
        public int? OutputTokens { get; private set; }
        public bool Uncertain { get; private set; }

        public Task<AiProviderBudgetReservationStatus> TryReserveAsync(
            AiProviderSpendReservation request,
            CancellationToken cancellationToken)
        {
            ReserveCount++;
            return Task.FromResult(reservationStatus);
        }

        public Task<bool> CompleteAsync(
            Guid accountId,
            string requestId,
            decimal? actualCostUsd,
            int? inputTokens,
            int? outputTokens,
            bool uncertain,
            bool released,
            CancellationToken cancellationToken)
        {
            SettleCount++;
            ActualCostUsd = actualCostUsd;
            InputTokens = inputTokens;
            OutputTokens = outputTokens;
            Uncertain = uncertain;
            return Task.FromResult(true);
        }
    }
}
