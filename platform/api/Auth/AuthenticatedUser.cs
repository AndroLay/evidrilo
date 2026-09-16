using System.Security.Claims;

namespace Evidrilo.Api.Auth;

public static class AuthenticatedUser
{
    public static bool TryGetAccountId(ClaimsPrincipal principal, out Guid accountId)
    {
        accountId = Guid.Empty;
        if (principal.Identity?.IsAuthenticated != true) return false;
        return Guid.TryParse(principal.FindFirstValue("sub"), out accountId)
            && accountId != Guid.Empty;
    }

    public static bool IsEmailVerified(ClaimsPrincipal principal) =>
        IsTrue(principal.FindFirstValue("email_verified"));

    public static bool TryGetVerifiedAccountId(ClaimsPrincipal principal, out Guid accountId)
    {
        return TryGetAccountId(principal, out accountId) && IsEmailVerified(principal);
    }

    private static bool IsTrue(string? value) =>
        string.Equals(value, "true", StringComparison.OrdinalIgnoreCase) || value == "1";
}
