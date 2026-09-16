using System.Text.Json.Serialization;
using Evidrilo.Api.Auth;
using Evidrilo.Api.Common;

namespace Evidrilo.Api.Billing;

public sealed record EntitlementResponse(
    [property: JsonPropertyName("schema")] string Schema,
    [property: JsonPropertyName("version")] string Version,
    [property: JsonPropertyName("entitlements")] IReadOnlyList<EntitlementRecord> Entitlements,
    [property: JsonPropertyName("requestId")] string RequestId);

public static class EntitlementEndpoints
{
    public static IEndpointRouteBuilder MapEntitlementEndpoints(this IEndpointRouteBuilder endpoints)
    {
        endpoints.MapGet(
            "/v1/billing/entitlements",
            async (HttpContext context, IEntitlementStore store, CancellationToken cancellationToken) =>
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
                        ApiErrors.Create(context, "FORBIDDEN", "You are not allowed to access entitlements."),
                        statusCode: StatusCodes.Status403Forbidden);
                }

                var entitlements = await store.GetOwnAsync(accountId, cancellationToken);
                return Results.Ok(new EntitlementResponse(
                    "evidrilo.entitlements",
                    "1",
                    entitlements,
                    RequestIdMiddleware.Get(context)));
            })
            .RequireAuthorization()
            .RequireRateLimiting("api");

        return endpoints;
    }
}
