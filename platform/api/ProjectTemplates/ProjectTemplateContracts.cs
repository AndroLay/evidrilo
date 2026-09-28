using System.Text.Json;
using System.Text.Json.Serialization;
using System.Text.RegularExpressions;

namespace Evidrilo.Api.ProjectTemplates;

public sealed record ProjectTemplateFamilyInfo(string Id, string DisplayName);

public static class ProjectTemplateFamilies
{
    private static readonly IReadOnlyList<ProjectTemplateFamilyInfo> Families = Array.AsReadOnly(
    new ProjectTemplateFamilyInfo[]
    {
        new("experimental_laboratory", "Experimental and laboratory work"),
        new("observational_survey", "Observational and survey studies"),
        new("literature_review", "Literature reviews"),
        new("qualitative_interview_field_study", "Qualitative interviews and field studies"),
        new("design_engineering", "Design and engineering projects"),
    });

    public static IReadOnlyList<ProjectTemplateFamilyInfo> All => Families;

    public static bool TryFind(string? id, out ProjectTemplateFamilyInfo family)
    {
        family = Families.FirstOrDefault(candidate =>
            string.Equals(candidate.Id, id, StringComparison.Ordinal))!;
        return family is not null;
    }
}

public enum ProjectTemplateInputKind
{
    AssignmentBrief,
    ResearchQuestion,
    Hypothesis,
    Source,
    Data,
    Analysis,
    Claim,
    Limitation,
    NextAction,
}

public enum ProjectTemplateLifecycleState
{
    Draft,
    Review,
    Approved,
    Published,
    Retired,
}

public enum ProjectTemplateExampleKind
{
    // Unspecified is retained only for previously published immutable template versions.
    Unspecified,
    // Normal means the nominal workflow scenario, not a correct answer or favorable result.
    Normal,
    EdgeOrConflicting,
}

public sealed record ProjectTemplateInputField(
    string? Id,
    ProjectTemplateInputKind Kind,
    string? Label,
    bool Required);

public sealed record ProjectTemplateStep(
    string? Id,
    string? Title,
    IReadOnlyList<string>? InputFieldIds,
    IReadOnlyList<ProjectTemplateAiOperationCapability>? AiOperations = null);

public sealed record ProjectTemplateAiOperationCapability(
    [property: JsonPropertyName("id")] string? Id,
    [property: JsonPropertyName("inputFieldIds")] IReadOnlyList<string>? InputFieldIds,
    [property: JsonPropertyName("outputFieldIds")] IReadOnlyList<string>? OutputFieldIds);

public sealed record ProjectTemplateExample(
    string? Id,
    string? Summary,
    bool Reviewed,
    ProjectTemplateExampleKind Kind = ProjectTemplateExampleKind.Unspecified);

/// <summary>
/// Student-facing structure and method boundaries. This is not an evaluator
/// score, a grade, or a claim that an example has received external review.
/// </summary>
public sealed record ProjectTemplateDocument(
    string? Title,
    string? Summary,
    string? IntendedOutput,
    IReadOnlyList<ProjectTemplateInputField>? InputFields,
    IReadOnlyList<ProjectTemplateStep>? Steps,
    IReadOnlyList<string>? MethodSpecificLimitations,
    IReadOnlyList<string>? ProvenanceRequirements,
    IReadOnlyList<string>? AccessibilityExpectations,
    IReadOnlyList<ProjectTemplateExample>? Examples);

public sealed record ProjectTemplateDraftCreateRequest(
    string? Schema,
    string? Version,
    Guid OrganizationId,
    string? TemplateId,
    int TemplateVersion,
    string? Family,
    ProjectTemplateDocument? Template);

public sealed record ProjectTemplateTransitionRequest(
    string? Schema,
    string? Version,
    ProjectTemplateLifecycleState TargetState,
    string? Reason,
    IReadOnlyList<string>? ReviewedExampleIds = null);

public sealed record ProjectTemplateVersionRecord(
    string TemplateId,
    int TemplateVersion,
    string Family,
    ProjectTemplateDocument Template,
    ProjectTemplateLifecycleState State,
    Guid AuthorId,
    Guid? ReviewerId,
    Guid OrganizationId,
    DateTimeOffset CreatedAt,
    DateTimeOffset UpdatedAt,
    DateTimeOffset? PublishedAt,
    bool IsCurrentPublished);

