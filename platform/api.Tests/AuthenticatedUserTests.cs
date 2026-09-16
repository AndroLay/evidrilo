using System.Security.Claims;
using Evidrilo.Api.Auth;

namespace Evidrilo.Api.Tests;

public sealed class AuthenticatedUserTests
{
    [Fact]
    public void Empty_subject_is_not_an_account_identity()
    {
        var principal = new ClaimsPrincipal(new ClaimsIdentity(
            [new Claim("sub", Guid.Empty.ToString())],
            "synthetic"));

        Assert.False(AuthenticatedUser.TryGetAccountId(principal, out _));
    }
}
