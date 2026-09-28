using Evidrilo.Api.ProjectAi;
using Evidrilo.Api.ProjectTemplates;

namespace Evidrilo.Api.Tests;

public sealed class ProjectAiScaffoldContractTests
{
    [Fact]
    public void Client_consent_claims_are_not_used_as_server_consent()
    {
        var request = ValidRequest();

        Assert.Null(ProjectAiScaffoldValidator.ValidateRequest(request with { OptedIn = false }));
        Assert.Null(ProjectAiScaffoldValidator.ValidateRequest(request with { ProjectDataConsent = false }));
        Assert.Null(ProjectAiScaffoldValidator.ValidateRequest(request with { ProjectDataConsentVersion = "old-version" }));
        Assert.Null(ProjectAiScaffoldValidator.ValidateRequest(request));
        Assert.Null(ProjectAiScaffoldValidator.ValidateRequest(request with
        {
            Operation = ProjectAiScaffoldValidator.AssistOperation,
            ProjectId = "11111111-1111-4111-8111-111111111111",
            BaseProjectRevision = 4,
        }));
        Assert.Equal("INVALID_PROJECT_AI_REQUEST", ProjectAiScaffoldValidator.ValidateRequest(request with
        {
            BaseProjectRevision = 4,
        }));
        Assert.Equal("INVALID_PROJECT_AI_REQUEST", ProjectAiScaffoldValidator.ValidateRequest(request with
        {
            Operation = ProjectAiScaffoldValidator.AssistOperation,
            ProjectId = "not-a-project-id",
            BaseProjectRevision = 4,
        }));
    }

    [Fact]
    public void Request_fields_must_belong_to_the_exact_published_template()
    {
        var template = PublishedTemplate();
        var request = ValidRequest() with { CurrentFields = new Dictionary<string, string> { ["question"] = "Why?" } };

        Assert.Null(ProjectAiScaffoldValidator.ValidateRequestFields(request, template));
        Assert.Equal(
            "PROJECT_AI_UNKNOWN_FIELD",
            ProjectAiScaffoldValidator.ValidateRequestFields(
                request with { CurrentFields = new Dictionary<string, string> { ["other"] = "value" } },
                template));
        Assert.Equal(
            "PROJECT_AI_TEMPLATE_NOT_READY",
            ProjectAiScaffoldValidator.ValidateRequestFields(request with { TemplateVersion = 99 }, template));
    }

    [Fact]
    public void Output_accepts_only_scaffold_fields_not_student_facts_or_final_claims()
    {
        var template = PublishedTemplate();
        var valid = new ProjectAiScaffoldOutput(
            template.TemplateId,
            template.TemplateVersion,
            ProjectAiScaffoldValidator.PromptVersion,
            "Review the framing against the assignment instructions.",
            [new ProjectAiFieldSuggestion("question", "How does the measured outcome vary?")],
            ["Which outcome will you record?"],
            ["What evidence would answer this question?"]);

        Assert.Null(ProjectAiScaffoldValidator.ValidateOutput(template, valid));

        foreach (var forbiddenField in new[] { "source", "data", "analysis", "claim", "limitation" })
        {
            var unsafeOutput = valid with
            {
                FieldSuggestions = [new ProjectAiFieldSuggestion(forbiddenField, "Invented content")],
            };
            Assert.Equal("PROJECT_AI_FIELD_NOT_ALLOWED", ProjectAiScaffoldValidator.ValidateOutput(template, unsafeOutput));
        }
    }

    [Fact]
    public void Output_rejects_unknown_fields_duplicate_fields_and_credentials()
    {
        var template = PublishedTemplate();
        var valid = new ProjectAiScaffoldOutput(
            template.TemplateId,
            template.TemplateVersion,
            ProjectAiScaffoldValidator.PromptVersion,
            "Review the framing against the assignment instructions.",
            [new ProjectAiFieldSuggestion("question", "How does the measured outcome vary?")],
            [],
            []);

        Assert.Equal(
            "PROJECT_AI_UNKNOWN_FIELD",
            ProjectAiScaffoldValidator.ValidateOutput(
                template,
                valid with { FieldSuggestions = [new ProjectAiFieldSuggestion("unknown", "Text")] }));
        Assert.Equal(
            "PROJECT_AI_INVALID_RESPONSE",
            ProjectAiScaffoldValidator.ValidateOutput(
                template,
                valid with
                {
                    FieldSuggestions =
                    [
                        new ProjectAiFieldSuggestion("question", "First suggestion"),
                        new ProjectAiFieldSuggestion("question", "Duplicate suggestion"),
                    ],
                }));
        Assert.Equal(
            "PROJECT_AI_INVALID_RESPONSE",
            ProjectAiScaffoldValidator.ValidateOutput(
                template,
                valid with { FieldSuggestions = [new ProjectAiFieldSuggestion("question", "api_key=secret")] }));
    }

