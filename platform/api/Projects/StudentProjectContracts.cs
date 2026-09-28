using System.Security.Cryptography;
using System.Text.Json;
using System.Text.Json.Serialization;
using System.Text.RegularExpressions;

namespace Evidrilo.Api.Projects;

public enum StudentProjectEvidenceKind
{
    Source,
    Data,
    Observation,
}

public sealed record StudentProjectCriterion(string? Id, string? Text);

public sealed record StudentProjectEvidenceItem(
    string? Id,
    StudentProjectEvidenceKind Kind,
    string? Label,
    [property: JsonIgnore(Condition = JsonIgnoreCondition.Never)] string? Summary,
    [property: JsonIgnore(Condition = JsonIgnoreCondition.Never)] string? Origin);

public sealed record StudentProjectCriterionEvidenceLink(string? CriterionId, string? EvidenceItemId);

public sealed record StudentProjectLimitation(string? Id, string? Text);

public enum StudentProjectClaimReviewStatus
{
    Draft,
    ReadyForReview,
    NeedsRevision,
}

public enum StudentProjectClaimRelation
{
    Supports,
    Contradicts,
    ProvidesContext,
}

public sealed record StudentProjectClaim(
    string? Id,
    string? Statement,
    string? ScopeNote,
    [property: JsonIgnore(Condition = JsonIgnoreCondition.Never)] string? LimitationsNote,
    StudentProjectClaimReviewStatus ReviewStatus);

public sealed record StudentProjectClaimEvidenceLink(
    string? ClaimId,
    string? EvidenceItemId,
    StudentProjectClaimRelation Relationship,
    [property: JsonIgnore(Condition = JsonIgnoreCondition.Never)] string? Rationale);

/// <summary>
/// Manually entered student work. These fields preserve structure and
/// provenance; they are not a universal academic-method rubric.
/// </summary>
public sealed record StudentProjectDocument(
    string? Title,
    string? TaskBrief,
    string? Question,
    string? Method,
    [property: JsonIgnore(Condition = JsonIgnoreCondition.Never)] string? Hypothesis,
    IReadOnlyList<StudentProjectCriterion>? Criteria,
    IReadOnlyList<StudentProjectEvidenceItem>? EvidenceItems,
    IReadOnlyList<StudentProjectCriterionEvidenceLink>? CriterionEvidenceLinks,
    [property: JsonIgnore(Condition = JsonIgnoreCondition.Never)] string? Analysis,
    [property: JsonIgnore(Condition = JsonIgnoreCondition.Never)] string? Claim,
    IReadOnlyList<string>? ClaimEvidenceIds,
    IReadOnlyList<StudentProjectLimitation>? Limitations,
    [property: JsonIgnore(Condition = JsonIgnoreCondition.Never)] string? NextAction,
    [property: JsonIgnore(Condition = JsonIgnoreCondition.Never)] IReadOnlyList<StudentProjectClaim>? Claims = null,
    [property: JsonIgnore(Condition = JsonIgnoreCondition.Never)] IReadOnlyList<StudentProjectClaimEvidenceLink>? ClaimEvidenceLinks = null);

public sealed record StudentProjectCreateRequest(
    string? Schema,
    string? Version,
    StudentProjectDocument? Project);

public sealed record StudentProjectSaveRequest(
    string? Schema,
    string? Version,
    int ExpectedVersion,
    StudentProjectDocument? Project);

public sealed record StudentProjectPermanentDeleteRequest(
    string? Schema,
    string? Version,
    int ExpectedVersion,
    bool ConfirmPermanently);

public sealed record StudentProjectCloudConsentUpdateRequest(
    string? Schema,
    string? Version,
    string? PolicyVersion,
    string? Decision);

public sealed record StudentProjectCloudConsentState(
    bool Granted,
    string PolicyVersion,
    DateTimeOffset? GrantedAt,
    DateTimeOffset? RevokedAt,
    DateTimeOffset? UpdatedAt);

