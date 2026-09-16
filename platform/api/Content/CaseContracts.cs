using System.Text.Json;
using System.Text.Json.Serialization;
using System.Text.RegularExpressions;

namespace Evidrilo.Api.Content;

public enum CaseLifecycleState
{
    Draft,
    Review,
    Approved,
    Published,
    Retired,
}

public enum ContentActorRole
{
    Author,
    Reviewer,
    Maintainer,
}

public sealed record CaseContent(
    string CaseId,
    string CaseVersionId,
    string Title,
    string ContentHash,
    string EvaluatorVersion,
    IReadOnlyList<string> FactAnchors,
    IReadOnlyList<string> SkillTags);

public sealed record PublishedCaseContent(
    string Objective,
    int Difficulty,
    IReadOnlyList<string> EvidenceReferences,
    IReadOnlyList<AuthoringFact> Facts,
    IReadOnlyList<AuthoringRule> Rules,
    IReadOnlyList<AuthoringVariant> Variants);

public sealed record CaseVersionRecord(
    CaseContent Content,
    CaseLifecycleState State,
    Guid AuthorId,
    Guid? ReviewerId);

public sealed record AuthoringFact(
    [property: JsonPropertyName("id")] string Id,
    [property: JsonPropertyName("type")] string Type,
    [property: JsonPropertyName("text")] string Text);

public sealed record AuthoringRule(
    [property: JsonPropertyName("id")] string Id,
    [property: JsonPropertyName("outcome")] string Outcome,
    [property: JsonPropertyName("anchorIds")] IReadOnlyList<string> AnchorIds);

public sealed record AuthoringVariant(
    [property: JsonPropertyName("id")] string Id,
    [property: JsonPropertyName("removedFactIds")] IReadOnlyList<string> RemovedFactIds);

public sealed record CaseAuthoringDocument(
    [property: JsonPropertyName("content")] CaseContent Content,
    [property: JsonPropertyName("objective")] string Objective,
    [property: JsonPropertyName("difficulty")] int Difficulty,
    [property: JsonPropertyName("evidenceReferences")] IReadOnlyList<string> EvidenceReferences,
    [property: JsonPropertyName("facts")] IReadOnlyList<AuthoringFact> Facts,
    [property: JsonPropertyName("rules")] IReadOnlyList<AuthoringRule> Rules,
    [property: JsonPropertyName("variants")] IReadOnlyList<AuthoringVariant> Variants);

public static class PublishedCaseContentReader
{
    private static readonly JsonSerializerOptions JsonOptions = new(JsonSerializerDefaults.Web)
    {
        UnmappedMemberHandling = JsonUnmappedMemberHandling.Disallow,
    };

    public static bool TryRead(
        JsonElement storedDocument,
        CaseContent expectedContent,
        out PublishedCaseContent? content)
    {
        content = null;
        if (storedDocument.ValueKind != JsonValueKind.Object)
            return false;

        try
        {
            var document = storedDocument.Deserialize<CaseAuthoringDocument>(JsonOptions);
            if (document is null
                || document.Content is null
                || !Matches(document.Content, expectedContent)
                || !CaseAuthoringValidator.Validate(document).IsValid)
                return false;

            content = new PublishedCaseContent(
                document.Objective,
                document.Difficulty,
                document.EvidenceReferences,
                document.Facts,
                document.Rules,
                document.Variants);
            return true;
        }
        catch (JsonException)
        {
            return false;
        }
        catch (NotSupportedException)
        {
            return false;
        }
    }

    private static bool Matches(CaseContent actual, CaseContent expected) =>
        actual.CaseId == expected.CaseId
        && actual.CaseVersionId == expected.CaseVersionId
        && actual.Title == expected.Title
        && actual.ContentHash == expected.ContentHash
        && actual.EvaluatorVersion == expected.EvaluatorVersion
        && actual.FactAnchors.SequenceEqual(expected.FactAnchors, StringComparer.Ordinal)
        && actual.SkillTags.SequenceEqual(expected.SkillTags, StringComparer.Ordinal);
}

