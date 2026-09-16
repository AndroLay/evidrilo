using System.Text.Json;
using System.Text.Json.Serialization;
using Evidrilo.Api.Auth;
using Evidrilo.Api.Common;

namespace Evidrilo.Api.Sync;

public static class SyncEndpoints
{
    // Keep the server cursor bound aligned with the mobile parser so an
    // accepted response can always be represented by the client.
    private const long MaxSyncCursor = 1_000_000_000_000_000L;

    private static readonly JsonSerializerOptions RequestJsonOptions = new(JsonSerializerDefaults.Web)
    {
        PropertyNamingPolicy = JsonNamingPolicy.CamelCase,
        UnmappedMemberHandling = JsonUnmappedMemberHandling.Disallow,
    };

    static SyncEndpoints()
    {
        RequestJsonOptions.Converters.Add(new JsonStringEnumConverter(JsonNamingPolicy.SnakeCaseLower));
    }

    public static IEndpointRouteBuilder MapSyncEndpoints(this IEndpointRouteBuilder endpoints)
    {
        endpoints.MapPost(
            "/v1/sync/commands",
            async (HttpContext context, ISyncStore store, CancellationToken cancellationToken) =>
            {
                if (!TryGetVerifiedAccount(context, out var accountId, out var authFailure))
                {
                    return authFailure!;
                }

                var payload = await RequestJsonReader.ReadAsync(
                    context.Request,
                    "INVALID_SYNC_REQUEST",
                    "The sync request is invalid.",
                    cancellationToken);
                SyncPushRequest? request;
                try
                {
                    request = payload.Deserialize<SyncPushRequest>(RequestJsonOptions);
                }
                catch (JsonException)
                {
                    return BadRequest(context, "INVALID_SYNC_REQUEST", "The sync request is invalid.");
                }

                if (request is null
                    || request.Schema != "evidrilo.sync-push-request"
                    || request.Version != "1"
                    || request.Commands is null
                    || request.Commands.Count is < 1 or > 100)
                {
                    return BadRequest(context, "INVALID_SYNC_REQUEST", "The sync request is invalid.");
                }

                if (request.Commands.Any(command => command is null))
                {
                    return BadRequest(context, "INVALID_SYNC_COMMAND", "A sync command is invalid.");
                }

                if (request.Commands
                    .Select(command => command.CommandId)
                    .Distinct()
                    .Count() != request.Commands.Count)
                {
                    return BadRequest(context, "INVALID_SYNC_REQUEST", "The sync request is invalid.");
                }

                foreach (var command in request.Commands)
                {
                    var validation = SyncCommandValidator.Validate(command);
                    if (!validation.IsValid)
                    {
                        return BadRequest(context, "INVALID_SYNC_COMMAND", "A sync command is invalid.");
                    }
                }

                var result = await store.PushAsync(accountId, request.Commands, cancellationToken);
                return Results.Ok(new SyncPushResponse(
                    "evidrilo.sync-push-result",
                    "1",
                    result.Results,
                    result.NextCursor,
                    RequestIdMiddleware.Get(context)));
            })
            .RequireAuthorization()
            .RequireRateLimiting("api");

        endpoints.MapGet(
            "/v1/sync/pull",
            async (
                HttpContext context,
                long? cursor,
                int? limit,
                ISyncStore store,
                CancellationToken cancellationToken) =>
            {
                if (!TryGetVerifiedAccount(context, out var accountId, out var authFailure))
                {
                    return authFailure!;
                }

                var actualCursor = cursor ?? 0;
                var actualLimit = limit ?? 100;
                if (actualCursor is < 0 or > MaxSyncCursor || actualLimit is < 1 or > 100)
                {
                    return BadRequest(context, "INVALID_SYNC_CURSOR", "The sync cursor is invalid.");
                }

                var result = await store.PullAsync(accountId, actualCursor, actualLimit, cancellationToken);
                return Results.Ok(new SyncPullResponse(
                    "evidrilo.sync-pull",
                    "1",
                    result.Cursor,
                    result.NextCursor,
                    result.HasMore,
                    result.Changes,
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
        accountId = Guid.Empty;
        failure = null;
        if (context.User.Identity?.IsAuthenticated != true)
        {
            failure = Results.Json(
                ApiErrors.Create(context, "AUTH_REQUIRED", "Authentication is required."),
                statusCode: StatusCodes.Status401Unauthorized);
            return false;
        }

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
                ApiErrors.Create(context, "FORBIDDEN", "You are not allowed to perform this action."),
                statusCode: StatusCodes.Status403Forbidden);
            return false;
        }

        return true;
    }

    private static IResult BadRequest(HttpContext context, string code, string message) =>
        Results.Json(ApiErrors.Create(context, code, message), statusCode: StatusCodes.Status400BadRequest);
}
