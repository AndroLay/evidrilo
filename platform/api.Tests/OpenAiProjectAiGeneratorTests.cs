using System.Text.Json;
using Evidrilo.Api.Ai;
using Evidrilo.Api.Configuration;
using Evidrilo.Api.ProjectAi;
using Evidrilo.Api.ProjectTemplates;
using Microsoft.Extensions.Configuration;

namespace Evidrilo.Api.Tests;

public sealed class OpenAiProjectAiGeneratorTests
{
    private static readonly Guid AccountId = Guid.Parse("123e4567-e89b-42d3-a456-426614174000");
    private static readonly AiProviderTokenUsage Usage = new(100, 0, 0, 40, 10);

    [Fact]
    public async Task Scaffold_generator_sends_only_redacted_allowlisted_context_and_binds_server_metadata()
    {
        const string outputJson = """
            {
              "guidanceText": "Start with a measurable question.",
              "fieldSuggestions": [{ "fieldId": "research_question", "suggestedValue": "How does temperature affect dissolving time?" }],
              "clarificationQuestions": [],
              "recommendedNextPrompts": ["Compare the available temperature groups."]
            }
            """;
        var provider = new RecordingAiProvider(outputJson);
        var generator = CreateGenerator(provider);
        var request = ScaffoldRequest();

        Assert.Equal(7, generator.EstimateMaximumCreditCost(request));
        var output = await generator.GenerateAsync(request, CancellationToken.None);

        Assert.NotNull(output);
        Assert.Equal("template-1", output.TemplateId);
        Assert.Equal(3, output.TemplateVersion);
        Assert.Equal(ProjectAiScaffoldValidator.PromptVersion, output.PromptVersion);
        Assert.Equal(Usage, output.Usage);
        Assert.Equal("research_question", Assert.Single(output.FieldSuggestions).FieldId);
        Assert.NotNull(provider.Request);
        Assert.NotEqual(request.RequestId, provider.Request.RequestId);
        Assert.Equal(AccountId, provider.Request.AccountId);
        Assert.Equal("project_scaffold_v1", provider.Request.StructuredOutputSchemaName);
        Assert.Contains("untrusted", provider.Request.SystemInstructions!, StringComparison.OrdinalIgnoreCase);
        Assert.Contains("Assignment: describe dissolving tablets.", provider.Request.RedactedInput, StringComparison.Ordinal);
        Assert.DoesNotContain("do not send this source field", provider.Request.RedactedInput, StringComparison.Ordinal);
        Assert.DoesNotContain(AccountId.ToString(), provider.Request.RedactedInput, StringComparison.Ordinal);
        using var schema = JsonDocument.Parse(provider.Request.StructuredOutputSchema!.ToJsonString());
        var fieldIdSchema = schema.RootElement.GetProperty("properties").GetProperty("fieldSuggestions")
            .GetProperty("items").GetProperty("properties").GetProperty("fieldId");
        Assert.Equal(new[] { "research_question" }, fieldIdSchema.GetProperty("enum").EnumerateArray()
            .Select(item => item.GetString()!).ToArray());
    }

    [Fact]
    public async Task Stage_generator_limits_reference_and_proposal_ids_to_selected_context()
    {
        const string outputJson = """
            {
              "items": [{
                "id": "explain_1", "kind": "EXPLANATION", "text": "Use the selected observation carefully.",
                "targetFieldId": null, "beforeValue": null, "afterValue": null, "referenceIds": [],
                "assumptions": [], "uncertainties": [], "knownLimits": []
              }],
              "reportedConflicts": [], "reportedOutOfScopeItems": []
            }
            """;
        var provider = new RecordingAiProvider(outputJson);
        var generator = CreateGenerator(provider);
        var request = StageRequest();

        var output = await generator.GenerateAsync(request, CancellationToken.None);

        Assert.NotNull(output);
        Assert.Equal("template-1", output.TemplateId);
        Assert.Equal(3, output.TemplateVersion);
        Assert.Equal(ProjectAiStageAssistValidator.PromptVersion, output.PromptVersion);
        Assert.Equal(Usage, output.Usage);
        Assert.NotNull(provider.Request);
        Assert.DoesNotContain(request.RequestId, provider.Request.RequestId, StringComparison.Ordinal);
        Assert.Contains("selected observation", provider.Request.RedactedInput, StringComparison.Ordinal);
        Assert.DoesNotContain("not selected", provider.Request.RedactedInput, StringComparison.Ordinal);
        Assert.DoesNotContain(AccountId.ToString(), provider.Request.RedactedInput, StringComparison.Ordinal);
        using var schema = JsonDocument.Parse(provider.Request.StructuredOutputSchema!.ToJsonString());
        var itemSchema = schema.RootElement.GetProperty("properties").GetProperty("items").GetProperty("items");
        Assert.Equal(new[] { "research_question" }, itemSchema.GetProperty("properties")
            .GetProperty("targetFieldId").GetProperty("anyOf")[0].GetProperty("enum").EnumerateArray()
            .Select(item => item.GetString()!).ToArray());
        Assert.Equal(new[] { "evidence_1" }, itemSchema.GetProperty("properties")
            .GetProperty("referenceIds").GetProperty("items").GetProperty("enum").EnumerateArray()
            .Select(item => item.GetString()!).ToArray());
    }

