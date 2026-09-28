namespace Evidrilo.Api.Ai;

public sealed record AiOutputValidationResult(
    bool IsValid,
    string? ReasonCode,
    IReadOnlyList<string> ReferencedAnchorIds)
{
    public static AiOutputValidationResult Invalid(string reasonCode) =>
        new(false, reasonCode, Array.Empty<string>());
}

public sealed record AiDraftProposal(
    string Field,
    string? BeforeValue,
    string SuggestedValue,
    IReadOnlyList<string> AnchorIds);

public sealed record AiConversationOutputValidationResult(
    bool IsValid,
    string? ReasonCode,
    IReadOnlyList<string> ReferencedAnchorIds,
    AiDraftProposal? Proposal)
{
    public bool AutoApply => false;

    public static AiConversationOutputValidationResult Invalid(string reasonCode) =>
        new(false, reasonCode, Array.Empty<string>(), null);
}

public static class AiConversationOutputValidator
{
    public static AiConversationOutputValidationResult Validate(
        AiAssistPurpose purpose,
        AiProviderResponse? response,
        IReadOnlyCollection<string>? allowedAnchorIds,
        AiConversationDraftSnapshot currentDraft)
    {
        if (response is null
            || allowedAnchorIds is null
            || string.IsNullOrWhiteSpace(response.Text)
            || response.Text.Length > 2000
            || AiRedactor.ContainsCredential(response.Text))
            return AiConversationOutputValidationResult.Invalid("AI_INVALID_RESPONSE");

        var references = response.ReferencedAnchorIds;
        if (references is null || references.Count == 0)
            return AiConversationOutputValidationResult.Invalid("AI_OUTPUT_MISSING_ANCHORS");
        if (references.Count > 32
            || references.Any(string.IsNullOrWhiteSpace)
            || references.Distinct(StringComparer.Ordinal).Count() != references.Count)
            return AiConversationOutputValidationResult.Invalid("AI_OUTPUT_INVALID_ANCHORS");

        var allowed = allowedAnchorIds.ToHashSet(StringComparer.Ordinal);
        if (references.Any(anchor => !allowed.Contains(anchor)))
            return AiConversationOutputValidationResult.Invalid("AI_OUTPUT_UNKNOWN_ANCHOR");

        var isProposal = string.Equals(response.Kind, "draft_proposal", StringComparison.Ordinal);
        var kindMatches = (purpose, response.Kind) switch
        {
            (AiAssistPurpose.ExplainFeedback, "explanation") => true,
            (AiAssistPurpose.ReflectionQuestion, "reflection_question") => true,
            (AiAssistPurpose.LanguageAlternative, "language_alternative") => true,
            (AiAssistPurpose.LanguageAlternative, "draft_proposal") => true,
            _ => false,
        };
        if (!kindMatches)
            return AiConversationOutputValidationResult.Invalid("AI_OUTPUT_PURPOSE_MISMATCH");

        if (!isProposal && response.Proposal is not null)
            return AiConversationOutputValidationResult.Invalid("AI_PROPOSAL_KIND_MISMATCH");
        if (!isProposal)
            return new AiConversationOutputValidationResult(true, null, references.ToArray(), null);

        var proposal = response.Proposal;
        if (proposal is null)
            return AiConversationOutputValidationResult.Invalid("AI_PROPOSAL_REQUIRED");
        if (proposal.Field is not ("claim_text" or "claim_scope" or "learner_limitation" or "next_action"))
            return AiConversationOutputValidationResult.Invalid("AI_PROPOSAL_FIELD_NOT_ALLOWED");
        if (string.IsNullOrWhiteSpace(proposal.SuggestedValue)
            || proposal.SuggestedValue.Length > 2000
            || AiRedactor.ContainsCredential(proposal.SuggestedValue)
            || proposal.AnchorIds is null
            || proposal.AnchorIds.Count is < 1 or > 32
            || proposal.AnchorIds.Distinct(StringComparer.Ordinal).Count() != proposal.AnchorIds.Count)
            return AiConversationOutputValidationResult.Invalid("AI_PROPOSAL_INVALID");
        if (proposal.AnchorIds.Any(anchor => !allowed.Contains(anchor)))
            return AiConversationOutputValidationResult.Invalid("AI_OUTPUT_UNKNOWN_ANCHOR");
        if (proposal.AnchorIds.Any(anchor => !references.Contains(anchor, StringComparer.Ordinal)))
            return AiConversationOutputValidationResult.Invalid("AI_PROPOSAL_ANCHOR_MISMATCH");

        var currentValue = proposal.Field switch
        {
            "claim_text" => currentDraft.ClaimText,
            "claim_scope" => currentDraft.ClaimScope,
            "learner_limitation" => currentDraft.LearnerLimitation,
            "next_action" => currentDraft.NextAction,
            _ => null,
        };
        if (!string.Equals(proposal.BeforeValue, currentValue, StringComparison.Ordinal))
            return AiConversationOutputValidationResult.Invalid("AI_PROPOSAL_STALE_FIELD");
        if (currentValue is { Length: > 2000 } || AiRedactor.ContainsCredential(currentValue ?? string.Empty))
            return AiConversationOutputValidationResult.Invalid("AI_PROPOSAL_SENSITIVE_FIELD");
        if (string.Equals(proposal.BeforeValue, proposal.SuggestedValue, StringComparison.Ordinal))
            return AiConversationOutputValidationResult.Invalid("AI_PROPOSAL_NO_CHANGE");

        return new AiConversationOutputValidationResult(true, null, references.ToArray(), proposal);
    }
}

