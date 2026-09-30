using System.Text.Json;
using System.Text.Json.Serialization;
using System.Text.RegularExpressions;
using Evidrilo.Api.Auth;
using Evidrilo.Api.Common;
using Evidrilo.Api.ProjectTemplates;

namespace Evidrilo.Api.ProjectAi;

public sealed record ProjectAiLocalProjectContextRegistrationRequest(
    [property: JsonRequired, JsonPropertyName("schema")] string? Schema,
    [property: JsonRequired, JsonPropertyName("version")] string? Version,
    [property: JsonRequired, JsonPropertyName("installationId")] string? InstallationId,
    [property: JsonRequired, JsonPropertyName("templateId")] string? TemplateId,
    [property: JsonRequired, JsonPropertyName("templateVersion")] int? TemplateVersion,
    [property: JsonRequired, JsonPropertyName("projectRevision")] int? ProjectRevision,
    [property: JsonRequired, JsonPropertyName("availableEvidenceIds")] IReadOnlyList<string>? AvailableEvidenceIds);

public sealed record ProjectAiLocalProjectContextResponse(
    [property: JsonPropertyName("schema")] string Schema,
    [property: JsonPropertyName("version")] string Version,
    [property: JsonPropertyName("projectId")] Guid ProjectId,
    [property: JsonPropertyName("projectRevision")] int ProjectRevision,
    [property: JsonPropertyName("bindingGeneration")] long BindingGeneration,
    [property: JsonPropertyName("templateId")] string TemplateId,
    [property: JsonPropertyName("templateVersion")] int TemplateVersion,
    [property: JsonPropertyName("status")] string Status,
    [property: JsonPropertyName("requestId")] string RequestId);

public sealed record ProjectAiLocalProjectContextDeleteResponse(
    [property: JsonPropertyName("schema")] string Schema,
    [property: JsonPropertyName("version")] string Version,
    [property: JsonPropertyName("projectId")] Guid ProjectId,
    [property: JsonPropertyName("status")] string Status);

public static class ProjectAiLocalProjectContextEndpoints
{
    public const string Schema = "evidrilo.project-ai-local-project-context";
    public const string Version = "1";

    private static readonly Regex EvidenceIdPattern = new(
        "\\A[A-Za-z0-9_-]{1,64}\\z",
        RegexOptions.CultureInvariant);
    private static readonly JsonSerializerOptions RequestJsonOptions = new(JsonSerializerDefaults.Web)
    {
        UnmappedMemberHandling = JsonUnmappedMemberHandling.Disallow,
    };