public static class StudentProjectCloudConsentPolicy
{
    public const string CurrentVersion = "student-project-cloud.v1";

    public static bool IsDecision(string? decision) =>
        string.Equals(decision, "grant", StringComparison.Ordinal)
        || string.Equals(decision, "revoke", StringComparison.Ordinal);

    public static bool IsValidPolicyVersion(string? policyVersion) =>
        !string.IsNullOrWhiteSpace(policyVersion)
        && policyVersion.Length <= 80
        && policyVersion.StartsWith("student-project-cloud.v", StringComparison.Ordinal)
        && policyVersion.AsSpan("student-project-cloud.v".Length).ToString() is { Length: > 0 } suffix
        && suffix.All(char.IsAsciiDigit);

    public static bool AcceptsGrant(string? policyVersion) =>
        string.Equals(policyVersion, CurrentVersion, StringComparison.Ordinal);
}

public sealed record StudentProjectRecord(
    Guid ProjectId,
    int Version,
    StudentProjectDocument Document,
    DateTimeOffset CreatedAt,
    DateTimeOffset UpdatedAt);

public sealed record StudentProjectListItem(
    Guid ProjectId,
    int Version,
    string Title,
    string Question,
    string Method,
    DateTimeOffset CreatedAt,
    DateTimeOffset UpdatedAt);

public sealed record StudentProjectRevisionRecord(
    Guid RevisionId,
    int Version,
    StudentProjectDocument Document,
    DateTimeOffset CreatedAt);

public sealed record StudentProjectMutation(string Outcome, Guid ProjectId, int Version);

public sealed record StudentProjectListResult(
    IReadOnlyList<StudentProjectListItem> Projects,
    bool HasMore);

public sealed record StudentProjectRevisionPage(
    IReadOnlyList<StudentProjectRevisionRecord> Revisions,
    bool HasMore);

public sealed record StudentProjectSectionState(string Section, string State, int ItemCount);

public sealed record StudentProjectStructureReport(
    [property: JsonPropertyName("schema")] string Schema,
    [property: JsonPropertyName("version")] string Version,
    [property: JsonPropertyName("projectId")] Guid ProjectId,
    [property: JsonPropertyName("projectVersion")] int ProjectVersion,
    [property: JsonPropertyName("assessmentStatus")] string AssessmentStatus,
    [property: JsonPropertyName("academicMeritAssessed")] bool AcademicMeritAssessed,
    [property: JsonPropertyName("sections")] IReadOnlyList<StudentProjectSectionState> Sections,
    [property: JsonPropertyName("criterionEvidenceLinkCount")] int CriterionEvidenceLinkCount,
    [property: JsonPropertyName("claimEvidenceLinkCount")] int ClaimEvidenceLinkCount,
    [property: JsonPropertyName("notice")] string Notice,
    [property: JsonPropertyName("requestId")] string RequestId);

public static class StudentProjectDocumentValidator
{
    // Matches the mobile project's 2 MiB encoded-draft budget. The database
    // leaves headroom for jsonb's canonical text representation.
    private const int MaximumDocumentBytes = 2 * 1024 * 1024;
    private static readonly Regex IdentifierPattern = new(
        "\\A[A-Za-z0-9_-]{1,64}\\z",
        RegexOptions.Compiled | RegexOptions.CultureInvariant);
    private static readonly JsonSerializerOptions SerializerOptions = new(JsonSerializerDefaults.Web)
    {
        Converters = { new JsonStringEnumConverter(JsonNamingPolicy.SnakeCaseLower) },
    };

