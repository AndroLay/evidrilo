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

                return new AccountExportResult(
                    store,
                    accountId,
                    RequestIdMiddleware.Get(context));
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

internal sealed class AccountExportResult(
    IAccountExportStore store,
    Guid accountId,
    string requestId) : IResult
{
    internal const int MaximumResponseBytes = 128 * 1024;

    public async Task ExecuteAsync(HttpContext httpContext)
    {
        var cancellationToken = httpContext.RequestAborted;
        httpContext.Response.Headers.CacheControl = "no-store";
        await using var payload = new BoundedAccountExportStream(MaximumResponseBytes);
        try
        {
            await using (var export = await store.OpenOwnAsync(accountId, cancellationToken))
            {
                await using var writer = new Utf8JsonWriter(payload);
                writer.WriteStartObject();
                writer.WriteString("schema", "evidrilo.account-export");
                writer.WriteString("version", "1");
                writer.WriteString("accountId", accountId.ToString());
                writer.WriteString(
                    "generatedAt",
                    DateTimeOffset.UtcNow.ToString(
                        "yyyy-MM-dd'T'HH:mm:ss.fff'Z'",
                        CultureInfo.InvariantCulture));
                writer.WritePropertyName("data");
                await export.WriteDataAsync(writer, cancellationToken);
                writer.WriteString("requestId", requestId);
                writer.WriteEndObject();
                await writer.FlushAsync(cancellationToken);
            }
        }
        catch (AccountExportTooLargeException)
        {
            await ApiErrors.WriteAsync(
                httpContext,
                StatusCodes.Status413PayloadTooLarge,
                "ACCOUNT_EXPORT_TOO_LARGE",
                "The account export exceeds the supported response size.",
                cancellationToken);
            return;
        }

        httpContext.Response.ContentType = "application/json; charset=utf-8";
        httpContext.Response.ContentLength = payload.Length;
        payload.Position = 0;
        await payload.CopyToAsync(httpContext.Response.Body, cancellationToken);
    }
}

internal sealed class AccountExportTooLargeException()
    : IOException("The account export exceeded its response size limit.");

internal sealed class BoundedAccountExportStream(int maximumBytes) : MemoryStream
{
    public override void Write(byte[] buffer, int offset, int count)
    {
        EnsureWithinLimit(count);
        base.Write(buffer, offset, count);
    }

    public override void Write(ReadOnlySpan<byte> buffer)
    {
        EnsureWithinLimit(buffer.Length);
        base.Write(buffer);
    }

    public override void WriteByte(byte value)
    {
        EnsureWithinLimit(1);
        base.WriteByte(value);
    }

    public override Task WriteAsync(
        byte[] buffer,
        int offset,
        int count,
        CancellationToken cancellationToken)
    {
        EnsureWithinLimit(count);
        return base.WriteAsync(buffer, offset, count, cancellationToken);
    }

    public override ValueTask WriteAsync(
        ReadOnlyMemory<byte> buffer,
        CancellationToken cancellationToken = default)
    {
        EnsureWithinLimit(buffer.Length);
        return base.WriteAsync(buffer, cancellationToken);
    }

    private void EnsureWithinLimit(int count)
    {
        if (count < 0 || count > maximumBytes - Length)
            throw new AccountExportTooLargeException();
    }
}
