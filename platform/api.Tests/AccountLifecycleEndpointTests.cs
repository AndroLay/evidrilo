using System.Net;
using System.Net.Http.Json;
using System.Text.Json;

namespace Evidrilo.Api.Tests;

public sealed class AccountLifecycleEndpointTests : IClassFixture<ApiFactory>
{
    private static readonly Guid UserId = Guid.Parse("123e4567-e89b-42d3-a456-426614174000");
    private readonly HttpClient client;

    public AccountLifecycleEndpointTests(ApiFactory factory)
    {
        client = factory.CreateClient();
    }

    [Fact]
    public async Task Account_deletion_requires_authenticated_identity()
    {
        using var response = await client.DeleteAsync("/v1/account/me");
        var body = await response.Content.ReadFromJsonAsync<JsonElement>();

        Assert.Equal(HttpStatusCode.Unauthorized, response.StatusCode);
        Assert.Equal("AUTH_REQUIRED", body.GetProperty("code").GetString());
    }

    [Fact]
    public async Task Account_deletion_requires_verified_identity()
    {
        using var request = new HttpRequestMessage(HttpMethod.Delete, "/v1/account/me");
        request.Headers.Add("X-Test-User", $"{UserId}|false");
        request.Headers.Add("X-Account-Deletion-Confirm", "delete-my-account");

        using var response = await client.SendAsync(request);
        var body = await response.Content.ReadFromJsonAsync<JsonElement>();

        Assert.Equal(HttpStatusCode.Forbidden, response.StatusCode);
        Assert.Equal("FORBIDDEN", body.GetProperty("code").GetString());
    }

    [Fact]
    public async Task Account_deletion_requires_explicit_confirmation()
    {
        using var request = new HttpRequestMessage(HttpMethod.Delete, "/v1/account/me");
        request.Headers.Add("X-Test-User", $"{UserId}|true");

        using var response = await client.SendAsync(request);
        var body = await response.Content.ReadFromJsonAsync<JsonElement>();

        Assert.Equal(HttpStatusCode.BadRequest, response.StatusCode);
        Assert.Equal("ACCOUNT_DELETION_CONFIRMATION_REQUIRED", body.GetProperty("code").GetString());
    }

    [Fact]
    public async Task Account_deletion_fails_closed_when_database_is_not_configured()
    {
        using var request = new HttpRequestMessage(HttpMethod.Delete, "/v1/account/me");
        request.Headers.Add("X-Test-User", $"{UserId}|true");
        request.Headers.Add("X-Account-Deletion-Confirm", "delete-my-account");

        using var response = await client.SendAsync(request);
        var body = await response.Content.ReadFromJsonAsync<JsonElement>();

        Assert.Equal(HttpStatusCode.ServiceUnavailable, response.StatusCode);
        Assert.Equal("DATABASE_NOT_CONFIGURED", body.GetProperty("code").GetString());
    }
}
