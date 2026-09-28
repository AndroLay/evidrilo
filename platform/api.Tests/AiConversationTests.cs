using System.Text.Json;
using System.Text.Json.Serialization;
using Evidrilo.Api.Ai;
using Evidrilo.Api.Content;

namespace Evidrilo.Api.Tests;

public sealed class AiConversationTests
{
    [Fact]
    public void Context_fingerprint_changes_when_any_learning_context_changes()
    {
        var publishedCase = PublishedCase();
        var context = ValidContext();
        var baseline = AiConversationFingerprint.Create(publishedCase, context, "The trial was measured once.");

        Assert.Equal(baseline, AiConversationFingerprint.Create(publishedCase, context, "The trial was measured once."));
        Assert.NotEqual(baseline, AiConversationFingerprint.Create(
            publishedCase,
            context with { ClaimText = "A narrower claim." },
            "The trial was measured once."));
        Assert.NotEqual(baseline, AiConversationFingerprint.Create(
            publishedCase,
            context with { AnchorIds = ["OBS-COLD-01"] },
            "The trial was measured once."));
        Assert.NotEqual(baseline, AiConversationFingerprint.Create(
            publishedCase with { ContentHash = new string('b', 64) },
            context,
            "The trial was measured once."));
        Assert.NotEqual(baseline, AiConversationFingerprint.Create(
            publishedCase,
            context,
            "The temperature was not controlled."));
    }

    [Fact]
    public void Conversation_transcript_is_bounded_and_explicitly_untrusted()
    {
        var history = new[]
        {
            new AiConversationMessage("user", "Why is this claim too broad?", []),
            new AiConversationMessage("assistant", "The selected observation is limited.", ["OBS-WARM-01"]),
        };

        var result = AiConversationPrompt.TryBuild(
            "Canonical evidence context.",
            history,
            "Can I narrow it to this trial?",
            ["OBS-WARM-01"]);

        Assert.True(result.IsValid);
        Assert.Contains("UNTRUSTED DIALOGUE", result.Prompt, StringComparison.Ordinal);
        Assert.Contains("[student] Why is this claim too broad?", result.Prompt, StringComparison.Ordinal);
        Assert.Contains("[assistant suggestion; not evidence] The selected observation is limited.", result.Prompt, StringComparison.Ordinal);

        var tooManyMessages = Enumerable.Range(0, AiConversationPrompt.MaxHistoryMessages + 1)
            .Select(_ => new AiConversationMessage("user", "Question", []))
            .ToArray();
        var rejected = AiConversationPrompt.TryBuild("Context.", tooManyMessages, "Question", ["OBS-WARM-01"]);
        Assert.False(rejected.IsValid);
        Assert.Equal("AI_CONVERSATION_HISTORY_LIMIT", rejected.ReasonCode);
    }

    [Fact]
    public void Conversation_prompt_rejects_unapproved_prior_assistant_anchors()
    {
        var result = AiConversationPrompt.TryBuild(
            "Canonical evidence context.",
            [new AiConversationMessage("assistant", "This is a fact.", ["OBS-FABRICATED-01"])],
            "Explain.",
            ["OBS-WARM-01"]);

        Assert.False(result.IsValid);
        Assert.Equal("AI_OUTPUT_UNKNOWN_ANCHOR", result.ReasonCode);
    }

    [Fact]
    public void Proposal_is_a_typed_preview_bound_to_the_current_learner_field()
    {
        var response = new AiProviderResponse(
            "draft_proposal",
            "Narrow the statement to the observed comparison.",
            ["OBS-WARM-01"])
        {
            Proposal = new AiDraftProposal(
                "claim_scope",
                "GENERAL_CAUSAL",
                "OBSERVED_COMPARISON_ONLY",
                ["OBS-WARM-01"]),
        };

        var valid = AiConversationOutputValidator.Validate(
            AiAssistPurpose.LanguageAlternative,
            response,
            ["OBS-WARM-01", "LIMIT-TRIAL-01"],
            new AiConversationDraftSnapshot(
                "Warm water causes faster dissolution.",
                "GENERAL_CAUSAL",
                "Each condition was measured once.",
                "Repeat the measurement."));

        Assert.True(valid.IsValid);
        Assert.Equal("claim_scope", valid.Proposal?.Field);
        Assert.Equal("GENERAL_CAUSAL", valid.Proposal?.BeforeValue);
        Assert.Equal("OBSERVED_COMPARISON_ONLY", valid.Proposal?.SuggestedValue);
        Assert.False(valid.AutoApply);
    }

