using System.Text.RegularExpressions;
using System.Text.Json.Serialization;
using Evidrilo.Api.Ai;
using Evidrilo.Api.ProjectTemplates;

namespace Evidrilo.Api.ProjectAi;

public sealed record ProjectAiScaffoldRequest(
    [property: JsonRequired, JsonPropertyName("schema")] string? Schema,
    [property: JsonRequired, JsonPropertyName("version")] string? Version,
    [property: JsonRequired, JsonPropertyName("operation")] string? Operation,
    [property: JsonRequired, JsonPropertyName("projectId")] string? ProjectId,
    [property: JsonRequired, JsonPropertyName("templateId")] string? TemplateId,
    [property: JsonRequired, JsonPropertyName("templateVersion")] int TemplateVersion,
    [property: JsonRequired, JsonPropertyName("baseProjectRevision")] int? BaseProjectRevision,
    [property: JsonRequired, JsonPropertyName("assignmentBrief")] string? AssignmentBrief,
    [property: JsonPropertyName("researchQuestion")] string? ResearchQuestion,
    [property: JsonPropertyName("studentQuestion")] string? StudentQuestion,
    [property: JsonPropertyName("currentFields")] IReadOnlyDictionary<string, string>? CurrentFields,
    [property: JsonPropertyName("constraints")] IReadOnlyList<string>? Constraints,
    [property: JsonRequired, JsonPropertyName("locale")] string? Locale,
    // Legacy client claims are accepted only for wire compatibility. Consent
    // is read exclusively from IProjectAiConsentStore on the authenticated
    // account; these fields must never grant or revoke it.
    [property: JsonPropertyName("optedIn")] bool OptedIn,
    [property: JsonPropertyName("projectDataConsent")] bool ProjectDataConsent,
    [property: JsonPropertyName("projectDataConsentVersion")] string? ProjectDataConsentVersion);

public sealed record ProjectAiFieldSuggestion(
    [property: JsonPropertyName("fieldId")] string FieldId,
    [property: JsonPropertyName("suggestedValue")] string SuggestedValue);

public sealed record ProjectAiScaffoldOutput(
    [property: JsonPropertyName("templateId")] string TemplateId,
    [property: JsonPropertyName("templateVersion")] int TemplateVersion,
    [property: JsonPropertyName("promptVersion")] string PromptVersion,
    [property: JsonPropertyName("guidanceText")] string GuidanceText,
    [property: JsonPropertyName("fieldSuggestions")] IReadOnlyList<ProjectAiFieldSuggestion> FieldSuggestions,
    [property: JsonPropertyName("clarificationQuestions")] IReadOnlyList<string> ClarificationQuestions,
    [property: JsonPropertyName("recommendedNextPrompts")] IReadOnlyList<string> RecommendedNextPrompts)
{
    [JsonIgnore]
    public AiProviderTokenUsage? Usage { get; init; }
}

public sealed record ProjectAiScaffoldResponse(
    [property: JsonPropertyName("schema")] string Schema,
    [property: JsonPropertyName("version")] string Version,
    [property: JsonPropertyName("status")] string Status,
    [property: JsonPropertyName("scaffold")] ProjectAiScaffoldOutput? Scaffold,
    [property: JsonPropertyName("baseProjectRevision"), JsonIgnore(Condition = JsonIgnoreCondition.Never)] int? BaseProjectRevision,
    [property: JsonPropertyName("reasonCode"), JsonIgnore(Condition = JsonIgnoreCondition.Never)] string? ReasonCode,
    [property: JsonPropertyName("requestId")] string RequestId,
    [property: JsonPropertyName("creditCost")] int CreditCost,
    [property: JsonPropertyName("operation")] string Operation,
    [property: JsonPropertyName("projectId"), JsonIgnore(Condition = JsonIgnoreCondition.Never)] string? ProjectId);

public sealed record ProjectAiScaffoldSettlementRequest(
    [property: JsonRequired, JsonPropertyName("schema")] string? Schema,
    [property: JsonRequired, JsonPropertyName("version")] string? Version,
    [property: JsonRequired, JsonPropertyName("requestId")] string? RequestId,
    [property: JsonRequired, JsonPropertyName("decision")] string? Decision);