    [Fact]
    public void Output_requires_bounded_guidance_and_next_prompts()
    {
        var template = PublishedTemplate();
        var valid = new ProjectAiScaffoldOutput(
            template.TemplateId,
            template.TemplateVersion,
            ProjectAiScaffoldValidator.PromptVersion,
            "Review the framing against the assignment instructions.",
            [],
            [],
            ["What evidence would answer this question?"]);

        Assert.Equal("PROJECT_AI_INVALID_RESPONSE", ProjectAiScaffoldValidator.ValidateOutput(
            template,
            valid with { GuidanceText = " " }));
        Assert.Equal("PROJECT_AI_INVALID_RESPONSE", ProjectAiScaffoldValidator.ValidateOutput(
            template,
            valid with { GuidanceText = "api_key=secret" }));
        Assert.Equal("PROJECT_AI_INVALID_RESPONSE", ProjectAiScaffoldValidator.ValidateOutput(
            template,
            valid with { RecommendedNextPrompts = [new string('x', 241)] }));
    }

    [Fact]
    public void Settlement_accepts_only_the_two_student_decisions_and_a_valid_request_key()
    {
        var valid = new ProjectAiScaffoldSettlementRequest(
            ProjectAiScaffoldValidator.SettlementSchema,
            ProjectAiScaffoldValidator.SettlementVersion,
            "project-ai-settle-0001",
            "apply");

        Assert.Null(ProjectAiScaffoldValidator.ValidateSettlementRequest(valid));
        Assert.Null(ProjectAiScaffoldValidator.ValidateSettlementRequest(valid with { Decision = "dismiss" }));
        Assert.Equal(
            "INVALID_PROJECT_AI_SETTLEMENT",
            ProjectAiScaffoldValidator.ValidateSettlementRequest(valid with { Decision = "consume" }));
        Assert.Equal(
            "INVALID_PROJECT_AI_SETTLEMENT",
            ProjectAiScaffoldValidator.ValidateSettlementRequest(valid with { RequestId = "bad key" }));
    }

    private static ProjectAiScaffoldRequest ValidRequest() => new(
        ProjectAiScaffoldValidator.Schema,
        ProjectAiScaffoldValidator.Version,
        ProjectAiScaffoldValidator.CreateOperation,
        null,
        "reviewed-template",
        3,
        null,
        "The assignment asks me to compare two measured conditions.",
        "How does the measured outcome vary?",
        "Help me define a manageable first step.",
        new Dictionary<string, string>(),
        ["I have limited time."],
        "en",
        true,
        true,
        ProjectAiScaffoldValidator.ProjectDataConsentVersion);

    private static ProjectTemplateCatalogEntry PublishedTemplate() => new(
        "reviewed-template",
        3,
        "experimental_laboratory",
        new ProjectTemplateDocument(
            "Experimental project",
            "A bounded template.",
            "A student-owned research plan.",
            [
                new ProjectTemplateInputField("question", ProjectTemplateInputKind.ResearchQuestion, "Question", true),
                new ProjectTemplateInputField("hypothesis", ProjectTemplateInputKind.Hypothesis, "Hypothesis", false),
                new ProjectTemplateInputField("source", ProjectTemplateInputKind.Source, "Source", false),
                new ProjectTemplateInputField("data", ProjectTemplateInputKind.Data, "Data", false),
                new ProjectTemplateInputField("analysis", ProjectTemplateInputKind.Analysis, "Analysis", false),
                new ProjectTemplateInputField("claim", ProjectTemplateInputKind.Claim, "Claim", false),
                new ProjectTemplateInputField("limitation", ProjectTemplateInputKind.Limitation, "Limitation", false),
                new ProjectTemplateInputField("next", ProjectTemplateInputKind.NextAction, "Next action", false),
            ],
            [new ProjectTemplateStep("frame", "Frame the question", ["question", "hypothesis"])],
            ["Do not infer causation from one observation."],
            ["Record source provenance."],
            ["Use text labels."],
            [
                new ProjectTemplateExample(
                    "normal-example",
                    "Reviewed normal example.",
                    true,
                    ProjectTemplateExampleKind.Normal),
                new ProjectTemplateExample(
                    "edge-example",
                    "Reviewed edge example.",
                    true,
                    ProjectTemplateExampleKind.EdgeOrConflicting),
            ]),
        DateTimeOffset.Parse("2026-09-27T00:00:00Z"));
}