public static class AiOutputValidator
{
    private static readonly HashSet<string> AllowedKinds =
    [
        "explanation",
        "reflection_question",
        "language_alternative",
    ];

    public static AiOutputValidationResult Validate(
        AiProviderResponse? response,
        IReadOnlyCollection<string>? allowedAnchorIds)
        => ValidateCore(null, response, allowedAnchorIds);

    public static AiOutputValidationResult Validate(
        AiAssistPurpose purpose,
        AiProviderResponse? response,
        IReadOnlyCollection<string>? allowedAnchorIds)
        => ValidateCore(purpose, response, allowedAnchorIds);

    private static AiOutputValidationResult ValidateCore(
        AiAssistPurpose? purpose,
        AiProviderResponse? response,
        IReadOnlyCollection<string>? allowedAnchorIds)
    {
        if (response is null
            || !AllowedKinds.Contains(response.Kind)
            || string.IsNullOrWhiteSpace(response.Text)
            || response.Text.Length > 2000
            || AiRedactor.ContainsCredential(response.Text))
            return AiOutputValidationResult.Invalid("AI_INVALID_RESPONSE");

        if (purpose.HasValue && !MatchesPurpose(purpose.Value, response.Kind))
            return AiOutputValidationResult.Invalid("AI_OUTPUT_PURPOSE_MISMATCH");

        var references = response.ReferencedAnchorIds;
        if (references is null || references.Count == 0)
            return AiOutputValidationResult.Invalid("AI_OUTPUT_MISSING_ANCHORS");
        if (references.Count > 32
            || references.Any(reference => string.IsNullOrWhiteSpace(reference))
            || references.Distinct(StringComparer.Ordinal).Count() != references.Count)
            return AiOutputValidationResult.Invalid("AI_OUTPUT_INVALID_ANCHORS");

        var allowed = allowedAnchorIds is null
            ? new HashSet<string>(StringComparer.Ordinal)
            : allowedAnchorIds.ToHashSet(StringComparer.Ordinal);
        if (references.Any(reference => !allowed.Contains(reference)))
            return AiOutputValidationResult.Invalid("AI_OUTPUT_UNKNOWN_ANCHOR");

        return new AiOutputValidationResult(
            true,
            null,
            references.ToArray());
    }

    private static bool MatchesPurpose(AiAssistPurpose purpose, string kind) =>
        (purpose, kind) switch
        {
            (AiAssistPurpose.ExplainFeedback, "explanation") => true,
            (AiAssistPurpose.ReflectionQuestion, "reflection_question") => true,
            (AiAssistPurpose.LanguageAlternative, "language_alternative") => true,
            _ => false,
        };
}
