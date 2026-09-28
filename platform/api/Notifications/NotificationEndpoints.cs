using System.Text.Json;
using System.Text.Json.Serialization;
using Evidrilo.Api.Auth;
using Evidrilo.Api.Common;

namespace Evidrilo.Api.Notifications;

public static class NotificationEndpoints
{
    private static readonly JsonSerializerOptions RequestJsonOptions = new(JsonSerializerDefaults.Web)
    {
        PropertyNamingPolicy = JsonNamingPolicy.CamelCase,
        UnmappedMemberHandling = JsonUnmappedMemberHandling.Disallow,
    };
    private static readonly JsonSerializerOptions ResponseJsonOptions = new(JsonSerializerDefaults.Web)
    {
        DefaultIgnoreCondition = JsonIgnoreCondition.Never,
    };

    static NotificationEndpoints()
    {
        RequestJsonOptions.Converters.Add(new JsonStringEnumConverter(JsonNamingPolicy.SnakeCaseLower));
    }

    public static IEndpointRouteBuilder MapNotificationEndpoints(this IEndpointRouteBuilder endpoints)
    {
        endpoints.MapGet(
            "/v1/notifications/preferences",
            async (
                HttpContext context,
                INotificationPreferencesStore store,
                CancellationToken cancellationToken) =>
            {
                if (!TryGetVerifiedAccount(context, out var accountId, out var failure))
                    return failure!;

                var preferences = await store.GetOwnAsync(accountId, cancellationToken);
                return Results.Json(
                    NotificationPreferencesResponse.From(
                        preferences,
                        RequestIdMiddleware.Get(context)),
                    options: ResponseJsonOptions);
            })
            .RequireAuthorization()
            .RequireRateLimiting("api");

        endpoints.MapPut(
            "/v1/notifications/preferences",
            async (
                HttpContext context,
                INotificationPreferencesStore store,
                CancellationToken cancellationToken) =>
            {
                if (!TryGetVerifiedAccount(context, out var accountId, out var failure))
                    return failure!;

                var payload = await RequestJsonReader.ReadAsync(
                    context.Request,
                    "INVALID_NOTIFICATION_PREFERENCES",
                    "The notification preferences are invalid.",
                    cancellationToken);
                NotificationPreferencesUpdateRequest? request;
                try
                {
                    request = payload.Deserialize<NotificationPreferencesUpdateRequest>(RequestJsonOptions);
                }
                catch (JsonException)
                {
                    return Results.Json(
                        ApiErrors.Create(
                            context,
                            "INVALID_NOTIFICATION_PREFERENCES",
                            "The notification preferences are invalid."),
                        statusCode: StatusCodes.Status400BadRequest);
                }

                var validationCode = NotificationPreferencesValidator.Validate(request);
                if (validationCode is not null)
                {
                    return Results.Json(
                        ApiErrors.Create(
                            context,
                            validationCode,
                            "The notification preferences are invalid."),
                        statusCode: StatusCodes.Status400BadRequest);
                }

                var result = await store.PutOwnAsync(accountId, request!, cancellationToken);
                return Results.Json(
                    NotificationPreferencesUpdateResponse.From(
                        result,
                        RequestIdMiddleware.Get(context)),
                    options: ResponseJsonOptions);
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
                ApiErrors.Create(context, "FORBIDDEN", "You are not allowed to manage notification preferences."),
                statusCode: StatusCodes.Status403Forbidden);
            return false;
        }

        return true;
    }
}
