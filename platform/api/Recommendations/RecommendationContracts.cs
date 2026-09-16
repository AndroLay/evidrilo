using System.Text.Json.Serialization;
using System.Text.RegularExpressions;

namespace Evidrilo.Api.Recommendations;

public sealed record RecommendationCandidate(
    string CaseVersionId,
    string Objective,
    string SkillId,
    int Difficulty,
    bool Published,
    IReadOnlyList<string> EvidenceReferences);

public sealed record RecommendationInput(
    string CalculationVersion,
    int AttemptsObserved,
    int ActionRequiredCount,
    int PassCount,
    int AbstentionCount,
    IReadOnlyList<RecommendationCandidate> Candidates);

public sealed record RecommendationResult(
    [property: JsonPropertyName("schema")] string Schema,
    [property: JsonPropertyName("version")] string Version,
    [property: JsonPropertyName("status")] string Status,
    [property: JsonPropertyName("calculationVersion")] string CalculationVersion,
    [property: JsonPropertyName("caseVersionId")] string? CaseVersionId,
    [property: JsonPropertyName("objective")] string? Objective,
    [property: JsonPropertyName("reasonCode")] string ReasonCode,
    [property: JsonPropertyName("evidenceReferences")] IReadOnlyList<string> EvidenceReferences,
    [property: JsonPropertyName("requestId")] string RequestId);

public enum RecommendationInteraction
{
    Shown,
    Accepted,
    Dismissed,
}

public sealed record RecommendationInteractionRequest(
    [property: JsonPropertyName("schema")] string Schema,
    [property: JsonPropertyName("version")] string Version,
    [property: JsonPropertyName("clientEventId")] Guid ClientEventId,
    [property: JsonPropertyName("caseVersionId")] string? CaseVersionId,
    [property: JsonRequired, JsonPropertyName("interaction")] RecommendationInteraction Interaction,
    [property: JsonPropertyName("calculationVersion")] string CalculationVersion,
    [property: JsonPropertyName("reasonCode")] string ReasonCode,
    [property: JsonPropertyName("consent")] string Consent);

public sealed record RecommendationInteractionResponse(
    [property: JsonPropertyName("schema")] string Schema,
    [property: JsonPropertyName("version")] string Version,
    [property: JsonPropertyName("outcome")] string Outcome,
    [property: JsonPropertyName("requestId")] string RequestId);

public static class RecommendationInteractionValidator
{
    private static readonly Regex CaseVersionPattern =
        new("\\A[A-Za-z0-9._:-]{1,128}\\z", RegexOptions.CultureInvariant);
    private static readonly Regex ReasonPattern =
        new("\\A[A-Z0-9_]{3,64}\\z", RegexOptions.CultureInvariant);
    private static readonly HashSet<string> ReasonCodes =
    [
        "START_HERE",
        "PRACTICE_ACTION_REQUIRED",
        "NEXT_PRACTICE",
        "NO_ELIGIBLE_CASE",
        "INSUFFICIENT_PROJECTION",
    ];

    public static string? Validate(RecommendationInteractionRequest? request)
    {
        if (request is null
            || request.Schema != "evidrilo.recommendation-interaction"
            || request.Version != "1"
            || request.ClientEventId == Guid.Empty
            || !Enum.IsDefined(request.Interaction)
            || request.CalculationVersion != RecommendationEngine.CalculationVersion
            || string.IsNullOrWhiteSpace(request.ReasonCode)
            || !ReasonPattern.IsMatch(request.ReasonCode)
            || !ReasonCodes.Contains(request.ReasonCode)
            || (request.CaseVersionId is { } caseVersionId
                && !CaseVersionPattern.IsMatch(caseVersionId))
            || ((request.Interaction is RecommendationInteraction.Accepted or RecommendationInteraction.Dismissed)
                && request.CaseVersionId is null))
            return "INVALID_RECOMMENDATION_INTERACTION";
        if (!string.Equals(request.Consent, "granted", StringComparison.Ordinal))
            return "RECOMMENDATION_CONSENT_REQUIRED";
        return null;
    }
}

public static class RecommendationEngine
{
    public const string CalculationVersion = "recommendation.v1";

    public static RecommendationDecision Decide(RecommendationInput input)
    {
        if (input is null
            || input.CalculationVersion != "progress.v1"
            || input.AttemptsObserved < 0
            || input.ActionRequiredCount < 0
            || input.PassCount < 0
            || input.AbstentionCount < 0
            || (long)input.PassCount + input.ActionRequiredCount + input.AbstentionCount
                > input.AttemptsObserved)
            return RecommendationDecision.Abstain("INSUFFICIENT_PROJECTION");

        var candidates = input.Candidates
            .Where(candidate => candidate.Published
                && !string.IsNullOrWhiteSpace(candidate.CaseVersionId)
                && !string.IsNullOrWhiteSpace(candidate.Objective)
                && !string.IsNullOrWhiteSpace(candidate.SkillId)
                && candidate.EvidenceReferences.Count > 0)
            .OrderBy(candidate => candidate.Difficulty)
            .ThenBy(candidate => candidate.SkillId, StringComparer.Ordinal)
            .ThenBy(candidate => candidate.CaseVersionId, StringComparer.Ordinal)
            .ToArray();
        if (candidates.Length == 0)
            return RecommendationDecision.Abstain("NO_ELIGIBLE_CASE");

        var preferred = input.AttemptsObserved == 0
            ? candidates[0]
            : input.ActionRequiredCount > input.PassCount
                ? candidates.FirstOrDefault(candidate => candidate.SkillId.Length > 0) ?? candidates[0]
                : candidates[0];
        var reason = input.AttemptsObserved == 0
            ? "START_HERE"
            : input.ActionRequiredCount > input.PassCount
                ? "PRACTICE_ACTION_REQUIRED"
                : "NEXT_PRACTICE";
        return new RecommendationDecision(
            "recommended",
            preferred.CaseVersionId,
            preferred.Objective,
            reason,
            preferred.EvidenceReferences);
    }
}

public sealed record RecommendationDecision(
    string Status,
    string? CaseVersionId,
    string? Objective,
    string ReasonCode,
    IReadOnlyList<string> EvidenceReferences)
{
    public static RecommendationDecision Abstain(string reasonCode) =>
        new("abstain", null, null, reasonCode, Array.Empty<string>());
}