public sealed record ProjectTemplateOperation(
    string Outcome,
    string TemplateId,
    int TemplateVersion,
    ProjectTemplateLifecycleState State);

public sealed record ProjectTemplateFamilyOffering(
    ProjectTemplateFamilyInfo Family,
    int SelectableTemplateCount);

public sealed record ProjectTemplateCatalogEntry(
    string TemplateId,
    int TemplateVersion,
    string Family,
    ProjectTemplateDocument Template,
    DateTimeOffset PublishedAt);

public sealed record ProjectTemplateCatalogPage(
    IReadOnlyList<ProjectTemplateCatalogEntry> Templates,
    string? NextAfterTemplateId);

public sealed record ProjectTemplateFamiliesResponse(
    [property: JsonPropertyName("schema")] string Schema,
    [property: JsonPropertyName("version")] string Version,
    [property: JsonPropertyName("families")] IReadOnlyList<ProjectTemplateFamilyOffering> Families,
    [property: JsonPropertyName("requestId")] string RequestId);

public sealed record ProjectTemplateSummaryResponse(
    [property: JsonPropertyName("id")] string Id,
    [property: JsonPropertyName("version")] int Version,
    [property: JsonPropertyName("family")] string Family,
    [property: JsonPropertyName("title")] string Title,
    [property: JsonPropertyName("summary")] string Summary,
    [property: JsonPropertyName("publication")] string Publication);

public sealed record ProjectTemplatePublicDefinitionResponse(
    [property: JsonPropertyName("id")] string Id,
    [property: JsonPropertyName("version")] int Version,
    [property: JsonPropertyName("family")] string Family,
    [property: JsonPropertyName("title")] string Title,
    [property: JsonPropertyName("summary")] string Summary,
    [property: JsonPropertyName("intendedOutput")] string IntendedOutput,
    [property: JsonPropertyName("inputFields")] IReadOnlyList<ProjectTemplateInputField> InputFields,
    [property: JsonPropertyName("steps")] IReadOnlyList<ProjectTemplateStep> Steps,
    [property: JsonPropertyName("methodSpecificLimitations")] IReadOnlyList<string> MethodSpecificLimitations,
    [property: JsonPropertyName("provenanceRequirements")] IReadOnlyList<string> ProvenanceRequirements,
    [property: JsonPropertyName("accessibilityExpectations")] IReadOnlyList<string> AccessibilityExpectations,
    [property: JsonPropertyName("examples")] IReadOnlyList<ProjectTemplateExample> Examples,
    [property: JsonPropertyName("publication")] string Publication,
    [property: JsonPropertyName("publishedAt")] DateTimeOffset PublishedAt);

public sealed record ProjectTemplateCatalogListResponse(
    [property: JsonPropertyName("schema")] string Schema,
    [property: JsonPropertyName("version")] string Version,
    [property: JsonPropertyName("templates")] IReadOnlyList<ProjectTemplateSummaryResponse> Templates,
    [property: JsonPropertyName("nextAfterTemplateId"), JsonIgnore(Condition = JsonIgnoreCondition.Never)] string? NextAfterTemplateId,
    [property: JsonPropertyName("requestId")] string RequestId);

public sealed record ProjectTemplateCatalogDetailResponse(
    [property: JsonPropertyName("schema")] string Schema,
    [property: JsonPropertyName("version")] string Version,
    [property: JsonPropertyName("template")] ProjectTemplatePublicDefinitionResponse Template,
    [property: JsonPropertyName("requestId")] string RequestId);

public sealed record ProjectTemplateAuthoringResponse(
    [property: JsonPropertyName("schema")] string Schema,
    [property: JsonPropertyName("version")] string Version,
    [property: JsonPropertyName("outcome")] string Outcome,
    [property: JsonPropertyName("templateId")] string TemplateId,
    [property: JsonPropertyName("templateVersion")] int TemplateVersion,
    [property: JsonPropertyName("state")] string State,
    [property: JsonPropertyName("requestId")] string RequestId);

