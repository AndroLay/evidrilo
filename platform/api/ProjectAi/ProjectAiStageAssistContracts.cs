using System.Text.Json;
using System.Text.Json.Serialization;
using System.Text.RegularExpressions;
using Evidrilo.Api.Ai;
using Evidrilo.Api.ProjectTemplates;
using Evidrilo.Api.Projects;

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
    [property: JsonPropertyName("selectedFieldIds")] IReadOnlyList<string>? SelectedFieldIds,
    [property: JsonPropertyName("selectedEvidenceIds")] IReadOnlyList<string>? SelectedEvidenceIds,
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
    [property: JsonPropertyName("assist")] ProjectAiStageAssistOutput Assist,
    [property: JsonPropertyName("evaluationPreview")] ProjectAiStageAssistEvaluationPreview EvaluationPreview,
    [property: JsonPropertyName("requestId")] string RequestId,
    [property: JsonPropertyName("creditCost")] int CreditCost);

public sealed record ProjectAiStageAssistItem(
    [property: JsonPropertyName("id")] string Id,
    [property: JsonPropertyName("kind")] string Kind,
    [property: JsonPropertyName("text")] string? Text,
    [property: JsonPropertyName("targetFieldId")] string? TargetFieldId,
    [property: JsonPropertyName("beforeValue")] string? BeforeValue,
    [property: JsonPropertyName("afterValue")] string? AfterValue,
    [property: JsonPropertyName("referenceIds")] IReadOnlyList<string>? ReferenceIds,
    [property: JsonPropertyName("assumptions")] IReadOnlyList<string>? Assumptions,
    [property: JsonPropertyName("uncertainties")] IReadOnlyList<string>? Uncertainties,
    [property: JsonPropertyName("knownLimits")] IReadOnlyList<string>? KnownLimits);

public sealed record ProjectAiStageAssistOutput(
    [property: JsonPropertyName("templateId")] string TemplateId,
    [property: JsonPropertyName("templateVersion")] int TemplateVersion,
    [property: JsonPropertyName("promptVersion")] string PromptVersion,
    [property: JsonPropertyName("items")] IReadOnlyList<ProjectAiStageAssistItem> Items,
    [property: JsonPropertyName("reportedConflicts")] IReadOnlyList<string> ReportedConflicts,
    [property: JsonPropertyName("reportedOutOfScopeItems")] IReadOnlyList<string> ReportedOutOfScopeItems)
{
    [JsonIgnore]
    public AiProviderTokenUsage? Usage { get; init; }
}

public sealed record ProjectAiStageAssistEvidenceContext(
    string Id,
    string Kind,
    string Label,
    string? Summary,
    string? Origin);

public sealed record ProjectAiStageAssistSelectedFieldContext(
    string Id,
    ProjectTemplateInputKind Kind,
    string Label,
    string Value);

public sealed record ProjectAiStageAssistProviderRequest(
    string TemplateId,
    int TemplateVersion,
    string TemplateFamily,
    string TemplateTitle,
    string TemplateSummary,
    string StageId,
    string StageTitle,
    string OperationId,
    IReadOnlyList<ProjectAiStageAssistSelectedFieldContext> SelectedFields,
    IReadOnlyList<ProjectAiStageAssistEvidenceContext> SelectedEvidence,
    IReadOnlyList<string> AllowedOutputFieldIds,
    IReadOnlyList<string> MethodSpecificLimitations,
    IReadOnlyList<string> ProvenanceRequirements,
    string Locale)
{
    public Guid AccountId { get; init; }

    public string RequestId { get; init; } = string.Empty;
}

public sealed record ProjectAiStageAssistEvidenceReference(
    [property: JsonPropertyName("id")] string Id,
    [property: JsonPropertyName("kind")] string Kind,
    [property: JsonPropertyName("label")] string Label);

