using System.Security.Claims;
using Evidrilo.Api.Common;
using Evidrilo.Api.Configuration;
using Microsoft.AspNetCore.Authentication;
using Microsoft.AspNetCore.Authentication.JwtBearer;
using Microsoft.IdentityModel.Tokens;

namespace Evidrilo.Api.Auth;

public static class AuthenticationSetup
{
    public const string Scheme = JwtBearerDefaults.AuthenticationScheme;

    public static IServiceCollection AddSupabaseAuthentication(
        this IServiceCollection services,
        PlatformOptions platformOptions)
    {
        services.AddAuthentication(options =>
            {
                options.DefaultAuthenticateScheme = Scheme;
                options.DefaultChallengeScheme = Scheme;
            })
            .AddJwtBearer(options => ConfigureJwt(options, platformOptions));

        services.AddAuthorization();
        return services;
    }

    private static void ConfigureJwt(JwtBearerOptions options, PlatformOptions platformOptions)
    {
        // Supabase claims are consumed by their wire names (notably `sub` and
        // the server-derived `email_verified` claim from migration 020).
        // Mapping them to legacy WS-Federation names would make ownership
        // checks depend on handler defaults.
        options.MapInboundClaims = false;
        if (platformOptions.SupabaseConfigured)
        {
            var authority = $"{platformOptions.SupabaseUrl!.TrimEnd('/')}/auth/v1";
            options.Authority = authority;
            options.MetadataAddress = $"{authority}/.well-known/openid-configuration";
            options.RequireHttpsMetadata = true;
            options.TokenValidationParameters = new TokenValidationParameters
            {
                ValidateIssuer = true,
                ValidIssuer = authority,
                ValidateAudience = true,
                ValidAudience = "authenticated",
                ValidateLifetime = true,
                ValidateIssuerSigningKey = true,
                NameClaimType = "sub",
                RoleClaimType = ClaimTypes.Role,
            };
        }

        options.Events = new JwtBearerEvents
        {
            OnMessageReceived = context =>
            {
                if (!platformOptions.SupabaseConfigured) context.NoResult();
                return Task.CompletedTask;
            },
            OnChallenge = async context =>
            {
                context.HandleResponse();
                await ApiErrors.WriteAsync(
                    context.HttpContext,
                    StatusCodes.Status401Unauthorized,
                    "AUTH_REQUIRED",
                    "Authentication is required.");
            },
            OnForbidden = async context =>
            {
                await ApiErrors.WriteAsync(
                    context.HttpContext,
                    StatusCodes.Status403Forbidden,
                    "FORBIDDEN",
                    "You are not allowed to perform this action.");
            },
        };
    }
}
