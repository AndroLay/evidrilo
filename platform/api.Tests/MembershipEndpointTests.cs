using System.Net;
using System.Net.Http.Json;
using System.Text.Json;

namespace Evidrilo.Api.Tests;

public sealed class MembershipEndpointTests : IClassFixture<ApiFactory>
{
    private static readonly Guid UserId = Guid.Parse("123e4567-e89b-42d3-a456-426614174000");
    private static readonly Guid OrganizationId = Guid.Parse("123e4567-e89b-42d3-a456-426614174020");
    private static readonly Guid TargetAccountId = Guid.Parse("123e4567-e89b-42d3-a456-426614174021");
    private readonly HttpClient client;

    public MembershipEndpointTests(ApiFactory factory)
    {
        client = factory.CreateClient();
    }

    [Fact]
    public async Task Membership_grant_requires_authenticated_identity()
    {
        using var response = await client.PostAsJsonAsync(
            $"/v1/organizations/{OrganizationId}/members",
            ValidCommand());
        var body = await response.Content.ReadFromJsonAsync<JsonElement>();

        Assert.Equal(HttpStatusCode.Unauthorized, response.StatusCode);
        Assert.Equal("AUTH_REQUIRED", body.GetProperty("code").GetString());
    }

    [Fact]
    public async Task Membership_grant_requires_verified_identity()
    {
        using var request = new HttpRequestMessage(
            HttpMethod.Post,
            $"/v1/organizations/{OrganizationId}/members")
        {
            Content = JsonContent.Create(ValidCommand()),
        };
        request.Headers.Add("X-Test-User", $"{UserId}|false");

        using var response = await client.SendAsync(request);
        var body = await response.Content.ReadFromJsonAsync<JsonElement>();

        Assert.Equal(HttpStatusCode.Forbidden, response.StatusCode);
        Assert.Equal("FORBIDDEN", body.GetProperty("code").GetString());
    }

    [Fact]
    public async Task Membership_grant_rejects_a_command_without_a_role()
    {
        using var request = new HttpRequestMessage(
            HttpMethod.Post,
            $"/v1/organizations/{OrganizationId}/members")
        {
            Content = JsonContent.Create(new
            {
                schema = "evidrilo.membership-command",
                version = "1",
                targetAccountId = TargetAccountId,
                reason = "Synthetic organization setup",
            }),
        };
        request.Headers.Add("X-Test-User", $"{UserId}|true");

        using var response = await client.SendAsync(request);
        var body = await response.Content.ReadFromJsonAsync<JsonElement>();

        Assert.Equal(HttpStatusCode.BadRequest, response.StatusCode);
        Assert.Equal("INVALID_MEMBERSHIP_COMMAND", body.GetProperty("code").GetString());
    }

    [Fact]
    public async Task Membership_grant_fails_closed_without_database()
    {
        using var request = new HttpRequestMessage(
            HttpMethod.Post,
            $"/v1/organizations/{OrganizationId}/members")
        {
            Content = JsonContent.Create(ValidCommand()),
        };
        request.Headers.Add("X-Test-User", $"{UserId}|true");

        using var response = await client.SendAsync(request);
        var body = await response.Content.ReadFromJsonAsync<JsonElement>();

        Assert.Equal(HttpStatusCode.ServiceUnavailable, response.StatusCode);
        Assert.Equal("DATABASE_NOT_CONFIGURED", body.GetProperty("code").GetString());
    }

    [Fact]
    public async Task Membership_leave_fails_closed_without_database()
    {
        using var request = new HttpRequestMessage(
            HttpMethod.Delete,
            $"/v1/organizations/{OrganizationId}/members/me");
        request.Headers.Add("X-Test-User", $"{UserId}|true");

        using var response = await client.SendAsync(request);
        var body = await response.Content.ReadFromJsonAsync<JsonElement>();

        Assert.Equal(HttpStatusCode.ServiceUnavailable, response.StatusCode);
        Assert.Equal("DATABASE_NOT_CONFIGURED", body.GetProperty("code").GetString());
    }

    private static object ValidCommand() => new
    {
        schema = "evidrilo.membership-command",
        version = "1",
        targetAccountId = TargetAccountId,
        role = "teacher",
        reason = "Synthetic organization setup",
    };
}