public sealed record ProjectAiScaffoldSettlementResponse(
    [property: JsonPropertyName("schema")] string Schema,
    [property: JsonPropertyName("version")] string Version,
    [property: JsonPropertyName("status")] string Status,
    [property: JsonPropertyName("requestId")] string RequestId,
    [property: JsonPropertyName("creditCost")] int CreditCost);

public static partial class ProjectAiScaffoldValidator
{
    public const string Schema = "evidrilo.project-ai-scaffold";
    public const string Version = "1";
    public const string SettlementSchema = "evidrilo.project-ai-scaffold-settlement";
    public const string SettlementVersion = "1";
    public const string PromptVersion = "project-scaffold.v1";
    public const string ProjectDataConsentVersion = ProjectAiConsentPolicy.CurrentPolicyVersion;
    public const string CreateOperation = "create_project";
    public const string AssistOperation = "assist_project";

    private static readonly Regex LocalePattern = new(
        "\\A[A-Za-z]{2,8}(?:-[A-Za-z0-9]{1,8})*\\z",
        RegexOptions.CultureInvariant);

    public static string? ValidateRequest(ProjectAiScaffoldRequest? request)
    {
        if (request is null) return "INVALID_PROJECT_AI_REQUEST";
        if (request.Schema != Schema
            || request.Version != Version
            || request.Operation is not (CreateOperation or AssistOperation)
            || !ProjectTemplateDocumentValidator.IsValidTemplateId(request.TemplateId)
            || request.TemplateVersion < 1
            || request.Operation == CreateOperation
                && (request.ProjectId is not null || request.BaseProjectRevision is not null)
            || request.Operation == AssistOperation
                && (!IsValidProjectId(request.ProjectId) || request.BaseProjectRevision is null or < 1)
            || !HasText(request.AssignmentBrief, 8_000)
            || request.ResearchQuestion is { Length: > 2_000 }
            || request.StudentQuestion is { Length: > 2_000 }
            || request.CurrentFields is { Count: > 32 }
            || request.Constraints is { Count: > 8 }
            || string.IsNullOrWhiteSpace(request.Locale)
            || request.Locale.Length > 32
            || !LocalePattern.IsMatch(request.Locale))
        {
            return "INVALID_PROJECT_AI_REQUEST";
        }

        if (request.CurrentFields is not null
            && request.CurrentFields.Any(pair =>
                !ProjectTemplateDocumentValidator.IsValidTemplateId(pair.Key)
                || pair.Value is null
                || pair.Value.Length > 8_000
                || pair.Value.Contains('\0')))
        {
            return "INVALID_PROJECT_AI_FIELDS";
        }
        if (request.Constraints is not null
            && request.Constraints.Any(value => !HasText(value, 400)))
        {
            return "INVALID_PROJECT_AI_CONSTRAINTS";
        }
        if (request.ResearchQuestion is { } question && question.Contains('\0'))
            return "INVALID_PROJECT_AI_REQUEST";
        if (request.StudentQuestion is { } studentQuestion
            && (!HasText(studentQuestion, 2_000) || studentQuestion.Contains('\0')))
            return "INVALID_PROJECT_AI_REQUEST";

        return null;
    }

    public static bool IsValidProjectId(string? value) =>
        value is { Length: 36 }
        && Guid.TryParseExact(value, "D", out var projectId)
        && projectId != Guid.Empty;

    public static string? ValidateSettlementRequest(ProjectAiScaffoldSettlementRequest? request)
    {
        if (request is null
            || !string.Equals(request.Schema, SettlementSchema, StringComparison.Ordinal)
            || !string.Equals(request.Version, SettlementVersion, StringComparison.Ordinal)
            || !IsValidRequestId(request.RequestId)
            || request.Decision is not ("apply" or "dismiss"))
        {
            return "INVALID_PROJECT_AI_SETTLEMENT";
        }
        return null;
    }

