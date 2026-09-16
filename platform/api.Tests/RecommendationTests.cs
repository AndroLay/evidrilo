using Evidrilo.Api.Recommendations;

namespace Evidrilo.Api.Tests;

public sealed class RecommendationTests
{
    [Fact]
    public void Recommendation_is_deterministic_and_explainable()
    {
        var input = new RecommendationInput(
            "progress.v1",
            4,
            3,
            1,
            0,
            [
                new("M0_T2:2", "Revise evidence link", "evidence-linking", 2, true, ["attempt:1"]),
                new("M0_T2:1", "Check scope", "scope", 1, true, ["attempt:2"]),
                new("M0_T2:0", "Unpublished", "scope", 0, false, ["attempt:3"]),
            ]);

        var result = RecommendationEngine.Decide(input);

        Assert.Equal("recommended", result.Status);
        Assert.Equal("M0_T2:1", result.CaseVersionId);
        Assert.Equal("PRACTICE_ACTION_REQUIRED", result.ReasonCode);
        Assert.NotEmpty(result.EvidenceReferences);
    }

    [Fact]
    public void Recommendation_abstains_without_projection_or_eligible_case()
    {
        var noProjection = RecommendationEngine.Decide(new RecommendationInput(
            "unknown", 0, 0, 0, 0, []));
        Assert.Equal("abstain", noProjection.Status);
        Assert.Equal("INSUFFICIENT_PROJECTION", noProjection.ReasonCode);

        var noCase = RecommendationEngine.Decide(new RecommendationInput(
            "progress.v1", 1, 0, 1, 0,
            [new("M0_T2:1", "", "scope", 1, true, ["attempt:1"])]));
        Assert.Equal("NO_ELIGIBLE_CASE", noCase.ReasonCode);
    }

    [Fact]
    public void Recommendation_abstains_for_an_invalid_projection_shape()
    {
        var invalid = new RecommendationInput(
            "progress.v1",
            2,
            1,
            -1,
            0,
            [new("M0_T2:1", "Check scope", "scope", 1, true, ["attempt:1"])]);

        var result = RecommendationEngine.Decide(invalid);

        Assert.Equal("abstain", result.Status);
        Assert.Equal("INSUFFICIENT_PROJECTION", result.ReasonCode);
        Assert.Null(result.CaseVersionId);
    }

    [Fact]
    public void Recommendation_abstains_when_no_candidate_has_an_explainable_skill()
    {
        var input = new RecommendationInput(
            "progress.v1",
            0,
            0,
            0,
            0,
            [new("M0_T2:1", "Check scope", "", 1, true, ["attempt:1"])]);

        var result = RecommendationEngine.Decide(input);

        Assert.Equal("abstain", result.Status);
        Assert.Equal("NO_ELIGIBLE_CASE", result.ReasonCode);
        Assert.Null(result.CaseVersionId);
    }

    [Fact]
    public void Recommendation_uses_stable_tie_breaks()
    {
        var input = new RecommendationInput(
            "progress.v1", 0, 0, 0, 0,
            [
                new("M0_T2:2", "Second", "scope", 1, true, ["attempt:2"]),
                new("M0_T2:1", "First", "scope", 1, true, ["attempt:1"]),
            ]);

        Assert.Equal("M0_T2:1", RecommendationEngine.Decide(input).CaseVersionId);
    }
}
