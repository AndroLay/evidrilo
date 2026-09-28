using Evidrilo.Api.ProjectTemplates;
using System.Text.Json;

namespace Evidrilo.Api.Tests;

public sealed class ProjectTemplateContractTests
{
    [Fact]
    public void Family_ids_match_the_five_mobile_catalog_families()
    {
        Assert.Equal(
            new[]
            {
                "experimental_laboratory",
                "observational_survey",
                "literature_review",
                "qualitative_interview_field_study",
                "design_engineering",
            },
            ProjectTemplateFamilies.All.Select(family => family.Id));
    }

    [Fact]
    public void Hypothesis_is_not_required_for_a_template()
    {
        Assert.Null(ProjectTemplateDocumentValidator.Validate(ValidDocument()));
    }

    [Fact]
    public void Draft_author_cannot_self_assert_that_an_example_was_reviewed()
    {
        var document = ValidDocument() with
        {
            Examples =
            [
                new ProjectTemplateExample("example-normal", "Synthetic normal example.", true, ProjectTemplateExampleKind.Normal),
                new ProjectTemplateExample("example-edge", "Synthetic edge example.", true, ProjectTemplateExampleKind.EdgeOrConflicting),
            ],
        };

        Assert.Equal(
            "TEMPLATE_REVIEW_METADATA_SERVER_OWNED",
            ProjectTemplateDocumentValidator.ValidateDraft(document));
    }

    [Fact]
    public void Reviewer_selection_requires_an_existing_example_id()
    {
        var document = ValidDocument();

        Assert.Equal(
            "PUBLISHED_TEMPLATE_REQUIRES_NORMAL_AND_EDGE_EXAMPLES",
            ProjectTemplateDocumentValidator.ValidateReviewedExampleIds(document, [], required: true));
        Assert.Equal(
            "INVALID_REVIEWED_EXAMPLE_REFERENCE",
            ProjectTemplateDocumentValidator.ValidateReviewedExampleIds(document, ["missing-example"], required: true));
        Assert.Equal("PUBLISHED_TEMPLATE_REQUIRES_NORMAL_AND_EDGE_EXAMPLES", ProjectTemplateDocumentValidator.ValidateReviewedExampleIds(
            document,
            ["example-normal"],
            required: true));
        Assert.Equal("PUBLISHED_TEMPLATE_REQUIRES_NORMAL_AND_EDGE_EXAMPLES", ProjectTemplateDocumentValidator.ValidateReviewedExampleIds(
            document,
            ["example-edge"],
            required: true));
        Assert.Null(ProjectTemplateDocumentValidator.ValidateReviewedExampleIds(
            document,
            ["example-normal", "example-edge"],
            required: true));
    }

    [Fact]
    public void Template_without_normal_and_edge_examples_is_not_valid_for_review()
    {
        var onlyNormal = ValidDocument() with
        {
            Examples = [new ProjectTemplateExample("example-normal", "Normal example.", false)],
        };

        Assert.Equal("PROJECT_TEMPLATE_REQUIRES_NORMAL_AND_EDGE_EXAMPLES", ProjectTemplateDocumentValidator.ValidateDraft(onlyNormal));
    }

    [Fact]
    public void Previously_published_single_kind_template_remains_readable()
    {
        var legacy = ValidDocument() with
        {
            Examples = [new ProjectTemplateExample("legacy-example", "Previously reviewed example.", true)],
        };

        Assert.Equal(ProjectTemplateExampleKind.Unspecified, legacy.Examples![0].Kind);
        Assert.Null(ProjectTemplateDocumentValidator.Validate(legacy));
        Assert.Equal(
            "PUBLISHED_TEMPLATE_REQUIRES_NORMAL_AND_EDGE_EXAMPLES",
            ProjectTemplateDocumentValidator.ValidateReviewedExampleIds(legacy, ["legacy-example"], required: true));
        Assert.Null(ProjectTemplateDocumentValidator.ValidateReviewedExampleIds(
            legacy,
            ["legacy-example"],
            required: true,
            requireBothScenarioKinds: false));
        Assert.Equal(
            "PROJECT_TEMPLATE_REQUIRES_NORMAL_AND_EDGE_EXAMPLES",
            ProjectTemplateDocumentValidator.ValidateDraft(legacy with
            {
                Examples = [new ProjectTemplateExample("legacy-example", "Previously reviewed example.", false)],
            }));
    }

