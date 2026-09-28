using System.Text.Json;
using System.Text.Json.Serialization;
using Evidrilo.Api.Auth;
using Evidrilo.Api.Common;

namespace Evidrilo.Api.ProjectAi;

public static class ProjectAiConsentEndpoints
{
    private static readonly JsonSerializerOptions RequestJsonOptions = new(JsonSerializerDefaults.Web)
    {
        UnmappedMemberHandling = JsonUnmappedMemberHandling.Disallow,
    };
    private static readonly JsonSerializerOptions ResponseJsonOptions = new(JsonSerializerDefaults.Web)
    {
        DefaultIgnoreCondition = JsonIgnoreCondition.Never,
    };

    public static IEndpointRouteBuilder MapProjectAiConsentEndpoints(this IEndpointRouteBuilder endpoints)
    {
        endpoints.MapGet(
                "/v1/project-ai/consent",
                async (HttpContext context, IProjectAiConsentStore store, CancellationToken cancellationToken) =>
                {
                    if (!TryGetVerifiedAccount(context, out var accountId)) return AuthRequired(context);
                    return Results.Json(
                        await store.ReadOwnAsync(accountId, cancellationToken),
                        options: ResponseJsonOptions);
                })
            .RequireAuthorization()
            .RequireRateLimiting("ai");

        endpoints.MapPut(
                "/v1/project-ai/consent",
                async (HttpContext context, IProjectAiConsentStore store, CancellationToken cancellationToken) =>
                {
                    if (!TryGetVerifiedAccount(context, out var accountId)) return AuthRequired(context);
                    var payload = await RequestJsonReader.ReadAsync(
                        context.Request,
                        "INVALID_PROJECT_AI_CONSENT",
                        "The Project AI consent request is invalid.",
                        cancellationToken);
                    ProjectAiConsentGrantRequest? request;
                    try
                    {
                        request = payload.Deserialize<ProjectAiConsentGrantRequest>(RequestJsonOptions);
                    }
                    catch (JsonException)
                    {
                        return Invalid(context, "INVALID_PROJECT_AI_CONSENT");
                    }

                    var error = ProjectAiConsentPolicy.ValidateGrant(request);
                    if (error == "PROJECT_AI_CONSENT_POLICY_STALE")
                    {
                        return Results.Json(
                            ApiErrors.Create(context, error, "Review the current Project AI data-use policy before enabling assistance."),
                            statusCode: StatusCodes.Status409Conflict);
                    }
                    if (error is not null) return Invalid(context, error);

                    var state = await store.GrantOwnAsync(
                        accountId,
                        ProjectAiConsentPolicy.CurrentPolicyVersion,
                        cancellationToken);
                    return Results.Json(state, options: ResponseJsonOptions);
                })
            .RequireAuthorization()
            .RequireRateLimiting("ai");

        endpoints.MapDelete(
                "/v1/project-ai/consent",
                async (HttpContext context, IProjectAiConsentStore store, CancellationToken cancellationToken) =>
                {
                    if (!TryGetVerifiedAccount(context, out var accountId)) return AuthRequired(context);
                    return Results.Json(
                        await store.RevokeOwnAsync(accountId, cancellationToken),
                        options: ResponseJsonOptions);
                })
            .RequireAuthorization()
            .RequireRateLimiting("ai");

        return endpoints;
    }

    private static bool TryGetVerifiedAccount(HttpContext context, out Guid accountId)
    {
        if (AuthenticatedUser.TryGetVerifiedAccountId(context.User, out accountId)) return true;
        accountId = Guid.Empty;
        return false;
    }

    private static IResult AuthRequired(HttpContext context) =>
        !AuthenticatedUser.TryGetAccountId(context.User, out _)
            ? Results.Json(
                ApiErrors.Create(context, "AUTH_REQUIRED", "Authentication is required."),
                statusCode: StatusCodes.Status401Unauthorized)
            : Results.Json(
                ApiErrors.Create(context, "FORBIDDEN", "A verified account is required for Project AI consent."),
                statusCode: StatusCodes.Status403Forbidden);

    private static IResult Invalid(HttpContext context, string code) => Results.Json(
        ApiErrors.Create(context, code, "The Project AI consent request is invalid."),
        statusCode: StatusCodes.Status400BadRequest);
}