    public static bool IsValid(StudentProjectDocument? project)
    {
        if (project is null
            || !HasText(project.Title, 160)
            || !HasText(project.TaskBrief, 5_000)
            || !HasText(project.Question, 1_200)
            || !HasText(project.Method, 160)
            || !HasOptionalText(project.Hypothesis, 1_200)
            || !HasOptionalText(project.Analysis, 4_000)
            || !HasOptionalText(project.Claim, 2_000)
            || !HasOptionalText(project.NextAction, 1_000)
            || project.Criteria is null or { Count: > 30 }
            || project.EvidenceItems is null or { Count: > 100 }
            || project.CriterionEvidenceLinks is null or { Count: > 300 }
            || project.ClaimEvidenceIds is null or { Count: > 100 }
            || project.Limitations is null or { Count: > 30 }
            || project.Claims is { Count: > 100 }
            || project.ClaimEvidenceLinks is { Count: > 500 })
        {
            return false;
        }

        var criterionIds = new HashSet<string>(StringComparer.Ordinal);
        foreach (var criterion in project.Criteria)
        {
            if (criterion is null
                || !IsIdentifier(criterion.Id)
                || !HasText(criterion.Text, 800)
                || !criterionIds.Add(criterion.Id!))
                return false;
        }

        var evidenceIds = new HashSet<string>(StringComparer.Ordinal);
        foreach (var item in project.EvidenceItems)
        {
            if (item is null
                || !IsIdentifier(item.Id)
                || !Enum.IsDefined(item.Kind)
                || !HasText(item.Label, 160)
                || !HasOptionalText(item.Summary, 2_000)
                || !HasOptionalText(item.Origin, 500)
                || !evidenceIds.Add(item.Id!))
                return false;
        }

        var linkKeys = new HashSet<string>(StringComparer.Ordinal);
        foreach (var link in project.CriterionEvidenceLinks)
        {
            if (link is null
                || !IsIdentifier(link.CriterionId)
                || !IsIdentifier(link.EvidenceItemId)
                || !criterionIds.Contains(link.CriterionId!)
                || !evidenceIds.Contains(link.EvidenceItemId!))
                return false;

            var key = link.CriterionId + "\n" + link.EvidenceItemId;
            if (!linkKeys.Add(key)) return false;
        }

        var claimEvidenceIds = new HashSet<string>(StringComparer.Ordinal);
        if (project.ClaimEvidenceIds.Count > 0
            && string.IsNullOrWhiteSpace(project.Claim)
            && project.Claims is not { Count: > 0 })
            return false;

        foreach (var evidenceId in project.ClaimEvidenceIds)
        {
            if (!IsIdentifier(evidenceId)
                || !evidenceIds.Contains(evidenceId!)
                || !claimEvidenceIds.Add(evidenceId!))
                return false;
        }

        var claims = project.Claims ?? Array.Empty<StudentProjectClaim>();
        var claimIds = new HashSet<string>(StringComparer.Ordinal);
        foreach (var claim in claims)
        {
            if (claim is null
                || !IsIdentifier(claim.Id)
                || !HasText(claim.Statement, 2_000)
                || !HasText(claim.ScopeNote, 1_200)
                || !HasOptionalText(claim.LimitationsNote, 2_000)
                || !Enum.IsDefined(claim.ReviewStatus)
                || !claimIds.Add(claim.Id!))
                return false;
        }

        var claimEvidenceLinkKeys = new HashSet<string>(StringComparer.Ordinal);
        foreach (var link in project.ClaimEvidenceLinks ?? Array.Empty<StudentProjectClaimEvidenceLink>())
        {
            if (link is null
                || !IsIdentifier(link.ClaimId)
                || !IsIdentifier(link.EvidenceItemId)
                || !claimIds.Contains(link.ClaimId!)
                || !evidenceIds.Contains(link.EvidenceItemId!)
                || !Enum.IsDefined(link.Relationship)
                || !HasOptionalText(link.Rationale, 2_000))
                return false;

            var key = link.ClaimId + "\n" + link.EvidenceItemId;
            if (!claimEvidenceLinkKeys.Add(key)) return false;
        }

        var limitationIds = new HashSet<string>(StringComparer.Ordinal);
        foreach (var limitation in project.Limitations)
        {
            if (limitation is null
                || !IsIdentifier(limitation.Id)
                || !HasText(limitation.Text, 800)
                || !limitationIds.Add(limitation.Id!))
                return false;
        }

        try
        {
            return JsonSerializer.SerializeToUtf8Bytes(project, SerializerOptions).Length <= MaximumDocumentBytes;
        }
        catch (JsonException)
        {
            return false;
        }
    }

