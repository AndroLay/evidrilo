using Evidrilo.Api.Common;

namespace Evidrilo.Api.Tests;

public sealed class RequestIdTests
{
    [Fact]
    public void Request_id_rejects_a_trailing_newline()
    {
        Assert.False(RequestIdMiddleware.IsValid("abc12345\n"));
    }

    [Fact]
    public void Idempotency_key_uses_the_same_bounded_safe_identifier_policy()
    {
        Assert.True(RequestIdMiddleware.IsValid("assist-20260921-001"));
        Assert.False(RequestIdMiddleware.IsValid("assist key"));
        Assert.False(RequestIdMiddleware.IsValid("short"));
        Assert.False(RequestIdMiddleware.IsValid(new string('a', 129)));
    }
}
