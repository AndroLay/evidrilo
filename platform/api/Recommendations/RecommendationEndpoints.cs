using Evidrilo.Api.Auth;
using Evidrilo.Api.Common;

namespace Evidrilo.Api.Recommendations;

public static class RecommendationEndpoints
{
    public static IEndpointRouteBuilder MapRecommendationEndpoints(this IEndpointRouteBuilder endpoints)
    {
        endpoints.MapGet(
            "/v1/recommendations/next",
            async (HttpContext context, IRecommendationStore store, CancellationToken cancellationToken) =>
            {
                if (!AuthenticatedUser.TryGetAccountId(context.User, out var accountId))
                {
                    return Results.Json(
                        ApiErrors.Create(context, "AUTH_REQUIRED", "Authentication is required."),
                        statusCode: StatusCodes.Status401Unauthorized);
                }

                if (!AuthenticatedUser.IsEmailVerified(context.User))
                {
                    return Results.Json(
                        ApiErrors.Create(context, "FORBIDDEN", "You are not allowed to access recommendations."),
                        statusCode: StatusCodes.Status403Forbidden);
                }

                var input = await store.GetInputAsync(accountId, cancellationToken);
                var decision = RecommendationEngine.Decide(input);
                return Results.Ok(new RecommendationResult(
                    "evidrilo.recommendation",
                    "1",
                    decision.Status,
                    RecommendationEngine.CalculationVersion,
                    decision.CaseVersionId,
                    decision.Objective,
                    decision.ReasonCode,
                    decision.EvidenceReferences,
                    RequestIdMiddleware.Get(context)));
            })
            .RequireAuthorization()
            .RequireRateLimiting("api");

        return endpoints;
    }
}
