using System.Text.Json.Serialization;
using System.Text.Json;
using Evidrilo.Api.Auth;
using Evidrilo.Api.Common;

namespace Evidrilo.Api.ProjectAi;

public sealed record ProjectAiActivityHistoryEntry(
    [property: JsonPropertyName("activityId")] string ActivityId,
    [property: JsonPropertyName("mode")] string Mode,
    [property: JsonPropertyName("projectId")] string? ProjectId,
    [property: JsonPropertyName("stageId")] string? StageId,
    [property: JsonPropertyName("operationId")] string? OperationId,
    [property: JsonPropertyName("baseProjectRevision")] int? BaseProjectRevision,
    [property: JsonPropertyName("resultProjectRevision")] int? ResultProjectRevision,
    [property: JsonPropertyName("outcome")] string Outcome,
    [property: JsonPropertyName("createdAt")] DateTimeOffset CreatedAt,
    [property: JsonPropertyName("updatedAt")] DateTimeOffset UpdatedAt);

public sealed record ProjectAiActivityHistoryResponse(
    [property: JsonPropertyName("schema")] string Schema,
    [property: JsonPropertyName("version")] string Version,
    [property: JsonPropertyName("activities")] IReadOnlyList<ProjectAiActivityHistoryEntry> Activities,
    [property: JsonPropertyName("nextCursor")] string? NextCursor);

public sealed record ProjectAiActivityClearResponse(
    [property: JsonPropertyName("schema")] string Schema,
    [property: JsonPropertyName("version")] string Version,
    [property: JsonPropertyName("clearedCount")] int ClearedCount);

public static class ProjectAiActivityEndpoints
{
    public const string Schema = "evidrilo.project-ai-activity";
    public const string Version = "1";
    private const int DefaultPageSize = 50;
    private const int MaximumPageSize = 100;
    private static readonly JsonSerializerOptions ResponseJsonOptions = new(JsonSerializerDefaults.Web)
    {
        DefaultIgnoreCondition = JsonIgnoreCondition.Never,
    };

    public static IEndpointRouteBuilder MapProjectAiActivityEndpoints(this IEndpointRouteBuilder endpoints)
    {
        endpoints.MapGet(
            "/v1/project-ai/activity",
            async (
                HttpContext context,
                IProjectAiActivityStore activityStore,
                CancellationToken cancellationToken) =>
            {
                if (!AuthenticatedUser.TryGetAccountId(context.User, out var accountId))
                    return Error(context, "AUTH_REQUIRED", "Authentication is required.", StatusCodes.Status401Unauthorized);
                if (!AuthenticatedUser.IsEmailVerified(context.User))
                    return Error(context, "FORBIDDEN", "A verified account is required for project AI.", StatusCodes.Status403Forbidden);

                if (!TryReadGuid(context.Request.Query["installationId"].ToString(), out var installationId)
                    || installationId == Guid.Empty
                    || !TryReadOptionalGuid(context.Request.Query["projectId"].ToString(), out var projectId)
                    || !TryReadLimit(context.Request.Query["limit"].ToString(), out var limit)
                    || !TryReadCursor(context.Request.Query["cursor"].ToString(), out var cursor))
                    return Invalid(context, "INVALID_PROJECT_AI_ACTIVITY_QUERY");

                var page = await activityStore.ListOwnAsync(
                    accountId,
                    installationId,
                    projectId,
                    limit,
                    cursor,
                    cancellationToken);
                return Results.Json(
                    new ProjectAiActivityHistoryResponse(
                        Schema,
                        Version,
                        page.Activities.Select(ToResponse).ToArray(),
                        page.NextCursor),
                    options: ResponseJsonOptions);
            })
            .RequireAuthorization()
            .RequireRateLimiting("api");

        endpoints.MapDelete(
            "/v1/project-ai/activity/general",
            async (
                HttpContext context,
                IProjectAiActivityStore activityStore,
                CancellationToken cancellationToken) =>
            {
                if (!AuthenticatedUser.TryGetAccountId(context.User, out var accountId))
                    return Error(context, "AUTH_REQUIRED", "Authentication is required.", StatusCodes.Status401Unauthorized);
                if (!AuthenticatedUser.IsEmailVerified(context.User))
                    return Error(context, "FORBIDDEN", "A verified account is required for project AI.", StatusCodes.Status403Forbidden);
                if (!TryReadGuid(context.Request.Query["installationId"].ToString(), out var installationId)
                    || installationId == Guid.Empty)
                    return Invalid(context, "INVALID_PROJECT_AI_ACTIVITY_CLEAR");

                var clearedCount = await activityStore.ClearGeneralOwnAsync(
                    accountId,
                    installationId,
                    cancellationToken);
                return Results.Json(
                    new ProjectAiActivityClearResponse(Schema, Version, clearedCount),
                    options: ResponseJsonOptions);
            })
            .RequireAuthorization()
            .RequireRateLimiting("api");

        return endpoints;
    }

    private static ProjectAiActivityHistoryEntry ToResponse(ProjectAiActivityRecord activity) => new(
        activity.ActivityId.ToString("D"),
        activity.Mode,
        activity.ProjectId?.ToString("D"),
        activity.StageId,
        activity.OperationId,
        activity.BaseProjectRevision,
        activity.ResultProjectRevision,
        activity.Outcome,
        activity.CreatedAt,
        activity.UpdatedAt);

    private static bool TryReadGuid(string value, out Guid id) =>
        Guid.TryParseExact(value, "D", out id) && id != Guid.Empty;

    private static bool TryReadOptionalGuid(string value, out Guid? id)
    {
        id = null;
        if (string.IsNullOrEmpty(value)) return true;
        if (!Guid.TryParseExact(value, "D", out var parsed) || parsed == Guid.Empty) return false;
        id = parsed;
        return true;
    }

    private static bool TryReadLimit(string value, out int limit)
    {
        if (string.IsNullOrEmpty(value))
        {
            limit = DefaultPageSize;
            return true;
        }
        return int.TryParse(value, out limit) && limit is >= 1 and <= MaximumPageSize;
    }

    private static bool TryReadCursor(string value, out ProjectAiActivityCursor? cursor)
    {
        cursor = null;
        return string.IsNullOrEmpty(value) || NpgsqlProjectAiActivityStore.TryDecodeCursor(value, out cursor);
    }

    private static IResult Invalid(HttpContext context, string code) =>
        Error(context, code, "The project-AI activity request is invalid.", StatusCodes.Status400BadRequest);

    private static IResult Error(HttpContext context, string code, string message, int statusCode) =>
        Results.Json(ApiErrors.Create(context, code, message), statusCode: statusCode);
}
