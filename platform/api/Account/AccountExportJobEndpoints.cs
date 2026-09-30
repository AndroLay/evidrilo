using System.Security.Cryptography;
using System.Text;
using System.Text.Json.Serialization;
using Evidrilo.Api.Auth;
using Evidrilo.Api.Common;

namespace Evidrilo.Api.Account;

public sealed record AccountExportJobResponse(
    [property: JsonPropertyName("schema")] string Schema,
    [property: JsonPropertyName("version")] string Version,
    [property: JsonPropertyName("exportId")] string ExportId,
    [property: JsonPropertyName("status")] string Status,
    [property: JsonPropertyName("requestId")] string RequestId,
    [property: JsonPropertyName("createdAt")] DateTimeOffset CreatedAt,
    [property: JsonPropertyName("completedAt")] DateTimeOffset? CompletedAt,
    [property: JsonPropertyName("expiresAt")] DateTimeOffset ExpiresAt,
    [property: JsonPropertyName("artifactBytes")] long? ArtifactBytes,
    [property: JsonPropertyName("errorCode")] string? ErrorCode)
{
    public const string MediaType = "application/vnd.evidrilo.account-export-job.v2+json";

    public static AccountExportJobResponse From(AccountExportJob job) => new(
        "evidrilo.account-export-job",
        "2",
        job.ExportId.ToString("D"),
        job.Status,
        job.RequestId,
        job.CreatedAt,
        job.CompletedAt,
        job.ExpiresAt,
        job.ArtifactBytes,
        job.ErrorCode);
}

public static class AccountExportJobEndpoints
{
    private const string KeyErrorCode = "INVALID_IDEMPOTENCY_KEY";
    private const string DownloadFileName = "evidrilo-account-export-v2.json";

    public static IEndpointRouteBuilder MapAccountExportJobEndpoints(this IEndpointRouteBuilder endpoints)
    {
        endpoints.MapPost(
            "/v2/account/exports",
            async (HttpContext context, IAccountExportJobStore store, CancellationToken cancellationToken) =>
            {
                SetNoStore(context);
                if (!TryGetVerifiedAccount(context, out var accountId, out var failure))
                    return failure!;
                if (!TryReadIdempotencyKey(context, out var key))
                    return Error(context, KeyErrorCode, "A valid Idempotency-Key header is required.", StatusCodes.Status400BadRequest);

                var keyHash = Convert.ToHexString(SHA256.HashData(Encoding.UTF8.GetBytes(key))).ToLowerInvariant();
                try
                {
                    var job = await store.CreateOwnAsync(
                        accountId,
                        keyHash,
                        RequestIdMiddleware.Get(context),
                        cancellationToken);
                    return Results.Json(
                        AccountExportJobResponse.From(job),
                        statusCode: StatusCodes.Status202Accepted,
                        contentType: AccountExportJobResponse.MediaType);
                }
                catch (AccountExportCapacityException)
                {
                    return Error(
                        context,
                        "EXPORT_CAPACITY_LIMITED",
                        "Account export capacity is temporarily full.",
                        StatusCodes.Status429TooManyRequests);
                }
            })
            .RequireAuthorization()
            .RequireRateLimiting("account-export-create");

        endpoints.MapGet(
            "/v2/account/exports/{exportId:guid}",
            async (HttpContext context, Guid exportId, IAccountExportJobStore store, CancellationToken cancellationToken) =>
            {
                SetNoStore(context);
                if (!TryGetVerifiedAccount(context, out var accountId, out var failure))
                    return failure!;

                var job = await store.ReadOwnAsync(accountId, exportId, cancellationToken);
                if (job is null)
                    return NotFound(context);
                return Results.Json(
                    AccountExportJobResponse.From(job),
                    contentType: AccountExportJobResponse.MediaType);
            })
            .RequireAuthorization()
            .RequireRateLimiting("account-export-status");

        endpoints.MapGet(
            "/v2/account/exports/{exportId:guid}/download",
            async (HttpContext context, Guid exportId, IAccountExportJobStore store, CancellationToken cancellationToken) =>
            {
                SetNoStore(context);
                if (!TryGetVerifiedAccount(context, out var accountId, out var failure))
                    return failure!;

                var job = await store.ReadOwnAsync(accountId, exportId, cancellationToken);
                if (job is null)
                    return NotFound(context);
                if (job.Status is "queued" or "running")
                    return Error(context, "EXPORT_NOT_READY", "The account export is not ready to download.", StatusCodes.Status409Conflict);
                if (!string.Equals(job.Status, "ready", StringComparison.Ordinal))
                    return NotFound(context);

                var artifact = await store.OpenReadyArtifactOwnAsync(accountId, exportId, cancellationToken);
                if (artifact is null)
                    return NotFound(context);
                return new AccountExportDownloadResult(artifact, DownloadFileName);
            })
            .RequireAuthorization()
            .RequireRateLimiting("account-export-download");

        endpoints.MapDelete(
            "/v2/account/exports/{exportId:guid}",
            async (HttpContext context, Guid exportId, IAccountExportJobStore store, CancellationToken cancellationToken) =>
            {
                SetNoStore(context);
                if (!TryGetVerifiedAccount(context, out var accountId, out var failure))
                    return failure!;

                var job = await store.CancelOwnAsync(accountId, exportId, cancellationToken);
                if (job is null)
                    return NotFound(context);
                return Results.Json(
                    AccountExportJobResponse.From(job),
                    contentType: AccountExportJobResponse.MediaType);
            })
            .RequireAuthorization()
            .RequireRateLimiting("account-export-status");

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
            failure = Error(context, "AUTH_REQUIRED", "Authentication is required.", StatusCodes.Status401Unauthorized);
            return false;
        }