public static class ProjectTemplateDocumentValidator
{
    private const int MaximumSerializedBytes = 128 * 1024;
    private const int MaximumAiOperationsPerStep = 8;
    private const int MaximumAiFieldsPerOperation = 32;
    private static readonly HashSet<string> AllowedAiOperationIds = new(StringComparer.Ordinal)
    {
        "explain_template_step",
        "organize_selected_material",
        "summarize_selected_material",
        "suggest_analysis",
        "check_evidence_links",
        "suggest_revision",
        "prepare_output_section",
    };
    private static readonly Regex IdentifierPattern = new(
        "\\A[a-z0-9]+(?:[._-][a-z0-9]+)*\\z",
        RegexOptions.CultureInvariant);
    private static readonly JsonSerializerOptions SerializerOptions = new(JsonSerializerDefaults.Web)
    {
        Converters = { new JsonStringEnumConverter(JsonNamingPolicy.SnakeCaseLower) },
        UnmappedMemberHandling = JsonUnmappedMemberHandling.Disallow,
    };

    public static string? Validate(ProjectTemplateDocument? template)
    {
        if (template is null
            || !HasText(template.Title, 160)
            || !HasText(template.Summary, 1_200)
            || !HasText(template.IntendedOutput, 1_200)
            || template.InputFields is null or { Count: < 1 or > 32 }
            || template.Steps is null or { Count: < 1 or > 48 }
            || !HasTextList(template.MethodSpecificLimitations, 24, 1_200)
            || !HasTextList(template.ProvenanceRequirements, 24, 1_200)
            || !HasTextList(template.AccessibilityExpectations, 24, 1_200)
            || template.Examples is null or { Count: > 24 })
        {
            return "INVALID_PROJECT_TEMPLATE";
        }

        var inputIds = new HashSet<string>(StringComparer.Ordinal);
        var inputFieldsById = new Dictionary<string, ProjectTemplateInputKind>(StringComparer.Ordinal);
        foreach (var input in template.InputFields)
        {
            if (input is null
                || !IsIdentifier(input.Id)
                || !Enum.IsDefined(input.Kind)
                || !HasText(input.Label, 200)
                || !inputIds.Add(input.Id!))
                return "INVALID_PROJECT_TEMPLATE_INPUT";
            inputFieldsById.Add(input.Id!, input.Kind);
        }

        var stepIds = new HashSet<string>(StringComparer.Ordinal);
        foreach (var step in template.Steps)
        {
            if (step is null
                || !IsIdentifier(step.Id)
                || !HasText(step.Title, 200)
                || step.InputFieldIds is null or { Count: > 32 }
                || !stepIds.Add(step.Id!))
                return "INVALID_PROJECT_TEMPLATE_STEP";

            var references = new HashSet<string>(StringComparer.Ordinal);
            foreach (var inputId in step.InputFieldIds)
            {
                if (!IsIdentifier(inputId) || !inputIds.Contains(inputId!) || !references.Add(inputId!))
                    return "TEMPLATE_STEP_INPUT_NOT_FOUND";
            }

            var operationIds = new HashSet<string>(StringComparer.Ordinal);
            var aiOperations = step.AiOperations ?? Array.Empty<ProjectTemplateAiOperationCapability>();
            if (aiOperations.Count > MaximumAiOperationsPerStep)
                return "TOO_MANY_TEMPLATE_AI_OPERATIONS";

            foreach (var operation in aiOperations)
            {
                if (operation is null
                    || !AllowedAiOperationIds.Contains(operation.Id ?? string.Empty)
                    || !operationIds.Add(operation.Id!)
                    || operation.InputFieldIds is null or { Count: > MaximumAiFieldsPerOperation }
                    || operation.OutputFieldIds is null or { Count: > MaximumAiFieldsPerOperation }
                    || operation.Id != "explain_template_step" && operation.InputFieldIds.Count == 0)
                    return "INVALID_TEMPLATE_AI_OPERATION";

                var selectedInputs = new HashSet<string>(StringComparer.Ordinal);
                foreach (var inputId in operation.InputFieldIds)
                {
                    if (!IsIdentifier(inputId) || !references.Contains(inputId!) || !selectedInputs.Add(inputId!))
                        return "TEMPLATE_AI_OPERATION_FIELD_NOT_IN_STEP";
                }

                var outputFields = new HashSet<string>(StringComparer.Ordinal);
                foreach (var outputId in operation.OutputFieldIds)
                {
                    if (!IsIdentifier(outputId) || !references.Contains(outputId!) || !outputFields.Add(outputId!))
                        return "TEMPLATE_AI_OPERATION_FIELD_NOT_IN_STEP";
                    if (inputFieldsById[outputId!] is ProjectTemplateInputKind.Source or ProjectTemplateInputKind.Data)
                        return "TEMPLATE_AI_OPERATION_OUTPUT_NOT_ALLOWED";
                }
            }
        }

        var exampleIds = new HashSet<string>(StringComparer.Ordinal);
        foreach (var example in template.Examples)
        {
            if (example is null
                || !IsIdentifier(example.Id)
                || !HasText(example.Summary, 1_200)
                || !Enum.IsDefined(example.Kind)
                || !exampleIds.Add(example.Id!))
                return "INVALID_PROJECT_TEMPLATE_EXAMPLE";
        }

        try
        {
            return JsonSerializer.SerializeToUtf8Bytes(template, SerializerOptions).Length <= MaximumSerializedBytes
                ? null
                : "PROJECT_TEMPLATE_TOO_LARGE";
        }
        catch (JsonException)
        {
            return "INVALID_PROJECT_TEMPLATE";
        }
        catch (NotSupportedException)
        {
            return "INVALID_PROJECT_TEMPLATE";
        }
    }