    public static IEndpointRouteBuilder MapProjectAiLocalProjectContextEndpoints(
        this IEndpointRouteBuilder endpoints)
    {
        endpoints.MapPut(
            "/v1/project-ai/projects/{projectId:guid}/local-context",
            async (
                HttpContext context,
                Guid projectId,
                IProjectAiLocalProjectContextStore contextStore,
                IProjectTemplateStore templateStore,
                IProjectAiConsentStore consentStore,
                CancellationToken cancellationToken) =>
            {
                if (!AuthenticatedUser.TryGetAccountId(context.User, out var accountId))
                    return Error(context, "AUTH_REQUIRED", "Authentication is required.", StatusCodes.Status401Unauthorized);
                if (!AuthenticatedUser.IsEmailVerified(context.User))
                    return Error(context, "FORBIDDEN", "A verified account is required for project AI.", StatusCodes.Status403Forbidden);
                if (!ProjectAiStageAssistValidator.HasExplicitRequestConsent(context))
                    return Error(context, "PROJECT_AI_REQUEST_CONSENT_REQUIRED", "Confirm the selected project context for this request.", StatusCodes.Status403Forbidden);
                if (projectId == Guid.Empty)
                    return Error(context, "INVALID_PROJECT_AI_CONTEXT", "The project context is invalid.", StatusCodes.Status400BadRequest);

                var payload = await RequestJsonReader.ReadAsync(
                    context.Request,
                    "INVALID_PROJECT_AI_CONTEXT",
                    "The local project context is invalid.",
                    cancellationToken);
                ProjectAiLocalProjectContextRegistrationRequest? request;
                try
                {
                    request = payload.Deserialize<ProjectAiLocalProjectContextRegistrationRequest>(RequestJsonOptions);
                }
                catch (JsonException)
                {
                    return Error(context, "INVALID_PROJECT_AI_CONTEXT", "The local project context is invalid.", StatusCodes.Status400BadRequest);
                }

                if (!IsValid(request))
                    return Error(context, "INVALID_PROJECT_AI_CONTEXT", "The local project context is invalid.", StatusCodes.Status400BadRequest);

                var consent = await consentStore.ReadOwnAsync(accountId, cancellationToken);
                if (!ProjectAiConsentPolicy.StillAuthorizesDispatch(consent, consent))
                    return Error(context, "PROJECT_AI_CONSENT_REQUIRED", "Review and grant the current Project AI consent first.", StatusCodes.Status403Forbidden);

                var template = await templateStore.GetPublishedAsync(
                    request!.TemplateId!,
                    request.TemplateVersion!.Value,
                    cancellationToken);
                if (template is null || ProjectTemplateDocumentValidator.Validate(template.Template) is not null)
                    return Error(context, "PROJECT_AI_TEMPLATE_NOT_READY", "The selected project method is not published for assistance.", StatusCodes.Status404NotFound);

                var outcome = await contextStore.UpsertOwnAsync(
                    accountId,
                    projectId,
                    request.ProjectRevision!.Value,
                    request.TemplateId!,
                    request.TemplateVersion.Value,
                    request.AvailableEvidenceIds!,
                    cancellationToken);
                if (outcome == ProjectAiLocalContextWriteOutcome.Stale)
                    return Error(context, "PROJECT_AI_CONTEXT_STALE", "A newer project revision is already registered. Reload the project before requesting assistance.", StatusCodes.Status409Conflict);
                if (outcome == ProjectAiLocalContextWriteOutcome.Conflict)
                    return Error(context, "PROJECT_AI_CONTEXT_CONFLICT", "The project context changed without a new saved revision. Save and retry.", StatusCodes.Status409Conflict);

                var state = await contextStore.ReadOwnAsync(accountId, projectId, cancellationToken);
                if (state is null || state.CurrentRevision != request.ProjectRevision.Value)
                    return Error(context, "PROJECT_AI_CONTEXT_STALE", "The project revision changed while it was being registered.", StatusCodes.Status409Conflict);

                return Results.Ok(new ProjectAiLocalProjectContextResponse(
                    Schema,
                    Version,
                    projectId,
                    state.CurrentRevision,
                    state.BindingGeneration,
                    request.TemplateId!,
                    request.TemplateVersion.Value,
                    outcome == ProjectAiLocalContextWriteOutcome.Unchanged ? "unchanged" : "registered",
                    RequestIdMiddleware.Get(context)));
            })
            .RequireAuthorization()
            .RequireRateLimiting("api");

        endpoints.MapDelete(
            "/v1/project-ai/projects/{projectId:guid}/local-context",
            async (
                HttpContext context,
                Guid projectId,
                IProjectAiLocalProjectContextStore contextStore,
                CancellationToken cancellationToken) =>
            {
                if (!AuthenticatedUser.TryGetAccountId(context.User, out var accountId))
                    return Error(context, "AUTH_REQUIRED", "Authentication is required.", StatusCodes.Status401Unauthorized);
                if (!AuthenticatedUser.IsEmailVerified(context.User))
                    return Error(context, "FORBIDDEN", "A verified account is required.", StatusCodes.Status403Forbidden);
                if (projectId == Guid.Empty)
                    return Error(context, "INVALID_PROJECT_AI_CONTEXT", "The project identity is invalid.", StatusCodes.Status400BadRequest);

                await contextStore.DeleteOwnAsync(accountId, projectId, cancellationToken);
                return Results.Ok(new ProjectAiLocalProjectContextDeleteResponse(
                    Schema,
                    Version,
                    projectId,
                    "deleted"));
            })
            .RequireAuthorization()
            .RequireRateLimiting("api");

        return endpoints;
    }

    public static bool IsValid(ProjectAiLocalProjectContextRegistrationRequest? request) =>
        request is not null
        && request.Schema == Schema
        && request.Version == Version
        && Guid.TryParseExact(request.InstallationId, "D", out var installationId)
        && installationId != Guid.Empty
        && ProjectTemplateDocumentValidator.IsValidTemplateId(request.TemplateId)
        && request.TemplateVersion is >= 1
        && request.ProjectRevision is >= 1
        && request.AvailableEvidenceIds is { Count: <= 2048 }
        && request.AvailableEvidenceIds.All(id => id is not null && EvidenceIdPattern.IsMatch(id))
        && request.AvailableEvidenceIds.Distinct(StringComparer.Ordinal).Count() == request.AvailableEvidenceIds.Count;

    private static IResult Error(HttpContext context, string code, string message, int statusCode) =>
        Results.Json(ApiErrors.Create(context, code, message), statusCode: statusCode);
}