    public static bool IsValidRequestId(string? requestId) =>
        requestId is { Length: >= 8 and <= 128 }
        && requestId.All(character =>
            character is >= 'A' and <= 'Z' or >= 'a' and <= 'z' or >= '0' and <= '9' or '_' or '-');

    public static string? ValidateRequestFields(
        ProjectAiScaffoldRequest request,
        ProjectTemplateCatalogEntry? template)
    {
        if (template is null
            || template.TemplateId != request.TemplateId
            || template.TemplateVersion != request.TemplateVersion
            || ProjectTemplateDocumentValidator.Validate(template.Template) is not null)
        {
            return "PROJECT_AI_TEMPLATE_NOT_READY";
        }

        var knownIds = template.Template.InputFields!
            .Select(field => field.Id!)
            .ToHashSet(StringComparer.Ordinal);
        if (request.CurrentFields is not null
            && request.CurrentFields.Keys.Any(fieldId => !knownIds.Contains(fieldId)))
        {
            return "PROJECT_AI_UNKNOWN_FIELD";
        }
        return null;
    }

    public static string? ValidateOutput(
        ProjectTemplateCatalogEntry? template,
        ProjectAiScaffoldOutput? output)
    {
        if (template is null
            || !ProjectTemplateDocumentValidator.IsValidTemplateId(template.TemplateId)
            || template.TemplateVersion < 1
            || ProjectTemplateDocumentValidator.Validate(template.Template) is not null
            || !string.Equals(template.TemplateId, output?.TemplateId, StringComparison.Ordinal)
            || template.TemplateVersion != output?.TemplateVersion)
        {
            return "PROJECT_AI_TEMPLATE_MISMATCH";
        }
        if (output!.PromptVersion != PromptVersion
            || !HasText(output.GuidanceText, 2_500)
            || AiRedactor.ContainsCredential(output.GuidanceText)
            || output.FieldSuggestions is null
            || output.FieldSuggestions.Count > 32
            || output.ClarificationQuestions is null
            || output.ClarificationQuestions.Count > 8
            || output.RecommendedNextPrompts is null
            || output.RecommendedNextPrompts.Count > 6)
        {
            return "PROJECT_AI_INVALID_RESPONSE";
        }

        var fieldsById = template.Template.InputFields!
            .Where(field => field?.Id is not null)
            .ToDictionary(field => field.Id!, StringComparer.Ordinal);
        var seen = new HashSet<string>(StringComparer.Ordinal);
        foreach (var suggestion in output.FieldSuggestions)
        {
            if (suggestion is null
                || string.IsNullOrWhiteSpace(suggestion.FieldId)
                || !fieldsById.TryGetValue(suggestion.FieldId, out var field))
            {
                return "PROJECT_AI_UNKNOWN_FIELD";
            }
            if (!CanSuggest(field.Kind)) return "PROJECT_AI_FIELD_NOT_ALLOWED";
            if (!seen.Add(suggestion.FieldId)
                || !HasText(suggestion.SuggestedValue, 8_000)
                || AiRedactor.ContainsCredential(suggestion.SuggestedValue))
            {
                return "PROJECT_AI_INVALID_RESPONSE";
            }
        }
        if (output.ClarificationQuestions.Any(question =>
                !HasText(question, 400) || AiRedactor.ContainsCredential(question)))
        {
            return "PROJECT_AI_INVALID_RESPONSE";
        }
        if (output.RecommendedNextPrompts.Any(prompt =>
                !HasText(prompt, 240) || AiRedactor.ContainsCredential(prompt)))
        {
            return "PROJECT_AI_INVALID_RESPONSE";
        }

        return null;
    }

    private static bool CanSuggest(ProjectTemplateInputKind kind) => kind is
        ProjectTemplateInputKind.AssignmentBrief or
        ProjectTemplateInputKind.ResearchQuestion or
        ProjectTemplateInputKind.Hypothesis or
        ProjectTemplateInputKind.NextAction;

    private static bool HasText(string? value, int maximumLength) =>
        !string.IsNullOrWhiteSpace(value)
        && value.Length <= maximumLength
        && !value.Contains('\0');
}