    public static string? ValidateDraft(ProjectTemplateDocument? template)
    {
        var error = Validate(template);
        if (error is not null) return error;
        if (!HasNormalAndEdgeExamples(template!))
            return "PROJECT_TEMPLATE_REQUIRES_NORMAL_AND_EDGE_EXAMPLES";
        return template!.Examples!.Any(example => example.Reviewed)
            ? "TEMPLATE_REVIEW_METADATA_SERVER_OWNED"
            : null;
    }

    public static string? ValidateReviewedExampleIds(
        ProjectTemplateDocument? template,
        IReadOnlyList<string>? reviewedExampleIds,
        bool required,
        bool requireBothScenarioKinds = true)
    {
        if (template?.Examples is null || reviewedExampleIds is null)
            return required ? "PUBLISHED_TEMPLATE_REQUIRES_REVIEWED_EXAMPLE" : null;

        var availableExamples = template.Examples
            .Where(example => example is not null && IsIdentifier(example.Id))
            .ToDictionary(example => example.Id!, StringComparer.Ordinal);
        var selectedIds = new HashSet<string>(StringComparer.Ordinal);
        foreach (var id in reviewedExampleIds)
        {
            if (!IsIdentifier(id) || !availableExamples.ContainsKey(id!) || !selectedIds.Add(id!))
                return "INVALID_REVIEWED_EXAMPLE_REFERENCE";
        }

        if (!required) return null;
        if (selectedIds.Count == 0)
            return requireBothScenarioKinds
                ? "PUBLISHED_TEMPLATE_REQUIRES_NORMAL_AND_EDGE_EXAMPLES"
                : "PUBLISHED_TEMPLATE_REQUIRES_REVIEWED_EXAMPLE";
        if (!requireBothScenarioKinds) return null;
        var selectedKinds = selectedIds.Select(id => availableExamples[id].Kind).ToHashSet();
        return selectedKinds.Contains(ProjectTemplateExampleKind.Normal)
            && selectedKinds.Contains(ProjectTemplateExampleKind.EdgeOrConflicting)
                ? null
                : "PUBLISHED_TEMPLATE_REQUIRES_NORMAL_AND_EDGE_EXAMPLES";
    }

    private static bool HasNormalAndEdgeExamples(ProjectTemplateDocument template) =>
        template.Examples is not null
        && template.Examples.Any(example => example.Kind == ProjectTemplateExampleKind.Normal)
        && template.Examples.Any(example => example.Kind == ProjectTemplateExampleKind.EdgeOrConflicting);

    public static ProjectTemplateDocument WithReviewedExamples(
        ProjectTemplateDocument template,
        IReadOnlySet<string> reviewedExampleIds) => template with
    {
        Examples = (template.Examples ?? Array.Empty<ProjectTemplateExample>())
            .Select(example => example with { Reviewed = reviewedExampleIds.Contains(example.Id!) })
            .ToArray(),
    };

    private static bool IsIdentifier(string? value) =>
        value is not null && IdentifierPattern.IsMatch(value);

    public static bool IsValidIdentifier(string? value) => IsIdentifier(value);

    public static bool IsValidTemplateId(string? value) =>
        value is { Length: <= 96 } && IsIdentifier(value);

    private static bool HasText(string? value, int maximumLength) =>
        !string.IsNullOrWhiteSpace(value)
        && value.Length <= maximumLength
        && !value.Contains('\0');

    private static bool HasTextList(IReadOnlyList<string>? values, int maximumCount, int maximumLength) =>
        values is { Count: > 0 } && values.Count <= maximumCount && values.All(value => HasText(value, maximumLength));
}
