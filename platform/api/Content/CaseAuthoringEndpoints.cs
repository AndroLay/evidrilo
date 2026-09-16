using System.Text.Json;
using System.Text.Json.Serialization;
using System.Text.RegularExpressions;
using Evidrilo.Api.Auth;
using Evidrilo.Api.Common;

namespace Evidrilo.Api.Content;

public static class CaseAuthoringEndpoints
{
    private static readonly Regex CaseVersionIdPattern =
        new("\\A[A-Za-z0-9._:-]{1,128}\\z", RegexOptions.CultureInvariant);
    private static readonly JsonSerializerOptions RequestJsonOptions = new(JsonSerializerDefaults.Web)
    {
        PropertyNamingPolicy = JsonNamingPolicy.CamelCase,
        UnmappedMemberHandling = JsonUnmappedMemberHandling.Disallow,
    };

    static CaseAuthoringEndpoints()
    {
        RequestJsonOptions.Converters.Add(new JsonStringEnumConverter(JsonNamingPolicy.SnakeCaseLower));
    }

    public static IEndpointRouteBuilder MapCaseAuthoringEndpoints(this IEndpointRouteBuilder endpoints)
    {
        endpoints.MapPost(
            "/v1/authoring/cases",
            async (HttpContext context, ICaseAuthoringStore store, CancellationToken cancellationToken) =>
            {
                if (!TryGetVerifiedAccount(context, out var accountId, out var failure)) return failure!;
                var payload = await RequestJsonReader.ReadAsync(
                    context.Request,
                    "INVALID_CASE_AUTHORING_DOCUMENT",
                    "The case authoring document is invalid.",
                    cancellationToken);
                CaseAuthoringRequest? request;
                try
                {
                    request = payload.Deserialize<CaseAuthoringRequest>(RequestJsonOptions);
                }
                catch (JsonException)
                {
                    return Results.Json(
                        ApiErrors.Create(context, "INVALID_CASE_AUTHORING_DOCUMENT", "The case authoring document is invalid."),
                        statusCode: StatusCodes.Status400BadRequest);
                }

                if (request is null
                    || request.Schema != "evidrilo.case-authoring"
                    || request.Version != "1"
                    || request.OrganizationId == Guid.Empty
                    || !CaseAuthoringValidator.Validate(request.Document).IsValid)
                    return Results.Json(
                        ApiErrors.Create(context, "INVALID_CASE_AUTHORING_DOCUMENT", "The case authoring document is invalid."),
                        statusCode: StatusCodes.Status400BadRequest);

                var result = await store.CreateDraftAsync(accountId, request, cancellationToken);
                return Results.Ok(new CaseAuthoringResponse(
                    "evidrilo.case-authoring-result",
                    "1",
                    result.Outcome,
                    result.CaseVersionId,
                    result.State,
                    RequestIdMiddleware.Get(context)));
            })
            .RequireAuthorization()
            .RequireRateLimiting("api");

        endpoints.MapPost(
            "/v1/authoring/cases/{caseVersionId}/transition",
            async (
                HttpContext context,
                string caseVersionId,
                ICaseAuthoringStore store,
                CancellationToken cancellationToken) =>
            {
                if (!TryGetVerifiedAccount(context, out var accountId, out var failure)) return failure!;
                if (!CaseVersionIdPattern.IsMatch(caseVersionId))
                    return Results.Json(
                        ApiErrors.Create(context, "INVALID_CASE_VERSION", "The case version is invalid."),
                        statusCode: StatusCodes.Status400BadRequest);

                var payload = await RequestJsonReader.ReadAsync(
                    context.Request,
                    "INVALID_CASE_TRANSITION",
                    "The case transition is invalid.",
                    cancellationToken);
                CaseTransitionRequest? request;
                try
                {
                    request = payload.Deserialize<CaseTransitionRequest>(RequestJsonOptions);
                }
                catch (JsonException)
                {
                    return Results.Json(
                        ApiErrors.Create(context, "INVALID_CASE_TRANSITION", "The case transition is invalid."),
                        statusCode: StatusCodes.Status400BadRequest);
                }

                if (request is null
                    || request.Schema != "evidrilo.case-transition"
                    || request.Version != "1"
                    || !Enum.IsDefined(request.TargetState))
                    return Results.Json(
                        ApiErrors.Create(context, "INVALID_CASE_TRANSITION", "The case transition is invalid."),
                        statusCode: StatusCodes.Status400BadRequest);

                var result = await store.TransitionAsync(accountId, caseVersionId, request, cancellationToken);
                return Results.Ok(new CaseAuthoringResponse(
                    "evidrilo.case-authoring-result",
                    "1",
                    result.Outcome,
                    result.CaseVersionId,
                    result.State,
                    RequestIdMiddleware.Get(context)));
            })
            .RequireAuthorization()
            .RequireRateLimiting("api");

        endpoints.MapGet(
            "/v1/authoring/cases/{caseVersionId}/audit",
            async (
                HttpContext context,
                string caseVersionId,
                ICaseLifecycleAuditStore store,
                CancellationToken cancellationToken) =>
            {
                if (!TryGetVerifiedAccount(context, out var accountId, out var failure)) return failure!;
                if (!CaseVersionIdPattern.IsMatch(caseVersionId))
                    return Results.Json(
                        ApiErrors.Create(context, "INVALID_CASE_VERSION", "The case version is invalid."),
                        statusCode: StatusCodes.Status400BadRequest);

                var result = await store.GetAsync(accountId, caseVersionId, cancellationToken);
                if (result is null)
                    return Results.Json(
                        ApiErrors.Create(context, "CASE_NOT_FOUND", "The case version was not found."),
                        statusCode: StatusCodes.Status404NotFound);

                var truncated = result.Truncated
                    || result.Events.Count > CaseLifecycleAuditLimits.MaxEvents;
                var events = result.Events
                    .Take(CaseLifecycleAuditLimits.MaxEvents)
                    .Select(auditEvent => new CaseLifecycleAuditEventResponse(
                        auditEvent.AuditEventId,
                        auditEvent.ActorAccountId,
                        auditEvent.EventType,
                        auditEvent.FromState?.ToWire(),
                        auditEvent.ToState.ToWire(),
                        auditEvent.Reason,
                        auditEvent.CreatedAt))
                    .ToArray();
                return Results.Ok(new CaseLifecycleAuditResponse(
                    "evidrilo.case-lifecycle-audit",
                    "1",
                    result.CaseVersionId,
                    events,
                    truncated,
                    RequestIdMiddleware.Get(context)));
            })
            .RequireAuthorization()
            .RequireRateLimiting("api");

        return endpoints;
    }

    private static bool TryGetVerifiedAccount(
        HttpContext context,
        out Guid accountId,
        out IResult? failure)
    {
        failure = null;
        if (!AuthenticatedUser.TryGetAccountId(context.User, out accountId))
        {
            failure = Results.Json(
                ApiErrors.Create(context, "AUTH_REQUIRED", "Authentication is required."),
                statusCode: StatusCodes.Status401Unauthorized);
            return false;
        }

        if (!AuthenticatedUser.IsEmailVerified(context.User))
        {
            failure = Results.Json(
                ApiErrors.Create(context, "FORBIDDEN", "You are not allowed to perform this case operation."),
                statusCode: StatusCodes.Status403Forbidden);
            return false;
        }

        return true;
    }
}
