using Evidrilo.Api.Auth;
using Evidrilo.Api.Common;

namespace Evidrilo.Api.Authorization;

public static class CohortEndpoints
{
    public static IEndpointRouteBuilder MapCohortEndpoints(this IEndpointRouteBuilder endpoints)
    {
        endpoints.MapGet(
            "/v1/teacher/cohorts/{cohortId:guid}/summary",
            async (HttpContext context, Guid cohortId, ICohortStore store, CancellationToken cancellationToken) =>
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
                        ApiErrors.Create(context, "FORBIDDEN", "You are not allowed to access cohort summaries."),
                        statusCode: StatusCodes.Status403Forbidden);
                }

                var lookup = await store.GetSummaryAsync(accountId, cohortId, cancellationToken);
                var summary = lookup.Summary;
                if (summary is null)
                {
                    var code = lookup.DenialCode ?? "COHORT_NOT_FOUND";
                    var status = code == "COHORT_NOT_FOUND"
                        ? StatusCodes.Status404NotFound
                        : StatusCodes.Status403Forbidden;
                    return Results.Json(
                        ApiErrors.Create(context, code, "You are not allowed to access this cohort summary."),
                        statusCode: status);
                }

                return Results.Ok(new CohortSummaryResponse(
                    "evidrilo.cohort-summary",
                    "1",
                    summary.CohortId,
                    summary.LearnerCount,
                    false,
                    summary.CompletionRate,
                    summary.PassRate,
                    RequestIdMiddleware.Get(context)));
            })
            .RequireAuthorization()
            .RequireRateLimiting("api");

        return endpoints;
    }
}
