using System.IdentityModel.Tokens.Jwt;
using System.Security.Claims;
using System.Security.Cryptography;
using System.Text.Json;
using Evidrilo.Api.Auth;
using Evidrilo.Api.Common;
using Evidrilo.Api.Configuration;
using Microsoft.AspNetCore.Authentication.JwtBearer;
using Microsoft.AspNetCore.Builder;
using Microsoft.AspNetCore.Hosting;
using Microsoft.AspNetCore.Http;
using Microsoft.AspNetCore.TestHost;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.IdentityModel.Protocols;
using Microsoft.IdentityModel.Protocols.OpenIdConnect;
using Microsoft.IdentityModel.Tokens;

namespace Evidrilo.Api.Tests;

public sealed class SupabaseJwtValidationTests : IAsyncLifetime
{
    private const string Issuer = "https://local.supabase.test/auth/v1";
    private readonly RSA rsa = RSA.Create(2048);
    private readonly RsaSecurityKey signingKey;
    private WebApplication? app;
    private HttpClient? client;

    private Guid AccountId { get; } = Guid.Parse("2d86853d-6163-4f8f-8097-37c2a115ec3b");

    public SupabaseJwtValidationTests()
    {
        signingKey = new RsaSecurityKey(rsa) { KeyId = "local-synthetic-key" };
    }

    public async Task InitializeAsync()
    {
        var configuration = new ConfigurationBuilder()
            .AddInMemoryCollection(new Dictionary<string, string?>
            {
                ["Platform:Environment"] = "Testing",
                ["Platform:SupabaseUrl"] = "https://local.supabase.test",
                ["Platform:SupabasePublishableKey"] = "synthetic-publishable-key",
                ["Platform:CorsAllowedOrigins"] = "http://localhost:3000",
                ["AI_PROVIDER_ENABLED"] = "false",
            })
            .Build();
        var platformOptions = PlatformOptions.From(configuration, "Testing");

        var builder = WebApplication.CreateBuilder();
        builder.WebHost.UseTestServer();
        builder.Services.AddSingleton(platformOptions);
        builder.Services.AddSupabaseAuthentication(platformOptions);
        builder.Services.PostConfigure<JwtBearerOptions>(JwtBearerDefaults.AuthenticationScheme, options =>
        {
            options.ConfigurationManager = new StaticConfigurationManager<OpenIdConnectConfiguration>(
                new OpenIdConnectConfiguration
                {
                    Issuer = Issuer,
                    SigningKeys = { signingKey },
                });
        });

        app = builder.Build();
        app.UseMiddleware<RequestIdMiddleware>();
        app.UseAuthentication();
        app.UseAuthorization();
        app.MapGet("/v1/account/me", (HttpContext context) =>
        {
            var accountId = context.User.FindFirstValue("sub");
            return Results.Ok(new { accountId });
        }).RequireAuthorization();
        await app.StartAsync();
        client = app.GetTestClient();
    }

    public async Task DisposeAsync()
    {
        client?.Dispose();
        if (app is not null) await app.DisposeAsync();
        rsa.Dispose();
    }

    [Fact]
    public async Task Valid_supabase_shaped_jwt_authenticates_through_the_real_bearer_pipeline()
    {
        using var response = await SendTokenAsync(CreateToken());
        using var body = JsonDocument.Parse(await response.Content.ReadAsStringAsync());

        Assert.Equal(System.Net.HttpStatusCode.OK, response.StatusCode);
        Assert.Equal(AccountId.ToString(), body.RootElement.GetProperty("accountId").GetString());
    }

    [Theory]
    [InlineData("issuer")]
    [InlineData("audience")]
    [InlineData("expired")]
    [InlineData("signature")]
    public async Task Invalid_issuer_audience_lifetime_or_signature_is_rejected(string invalidPart)
    {
        using var response = await SendTokenAsync(CreateToken(invalidPart));
        using var body = JsonDocument.Parse(await response.Content.ReadAsStringAsync());

        Assert.Equal(System.Net.HttpStatusCode.Unauthorized, response.StatusCode);
        Assert.Equal("AUTH_REQUIRED", body.RootElement.GetProperty("code").GetString());
    }

    private async Task<HttpResponseMessage> SendTokenAsync(string token)
    {
        using var request = new HttpRequestMessage(HttpMethod.Get, "/v1/account/me");
        request.Headers.Authorization = new System.Net.Http.Headers.AuthenticationHeaderValue("Bearer", token);
        return await client!.SendAsync(request);
    }

    private string CreateToken(string invalidPart = "")
    {
        var issuer = invalidPart == "issuer" ? "https://wrong.local.test/auth/v1" : Issuer;
        var audience = invalidPart == "audience" ? "wrong-audience" : "authenticated";
        var notBefore = invalidPart == "expired"
            ? DateTime.UtcNow.AddMinutes(-10)
            : DateTime.UtcNow.AddMinutes(-1);
        var expires = invalidPart == "expired"
            ? DateTime.UtcNow.AddMinutes(-5)
            : DateTime.UtcNow.AddMinutes(5);
        var key = signingKey;
        if (invalidPart == "signature")
        {
            using var otherRsa = RSA.Create(2048);
            key = new RsaSecurityKey(otherRsa) { KeyId = "different-local-key" };
            return SignToken(issuer, audience, notBefore, expires, key);
        }

        return SignToken(issuer, audience, notBefore, expires, key);
    }

    private string SignToken(string issuer, string audience, DateTime notBefore, DateTime expires, SecurityKey key)
    {
        var token = new JwtSecurityToken(
            issuer,
            audience,
            [
                new Claim("sub", AccountId.ToString()),
                new Claim("email_verified", "true"),
            ],
            notBefore,
            expires,
            new SigningCredentials(key, SecurityAlgorithms.RsaSha256));
        return new JwtSecurityTokenHandler().WriteToken(token);
    }
}
