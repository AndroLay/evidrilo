using System.Text.Json;
using System.Text.Json.Serialization;
using Evidrilo.Api.Auth;
using Evidrilo.Api.Common;

namespace Evidrilo.Api.Recommendations;

public static class RecommendationInteractionEndpoints
{
    private static readonly JsonSerializerOptions RequestJsonOptions = new(JsonSerializerDefaults.Web)
    {
        PropertyNamingPolicy = JsonNamingPolicy.CamelCase,
        UnmappedMemberHandling = JsonUnmappedMemberHandling.Disallow,
    };

    static RecommendationInteractionEndpoints()
    {
        RequestJsonOptions.Converters.Add(new JsonStringEnumConverter(JsonNamingPolicy.SnakeCaseLower));
    }

    public static IEndpointRouteBuilder MapRecommendationInteractionEndpoints(this IEndpointRouteBuilder endpoints)
    {
        endpoints.MapPost(
            "/v1/recommendations/interactions",
            async (
                HttpContext context,
                IRecommendationInteractionStore store,
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
                        ApiErrors.Create(context, "FORBIDDEN", "You are not allowed to record recommendation interactions."),
                        statusCode: StatusCodes.Status403Forbidden);
                }

                var payload = await RequestJsonReader.ReadAsync(
                    context.Request,
                    "INVALID_RECOMMENDATION_INTERACTION",
                    "The recommendation interaction is invalid.",
                    cancellationToken);
                RecommendationInteractionRequest? request;
                try
                {
                    request = payload.Deserialize<RecommendationInteractionRequest>(RequestJsonOptions);
                }
                catch (JsonException)
                {
                    return Results.Json(
                        ApiErrors.Create(context, "INVALID_RECOMMENDATION_INTERACTION", "The recommendation interaction is invalid."),
                        statusCode: StatusCodes.Status400BadRequest);
                }

                var validationCode = RecommendationInteractionValidator.Validate(request);
                if (validationCode is not null)
                {
                    return Results.Json(
                        ApiErrors.Create(
                            context,
                            validationCode,
                            validationCode == "RECOMMENDATION_CONSENT_REQUIRED"
                                ? "Recommendation analytics consent is required."
                                : "The recommendation interaction is invalid."),
                        statusCode: validationCode == "RECOMMENDATION_CONSENT_REQUIRED"
                            ? StatusCodes.Status403Forbidden
                            : StatusCodes.Status400BadRequest);
                }

                var outcome = await store.AppendAsync(accountId, request!, cancellationToken);
                return Results.Ok(new RecommendationInteractionResponse(
                    "evidrilo.recommendation-interaction-result",
                    "1",
                    outcome,
                    RequestIdMiddleware.Get(context)));
            })
            .RequireAuthorization()
            .RequireRateLimiting("api");

        return endpoints;
    }
}
