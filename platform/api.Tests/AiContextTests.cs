using Evidrilo.Api.Ai;
using Evidrilo.Api.Content;

namespace Evidrilo.Api.Tests;

public sealed class AiContextTests
{
    [Fact]
    public void Context_builder_uses_only_facts_referenced_by_the_active_case()
    {
        var result = AiContextBuilder.TryBuild(
            PublishedCase(),
            new AiAssistContextRequest(
                "M0_T2:1",
                "MISSING_EVIDENCE",
                "ACTION_REQUIRED",
                ["OBS-WARM-01"],
                ["LIMIT-TRIAL-01"],
                "Warm water dissolved the tablet faster.",
                "LIMITED_COMPARISON",
                "State that the comparison is limited."),
            "Connect the claim to the supplied observation.");

        Assert.True(result.IsValid);
        Assert.NotNull(result.Context);
        Assert.Contains("[OBS-WARM-01] Warm water: 32 seconds.", result.Context!.Prompt);
        Assert.Contains("[LIMIT-TRIAL-01] Each condition was measured once.", result.Context.Prompt);
        Assert.DoesNotContain("OBS-COLD-01", result.Context.Prompt, StringComparison.Ordinal);
        Assert.DoesNotContain("unrequested private note", result.Context.Prompt, StringComparison.Ordinal);
    }

    [Fact]
    public void Context_builder_rejects_an_unknown_anchor_before_provider_use()
    {
        var result = AiContextBuilder.TryBuild(
            PublishedCase(),
            new AiAssistContextRequest(
                "M0_T2:1",
                "MISSING_EVIDENCE",
                "ACTION_REQUIRED",
                ["OBS-NOT-IN-CASE"],
                [],
                null,
                null,
                null),
            "Explain the missing evidence.");

        Assert.False(result.IsValid);
        Assert.Equal("INVALID_AI_CONTEXT", result.ReasonCode);
        Assert.Null(result.Context);
    }

    [Fact]
    public void Context_builder_rejects_a_limitation_selected_as_evidence()
    {
        var result = AiContextBuilder.TryBuild(
            PublishedCase(),
            new AiAssistContextRequest(
                "M0_T2:1",
                "MISSING_EVIDENCE",
                "ACTION_REQUIRED",
                ["LIMIT-TRIAL-01"],
                [],
                null,
                null,
                null),
            "Explain the evidence relationship.");

        Assert.False(result.IsValid);
        Assert.Equal("INVALID_AI_CONTEXT", result.ReasonCode);
    }

    [Fact]
    public void Context_builder_rejects_an_observation_selected_as_a_limitation()
    {
        var result = AiContextBuilder.TryBuild(
            PublishedCase(),
            new AiAssistContextRequest(
                "M0_T2:1",
                "MISSING_EVIDENCE",
                "ACTION_REQUIRED",
                ["OBS-WARM-01"],
                ["OBS-WARM-01"],
                null,
                null,
                null),
            "Explain the evidence relationship.");

        Assert.False(result.IsValid);
        Assert.Equal("INVALID_AI_CONTEXT", result.ReasonCode);
    }

    [Fact]
    public void Context_builder_rejects_a_case_identity_mismatch()
    {
        var result = AiContextBuilder.TryBuild(
            PublishedCase(),
            new AiAssistContextRequest(
                "OTHER:1",
                "MISSING_EVIDENCE",
                "ACTION_REQUIRED",
                ["OBS-WARM-01"],
                [],
                null,
                null,
                null),
            "Explain the missing evidence.");

        Assert.False(result.IsValid);
        Assert.Equal("AI_CONTEXT_CASE_MISMATCH", result.ReasonCode);
    }

    [Fact]
    public void Context_builder_rejects_a_prompt_that_exceeds_the_canonical_limit()
    {
        var facts = Enumerable.Range(1, 9)
            .Select(index => new AuthoringFact(
                $"OBS-LARGE-{index}",
                "observation",
                new string('x', 2000)))
            .ToArray();
        var result = AiContextBuilder.TryBuild(
            PublishedCase(facts),
            new AiAssistContextRequest(
                "M0_T2:1",
                "MISSING_EVIDENCE",
                "ACTION_REQUIRED",
                facts.Select(fact => fact.Id).ToArray(),
                [],
                null,
                null,
                null),
            "Keep the response grounded.");

        Assert.False(result.IsValid);
        Assert.Equal("AI_CONTEXT_TOO_LARGE", result.ReasonCode);
        Assert.Null(result.Context);
    }

    private static PublishedCaseSummary PublishedCase(IReadOnlyList<AuthoringFact>? facts = null)
    {
        facts ??=
        [
            new AuthoringFact("OBS-WARM-01", "observation", "Warm water: 32 seconds."),
            new AuthoringFact("OBS-COLD-01", "observation", "Cold water: 92 seconds."),
            new AuthoringFact("LIMIT-TRIAL-01", "limitation", "Each condition was measured once."),
        ];

        return new(
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
            facts,
            [new AuthoringRule("RULE-01", "ACTION_REQUIRED", ["OBS-WARM-01"])],
            [new AuthoringVariant("VARIANT-01", ["OBS-COLD-01"]) ]));
    }
}
