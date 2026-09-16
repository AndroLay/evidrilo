using Evidrilo.Api.Configuration;
using Evidrilo.Api.Auth;
using Microsoft.AspNetCore.Authentication.JwtBearer;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Options;

namespace Evidrilo.Api.Tests;

public sealed class ConfigurationTests
{
    [Fact]
    public void Invalid_port_is_rejected()
    {
        var configuration = BuildConfiguration(("Platform:Port", "70000"));

        Assert.Throws<PlatformConfigurationException>(() => PlatformOptions.From(configuration, "Testing"));
    }

    [Fact]
    public void Production_wildcard_cors_is_rejected()
    {
        var configuration = BuildConfiguration(("Platform:CorsAllowedOrigins", "*"));

        var exception = Assert.Throws<PlatformConfigurationException>(() => PlatformOptions.From(configuration, "Production"));
        Assert.Contains("CORS", exception.Message, StringComparison.Ordinal);
    }

    [Fact]
    public void Production_http_cors_origin_is_rejected()
    {
        var configuration = BuildConfiguration(
            ("Platform:CorsAllowedOrigins", "http://app.example"),
            ("Platform:SupabaseUrl", "https://example.supabase.co"),
            ("Platform:SupabasePublishableKey", "synthetic-public-key"),
            ("Platform:DatabaseConnectionString", "Host=example.invalid;Database=evidrilo"));

        var exception = Assert.Throws<PlatformConfigurationException>(
            () => PlatformOptions.From(configuration, "Production"));

        Assert.Contains("HTTPS", exception.Message, StringComparison.Ordinal);
    }

    [Fact]
    public void Production_cors_origin_with_path_or_query_is_rejected()
    {
        var configuration = BuildConfiguration(
            ("Platform:CorsAllowedOrigins", "https://app.example/path?query=1"),
            ("Platform:SupabaseUrl", "https://example.supabase.co"),
            ("Platform:SupabasePublishableKey", "synthetic-public-key"),
            ("Platform:DatabaseConnectionString", "Host=example.invalid;Database=evidrilo"));

        var exception = Assert.Throws<PlatformConfigurationException>(
            () => PlatformOptions.From(configuration, "Production"));

        Assert.Contains("origin", exception.Message, StringComparison.OrdinalIgnoreCase);
    }

    [Fact]
    public void Production_requires_explicit_cors_origins()
    {
        var configuration = BuildConfiguration(
            ("Platform:SupabaseUrl", "https://example.supabase.co"),
            ("Platform:SupabasePublishableKey", "synthetic-public-key"),
            ("Platform:DatabaseConnectionString", "Host=example.invalid;Database=evidrilo"));

        var exception = Assert.Throws<PlatformConfigurationException>(
            () => PlatformOptions.From(configuration, "Production"));

        Assert.Contains("CORS_ALLOWED_ORIGINS", exception.Message, StringComparison.Ordinal);
    }

    [Fact]
    public void Local_configuration_reports_degraded_auth_without_secrets()
    {
        var configuration = BuildConfiguration();
        var options = PlatformOptions.From(configuration, "Testing");

        Assert.False(options.SupabaseConfigured);
        Assert.Equal("missing", options.Readiness.Config);
        Assert.Equal("missing", options.Readiness.Auth);
        Assert.Equal("missing", options.Readiness.Database);
        Assert.False(options.DatabaseConfigured);
        Assert.DoesNotContain("example.supabase.co", options.ToSafeString(), StringComparison.Ordinal);
    }

    [Fact]
    public void Staging_environment_is_supported()
    {
        var options = PlatformOptions.From(
            BuildConfiguration(("Platform:CorsAllowedOrigins", "https://staging.example")),
            "Staging");

        Assert.Equal("staging", options.Environment);
    }

    [Fact]
    public void Staging_requires_explicit_cors_origins()
    {
        var exception = Assert.Throws<PlatformConfigurationException>(
            () => PlatformOptions.From(BuildConfiguration(), "Staging"));

        Assert.Contains("CORS_ALLOWED_ORIGINS", exception.Message, StringComparison.Ordinal);
    }

    [Fact]
    public void Production_configuration_fails_closed_when_supabase_is_missing()
    {
        var configuration = BuildConfiguration();
        Assert.Throws<PlatformConfigurationException>(() => PlatformOptions.From(configuration, "Production"));
    }

