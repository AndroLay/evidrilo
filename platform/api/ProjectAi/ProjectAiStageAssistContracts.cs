using System.Text.Json.Serialization;
using System.Text.RegularExpressions;
using Evidrilo.Api.ProjectTemplates;

namespace Evidrilo.Api.ProjectAi;

public sealed record ProjectAiStageAssistRequest(
    [property: JsonRequired, JsonPropertyName("schema")] string? Schema,
    [property: JsonRequired, JsonPropertyName("version")] string? Version,
    [property: JsonRequired, JsonPropertyName("mode")] string? Mode,
    [property: JsonRequired, JsonPropertyName("installationId")] string? InstallationId,
    [property: JsonPropertyName("projectId")] string? ProjectId,
    [property: JsonPropertyName("templateId")] string? TemplateId,
    [property: JsonPropertyName("templateVersion")] int? TemplateVersion,
    [property: JsonPropertyName("stageId")] string? StageId,
    [property: JsonPropertyName("operationId")] string? OperationId,
    [property: JsonPropertyName("baseProjectRevision")] int? BaseProjectRevision,
    [property: JsonPropertyName("selectedFields")] IReadOnlyDictionary<string, string>? SelectedFields,
    [property: JsonRequired, JsonPropertyName("locale")] string? Locale);

public sealed record ProjectAiStageAssistResponse(
    [property: JsonPropertyName("schema")] string Schema,
    [property: JsonPropertyName("version")] string Version,
    [property: JsonPropertyName("status")] string Status,
    [property: JsonPropertyName("mode")] string Mode,
    [property: JsonPropertyName("projectId")] string ProjectId,
    [property: JsonPropertyName("baseProjectRevision")] int BaseProjectRevision,
    [property: JsonPropertyName("stageId")] string StageId,
    [property: JsonPropertyName("operationId")] string OperationId,
    [property: JsonPropertyName("scaffold")] ProjectAiScaffoldOutput Scaffold,
    [property: JsonPropertyName("requestId")] string RequestId,
    [property: JsonPropertyName("creditCost")] int CreditCost);

public sealed record ProjectAiStageAssistSettlementRequest(
    [property: JsonRequired, JsonPropertyName("schema")] string? Schema,
    [property: JsonRequired, JsonPropertyName("version")] string? Version,
    [property: JsonRequired, JsonPropertyName("installationId")] string? InstallationId,
    [property: JsonRequired, JsonPropertyName("requestId")] string? RequestId,
    [property: JsonRequired, JsonPropertyName("outcome")] string? Outcome,
    [property: JsonPropertyName("resultProjectRevision")] int? ResultProjectRevision);

public sealed record ProjectAiStageAssistSettlementResponse(
    [property: JsonPropertyName("schema")] string Schema,
    [property: JsonPropertyName("version")] string Version,
    [property: JsonPropertyName("outcome")] string Outcome,
    [property: JsonPropertyName("requestId")] string RequestId,
    [property: JsonPropertyName("creditCost")] int CreditCost);

public static partial class ProjectAiStageAssistValidator
{
    public const string Schema = "evidrilo.project-ai-stage-assist";
    public const string Version = "1";
    public const string ProjectMode = "PROJECT";
    public const string GeneralMode = "GENERAL";
    public const string SettlementSchema = "evidrilo.project-ai-stage-assist-settlement";

    private static readonly Regex IdentifierPattern = new(
        "\\A[a-z0-9]+(?:[._-][a-z0-9]+)*\\z",
        RegexOptions.CultureInvariant);
    private static readonly Regex LocalePattern = new(
        "\\A[A-Za-z]{2,8}(?:-[A-Za-z0-9]{1,8})*\\z",
        RegexOptions.CultureInvariant);

    public static string? ValidateRequest(ProjectAiStageAssistRequest? request)
    {
        if (request is null
            || !string.Equals(request.Schema, Schema, StringComparison.Ordinal)
            || !string.Equals(request.Version, Version, StringComparison.Ordinal)
            || request.Mode is not (ProjectMode or GeneralMode)
            || !Guid.TryParseExact(request.InstallationId, "D", out var installationId)
            || installationId == Guid.Empty
            || request.Locale is null
            || !LocalePattern.IsMatch(request.Locale))
        {
            return "INVALID_PROJECT_AI_STAGE_ASSIST";
        }

        if (request.Mode == GeneralMode)
        {
            return request.ProjectId is null
                && request.TemplateId is null
                && request.TemplateVersion is null
                && request.StageId is null
                && request.OperationId is null
                && request.BaseProjectRevision is null
                && (request.SelectedFields is null || request.SelectedFields.Count == 0)
                    ? null
                    : "PROJECT_AI_GENERAL_CONTEXT_NOT_ALLOWED";
        }

        if (!ProjectAiScaffoldValidator.IsValidProjectId(request.ProjectId)
            || !ProjectTemplateDocumentValidator.IsValidTemplateId(request.TemplateId)
            || request.TemplateVersion is null or < 1
            || request.BaseProjectRevision is null or < 1
            || !IsIdentifier(request.StageId)
            || !IsIdentifier(request.OperationId)
            || request.SelectedFields is null or { Count: > 32 }
            || request.SelectedFields.Any(pair =>
                !IsIdentifier(pair.Key)
                || pair.Value is null
                || pair.Value.Length > 8_000
                || pair.Value.Contains('\0')))
        {
            return "INVALID_PROJECT_AI_STAGE_ASSIST";
        }

        return null;
    }