public sealed record ProjectAiStageAssistEvaluationPreview(
    [property: JsonPropertyName("assessmentStatus")] string AssessmentStatus,
    [property: JsonPropertyName("supportingItems")] IReadOnlyList<ProjectAiStageAssistEvidenceReference> SupportingItems,
    [property: JsonPropertyName("proposalsWithoutReferences")] IReadOnlyList<string> ProposalsWithoutReferences,
    [property: JsonPropertyName("reportedConflicts")] IReadOnlyList<string> ReportedConflicts,
    [property: JsonPropertyName("reportedAssumptions")] IReadOnlyList<string> ReportedAssumptions,
    [property: JsonPropertyName("reportedUncertainties")] IReadOnlyList<string> ReportedUncertainties,
    [property: JsonPropertyName("reportedKnownLimits")] IReadOnlyList<string> ReportedKnownLimits,
    [property: JsonPropertyName("templateLimits")] IReadOnlyList<string> TemplateLimits,
    [property: JsonPropertyName("reportedOutOfScopeItems")] IReadOnlyList<string> ReportedOutOfScopeItems,
    [property: JsonPropertyName("checksUnavailable")] IReadOnlyList<string> ChecksUnavailable);

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
    public const string PromptVersion = "project-ai-stage-assist.v1";

    private static readonly Regex IdentifierPattern = new(
        "\\A[a-z0-9]+(?:[._-][a-z0-9]+)*\\z",
        RegexOptions.CultureInvariant);
    private static readonly Regex LocalePattern = new(
        "\\A[A-Za-z]{2,8}(?:-[A-Za-z0-9]{1,8})*\\z",
        RegexOptions.CultureInvariant);
    private static readonly JsonSerializerOptions OutputJsonOptions = new(JsonSerializerDefaults.Web);
    private const int MaximumBoundResponseBytes = 320 * 1024;

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
                && (request.SelectedFieldIds is null || request.SelectedFieldIds.Count == 0)
                && (request.SelectedEvidenceIds is null || request.SelectedEvidenceIds.Count == 0)
                    ? null
                    : "PROJECT_AI_GENERAL_CONTEXT_NOT_ALLOWED";
        }

        if (!ProjectAiScaffoldValidator.IsValidProjectId(request.ProjectId)
            || !ProjectTemplateDocumentValidator.IsValidTemplateId(request.TemplateId)
            || request.TemplateVersion is null or < 1
            || request.BaseProjectRevision is null or < 1
            || !IsIdentifier(request.StageId)
            || !IsIdentifier(request.OperationId)
            || request.SelectedFieldIds is null or { Count: > 32 }
            || request.SelectedFieldIds.Any(id => !IsIdentifier(id))
            || request.SelectedFieldIds.Distinct(StringComparer.Ordinal).Count() != request.SelectedFieldIds.Count
            || request.SelectedEvidenceIds is null or { Count: > 32 }
            || request.SelectedEvidenceIds.Any(id => !IsProjectEvidenceId(id))
            || request.SelectedEvidenceIds.Distinct(StringComparer.Ordinal).Count() != request.SelectedEvidenceIds.Count)
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
        var templateInputs = template.Template.InputFields!.ToDictionary(field => field.Id!, StringComparer.Ordinal);
        if (request.SelectedFieldIds!.Any(fieldId => !allowedFields.Contains(fieldId)))
            return "PROJECT_AI_FIELD_NOT_ALLOWED_FOR_OPERATION";
        if (request.SelectedFieldIds!.Any(fieldId =>
                templateInputs[fieldId].Kind is ProjectTemplateInputKind.Source or ProjectTemplateInputKind.Data))
            return "PROJECT_AI_EVIDENCE_SELECTION_REQUIRES_IDS";
        if (allowedFields.Count > 0 && request.SelectedFieldIds!.Count == 0 && request.SelectedEvidenceIds!.Count == 0)
            return "PROJECT_AI_CONTEXT_REQUIRED";

        return null;
    }

    public static string? ValidateSelectedEvidence(
        ProjectAiStageAssistRequest request,
        ProjectTemplateCatalogEntry? template,
        StudentProjectRecord? project)
    {
        if (template is null || project?.Document?.EvidenceItems is null)
            return "PROJECT_AI_CONTEXT_NOT_READY";

        var capability = FindCapability(request, template);
        if (capability is null) return "PROJECT_AI_OPERATION_NOT_SUPPORTED";
        var inputFields = template.Template.InputFields!.ToDictionary(field => field.Id!, StringComparer.Ordinal);
        var allowedKinds = capability.InputFieldIds!
            .Select(id => inputFields[id].Kind)
            .ToHashSet();
        var evidenceById = project.Document.EvidenceItems
            .ToDictionary(item => item.Id!, StringComparer.Ordinal);

        foreach (var evidenceId in request.SelectedEvidenceIds!)
        {
            if (!evidenceById.TryGetValue(evidenceId, out var evidence))
                return "PROJECT_AI_EVIDENCE_NOT_FOUND";
            if (!Enum.IsDefined(evidence.Kind))
                return "PROJECT_AI_CONTEXT_NOT_READY";

            var requiredKind = evidence.Kind == StudentProjectEvidenceKind.Source
                ? ProjectTemplateInputKind.Source
                : ProjectTemplateInputKind.Data;
            if (!allowedKinds.Contains(requiredKind))
                return "PROJECT_AI_EVIDENCE_NOT_ALLOWED_FOR_OPERATION";
        }

        var selectedEvidence = request.SelectedEvidenceIds
            .Select(id => evidenceById[id])
            .Select(item => new ProjectAiStageAssistEvidenceContext(
                item.Id!,
                item.Kind switch
                {
                    StudentProjectEvidenceKind.Source => "SOURCE",
                    StudentProjectEvidenceKind.Data => "DATA",
                    StudentProjectEvidenceKind.Observation => "OBSERVATION",
                    _ => "UNKNOWN",
                },
                AiRedactor.Redact(item.Label!),
                item.Summary is null ? null : AiRedactor.Redact(item.Summary),
                item.Origin is null ? null : AiRedactor.Redact(item.Origin)))
            .ToArray();
        if (JsonSerializer.SerializeToUtf8Bytes(selectedEvidence, OutputJsonOptions).Length > 64 * 1024)
            return "PROJECT_AI_CONTEXT_TOO_LARGE";

        return null;
    }

    public static string? BuildSelectedFieldContexts(
        ProjectAiStageAssistRequest request,
        ProjectTemplateCatalogEntry? template,
        StudentProjectRecord? project,
        bool redact,
        out IReadOnlyList<ProjectAiStageAssistSelectedFieldContext> contexts)
    {
        contexts = Array.Empty<ProjectAiStageAssistSelectedFieldContext>();
        if (template is null || project?.Document is null)
            return "PROJECT_AI_CONTEXT_NOT_READY";

        var fieldsById = template.Template.InputFields!.ToDictionary(field => field.Id!, StringComparer.Ordinal);
        var selectedContexts = new List<ProjectAiStageAssistSelectedFieldContext>(request.SelectedFieldIds!.Count);
        var selectedKinds = new HashSet<ProjectTemplateInputKind>();
        foreach (var fieldId in request.SelectedFieldIds)
        {
            if (!fieldsById.TryGetValue(fieldId, out var field))
                return "PROJECT_AI_FIELD_NOT_ALLOWED_FOR_OPERATION";
            if (!selectedKinds.Add(field.Kind))
                return "PROJECT_AI_FIELD_MAPPING_AMBIGUOUS";
            if (field.Kind is ProjectTemplateInputKind.Source or ProjectTemplateInputKind.Data)
                return "PROJECT_AI_EVIDENCE_SELECTION_REQUIRES_IDS";
            if (!TryGetStoredFieldValue(field.Kind, project.Document, out var value))
                return "PROJECT_AI_FIELD_KIND_NOT_SUPPORTED";
            if (value.Length > 8_000 || value.Contains('\0'))
                return "PROJECT_AI_CONTEXT_TOO_LARGE";

            selectedContexts.Add(new ProjectAiStageAssistSelectedFieldContext(
                field.Id!,
                field.Kind,
                field.Label!,
                redact ? AiRedactor.Redact(value) : value));
        }

        if (JsonSerializer.SerializeToUtf8Bytes(selectedContexts, OutputJsonOptions).Length > 64 * 1024)
            return "PROJECT_AI_CONTEXT_TOO_LARGE";

        contexts = selectedContexts;
        return null;
    }

    public static string? ValidateOutput(
        ProjectAiStageAssistRequest request,
        ProjectTemplateCatalogEntry? template,
        StudentProjectRecord? project,
        ProjectAiStageAssistOutput? output)
    {
        if (template is null
            || project?.Document?.EvidenceItems is null
            || output is null
            || !string.Equals(template.TemplateId, output.TemplateId, StringComparison.Ordinal)
            || template.TemplateVersion != output.TemplateVersion
            || output.PromptVersion != PromptVersion
            || output.Items is null or { Count: < 1 or > 24 }
            || output.ReportedConflicts is null or { Count: > 8 }
            || output.ReportedOutOfScopeItems is null or { Count: > 8 })
            return "PROJECT_AI_INVALID_RESPONSE";

        var capability = FindCapability(request, template);
        if (capability is null)
            return "PROJECT_AI_OPERATION_NOT_SUPPORTED";

        var allowedOutputs = capability.OutputFieldIds!.ToHashSet(StringComparer.Ordinal);
        if (BuildSelectedFieldContexts(request, template, project, redact: false, out var selectedFieldContexts) is not null)
            return "PROJECT_AI_INVALID_RESPONSE";
        var selectedFields = selectedFieldContexts.ToDictionary(field => field.Id, field => field.Value, StringComparer.Ordinal);
        var selectedEvidence = request.SelectedEvidenceIds!.ToHashSet(StringComparer.Ordinal);
        var existingEvidence = project.Document.EvidenceItems
            .Select(item => item.Id!)
            .ToHashSet(StringComparer.Ordinal);
        var inputIds = template.Template.InputFields!
            .Select(field => field.Id!)
            .ToHashSet(StringComparer.Ordinal);
        var itemIds = new HashSet<string>(StringComparer.Ordinal);
        var proposalTargets = new HashSet<string>(StringComparer.Ordinal);

        foreach (var item in output.Items)
        {
            if (item is null
                || !IsIdentifier(item.Id)
                || !itemIds.Add(item.Id)
                || item.ReferenceIds is null or { Count: > 32 }
                || item.ReferenceIds.Distinct(StringComparer.Ordinal).Count() != item.ReferenceIds.Count
                || item.ReferenceIds.Any(id => !selectedEvidence.Contains(id) || !existingEvidence.Contains(id))
                || item.Assumptions is null or { Count: > 8 }
                || item.Uncertainties is null or { Count: > 8 }
                || item.KnownLimits is null or { Count: > 8 }
                || !ValidTextList(item.Assumptions, 800)
                || !ValidTextList(item.Uncertainties, 800)
                || !ValidTextList(item.KnownLimits, 800))
                return "PROJECT_AI_INVALID_RESPONSE";

            if (item.Kind is "EXPLANATION" or "QUESTION")
            {
                if (item.Text is null
                    || !HasText(item.Text, 2_000)
                    || item.TargetFieldId is not null
                    || item.BeforeValue is not null
                    || item.AfterValue is not null
                    || item.ReferenceIds.Count > 0
                    || item.Assumptions.Count > 0
                    || item.Uncertainties.Count > 0
                    || item.KnownLimits.Count > 0
                    || AiRedactor.ContainsCredential(item.Text))
                    return "PROJECT_AI_INVALID_RESPONSE";
                continue;
            }

            if (item.Kind != "PROPOSAL"
                || item.Text is not null
                || item.TargetFieldId is null
                || !inputIds.Contains(item.TargetFieldId)
                || !allowedOutputs.Contains(item.TargetFieldId))
                return "PROJECT_AI_OUTPUT_FIELD_NOT_ALLOWED_FOR_OPERATION";

            if (!selectedFields.TryGetValue(item.TargetFieldId, out var selectedBefore)
                || (item.BeforeValue is not null
                    && (!string.Equals(item.BeforeValue, AiRedactor.Redact(selectedBefore), StringComparison.Ordinal)
                        || item.BeforeValue.Length > 8_000
                        || item.BeforeValue.Contains('\0')
                        || AiRedactor.ContainsCredential(item.BeforeValue)))
                || item.AfterValue is null
                || !HasText(item.AfterValue, 8_000)
                || AiRedactor.ContainsCredential(item.AfterValue)
                || string.Equals(selectedBefore, item.AfterValue, StringComparison.Ordinal)
                || !proposalTargets.Add(item.TargetFieldId))
                return "PROJECT_AI_INVALID_RESPONSE";
        }

        if (!ValidTextList(output.ReportedConflicts, 800)
            || !ValidTextList(output.ReportedOutOfScopeItems, 800)
            || Distinct(output.Items.SelectMany(item => item.Assumptions!)).Count > 24
            || Distinct(output.Items.SelectMany(item => item.Uncertainties!)).Count > 24
            || Distinct(output.Items.SelectMany(item => item.KnownLimits!)).Count > 24)
            return "PROJECT_AI_INVALID_RESPONSE";

        try
        {
            return JsonSerializer.SerializeToUtf8Bytes(output, OutputJsonOptions).Length <= 64 * 1024
                ? null
                : "PROJECT_AI_INVALID_RESPONSE";
        }
        catch (JsonException)
        {
            return "PROJECT_AI_INVALID_RESPONSE";
        }
    }

    public static ProjectAiStageAssistOutput BindBeforeValues(
        IReadOnlyDictionary<string, string> selectedFields,
        ProjectAiStageAssistOutput output) =>
        output with
        {
            Items = output.Items
                .Select(item => item.Kind == "PROPOSAL"
                    ? item with { BeforeValue = selectedFields[item.TargetFieldId!] }
                    : item)
                .ToArray(),
        };

    public static bool IsBoundResponseWithinLimits(ProjectAiStageAssistOutput output)
    {
        try
        {
            return JsonSerializer.SerializeToUtf8Bytes(output, OutputJsonOptions).Length <= MaximumBoundResponseBytes;
        }
        catch (JsonException)
        {
            return false;
        }
    }

    public static ProjectAiStageAssistEvaluationPreview CreateEvaluationPreview(
        ProjectAiStageAssistRequest request,
        ProjectTemplateCatalogEntry template,
        StudentProjectRecord project,
        ProjectAiStageAssistOutput output)
    {
        var referencedIds = output.Items
            .Where(item => item.Kind == "PROPOSAL")
            .SelectMany(item => item.ReferenceIds!)
            .ToHashSet(StringComparer.Ordinal);
        var evidenceById = project.Document.EvidenceItems!
            .ToDictionary(item => item.Id!, StringComparer.Ordinal);
        var supportingItems = request.SelectedEvidenceIds!
            .Where(referencedIds.Contains)
            .Select(id => evidenceById[id])
            .Select(item => new ProjectAiStageAssistEvidenceReference(
                item.Id!,
                item.Kind switch
                {
                    StudentProjectEvidenceKind.Source => "SOURCE",
                    StudentProjectEvidenceKind.Data => "DATA",
                    StudentProjectEvidenceKind.Observation => "OBSERVATION",
                    _ => "UNKNOWN",
                },
                item.Label!))
            .ToArray();

        return new ProjectAiStageAssistEvaluationPreview(
            "NOT_ASSESSED",
            supportingItems,
            output.Items
                .Where(item => item.Kind == "PROPOSAL" && item.ReferenceIds!.Count == 0)
                .Select(item => item.Id)
                .ToArray(),
            output.ReportedConflicts,
            Distinct(output.Items.SelectMany(item => item.Assumptions!)),
            Distinct(output.Items.SelectMany(item => item.Uncertainties!)),
            Distinct(output.Items.SelectMany(item => item.KnownLimits!)),
            template.Template.MethodSpecificLimitations!,
            output.ReportedOutOfScopeItems,
            ["ACADEMIC_TRUTH", "SEMANTIC_REFERENCE_SUPPORT", "SOURCE_QUALITY", "POST_APPLY_STRUCTURE"]);
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

    private static bool IsProjectEvidenceId(string? value) =>
        value is { Length: > 0 and <= 64 }
        && value.All(character => char.IsAsciiLetterOrDigit(character) || character is '_' or '-');

    private static bool ValidTextList(IReadOnlyList<string> values, int maximumLength) =>
        values.All(value => HasText(value, maximumLength) && !AiRedactor.ContainsCredential(value));

    private static bool HasText(string? value, int maximumLength) =>
        !string.IsNullOrWhiteSpace(value)
        && value.Length <= maximumLength
        && !value.Contains('\0');

    private static IReadOnlyList<string> Distinct(IEnumerable<string> values) =>
        values.Distinct(StringComparer.Ordinal).ToArray();

    private static bool TryGetStoredFieldValue(
        ProjectTemplateInputKind kind,
        StudentProjectDocument project,
        out string value)
    {
        switch (kind)
        {
            case ProjectTemplateInputKind.AssignmentBrief:
                value = project.TaskBrief ?? string.Empty;
                return true;
            case ProjectTemplateInputKind.ResearchQuestion:
                value = project.Question ?? string.Empty;
                return true;
            case ProjectTemplateInputKind.Hypothesis:
                value = project.Hypothesis ?? string.Empty;
                return true;
            case ProjectTemplateInputKind.Analysis:
                value = project.Analysis ?? string.Empty;
                return true;
            case ProjectTemplateInputKind.Claim:
                value = project.Claims is { Count: > 0 }
                    ? string.Join("\n\n", project.Claims.Select(claim =>
                        $"{claim.Id}: {claim.Statement}\nScope: {claim.ScopeNote}\nLimitations: {claim.LimitationsNote ?? string.Empty}"))
                    : project.Claim ?? string.Empty;
                return true;
            case ProjectTemplateInputKind.Limitation:
                value = string.Join("\n", (project.Limitations ?? Array.Empty<StudentProjectLimitation>())
                    .Select(limitation => $"{limitation.Id}: {limitation.Text}"));
                return true;
            case ProjectTemplateInputKind.NextAction:
                value = project.NextAction ?? string.Empty;
                return true;
            default:
                value = string.Empty;
                return false;
        }
    }
}