public sealed record CaseAuthoringRequest(
    [property: JsonPropertyName("schema")] string Schema,
    [property: JsonPropertyName("version")] string Version,
    [property: JsonPropertyName("organizationId")] Guid OrganizationId,
    [property: JsonPropertyName("document")] CaseAuthoringDocument Document);

public sealed record CaseTransitionRequest(
    [property: JsonPropertyName("schema")] string Schema,
    [property: JsonPropertyName("version")] string Version,
    [property: JsonRequired, JsonPropertyName("targetState")] CaseLifecycleState TargetState,
    [property: JsonPropertyName("reason")] string? Reason);

public sealed record CaseAuthoringResponse(
    [property: JsonPropertyName("schema")] string Schema,
    [property: JsonPropertyName("version")] string Version,
    [property: JsonPropertyName("outcome")] string Outcome,
    [property: JsonPropertyName("caseVersionId")] string CaseVersionId,
    [property: JsonPropertyName("state")] CaseLifecycleState State,
    [property: JsonPropertyName("requestId")] string RequestId);

public sealed record ContentValidationResult(bool IsValid, string? Code)
{
    public static ContentValidationResult Valid { get; } = new(true, null);
}

public sealed record TransitionResult(bool IsAllowed, string? Code)
{
    public static TransitionResult Allowed { get; } = new(true, null);
}

public static class CaseContentValidator
{
    private static readonly Regex IdentifierPattern =
        new("\\A[A-Za-z0-9._:-]{1,128}\\z", RegexOptions.CultureInvariant);
    private static readonly Regex HashPattern =
        new("\\A[a-f0-9]{64}\\z", RegexOptions.CultureInvariant);

    public static ContentValidationResult Validate(CaseContent? content)
    {
        if (content is null) return Invalid("INVALID_CASE_CONTENT");
        if (!IdentifierPattern.IsMatch(content.CaseId)
            || !IdentifierPattern.IsMatch(content.CaseVersionId)
            || !IdentifierPattern.IsMatch(content.EvaluatorVersion)
            || !HashPattern.IsMatch(content.ContentHash))
            return Invalid("INVALID_CASE_CONTENT");
        if (string.IsNullOrWhiteSpace(content.Title) || content.Title.Length > 200)
            return Invalid("INVALID_CASE_CONTENT");
        if (content.FactAnchors is null
            || content.SkillTags is null
            || content.FactAnchors.Count is < 1 or > 128
            || content.FactAnchors.Any(anchor => !IdentifierPattern.IsMatch(anchor)))
            return Invalid("INVALID_CASE_CONTENT");
        if (content.SkillTags.Count is < 1 or > 32
            || content.SkillTags.Any(tag => !IdentifierPattern.IsMatch(tag)))
            return Invalid("INVALID_CASE_CONTENT");

        return ContentValidationResult.Valid;
    }

    private static ContentValidationResult Invalid(string code) => new(false, code);
}

public static class CaseAuthoringValidator
{
    private static readonly JsonSerializerOptions StoredDocumentJsonOptions = new(JsonSerializerDefaults.Web)
    {
        UnmappedMemberHandling = JsonUnmappedMemberHandling.Disallow,
    };
    private static readonly HashSet<string> FactTypes =
        ["aim", "context", "observation", "limitation", "boundary"];
    private static readonly HashSet<string> Outcomes =
        ["PASS", "ACTION_REQUIRED", "CANNOT_ASSESS", "INCOMPLETE"];