    [Fact]
    public async Task General_chat_generator_sends_only_the_redacted_message_and_returns_recommended_next_prompts()
    {
        const string answer = "Repeated measurements help describe variation.";
        var recommendedNextPrompts = new[]
        {
            "How many repetitions would help compare variation?",
            "Which limitation could still affect the conclusion?",
        };
        var provider = new RecordingAiProvider(JsonSerializer.Serialize(new { answer, recommendedNextPrompts }));
        var generator = CreateGenerator(provider);
        var request = new ProjectAiGeneralChatProviderRequest(
            AccountId,
            "client_request_123456",
            "Explain this and contact learner@example.org.",
            "en");

        Assert.Equal(7, generator.EstimateMaximumCreditCost(request));
        var output = await generator.GenerateGeneralAsync(request, CancellationToken.None);

        Assert.NotNull(output);
        Assert.Equal(answer, output.Answer);
        Assert.Equal(recommendedNextPrompts, output.RecommendedNextPrompts);
        Assert.Equal(Usage, output.Usage);
        Assert.NotNull(provider.Request);
        Assert.NotEqual(request.RequestId, provider.Request.RequestId);
        Assert.Contains("[REDACTED_EMAIL]", provider.Request.RedactedInput, StringComparison.Ordinal);
        Assert.DoesNotContain(AccountId.ToString(), provider.Request.RedactedInput, StringComparison.Ordinal);
        Assert.DoesNotContain("projectId", provider.Request.RedactedInput, StringComparison.OrdinalIgnoreCase);
        Assert.Equal("project_general_chat_v2", provider.Request.StructuredOutputSchemaName);
        using var schema = JsonDocument.Parse(provider.Request.StructuredOutputSchema!.ToJsonString());
        Assert.Equal(
            new[] { "answer", "recommendedNextPrompts" },
            schema.RootElement.GetProperty("required").EnumerateArray().Select(item => item.GetString()).ToArray());
        Assert.False(schema.RootElement.GetProperty("additionalProperties").GetBoolean());
        var promptSchema = schema.RootElement.GetProperty("properties").GetProperty("recommendedNextPrompts");
        Assert.Equal(1, promptSchema.GetProperty("minItems").GetInt32());
        Assert.Equal(3, promptSchema.GetProperty("maxItems").GetInt32());
        Assert.Contains("without access to any project", provider.Request.SystemInstructions!, StringComparison.OrdinalIgnoreCase);
    }

    [Fact]
    public async Task Disabled_project_generator_does_not_dispatch_to_the_shared_provider()
    {
        var provider = new RecordingAiProvider("{}");
        var generator = new OpenAiProjectAiGenerator(
            ProjectAiProviderOptions.From(BuildConfiguration(), AiProviderOptions.From(BuildConfiguration())),
            provider);

        Assert.False(generator.IsEnabled);
        Assert.Null(await generator.GenerateAsync(ScaffoldRequest(), CancellationToken.None));
        Assert.Null(provider.Request);
    }