    [Fact]
    public void Empty_proposal_before_value_is_serialized_as_explicit_null()
    {
        var options = new JsonSerializerOptions(JsonSerializerDefaults.Web)
        {
            DefaultIgnoreCondition = JsonIgnoreCondition.WhenWritingNull,
        };
        var json = JsonSerializer.Serialize(
            new AiConversationProposalResponse("claim_text", null, "A bounded first claim.", ["OBS-WARM-01"]),
            options);

        Assert.Contains("\"beforeValue\":null", json, StringComparison.Ordinal);
    }

    [Fact]
    public void Proposal_rejects_stale_before_value_unknown_field_and_fabricated_anchor()
    {
        var current = new AiConversationDraftSnapshot("A claim", "NARROW", null, null);
        var stale = Proposal("claim_text", "Older claim", "New claim", "OBS-WARM-01");
        var unknownField = Proposal("evaluator_status", "ACTION_REQUIRED", "PASS", "OBS-WARM-01");
        var fabricatedAnchor = Proposal("claim_text", "A claim", "A narrower claim", "OBS-FABRICATED-01");

        Assert.Equal("AI_PROPOSAL_STALE_FIELD", Validate(stale, current).ReasonCode);
        Assert.Equal("AI_PROPOSAL_FIELD_NOT_ALLOWED", Validate(unknownField, current).ReasonCode);
        Assert.Equal("AI_OUTPUT_UNKNOWN_ANCHOR", Validate(fabricatedAnchor, current).ReasonCode);
    }

    [Fact]
    public void Proposal_allowlist_covers_only_the_four_learner_authored_fields()
    {
        var current = new AiConversationDraftSnapshot("Claim", "Narrow", "One trial", "Repeat the test.");
        var allowed = new[]
        {
            ("claim_text", current.ClaimText),
            ("claim_scope", current.ClaimScope),
            ("learner_limitation", current.LearnerLimitation),
            ("next_action", current.NextAction),
        };

        foreach (var (field, before) in allowed)
        {
            var result = Validate(
                Proposal(field, before, $"{before} (reworded)", "OBS-WARM-01"),
                current);
            Assert.True(result.IsValid, $"The learner-authored field '{field}' should allow a validated preview.");
        }

        var forbidden = Validate(
            Proposal("evaluator_status", "ACTION_REQUIRED", "PASS", "OBS-WARM-01"),
            current);
        Assert.Equal("AI_PROPOSAL_FIELD_NOT_ALLOWED", forbidden.ReasonCode);
    }

    private static AiConversationOutputValidationResult Validate(
        AiDraftProposal proposal,
        AiConversationDraftSnapshot current) => AiConversationOutputValidator.Validate(
            AiAssistPurpose.LanguageAlternative,
            new AiProviderResponse("draft_proposal", "Consider this bounded wording.", proposal.AnchorIds)
            {
                Proposal = proposal,
            },
            ["OBS-WARM-01", "LIMIT-TRIAL-01"],
            current);

    private static AiDraftProposal Proposal(string field, string? before, string after, string anchor) => new(
        field,
        before,
        after,
        [anchor]);

    private static AiAssistContextRequest ValidContext() => new(
        "M0_T2:1",
        "MISSING_EVIDENCE",
        "ACTION_REQUIRED",
        ["OBS-WARM-01"],
        ["LIMIT-TRIAL-01"],
        "Warm water dissolved the tablet faster.",
        "GENERAL_CAUSAL",
        "Repeat the measurement.");

    private static PublishedCaseSummary PublishedCase() => new(
        "evidrilo-m0-t2-v1",
        "M0_T2:1",
        "Tablet dissolution",
        new string('a', 64),
        "evaluator.v1",
        ["evidence", "claim-boundary"],
        new PublishedCaseContent(
            "Compare supplied dissolution observations.",
            2,
            ["OBS-WARM-01", "OBS-COLD-01"],
            [
                new AuthoringFact("OBS-WARM-01", "observation", "Warm water: 32 seconds."),
                new AuthoringFact("OBS-COLD-01", "observation", "Cold water: 92 seconds."),
                new AuthoringFact("LIMIT-TRIAL-01", "limitation", "Each condition was measured once."),
            ],
            [new AuthoringRule("RULE-01", "ACTION_REQUIRED", ["OBS-WARM-01"])],
            [new AuthoringVariant("VARIANT-01", ["OBS-COLD-01"])]));
}
