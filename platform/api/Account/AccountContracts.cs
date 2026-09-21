using System.Globalization;
using System.Text.Json;
using System.Text.Json.Serialization;
using Evidrilo.Api.Auth;
using Evidrilo.Api.Common;

namespace Evidrilo.Api.Account;

public sealed record AccountSummary(
    [property: JsonPropertyName("schema")] string Schema,
    [property: JsonPropertyName("version")] string Version,
    [property: JsonPropertyName("accountId")] string AccountId,
    [property: JsonPropertyName("emailVerified")] bool EmailVerified,
    [property: JsonPropertyName("serverTime")] string ServerTime,
    [property: JsonPropertyName("requestId")] string RequestId);

public sealed record AccountDeletionResponse(
    [property: JsonPropertyName("schema")] string Schema,
    [property: JsonPropertyName("version")] string Version,
    [property: JsonPropertyName("outcome")] string Outcome,
    [property: JsonPropertyName("requestId")] string RequestId);

public sealed record AccountExportResponse(
    [property: JsonPropertyName("schema")] string Schema,
    [property: JsonPropertyName("version")] string Version,
    [property: JsonPropertyName("accountId")] string AccountId,
    [property: JsonPropertyName("generatedAt")] string GeneratedAt,
    [property: JsonPropertyName("data")] JsonElement Data,
    [property: JsonPropertyName("requestId")] string RequestId);

public static class AccountEndpoints
{
    public static IEndpointRouteBuilder MapAccountEndpoints(this IEndpointRouteBuilder endpoints)
    {
        endpoints.MapGet("/v1/account/me", (HttpContext context) =>
        {
            var principal = context.User;
            if (principal.Identity?.IsAuthenticated != true)
            {
                return Results.Json(
                    ApiErrors.Create(context, "AUTH_REQUIRED", "Authentication is required."),
                    statusCode: StatusCodes.Status401Unauthorized);
            }

            if (!AuthenticatedUser.TryGetAccountId(principal, out var accountId))
            {
                return Results.Json(
                    ApiErrors.Create(context, "AUTH_REQUIRED", "Authentication is required."),
                    statusCode: StatusCodes.Status401Unauthorized);
            }

            if (!AuthenticatedUser.IsEmailVerified(principal))
            {
                return Results.Json(
                    ApiErrors.Create(context, "FORBIDDEN", "You are not allowed to perform this action."),
                    statusCode: StatusCodes.Status403Forbidden);
            }

            var serverTime = DateTimeOffset.UtcNow.ToString(
                "yyyy-MM-dd'T'HH:mm:ss.fff'Z'",
                CultureInfo.InvariantCulture);
            return Results.Ok(new AccountSummary(
                "evidrilo.account-summary",
                "1",
                accountId.ToString(),
                true,
                serverTime,
                RequestIdMiddleware.Get(context)));
        })
        .RequireAuthorization()
        .RequireRateLimiting("api");

        endpoints.MapDelete(
            "/v1/account/me",
            async (HttpContext context, IAccountLifecycleStore store, CancellationToken cancellationToken) =>
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
                        ApiErrors.Create(context, "FORBIDDEN", "You are not allowed to delete this account."),
                        statusCode: StatusCodes.Status403Forbidden);
                }

                if (!string.Equals(
                        context.Request.Headers["X-Account-Deletion-Confirm"].FirstOrDefault(),
                        "delete-my-account",
                        StringComparison.Ordinal))
                {
                    return Results.Json(
                        ApiErrors.Create(
                            context,
                            "ACCOUNT_DELETION_CONFIRMATION_REQUIRED",
                            "Explicit account deletion confirmation is required."),
                        statusCode: StatusCodes.Status400BadRequest);
                }

                var result = await store.DeleteOwnAsync(accountId, cancellationToken);
                return Results.Ok(new AccountDeletionResponse(
                    "evidrilo.account-deletion-result",
                    "1",
                    result.Outcome,
                    RequestIdMiddleware.Get(context)));
            })
            .RequireAuthorization()
            .RequireRateLimiting("api");

        endpoints.MapGet(
            "/v1/account/me/export",
            async (HttpContext context, IAccountExportStore store, CancellationToken cancellationToken) =>
            {
                if (!TryGetVerifiedAccount(context, out var accountId, out var failure))
                    return failure!;

                var data = await store.GetOwnAsync(accountId, cancellationToken);
                return Results.Ok(new AccountExportResponse(
                    "evidrilo.account-export",
                    "1",
                    accountId.ToString(),
                    DateTimeOffset.UtcNow.ToString(
                        "yyyy-MM-dd'T'HH:mm:ss.fff'Z'",
                        CultureInfo.InvariantCulture),
                    data,
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
                ApiErrors.Create(context, "FORBIDDEN", "You are not allowed to export account data."),
                statusCode: StatusCodes.Status403Forbidden);
            return false;
        }

        return true;
    }
}