    public static ContentValidationResult Validate(CaseAuthoringDocument? document)
    {
        if (document is null
            || !CaseContentValidator.Validate(document.Content).IsValid
            || string.IsNullOrWhiteSpace(document.Objective)
            || document.Objective.Length > 200
            || document.Difficulty is < 1 or > 5
            || document.EvidenceReferences is null
            || document.EvidenceReferences.Count is < 1 or > 128
            || document.EvidenceReferences.Any(reference => !IsIdentifier(reference))
            || document.Facts is null
            || document.Facts.Count is < 1 or > 128
            || document.Rules is null
            || document.Rules.Count is < 1 or > 64
            || document.Variants is null
            || document.Variants.Count is < 1 or > 32)
            return Invalid();

        var factIds = new HashSet<string>(StringComparer.Ordinal);
        foreach (var fact in document.Facts)
        {
            if (fact is null
                || !IsIdentifier(fact.Id)
                || !FactTypes.Contains(fact.Type)
                || string.IsNullOrWhiteSpace(fact.Text)
                || fact.Text.Length > 2000
                || !factIds.Add(fact.Id))
                return Invalid();
        }

        if (document.Content.FactAnchors.Any(anchor => !factIds.Contains(anchor)))
            return Invalid();

        var ruleIds = new HashSet<string>(StringComparer.Ordinal);
        foreach (var rule in document.Rules)
        {
            if (rule is null
                || !IsIdentifier(rule.Id)
                || !ruleIds.Add(rule.Id)
                || !Outcomes.Contains(rule.Outcome)
                || rule.AnchorIds is null
                || rule.AnchorIds.Count is < 1 or > 32
                || rule.AnchorIds.Any(anchor => !factIds.Contains(anchor)))
                return Invalid();
        }

        var variantIds = new HashSet<string>(StringComparer.Ordinal);
        foreach (var variant in document.Variants)
        {
            if (variant is null
                || !IsIdentifier(variant.Id)
                || !variantIds.Add(variant.Id)
                || variant.RemovedFactIds is null
                || variant.RemovedFactIds.Count is < 1 or > 128
                || variant.RemovedFactIds.Any(factId => !factIds.Contains(factId)))
                return Invalid();
        }

        return ContentValidationResult.Valid;
    }

    public static ContentValidationResult ValidateSerializedDocument(string? serializedDocument)
    {
        if (string.IsNullOrWhiteSpace(serializedDocument))
            return Invalid();

        try
        {
            return Validate(JsonSerializer.Deserialize<CaseAuthoringDocument>(
                serializedDocument,
                StoredDocumentJsonOptions));
        }
        catch (JsonException)
        {
            return Invalid();
        }
        catch (NotSupportedException)
        {
            return Invalid();
        }
    }

    private static bool IsIdentifier(string value) =>
        !string.IsNullOrWhiteSpace(value)
        && value.Length <= 128
        && Regex.IsMatch(value, "\\A[A-Za-z0-9._:-]+\\z", RegexOptions.CultureInvariant);

    private static ContentValidationResult Invalid() => new(false, "INVALID_CASE_AUTHORING_DOCUMENT");
}

public static class CaseWorkflow
{
    public static TransitionResult ValidateTransition(
        CaseVersionRecord current,
        CaseLifecycleState target,
        Guid actorId,
        ContentActorRole actorRole)
    {
        if (current.State == CaseLifecycleState.Published && target != CaseLifecycleState.Retired)
            return Denied("PUBLISHED_VERSION_IMMUTABLE");
        if (current.State == CaseLifecycleState.Retired)
            return Denied("RETIRED_VERSION_IMMUTABLE");
        if (actorId == Guid.Empty) return Denied("ACTOR_REQUIRED");

        var allowed = (current.State, target, actorRole) switch
        {
            (CaseLifecycleState.Draft, CaseLifecycleState.Review, ContentActorRole.Author) => true,
            (CaseLifecycleState.Draft, CaseLifecycleState.Review, ContentActorRole.Maintainer) => true,
            (CaseLifecycleState.Review, CaseLifecycleState.Approved, ContentActorRole.Reviewer)
                when current.AuthorId != actorId => true,
            (CaseLifecycleState.Review, CaseLifecycleState.Draft, ContentActorRole.Reviewer)
                when current.AuthorId != actorId => true,
            (CaseLifecycleState.Approved, CaseLifecycleState.Published, ContentActorRole.Maintainer) => true,
            (CaseLifecycleState.Published, CaseLifecycleState.Retired, ContentActorRole.Maintainer) => true,
            (_, CaseLifecycleState.Retired, ContentActorRole.Maintainer) => true,
            _ => false,
        };
        return allowed ? TransitionResult.Allowed : Denied("INVALID_CASE_TRANSITION");
    }

    private static TransitionResult Denied(string code) => new(false, code);
}
