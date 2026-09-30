using Evidrilo.Api.Configuration;
using Evidrilo.Api.Auth;
using Microsoft.AspNetCore.Authentication.JwtBearer;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Options;
using Npgsql;

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
            BuildConfiguration(
                ("Platform:CorsAllowedOrigins", "https://staging.example"),
                ("Platform:SupabaseUrl", "https://example.supabase.co"),
                ("Platform:SupabasePublishableKey", "synthetic-public-key"),
                ("Platform:DatabaseConnectionString", "Host=example.invalid;Database=evidrilo;Username=student;SSL Mode=Require"),
                ("Platform:RevenueCatWebhookSecret", "synthetic-secret"),
                ("Platform:RevenueCatEntitlementId", "evidrilo_pro"),
                ("Platform:RevenueCatMonthlyProductId", "com.example.monthly"),
                ("Platform:RevenueCatYearlyProductId", "com.example.yearly")),
            "Staging");

        Assert.Equal("staging", options.Environment);
        Assert.True(options.BillingConfigured);
    }

    [Fact]
    public void Staging_requires_explicit_cors_origins()
    {
        var exception = Assert.Throws<PlatformConfigurationException>(
            () => PlatformOptions.From(BuildConfiguration(), "Staging"));

        Assert.Contains("CORS_ALLOWED_ORIGINS", exception.Message, StringComparison.Ordinal);
    }

    [Fact]
    public void Staging_configuration_fails_closed_when_supabase_or_database_is_missing()
    {
        var exception = Assert.Throws<PlatformConfigurationException>(() => PlatformOptions.From(
            BuildConfiguration(("Platform:CorsAllowedOrigins", "https://staging.example")),
            "Staging"));

        Assert.Contains("Required platform configuration", exception.Message, StringComparison.Ordinal);
    }

    [Theory]
    [InlineData("Staging")]
    [InlineData("Production")]
    public void Deployed_configuration_can_leave_revenuecat_entirely_disabled(string environment)
    {
        var options = PlatformOptions.From(
            BuildConfiguration(
                ("Platform:CorsAllowedOrigins", "https://app.example"),
                ("Platform:SupabaseUrl", "https://example.supabase.co"),
                ("Platform:SupabasePublishableKey", "synthetic-public-key"),
                ("Platform:DatabaseConnectionString", "Host=example.invalid;Database=evidrilo;Username=student;SSL Mode=Require")),
            environment);

        Assert.False(options.BillingConfigured);
        Assert.False(options.BillingConfigurationRequested);
    }

    [Fact]
    public void Production_configuration_fails_closed_when_supabase_is_missing()
    {
        var configuration = BuildConfiguration();
        Assert.Throws<PlatformConfigurationException>(() => PlatformOptions.From(configuration, "Production"));
    }

    [Fact]
    public void Deployed_postgresql_uri_is_normalized_and_defaults_to_encrypted_transport()
    {
        var options = PlatformOptions.From(
            BuildConfiguration(
                ("Platform:CorsAllowedOrigins", "https://app.example"),
                ("Platform:SupabaseUrl", "https://example.supabase.co"),
                ("Platform:SupabasePublishableKey", "synthetic-public-key"),
                ("Platform:DatabaseConnectionString", "postgresql://student:pass%40word%3A123+plus@db.example.invalid:5432/evidrilo")),
            "Production");

        var parsed = new NpgsqlConnectionStringBuilder(options.DatabaseConnectionString);

        Assert.Equal("db.example.invalid", parsed.Host);
        Assert.Equal("student", parsed.Username);
        Assert.Equal("pass@word:123+plus", parsed.Password);
        Assert.Equal("evidrilo", parsed.Database);
        Assert.Equal(SslMode.Require, parsed.SslMode);
    }

    [Theory]
    [InlineData("Disable")]
    [InlineData("Allow")]
    [InlineData("Prefer")]
    public void Deployed_api_rejects_database_transport_that_can_be_plaintext(string sslMode)
    {
        var configuration = BuildConfiguration(
            ("Platform:CorsAllowedOrigins", "https://app.example"),
            ("Platform:SupabaseUrl", "https://example.supabase.co"),
            ("Platform:SupabasePublishableKey", "synthetic-public-key"),
            ("Platform:DatabaseConnectionString", $"Host=db.example.invalid;Database=evidrilo;Username=student;SSL Mode={sslMode}"));

        var exception = Assert.Throws<PlatformConfigurationException>(
            () => PlatformOptions.From(configuration, "Production"));

        Assert.DoesNotContain("student", exception.Message, StringComparison.Ordinal);
    }

    [Fact]
    public void Deployed_api_rejects_connection_string_without_explicit_username()
    {
        var configuration = BuildConfiguration(
            ("Platform:CorsAllowedOrigins", "https://app.example"),
            ("Platform:SupabaseUrl", "https://example.supabase.co"),
            ("Platform:SupabasePublishableKey", "synthetic-public-key"),
            ("Platform:DatabaseConnectionString", "Host=db.example.invalid;Database=evidrilo;SSL Mode=Require"));

        var exception = Assert.Throws<PlatformConfigurationException>(
            () => PlatformOptions.From(configuration, "Production"));

        Assert.DoesNotContain("db.example.invalid", exception.Message, StringComparison.Ordinal);
    }

    [Fact]
    public void Deployed_api_rejects_a_uri_that_explicitly_allows_plaintext_fallback()
    {
        var configuration = BuildConfiguration(
            ("Platform:CorsAllowedOrigins", "https://app.example"),
            ("Platform:SupabaseUrl", "https://example.supabase.co"),
            ("Platform:SupabasePublishableKey", "synthetic-public-key"),
            ("Platform:DatabaseConnectionString", "postgresql://student:synthetic-password@db.example.invalid/evidrilo?sslmode=prefer"));

        var exception = Assert.Throws<PlatformConfigurationException>(
            () => PlatformOptions.From(configuration, "Production"));

        Assert.DoesNotContain("synthetic-password", exception.Message, StringComparison.Ordinal);
    }

    [Fact]
    public void Invalid_database_configuration_does_not_echo_connection_secrets()
    {
        var configuration = BuildConfiguration(
            ("Platform:DatabaseConnectionString", "Host=db.example.invalid;Database=evidrilo;Username=student;Password=synthetic-password;UnknownOption=value"));

        var exception = Assert.Throws<PlatformConfigurationException>(
            () => PlatformOptions.From(configuration, "Testing"));

        Assert.DoesNotContain("synthetic-password", exception.Message, StringComparison.Ordinal);
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
        Assert.True(secretOnly.BillingConfigurationRequested);
        Assert.False(authorizationOnly.BillingConfigured);
        Assert.True(authorizationOnly.BillingConfigurationRequested);
        Assert.True(complete.BillingConfigured);
        Assert.True(complete.BillingConfigurationRequested);
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
        Assert.True(options.BillingConfigurationRequested);
    }

    [Fact]
    public void Production_billing_requires_explicit_provider_product_ids()
    {
        var configuration = BuildConfiguration(
            ("Platform:CorsAllowedOrigins", "https://app.example"),
            ("Platform:SupabaseUrl", "https://example.supabase.co"),
            ("Platform:SupabasePublishableKey", "synthetic-public-key"),
            ("Platform:DatabaseConnectionString", "Host=example.invalid;Database=evidrilo;Username=student;SSL Mode=Require"),
            ("Platform:RevenueCatWebhookSecret", "synthetic-secret"),
            ("Platform:RevenueCatEntitlementId", "evidrilo_pro"));

        var exception = Assert.Throws<PlatformConfigurationException>(
            () => PlatformOptions.From(configuration, "Production"));

        Assert.Contains("RevenueCat", exception.Message, StringComparison.Ordinal);
    }

    [Theory]
    [InlineData("Staging")]
    [InlineData("Production")]
    public void Deployed_configuration_fails_closed_when_billing_webhook_authentication_is_missing(
        string environment)
    {
        var configuration = BuildConfiguration(
            ("Platform:CorsAllowedOrigins", "https://app.example"),
            ("Platform:SupabaseUrl", "https://example.supabase.co"),
            ("Platform:SupabasePublishableKey", "synthetic-public-key"),
            ("Platform:DatabaseConnectionString", "Host=example.invalid;Database=evidrilo;Username=student;SSL Mode=Require"),
            ("Platform:RevenueCatEntitlementId", "evidrilo_pro"),
            ("Platform:RevenueCatMonthlyProductId", "com.example.monthly"),
            ("Platform:RevenueCatYearlyProductId", "com.example.yearly"));

        var exception = Assert.Throws<PlatformConfigurationException>(
            () => PlatformOptions.From(configuration, environment));

        Assert.Contains("RevenueCat", exception.Message, StringComparison.Ordinal);
    }

    [Fact]
    public void Configured_provider_product_ids_are_exposed_without_secrets_in_safe_summary()
    {
        var options = PlatformOptions.From(
            BuildConfiguration(
                ("Platform:RevenueCatMonthlyProductId", "rc_monthly_real"),
                ("Platform:RevenueCatYearlyProductId", "rc_yearly_real")),
            "Testing");

        Assert.Equal("rc_monthly_real", options.RevenueCatMonthlyProductId);
        Assert.Equal("rc_yearly_real", options.RevenueCatYearlyProductId);
        Assert.DoesNotContain("rc_monthly_real", options.ToSafeString(), StringComparison.Ordinal);
        Assert.DoesNotContain("rc_yearly_real", options.ToSafeString(), StringComparison.Ordinal);
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

    [Theory]
    [InlineData("Staging")]
    [InlineData("Production")]
    public void Deployed_environments_reject_local_developer_access_flag(string environment)
    {
        var configuration = BuildConfiguration(
            ("Platform:SupabaseUrl", "https://example.supabase.co"),
            ("Platform:SupabasePublishableKey", "synthetic-public-key"),
            ("Platform:DatabaseConnectionString", "Host=example.invalid;Database=evidrilo;Username=student;SSL Mode=Require"),
            ("CORS_ALLOWED_ORIGINS", "https://staging.evidrilo.example"),
            ("LOCAL_DEVELOPER_ACCESS_ENABLED", "true"));

        var exception = Assert.Throws<PlatformConfigurationException>(
            () => PlatformOptions.From(configuration, environment));

        Assert.Contains("local developer", exception.Message, StringComparison.OrdinalIgnoreCase);
    }

    private static IConfiguration BuildConfiguration(params (string Key, string Value)[] values)
    {
        return new ConfigurationBuilder()
            .AddInMemoryCollection(values.Select(value => new KeyValuePair<string, string?>(value.Key, value.Value)))
            .Build();
    }
}
