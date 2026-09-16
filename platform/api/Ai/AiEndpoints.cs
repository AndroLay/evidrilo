using System.Text.Json;
using System.Text.Json.Serialization;
using Evidrilo.Api.Auth;
using Evidrilo.Api.Common;

namespace Evidrilo.Api.Ai;

public sealed record AiAssistResponse(
    [property: JsonPropertyName("schema")] string Schema,
    [property: JsonPropertyName("version")] string Version,
    [property: JsonPropertyName("status")] string Status,
    [property: JsonPropertyName("text"), JsonIgnore(Condition = JsonIgnoreCondition.Never)] string? Text,
    [property: JsonPropertyName("reasonCode"), JsonIgnore(Condition = JsonIgnoreCondition.Never)] string? ReasonCode,
    [property: JsonPropertyName("promptVersion")] string PromptVersion,
    [property: JsonPropertyName("requestId")] string RequestId);

public static class AiEndpoints
{
    private static readonly JsonSerializerOptions RequestJsonOptions = new(JsonSerializerDefaults.Web)
    {
        PropertyNamingPolicy = JsonNamingPolicy.CamelCase,
        UnmappedMemberHandling = JsonUnmappedMemberHandling.Disallow,
    };

    static AiEndpoints()
    {
        RequestJsonOptions.Converters.Add(new JsonStringEnumConverter(JsonNamingPolicy.SnakeCaseLower));
    }

    public static IEndpointRouteBuilder MapAiEndpoints(this IEndpointRouteBuilder endpoints)
    {
        endpoints.MapPost(
            "/v1/ai/assist",
            async (
                HttpContext context,
                AiGateway gateway,
                IAiAuditStore auditStore,
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
                        ApiErrors.Create(context, "FORBIDDEN", "You are not allowed to request AI assistance."),
                        statusCode: StatusCodes.Status403Forbidden);
                }

                var payload = await RequestJsonReader.ReadAsync(
                    context.Request,
                    "INVALID_AI_REQUEST",
                    "The AI request is invalid.",
                    cancellationToken);
                AiAssistRequest? request;
                try
                {
                    request = payload.Deserialize<AiAssistRequest>(RequestJsonOptions);
                }
                catch (JsonException)
                {
                    return Results.Json(
                        ApiErrors.Create(context, "INVALID_AI_REQUEST", "The AI request is invalid."),
                        statusCode: StatusCodes.Status400BadRequest);
                }

                if (request is null)
                {
                    return Results.Json(
                        ApiErrors.Create(context, "INVALID_AI_REQUEST", "The AI request is invalid."),
                        statusCode: StatusCodes.Status400BadRequest);
                }

                var result = await gateway.GenerateAsync(accountId, request, cancellationToken);
                if (!string.Equals(result.ReasonCode, "AI_OPT_IN_REQUIRED", StringComparison.Ordinal))
                {
                    await auditStore.RecordAsync(
                        accountId,
                        RequestIdMiddleware.Get(context),
                        result.Audit,
                        result.ReasonCode,
                        cancellationToken);
                }
                return Results.Ok(new AiAssistResponse(
                    "evidrilo.ai-assist-result",
                    "1",
                    result.Status,
                    result.Text,
                    result.ReasonCode,
                    result.Audit.PromptVersion,
                    RequestIdMiddleware.Get(context)));
            })
            .RequireAuthorization()
            .RequireRateLimiting("api");

        return endpoints;
    }
}
