using System.Security.Claims;
using System.Text.Encodings.Web;
using Evidrilo.Api;
using Evidrilo.Api.Common;
using Microsoft.AspNetCore.Authentication;
using Microsoft.AspNetCore.Hosting;
using Microsoft.AspNetCore.Http;
using Microsoft.AspNetCore.Mvc.Testing;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Logging;
using Microsoft.Extensions.Options;

namespace Evidrilo.Api.Tests;

public sealed class ApiFactory : WebApplicationFactory<Program>
{
    protected override void ConfigureWebHost(IWebHostBuilder builder)
    {
        builder.UseEnvironment("Testing");
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
        });
    }
}

public sealed class TestAuthenticationHandler : AuthenticationHandler<AuthenticationSchemeOptions>
{
    public const string TestScheme = "LocalTest";

    public TestAuthenticationHandler(
        IOptionsMonitor<AuthenticationSchemeOptions> options,
        ILoggerFactory logger,
        UrlEncoder encoder)
        : base(options, logger, encoder)
    {
    }

    protected override Task<AuthenticateResult> HandleAuthenticateAsync()
    {
        var value = Request.Headers["X-Test-User"].FirstOrDefault();
        if (string.IsNullOrWhiteSpace(value)) return Task.FromResult(AuthenticateResult.NoResult());

        var segments = value.Split('|', StringSplitOptions.TrimEntries);
        if (segments.Length != 2 || !Guid.TryParse(segments[0], out var id))
        {
            return Task.FromResult(AuthenticateResult.Fail("Invalid synthetic identity."));
        }

        var claims = new List<Claim>
        {
            new("sub", id.ToString()),
            new(ClaimTypes.NameIdentifier, id.ToString()),
            new("email_verified", segments[1]),
        };
        var identity = new ClaimsIdentity(claims, TestScheme);
        var principal = new ClaimsPrincipal(identity);
        var ticket = new AuthenticationTicket(principal, TestScheme);
        return Task.FromResult(AuthenticateResult.Success(ticket));
    }

    protected override Task HandleChallengeAsync(AuthenticationProperties properties)
    {
        return ApiErrors.WriteAsync(
            Context,
            StatusCodes.Status401Unauthorized,
            "AUTH_REQUIRED",
            "Authentication is required.");
    }

    protected override Task HandleForbiddenAsync(AuthenticationProperties properties)
    {
        return ApiErrors.WriteAsync(
            Context,
            StatusCodes.Status403Forbidden,
            "FORBIDDEN",
            "You are not allowed to perform this action.");
    }
}
