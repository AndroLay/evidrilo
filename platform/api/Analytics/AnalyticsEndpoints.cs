using System.Text.Json;
using System.Text.Json.Serialization;
using Evidrilo.Api.Auth;
using Evidrilo.Api.Common;

namespace Evidrilo.Api.Analytics;

public static class AnalyticsEndpoints
{
    private static readonly JsonSerializerOptions RequestJsonOptions = new(JsonSerializerDefaults.Web)
    {
        PropertyNamingPolicy = JsonNamingPolicy.CamelCase,
        UnmappedMemberHandling = JsonUnmappedMemberHandling.Disallow,
    };

    static AnalyticsEndpoints()
    {
        RequestJsonOptions.Converters.Add(new JsonStringEnumConverter(JsonNamingPolicy.SnakeCaseLower));
    }

    public static IEndpointRouteBuilder MapAnalyticsEndpoints(this IEndpointRouteBuilder endpoints)
    {
        endpoints.MapPost(
            "/v1/analytics/events",
            async (HttpContext context, IAnalyticsStore store, CancellationToken cancellationToken) =>
            {
                if (!TryGetVerifiedAccount(context, out var accountId, out var authFailure))
                    return authFailure!;

                var payload = await RequestJsonReader.ReadAsync(
                    context.Request,
                    "INVALID_ANALYTICS_EVENT",
                    "The analytics event is invalid.",
                    cancellationToken);
                AnalyticsEventRequest? request;
                try
                {
                    request = payload.Deserialize<AnalyticsEventRequest>(RequestJsonOptions);
                }
                catch (JsonException)
                {
                    return Results.Json(
                        ApiErrors.Create(context, "INVALID_ANALYTICS_EVENT", "The analytics event is invalid."),
                        statusCode: StatusCodes.Status400BadRequest);
                }

                var validation = AnalyticsEventValidator.Validate(request);
                if (!validation.IsValid)
                {
                    return Results.Json(
                        ApiErrors.Create(context, validation.Code!, validation.Code == "ANALYTICS_CONSENT_REQUIRED"
                            ? "Analytics consent is required."
                            : "The analytics event is invalid."),
                        statusCode: validation.Code == "ANALYTICS_CONSENT_REQUIRED"
                            ? StatusCodes.Status403Forbidden
                            : StatusCodes.Status400BadRequest);
                }

                var result = await store.AppendAsync(accountId, request!, cancellationToken);
                return Results.Ok(new AnalyticsEventResponse(
                    "evidrilo.analytics-event-result",
                    "1",
                    result.Outcome,
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
}
