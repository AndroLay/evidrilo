using System.Text.RegularExpressions;
using Evidrilo.Api.Auth;
using Evidrilo.Api.Common;

namespace Evidrilo.Api.Content;

public static class EvidenceGraphEndpoints
{
    private static readonly Regex CaseVersionIdPattern =
        new("\\A[A-Za-z0-9._:-]{1,128}\\z", RegexOptions.CultureInvariant);

    public static IEndpointRouteBuilder MapEvidenceGraphEndpoints(this IEndpointRouteBuilder endpoints)
    {
        endpoints.MapGet(
            "/v1/cases/{caseVersionId}/evidence-graph",
            async (
                HttpContext context,
                string caseVersionId,
                ICaseStore store,
                CancellationToken cancellationToken) =>
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

                var publishedCase = await store.GetPublishedAsync(accountId, caseVersionId, cancellationToken);
                if (publishedCase is null)
                {
                    return Results.Json(
                        ApiErrors.Create(context, "CASE_NOT_FOUND", "The published case was not found."),
                        statusCode: StatusCodes.Status404NotFound);
                }

                var graph = EvidenceGraphProjection.Build(publishedCase);
                return Results.Ok(new EvidenceGraphResponse(
                    graph.Schema,
                    graph.Version,
                    graph.CaseVersionId,
                    graph.ContentHash,
                    graph.Nodes,
                    graph.Edges,
                    RequestIdMiddleware.Get(context)));
            })
            .RequireAuthorization()
            .RequireRateLimiting("api");

        return endpoints;
    }
}