    [Fact]
    public void Unknown_step_input_reference_is_rejected()
    {
        var document = ValidDocument() with
        {
            Steps = [new ProjectTemplateStep("frame-question", "Frame the question", ["missing-field"])],
        };

        Assert.Equal("TEMPLATE_STEP_INPUT_NOT_FOUND", ProjectTemplateDocumentValidator.Validate(document));
    }

    [Fact]
    public void Stage_ai_capabilities_survive_template_contract_round_trip()
    {
        const string json = """
            {"id":"scope","title":"Set the scope","inputFieldIds":["question"],
             "aiOperations":[{"id":"summarize_selected_material","inputFieldIds":["question"],"outputFieldIds":["question"]}]}
            """;

        var options = new JsonSerializerOptions(JsonSerializerDefaults.Web);
        var step = JsonSerializer.Deserialize<ProjectTemplateStep>(json, options);
        Assert.NotNull(step);

        var roundTrip = JsonSerializer.Serialize(step, options);
        Assert.Contains("\"aiOperations\"", roundTrip);
    }

    [Fact]
    public void Unknown_stage_ai_operation_is_rejected()
    {
        var document = ValidDocument() with
        {
            Steps =
            [
                new ProjectTemplateStep(
                    "frame-question",
                    "Frame the question",
                    ["question"],
                    [new ProjectTemplateAiOperationCapability("invented_everything", ["question"], ["question"])]),
            ],
        };

        Assert.Equal("INVALID_TEMPLATE_AI_OPERATION", ProjectTemplateDocumentValidator.Validate(document));
    }

    [Fact]
    public void Stage_ai_operation_cannot_write_source_or_data_fields()
    {
        var document = ValidDocument() with
        {
            InputFields =
            [
                new ProjectTemplateInputField("question", ProjectTemplateInputKind.ResearchQuestion, "Question", true),
                new ProjectTemplateInputField("source", ProjectTemplateInputKind.Source, "Source", false),
            ],
            Steps =
            [
                new ProjectTemplateStep(
                    "frame-question",
                    "Frame the question",
                    ["question", "source"],
                    [new ProjectTemplateAiOperationCapability("summarize_selected_material", ["question"], ["source"])]),
            ],
        };

        Assert.Equal("TEMPLATE_AI_OPERATION_OUTPUT_NOT_ALLOWED", ProjectTemplateDocumentValidator.Validate(document));
    }

    [Fact]
    public void Unknown_family_is_rejected_before_persistence()
    {
        Assert.False(ProjectTemplateFamilies.TryFind("universal_assignment", out _));
        Assert.True(ProjectTemplateFamilies.TryFind("literature_review", out _));
    }

    private static ProjectTemplateDocument ValidDocument() => new(
        "A bounded literature review",
        "Compare findings across a defined set of sources.",
        "A source-to-claim outline with limitations.",
        [new ProjectTemplateInputField("question", ProjectTemplateInputKind.ResearchQuestion, "Research question", true)],
        [new ProjectTemplateStep("frame-question", "Frame the question", ["question"])],
        ["Do not treat selected sources as a complete census of the field."],
        ["Record each source identifier and retrieval location."],
        ["Use descriptive labels and preserve reading order."],
        [
            new ProjectTemplateExample(
                "example-normal",
                "Synthetic normal structure example.",
                false,
                ProjectTemplateExampleKind.Normal),
            new ProjectTemplateExample(
                "example-edge",
                "Synthetic edge or conflicting structure example.",
                false,
                ProjectTemplateExampleKind.EdgeOrConflicting),
        ]);
}
