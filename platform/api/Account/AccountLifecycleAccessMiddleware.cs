using Evidrilo.Api.Auth;
using Evidrilo.Api.Common;

namespace Evidrilo.Api.Account;

/// <summary>
/// Prevents a still-valid access token from using platform routes after the
/// server-owned account deletion transaction has tombstoned the account.
/// The deletion endpoint itself remains retryable so its idempotent outcome can
/// be observed by a client that lost its response.
/// </summary>
public sealed class AccountLifecycleAccessMiddleware
{
    private const string AccountPath = "/v1/account/me";
    private readonly RequestDelegate next;

    public AccountLifecycleAccessMiddleware(RequestDelegate next)
    {
        this.next = next;
    }

    public async Task InvokeAsync(
        HttpContext context,
        IAccountLifecycleStore store)
    {
        if (ShouldBypass(context)
            || !AuthenticatedUser.TryGetAccountId(context.User, out var accountId)
            || !AuthenticatedUser.IsEmailVerified(context.User))
        {
            await next(context);
            return;
        }

        if (await store.IsDeletedAsync(accountId, context.RequestAborted))
        {
            await ApiErrors.WriteAsync(
                context,
                StatusCodes.Status410Gone,
                "ACCOUNT_DELETED",
                "This account has been deleted.",
                context.RequestAborted);
            return;
        }

        await next(context);
    }

    private static bool ShouldBypass(HttpContext context) =>
        HttpMethods.IsDelete(context.Request.Method)
        && context.Request.Path.Equals(AccountPath, StringComparison.OrdinalIgnoreCase);
}