    private static OpenAiProjectAiGenerator CreateGenerator(RecordingAiProvider provider)
    {
        var configuration = BuildConfiguration(
            ("AI_PROVIDER_ENABLED", "true"),
            ("AI_PROVIDER_ACTIVATION_APPROVED", "true"),
            ("OPENAI_API_KEY", "synthetic-secret"),
            ("AI_OPENAI_MODEL", "gpt-test-snapshot"),
            ("AI_OPENAI_INPUT_USD_PER_MILLION_TOKENS", "1"),
            ("AI_OPENAI_CACHED_INPUT_USD_PER_MILLION_TOKENS", "0.01"),
            ("AI_OPENAI_CACHE_WRITE_INPUT_USD_PER_MILLION_TOKENS", "1.25"),
            ("AI_OPENAI_OUTPUT_USD_PER_MILLION_TOKENS", "2"),
            ("AI_MAX_REQUEST_COST_USD", "0.01"),
            ("AI_MONTHLY_SPEND_LIMIT_USD", "2"),
            ("PROJECT_AI_PROVIDER_ENABLED", "true"),
            ("PROJECT_AI_PROVIDER_ACTIVATION_APPROVED", "true"),
            ("PROJECT_AI_PRIVACY_APPROVED", "true"),
            ("PROJECT_AI_GENERAL_CHAT_POLICY_APPROVED", "true"));
        return new OpenAiProjectAiGenerator(
            ProjectAiProviderOptions.From(configuration, AiProviderOptions.From(configuration)),
            provider);
    }

    private static ProjectAiScaffoldProviderRequest ScaffoldRequest()
    {
        var template = Template();
        return new ProjectAiScaffoldProviderRequest(
            AccountId,
            "client_request_123456",
            "create_project",
            template,
            null,
            "Assignment: describe dissolving tablets.",
            "How does water temperature affect dissolving time?",
            null,
            new Dictionary<string, string>
            {
                ["research_question"] = "How does temperature affect dissolving time?",
                ["source_notes"] = "do not send this source field",
            },
            ["Use only the provided class observations."],
            "en");
    }

    private static ProjectAiStageAssistProviderRequest StageRequest() => new(
        "template-1",
        3,
        "experimental_laboratory",
        "Dissolving tablets",
        "Compare one bounded variable.",
        "plan",
        "Plan the comparison",
        "draft_question",
        [new ProjectAiStageAssistSelectedFieldContext(
            "research_question",
            ProjectTemplateInputKind.ResearchQuestion,
            "Research question",
            "How does temperature affect dissolving time?")],
        [new ProjectAiStageAssistEvidenceContext(
            "evidence_1",
            "OBSERVATION",
            "selected observation",
            "Warm water dissolved the sample faster.",
            "class log")],
        ["research_question"],
        ["Do not infer a causal mechanism from this observation."],
        ["Keep the observation source attached."],
        "en")
    {
        AccountId = OpenAiProjectAiGeneratorTests.AccountId,
        RequestId = "client_request_123456",
    };

    private static ProjectTemplateCatalogEntry Template() => new(
        "template-1",
        3,
        "experimental_laboratory",
        new ProjectTemplateDocument(
            "Dissolving tablets",
            "Compare one bounded variable.",
            "A tested research plan.",
            [
                new ProjectTemplateInputField("research_question", ProjectTemplateInputKind.ResearchQuestion, "Research question", true),
                new ProjectTemplateInputField("source_notes", ProjectTemplateInputKind.Source, "Source notes", false),
            ],
            [new ProjectTemplateStep("plan", "Plan the comparison", ["research_question"])],
            ["Do not infer a causal mechanism from this observation."],
            ["Keep the observation source attached."],
            [],
            []),
        DateTimeOffset.Parse("2026-01-01T00:00:00Z"));

    private static IConfiguration BuildConfiguration(params (string Key, string Value)[] values) =>
        new ConfigurationBuilder()
            .AddInMemoryCollection(values.ToDictionary(item => item.Key, item => (string?)item.Value))
            .Build();

    private sealed class RecordingAiProvider(string outputJson) : IAiProvider
    {
        public AiProviderRequest? Request { get; private set; }

        public int EstimateMaximumCreditCost(AiProviderRequest request)
        {
            Request = request;
            return 7;
        }

        public Task<AiProviderResponse?> CompleteAsync(
            AiProviderRequest request,
            CancellationToken cancellationToken)
        {
            Request = request;
            return Task.FromResult<AiProviderResponse?>(new AiProviderResponse(
                "structured_output",
                outputJson)
            {
                Usage = OpenAiProjectAiGeneratorTests.Usage,
            });
        }
    }
}