    [Fact]
    public void Safe_summary_does_not_include_configuration_values()
    {
        var configuration = BuildConfiguration(
            ("Platform:SupabaseUrl", "https://example.supabase.co"),
            ("Platform:SupabasePublishableKey", "synthetic-public-key"));
        var options = PlatformOptions.From(configuration, "Testing");

        Assert.DoesNotContain("example.supabase.co", options.ToSafeString(), StringComparison.Ordinal);
        Assert.DoesNotContain("synthetic-public-key", options.ToSafeString(), StringComparison.Ordinal);
    }

    [Fact]
    public void Invalid_trusted_proxy_address_is_rejected()
    {
        var configuration = BuildConfiguration(
            ("Platform:TrustedProxyAddresses", "not-an-ip"));

        var exception = Assert.Throws<PlatformConfigurationException>(
            () => PlatformOptions.From(configuration, "Testing"));

        Assert.Contains("TRUSTED_PROXY_ADDRESSES", exception.Message, StringComparison.Ordinal);
    }

    [Fact]
    public void Unspecified_trusted_proxy_address_is_rejected()
    {
        var configuration = BuildConfiguration(("Platform:TrustedProxyAddresses", "0.0.0.0"));

        var exception = Assert.Throws<PlatformConfigurationException>(
            () => PlatformOptions.From(configuration, "Testing"));

        Assert.Contains("TRUSTED_PROXY_ADDRESSES", exception.Message, StringComparison.Ordinal);
    }

    [Fact]
    public void Trusted_proxy_addresses_are_explicit_and_empty_by_default()
    {
        var local = PlatformOptions.From(BuildConfiguration(), "Testing");
        var configured = PlatformOptions.From(
            BuildConfiguration(("Platform:TrustedProxyAddresses", "127.0.0.1, ::1")),
            "Testing");

        Assert.Empty(local.TrustedProxyAddresses);
        Assert.Equal(2, configured.TrustedProxyAddresses.Count);
    }

    [Fact]
    public void Billing_is_configured_only_when_authentication_and_entitlement_are_present()
    {
        var secretOnly = PlatformOptions.From(
            BuildConfiguration(("Platform:RevenueCatWebhookSecret", "synthetic-secret")),
            "Testing");
        var authorizationOnly = PlatformOptions.From(
            BuildConfiguration(("Platform:RevenueCatWebhookAuthorization", "Bearer synthetic")),
            "Testing");
        var complete = PlatformOptions.From(
            BuildConfiguration(
                ("Platform:RevenueCatWebhookSecret", "synthetic-secret"),
                ("Platform:RevenueCatEntitlementId", "evidrilo_pro")),
            "Testing");

        Assert.False(secretOnly.BillingConfigured);
        Assert.False(authorizationOnly.BillingConfigured);
        Assert.True(complete.BillingConfigured);
    }

    [Fact]
    public void Billing_rejects_a_noncanonical_entitlement_identifier()
    {
        var options = PlatformOptions.From(
            BuildConfiguration(
                ("Platform:RevenueCatWebhookSecret", "synthetic-secret"),
                ("Platform:RevenueCatEntitlementId", "another_entitlement")),
            "Testing");

        Assert.False(options.BillingConfigured);
    }

    [Fact]
    public void Supabase_claim_names_are_not_remapped_by_the_jwt_handler()
    {
        var services = new ServiceCollection();
        var options = PlatformOptions.From(
            BuildConfiguration(
                ("Platform:SupabaseUrl", "https://example.supabase.co"),
                ("Platform:SupabasePublishableKey", "synthetic-public-key")),
            "Testing");

        services.AddSupabaseAuthentication(options);
        using var provider = services.BuildServiceProvider();
        var jwtOptions = provider
            .GetRequiredService<IOptionsMonitor<JwtBearerOptions>>()
            .Get(AuthenticationSetup.Scheme);

        Assert.False(jwtOptions.MapInboundClaims);
    }

    private static IConfiguration BuildConfiguration(params (string Key, string Value)[] values)
    {
        return new ConfigurationBuilder()
            .AddInMemoryCollection(values.Select(value => new KeyValuePair<string, string?>(value.Key, value.Value)))
            .Build();
    }
}
