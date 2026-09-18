using System.Text.RegularExpressions;
using Evidrilo.Api.Auth;
using Evidrilo.Api.Common;

namespace Evidrilo.Api.Content;

public static class CaseEndpoints
{
    private static readonly Regex CaseVersionIdPattern =
        new("\\A[A-Za-z0-9._:-]{1,128}\\z", RegexOptions.CultureInvariant);

    public static IEndpointRouteBuilder MapCaseEndpoints(this IEndpointRouteBuilder endpoints)
    {
        endpoints.MapGet(
            "/v1/cases",
            async (HttpContext context, ICaseStore store, CancellationToken cancellationToken) =>
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
                        ApiErrors.Create(context, "FORBIDDEN", "You are not allowed to access published cases."),
                        statusCode: StatusCodes.Status403Forbidden);
                }

                var cases = await store.ListPublishedAsync(accountId, cancellationToken);
                return Results.Ok(new
                {
                    schema = "evidrilo.case-catalogue",
                    version = "1",
                    cases = cases.Select(result => new
                    {
                        caseId = result.CaseId,
                        caseVersionId = result.CaseVersionId,
                        title = result.Title,
                        contentHash = result.ContentHash,
                        evaluatorVersion = result.EvaluatorVersion,
                        skillTags = result.SkillTags,
                        objective = result.Objective,
                        difficulty = result.Difficulty,
                    }),
                    requestId = RequestIdMiddleware.Get(context),
                });
            })
            .RequireAuthorization()
            .RequireRateLimiting("api");

        endpoints.MapGet(
            "/v1/cases/{caseVersionId}",
            async (HttpContext context, string caseVersionId, ICaseStore store, CancellationToken cancellationToken) =>
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
                        ApiErrors.Create(context, "FORBIDDEN", "You are not allowed to access this case."),
                        statusCode: StatusCodes.Status403Forbidden);
                }

                if (!CaseVersionIdPattern.IsMatch(caseVersionId))
                {
                    return Results.Json(
                        ApiErrors.Create(context, "INVALID_CASE_VERSION", "The case version is invalid."),
                        statusCode: StatusCodes.Status400BadRequest);
                }

                var result = await store.GetPublishedAsync(accountId, caseVersionId, cancellationToken);
                return result is null
                    ? Results.Json(
                        ApiErrors.Create(context, "CASE_NOT_FOUND", "The published case was not found."),
                        statusCode: StatusCodes.Status404NotFound)
                    : Results.Ok(new
                    {
                        schema = "evidrilo.case-summary",
                        version = "1",
                        caseId = result.CaseId,
                        caseVersionId = result.CaseVersionId,
                        title = result.Title,
                        contentHash = result.ContentHash,
                        evaluatorVersion = result.EvaluatorVersion,
                        skillTags = result.SkillTags,
                        objective = result.Content.Objective,
                        difficulty = result.Content.Difficulty,
                        evidenceReferences = result.Content.EvidenceReferences,
                        facts = result.Content.Facts,
                        rules = result.Content.Rules,
                        variants = result.Content.Variants,
                        requestId = RequestIdMiddleware.Get(context),
                    });
            })
            .RequireAuthorization()
            .RequireRateLimiting("api");

        return endpoints;
    }
}