    public static string? ValidateSettlementRequest(ProjectAiStageAssistSettlementRequest? request)
    {
        if (request is null
            || !string.Equals(request.Schema, SettlementSchema, StringComparison.Ordinal)
            || !string.Equals(request.Version, Version, StringComparison.Ordinal)
            || !Guid.TryParseExact(request.InstallationId, "D", out var installationId)
            || installationId == Guid.Empty
            || !ProjectAiScaffoldValidator.IsValidRequestId(request.RequestId))
            return "INVALID_PROJECT_AI_STAGE_ASSIST_SETTLEMENT";

        return request.Outcome switch
        {
            "APPLIED" or "EDITED" when request.ResultProjectRevision is >= 2 => null,
            "DISMISSED" or "STALE" when request.ResultProjectRevision is null => null,
            _ => "INVALID_PROJECT_AI_STAGE_ASSIST_SETTLEMENT",
        };
    }

    public static string? ValidateTemplateCapability(
        ProjectAiStageAssistRequest request,
        ProjectTemplateCatalogEntry? template)
    {
        if (template is null
            || !string.Equals(template.TemplateId, request.TemplateId, StringComparison.Ordinal)
            || template.TemplateVersion != request.TemplateVersion
            || ProjectTemplateDocumentValidator.Validate(template.Template) is not null)
        {
            return "PROJECT_AI_TEMPLATE_NOT_READY";
        }

        var capability = FindCapability(request, template);
        if (capability is null)
        {
            var stageExists = template.Template.Steps!.Any(step =>
                string.Equals(step.Id, request.StageId, StringComparison.Ordinal));
            return stageExists ? "PROJECT_AI_OPERATION_NOT_SUPPORTED" : "PROJECT_AI_STAGE_NOT_SUPPORTED";
        }

        var allowedFields = capability.InputFieldIds!.ToHashSet(StringComparer.Ordinal);
        if (request.SelectedFields!.Keys.Any(fieldId => !allowedFields.Contains(fieldId)))
            return "PROJECT_AI_FIELD_NOT_ALLOWED_FOR_OPERATION";
        if (allowedFields.Count > 0 && request.SelectedFields.Count == 0)
            return "PROJECT_AI_CONTEXT_REQUIRED";

        return null;
    }

    public static string? ValidateOutput(
        ProjectAiStageAssistRequest request,
        ProjectTemplateCatalogEntry? template,
        ProjectAiScaffoldOutput? output)
    {
        var outputError = ProjectAiScaffoldValidator.ValidateOutput(template, output);
        if (outputError is not null || template is null)
            return outputError ?? "PROJECT_AI_TEMPLATE_NOT_READY";

        var capability = FindCapability(request, template);
        if (capability is null)
            return "PROJECT_AI_OPERATION_NOT_SUPPORTED";

        var allowedOutputs = capability.OutputFieldIds!.ToHashSet(StringComparer.Ordinal);
        return output!.FieldSuggestions.Any(suggestion => !allowedOutputs.Contains(suggestion.FieldId))
            ? "PROJECT_AI_OUTPUT_FIELD_NOT_ALLOWED_FOR_OPERATION"
            : null;
    }

    public static ProjectTemplateAiOperationCapability? FindCapability(
        ProjectAiStageAssistRequest request,
        ProjectTemplateCatalogEntry template) =>
        template.Template.Steps!
            .FirstOrDefault(step => string.Equals(step.Id, request.StageId, StringComparison.Ordinal))?
            .AiOperations?
            .FirstOrDefault(operation => string.Equals(operation.Id, request.OperationId, StringComparison.Ordinal));

    private static bool IsIdentifier(string? value) =>
        value is { Length: > 0 and <= 80 } && IdentifierPattern.IsMatch(value);
}
