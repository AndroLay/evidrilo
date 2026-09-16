using Evidrilo.Api.Recommendations;

namespace Evidrilo.Api.Tests;

public sealed class RecommendationInteractionTests
{
    [Fact]
    public void Interaction_validator_requires_consent_and_versioned_calculation()
    {
        var valid = new RecommendationInteractionRequest(
            "evidrilo.recommendation-interaction",
            "1",
            Guid.NewGuid(),
            "M0_T2:1",
            RecommendationInteraction.Accepted,
            "recommendation.v1",
            "PRACTICE_ACTION_REQUIRED",
            "granted");

        Assert.Null(RecommendationInteractionValidator.Validate(valid));
        Assert.Equal(
            "RECOMMENDATION_CONSENT_REQUIRED",
            RecommendationInteractionValidator.Validate(valid with { Consent = "denied" }));
        Assert.Equal(
            "INVALID_RECOMMENDATION_INTERACTION",
            RecommendationInteractionValidator.Validate(valid with { CalculationVersion = "recommendation.unknown" }));
        Assert.Equal(
            "INVALID_RECOMMENDATION_INTERACTION",
            RecommendationInteractionValidator.Validate(valid with
            {
                CaseVersionId = null,
                Interaction = RecommendationInteraction.Accepted,
            }));
        Assert.Equal(
            "INVALID_RECOMMENDATION_INTERACTION",
            RecommendationInteractionValidator.Validate(valid with { ReasonCode = "CLIENT_GUESS" }));
        Assert.Equal(
            "INVALID_RECOMMENDATION_INTERACTION",
            RecommendationInteractionValidator.Validate(valid with { CaseVersionId = "M0_T2:1\n" }));
    }
}