        if (!AuthenticatedUser.IsEmailVerified(context.User))
        {
            failure = Error(context, "FORBIDDEN", "A verified account is required to export account data.", StatusCodes.Status403Forbidden);
            return false;
        }

        return true;
    }

    private static bool TryReadIdempotencyKey(HttpContext context, out string key)
    {
        var values = context.Request.Headers["Idempotency-Key"];
        key = values.Count == 1 ? values[0] ?? string.Empty : string.Empty;
        return key.Length is >= 8 and <= 80
            && key.All(character =>
                character is >= 'A' and <= 'Z'
                    or >= 'a' and <= 'z'
                    or >= '0' and <= '9'
                    or '_' or '-');
    }

    private static IResult NotFound(HttpContext context) =>
        Error(context, "ACCOUNT_EXPORT_NOT_FOUND", "The account export was not found.", StatusCodes.Status404NotFound);

    private static IResult Error(HttpContext context, string code, string message, int statusCode) =>
        Results.Json(ApiErrors.Create(context, code, message), statusCode: statusCode);

    private static void SetNoStore(HttpContext context)
    {
        context.Response.Headers.CacheControl = "no-store";
        context.Response.Headers.Pragma = "no-cache";
        context.Response.Headers.Expires = "0";
    }
}

internal sealed class AccountExportDownloadResult(
    IAccountExportArtifactLease artifact,
    string fileName) : IResult
{
    public async Task ExecuteAsync(HttpContext httpContext)
    {
        httpContext.Response.Headers.CacheControl = "no-store";
        httpContext.Response.Headers.Pragma = "no-cache";
        httpContext.Response.Headers.Expires = "0";
        httpContext.Response.Headers.ContentDisposition = $"attachment; filename=\"{fileName}\"";
        httpContext.Response.ContentType = "application/vnd.evidrilo.account-export.v2+json";
        httpContext.Response.ContentLength = artifact.Length;
        try
        {
            await artifact.CopyToAsync(httpContext.Response.Body, httpContext.RequestAborted);
        }
        finally
        {
            await artifact.DisposeAsync();
        }
    }
}
