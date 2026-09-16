using System.Net;
using System.Net.Http.Json;
using System.Security.Claims;
using System.Text.Json;
using Evidrilo.Api.Account;
using Microsoft.AspNetCore.Authentication;
using Microsoft.AspNetCore.Hosting;
using Microsoft.AspNetCore.Http;
using Microsoft.AspNetCore.Mvc.Testing;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.DependencyInjection;

namespace Evidrilo.Api.Tests;

public sealed class AccountLifecycleAccessTests
{
    private static readonly Guid UserId = Guid.Parse("123e4567-e89b-42d3-a456-426614174000");

    [Theory]
    [InlineData(false, false, false)]
    [InlineData(true, false, true)]
    [InlineData(false, true, true)]
    [InlineData(true, true, true)]
    public void Completed_deletion_ledger_or_profile_tombstone_blocks_access(
        bool deletionRequestCompleted,
        bool profileTombstoned,
        bool expectedDeleted)
    {
        Assert.Equal(
            expectedDeleted,
            AccountDeletionStatus.IsDeleted(deletionRequestCompleted, profileTombstoned));
    }

    [Fact]
    public async Task Deleted_verified_accounts_cannot_use_protected_api_routes()
    {
        var nextCalls = 0;
        var middleware = new AccountLifecycleAccessMiddleware(_ =>
        {
            nextCalls++;
            return Task.CompletedTask;
        });
        var context = CreateContext(HttpMethods.Get, "/v1/progress/me", verified: true);

        await middleware.InvokeAsync(context, new FakeAccountLifecycleStore(deleted: true));

        Assert.Equal(StatusCodes.Status410Gone, context.Response.StatusCode);
        Assert.Equal(0, nextCalls);
        var body = await ReadBodyAsync(context);
        Assert.Equal("ACCOUNT_DELETED", body.GetProperty("code").GetString());
    }

    [Fact]
    public async Task Active_accounts_continue_through_the_guard()
    {
        var nextCalls = 0;
        var middleware = new AccountLifecycleAccessMiddleware(_ =>
        {
            nextCalls++;
            return Task.CompletedTask;
        });
        var context = CreateContext(HttpMethods.Get, "/v1/progress/me", verified: true);

        await middleware.InvokeAsync(context, new FakeAccountLifecycleStore(deleted: false));

        Assert.Equal(1, nextCalls);
        Assert.Equal(200, context.Response.StatusCode);
    }

    [Fact]
    public async Task Account_deletion_retry_remains_available_for_a_deleted_account()
    {
        var nextCalls = 0;
        var middleware = new AccountLifecycleAccessMiddleware(_ =>
        {
            nextCalls++;
            return Task.CompletedTask;
        });
        var context = CreateContext(HttpMethods.Delete, "/v1/account/me", verified: true);

        await middleware.InvokeAsync(context, new FakeAccountLifecycleStore(deleted: true));

        Assert.Equal(1, nextCalls);
    }

    [Fact]
    public async Task Unverified_accounts_keep_the_endpoint_owned_forbidden_response()
    {
        var nextCalls = 0;
        var middleware = new AccountLifecycleAccessMiddleware(_ =>
        {
            nextCalls++;
            return Task.CompletedTask;
        });
        var context = CreateContext(HttpMethods.Get, "/v1/progress/me", verified: false);

        await middleware.InvokeAsync(context, new FakeAccountLifecycleStore(deleted: true));

        Assert.Equal(1, nextCalls);
    }

    [Fact]
    public async Task Configured_api_pipeline_blocks_a_deleted_account_before_the_route_handler()
    {
        using var factory = new ConfiguredApiFactory();
        using var client = factory.CreateClient();
        using var request = new HttpRequestMessage(HttpMethod.Get, "/v1/account/me");
        request.Headers.Add("X-Test-User", $"{UserId}|true");

        using var response = await client.SendAsync(request);
        var body = await response.Content.ReadFromJsonAsync<JsonElement>();

        Assert.Equal(HttpStatusCode.Gone, response.StatusCode);
        Assert.Equal("ACCOUNT_DELETED", body.GetProperty("code").GetString());
    }

    private static DefaultHttpContext CreateContext(string method, string path, bool verified)
    {
        var context = new DefaultHttpContext();
        context.Request.Method = method;
        context.Request.Path = path;
        context.Response.Body = new MemoryStream();
        context.User = new ClaimsPrincipal(new ClaimsIdentity(
        [
            new Claim("sub", UserId.ToString()),
            new Claim("email_verified", verified ? "true" : "false"),
        ],
        "synthetic"));
        return context;
    }

    private static async Task<JsonElement> ReadBodyAsync(DefaultHttpContext context)
    {
        context.Response.Body.Position = 0;
        return (await JsonSerializer.DeserializeAsync<JsonElement>(context.Response.Body))!;
    }

    private sealed class FakeAccountLifecycleStore(bool deleted) : IAccountLifecycleStore
    {
        public Task<bool> IsDeletedAsync(Guid accountId, CancellationToken cancellationToken) =>
            Task.FromResult(deleted);

        public Task<AccountDeletionOperation> DeleteOwnAsync(
            Guid accountId,
            CancellationToken cancellationToken) =>
            Task.FromResult(new AccountDeletionOperation("already_completed"));
    }

    private sealed class ConfiguredApiFactory : WebApplicationFactory<Program>
    {
        protected override void ConfigureWebHost(IWebHostBuilder builder)
        {
            builder.UseEnvironment("Testing");
            builder.UseSetting("Platform:DatabaseConnectionString", "Host=synthetic.invalid;Database=evidrilo");
            builder.ConfigureAppConfiguration((_, configuration) =>
            {
                configuration.AddInMemoryCollection(new Dictionary<string, string?>
                {
                    ["Platform:Environment"] = "Testing",
                    ["Platform:SupabaseUrl"] = "",
                    ["Platform:SupabasePublishableKey"] = "",
                    ["Platform:CorsAllowedOrigins"] = "http://localhost:3000",
                });
            });
            builder.ConfigureServices(services =>
            {
                services.AddAuthentication(options =>
                {
                    options.DefaultAuthenticateScheme = TestAuthenticationHandler.TestScheme;
                    options.DefaultChallengeScheme = TestAuthenticationHandler.TestScheme;
                }).AddScheme<AuthenticationSchemeOptions, TestAuthenticationHandler>(
                    TestAuthenticationHandler.TestScheme,
                    _ => { });
                services.AddSingleton<IAccountLifecycleStore>(new FakeAccountLifecycleStore(deleted: true));
            });
        }
    }
}
