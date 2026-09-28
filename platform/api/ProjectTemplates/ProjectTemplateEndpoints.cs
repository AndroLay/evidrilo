using System.Text.Json;
using System.Text.Json.Serialization;
using Evidrilo.Api.Auth;
using Evidrilo.Api.Common;

namespace Evidrilo.Api.ProjectTemplates;

public static class ProjectTemplateEndpoints
{
    private const int DefaultPageSize = 50;
    private const int MaximumPageSize = 100;
    private static readonly JsonSerializerOptions RequestJsonOptions = new(JsonSerializerDefaults.Web)
    {
        UnmappedMemberHandling = JsonUnmappedMemberHandling.Disallow,
        Converters = { new JsonStringEnumConverter(JsonNamingPolicy.SnakeCaseLower) },
    };

    public static IEndpointRouteBuilder MapProjectTemplateEndpoints(this IEndpointRouteBuilder endpoints)
    {
        endpoints.MapGet(
            "/v1/project-template-families",
            async (HttpContext context, IProjectTemplateStore store, CancellationToken cancellationToken) =>
            {
                var families = await store.ListFamiliesAsync(cancellationToken);
                return Results.Ok(new ProjectTemplateFamiliesResponse(
                    "evidrilo.project-template-families",
                    "1",
                    families,
                    RequestIdMiddleware.Get(context)));
            })
            .RequireRateLimiting("api");

        endpoints.MapGet(
            "/v1/project-templates",
            async (
                HttpContext context,
                IProjectTemplateStore store,
                string? family,
                int? limit,
                string? afterTemplateId,
                CancellationToken cancellationToken) =>
            {
                if (family is not null && !ProjectTemplateFamilies.TryFind(family, out _))
                    return Invalid(context, "INVALID_PROJECT_TEMPLATE_FAMILY");
                var pageSize = limit ?? DefaultPageSize;
                if (pageSize is < 1 or > MaximumPageSize
                    || afterTemplateId is not null && !ProjectTemplateDocumentValidator.IsValidTemplateId(afterTemplateId))
                    return Invalid(context, "INVALID_PROJECT_TEMPLATE_CATALOG_QUERY");

                var page = await store.ListPublishedAsync(family, pageSize, afterTemplateId, cancellationToken);
                var summaries = page.Templates.Select(entry => new ProjectTemplateSummaryResponse(
                    entry.TemplateId,
                    entry.TemplateVersion,
                    entry.Family,
                    entry.Template.Title!,
                    entry.Template.Summary!,
                    "published")).ToArray();
                return Results.Ok(new ProjectTemplateCatalogListResponse(
                    "evidrilo.project-template-catalog",
                    "1",
                    summaries,
                    page.NextAfterTemplateId,
                    RequestIdMiddleware.Get(context)));
            })
            .RequireRateLimiting("api");

        endpoints.MapGet(
            "/v1/project-templates/{templateId}/versions/{templateVersion:int}",
            async (
                HttpContext context,
                string templateId,
                int templateVersion,
                IProjectTemplateStore store,
                CancellationToken cancellationToken) =>
            {
                if (!ProjectTemplateDocumentValidator.IsValidTemplateId(templateId) || templateVersion < 1)
                    return Invalid(context, "INVALID_PROJECT_TEMPLATE_VERSION");

                var entry = await store.GetPublishedAsync(templateId, templateVersion, cancellationToken);
                if (entry is null)
                    return Results.Json(
                        ApiErrors.Create(context, "PROJECT_TEMPLATE_NOT_FOUND", "The published project template was not found."),
                        statusCode: StatusCodes.Status404NotFound);

                var template = entry.Template;
                return Results.Ok(new ProjectTemplateCatalogDetailResponse(
                    "evidrilo.project-template-detail",
                    "1",
                    new ProjectTemplatePublicDefinitionResponse(
                        entry.TemplateId,
                        entry.TemplateVersion,
                        entry.Family,
                        template.Title!,
                        template.Summary!,
                        template.IntendedOutput!,
                        template.InputFields!,
                        template.Steps!.Select(step => step with
                        {
                            AiOperations = step.AiOperations ?? Array.Empty<ProjectTemplateAiOperationCapability>(),
                        }).ToArray(),
                        template.MethodSpecificLimitations!,
                        template.ProvenanceRequirements!,
                        template.AccessibilityExpectations!,
                        template.Examples!,
                        "published",
                        entry.PublishedAt),
                    RequestIdMiddleware.Get(context)));
            })
            .RequireRateLimiting("api");

        endpoints.MapPost(
            "/v1/authoring/project-templates",
            async (HttpContext context, IProjectTemplateStore store, CancellationToken cancellationToken) =>
            {
                if (!TryGetVerifiedAccount(context, out var accountId, out var failure)) return failure!;
                var payload = await RequestJsonReader.ReadAsync(
                    context.Request,
                    "INVALID_PROJECT_TEMPLATE_DRAFT",
                    "The project template draft is invalid.",
                    cancellationToken);
                ProjectTemplateDraftCreateRequest? request;
                try
                {
                    request = payload.Deserialize<ProjectTemplateDraftCreateRequest>(RequestJsonOptions);
                }
                catch (JsonException)
                {
                    return Invalid(context, "INVALID_PROJECT_TEMPLATE_DRAFT");
                }

                if (request is null
                    || request.Schema != "evidrilo.project-template-draft"
                    || request.Version != "1"
                    || request.OrganizationId == Guid.Empty
                    || request.TemplateVersion < 1
                    || !ProjectTemplateDocumentValidator.IsValidTemplateId(request.TemplateId)
                    || !ProjectTemplateFamilies.TryFind(request.Family, out _)
                    || ProjectTemplateDocumentValidator.ValidateDraft(request.Template) is not null)
                    return Invalid(context, "INVALID_PROJECT_TEMPLATE_DRAFT");

                var result = await store.CreateDraftAsync(accountId, request, cancellationToken);
                return Results.Json(ToResponse(context, result), statusCode: StatusCodes.Status201Created);
            })
            .RequireAuthorization()
            .RequireRateLimiting("api");

        endpoints.MapPost(
            "/v1/authoring/project-templates/{templateId}/versions/{templateVersion:int}/transition",
            async (
                HttpContext context,
                string templateId,
                int templateVersion,
                IProjectTemplateStore store,
                CancellationToken cancellationToken) =>
            {
                if (!TryGetVerifiedAccount(context, out var accountId, out var failure)) return failure!;
                if (!ProjectTemplateDocumentValidator.IsValidTemplateId(templateId) || templateVersion < 1)
                    return Invalid(context, "INVALID_PROJECT_TEMPLATE_VERSION");

                var payload = await RequestJsonReader.ReadAsync(
                    context.Request,
                    "INVALID_PROJECT_TEMPLATE_TRANSITION",
                    "The project template transition is invalid.",
                    cancellationToken);
                ProjectTemplateTransitionRequest? request;
                try
                {
                    request = payload.Deserialize<ProjectTemplateTransitionRequest>(RequestJsonOptions);
                }
                catch (JsonException)
                {
                    return Invalid(context, "INVALID_PROJECT_TEMPLATE_TRANSITION");
                }

                if (request is null
                    || request.Schema != "evidrilo.project-template-transition"
                    || request.Version != "1"
                    || !Enum.IsDefined(request.TargetState)
                    || request.ReviewedExampleIds is { Count: > 24 })
                    return Invalid(context, "INVALID_PROJECT_TEMPLATE_TRANSITION");

                var result = await store.TransitionAsync(
                    accountId,
                    templateId,
                    templateVersion,
                    request,
                    cancellationToken);
                return Results.Ok(ToResponse(context, result));
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
                ApiErrors.Create(context, "FORBIDDEN", "A verified account is required for template authoring."),
                statusCode: StatusCodes.Status403Forbidden);
            return false;
        }
        return true;
    }

    private static ProjectTemplateAuthoringResponse ToResponse(HttpContext context, ProjectTemplateOperation operation) =>
        new(
            "evidrilo.project-template-operation",
            "1",
            operation.Outcome,
            operation.TemplateId,
            operation.TemplateVersion,
            operation.State.ToString().ToLowerInvariant(),
            RequestIdMiddleware.Get(context));

    private static IResult Invalid(HttpContext context, string code) => Results.Json(
        ApiErrors.Create(context, code, "The project template request is invalid."),
        statusCode: StatusCodes.Status400BadRequest);
}
