using Evidrilo.Api.Common;

namespace Evidrilo.Api.Tests;

public sealed class RequestIdTests
{
    [Fact]
    public void Request_id_rejects_a_trailing_newline()
    {
        Assert.False(RequestIdMiddleware.IsValid("abc12345\n"));
    }
}