    private static bool IsIdentifier(string? value) =>
        value is not null && IdentifierPattern.IsMatch(value);

    private static bool HasText(string? value, int maximumLength) =>
        !string.IsNullOrWhiteSpace(value)
        && value.Length <= maximumLength
        && !value.Contains('\0');

    private static bool HasOptionalText(string? value, int maximumLength) =>
        value is null || (value.Length <= maximumLength && !value.Contains('\0'));
}

public static class StudentProjectStructureReporter
{
    public const string NotAssessedNotice =
        "This report checks only whether project sections and student-entered links are present. It does not assess academic merit, evidence quality, or real-world truth.";

    public static StudentProjectStructureReport Create(
        Guid projectId,
        int projectVersion,
        StudentProjectDocument project,
        string requestId)
    {
        var claims = project.Claims ?? Array.Empty<StudentProjectClaim>();
        var legacyClaimEvidenceIds = project.ClaimEvidenceIds!;
        var sections = new StudentProjectSectionState[]
        {
            Section("task_brief", !string.IsNullOrWhiteSpace(project.TaskBrief)),
            Section("question", !string.IsNullOrWhiteSpace(project.Question)),
            Section("method", !string.IsNullOrWhiteSpace(project.Method)),
            new("criteria", project.Criteria!.Count == 0 ? "not_provided" : "provided", project.Criteria.Count),
            new("evidence_items", project.EvidenceItems!.Count == 0 ? "not_provided" : "provided", project.EvidenceItems.Count),
            new("criterion_evidence_links", project.CriterionEvidenceLinks!.Count == 0 ? "not_provided" : "provided", project.CriterionEvidenceLinks.Count),
            Section("analysis", !string.IsNullOrWhiteSpace(project.Analysis)),
            new("claims", claims.Count == 0 && string.IsNullOrWhiteSpace(project.Claim) ? "not_provided" : "provided", claims.Count == 0 ? (string.IsNullOrWhiteSpace(project.Claim) ? 0 : 1) : claims.Count),
            new("claim_evidence_links", project.ClaimEvidenceLinks is { Count: > 0 } ? "provided" : legacyClaimEvidenceIds.Count == 0 ? "not_provided" : "provided", project.ClaimEvidenceLinks?.Count ?? legacyClaimEvidenceIds.Count),
            new("limitations", project.Limitations!.Count == 0 ? "not_provided" : "provided", project.Limitations.Count),
            Section("next_action", !string.IsNullOrWhiteSpace(project.NextAction)),
        };

        return new StudentProjectStructureReport(
            "evidrilo.student-project-structure-report",
            "1",
            projectId,
            projectVersion,
            "not_assessed",
            AcademicMeritAssessed: false,
            sections,
            project.CriterionEvidenceLinks.Count,
            project.ClaimEvidenceLinks?.Count ?? legacyClaimEvidenceIds.Count,
            NotAssessedNotice,
            requestId);
    }

    private static StudentProjectSectionState Section(string name, bool isPresent) =>
        new(name, isPresent ? "provided" : "not_provided", isPresent ? 1 : 0);
}

public static class StudentProjectRequestFingerprint
{
    private static readonly JsonSerializerOptions SerializerOptions = new(JsonSerializerDefaults.Web)
    {
        Converters = { new JsonStringEnumConverter(JsonNamingPolicy.SnakeCaseLower) },
    };

    public static string Create<T>(string operation, Guid? projectId, T request)
        where T : notnull
    {
        var canonicalInput = JsonSerializer.SerializeToUtf8Bytes(
            new FingerprintInput(operation, projectId, request),
            SerializerOptions);
        return Convert.ToHexStringLower(SHA256.HashData(canonicalInput));
    }

    private sealed record FingerprintInput(string Operation, Guid? ProjectId, object Request);
}
