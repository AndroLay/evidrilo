using Evidrilo.Api.Ai;
using Evidrilo.Api.Configuration;
using Microsoft.Extensions.Configuration;

namespace Evidrilo.Api.Tests;

public sealed class AiProviderOptionsTests
{
    [Fact]
    public void Provider_is_disabled_even_when_a_key_is_present_without_explicit_enablement()
    {
        var options = AiProviderOptions.From(BuildConfiguration(
            ("OPENAI_API_KEY", "synthetic-secret")));

        Assert.False(options.Enabled);
        Assert.DoesNotContain("synthetic-secret", options.ToSafeString(), StringComparison.Ordinal);
    }

    [Fact]
    public void Enabling_provider_requires_activation_approval_and_cost_configuration()
    {
        var missingApproval = Assert.Throws<PlatformConfigurationException>(() =>
            AiProviderOptions.From(EnabledConfiguration(("AI_PROVIDER_ACTIVATION_APPROVED", "false"))));

        Assert.Contains("AI_PROVIDER_ACTIVATION_APPROVED", missingApproval.Message, StringComparison.Ordinal);
    }

    [Fact]
    public void Enabled_provider_requires_server_secret_model_and_cost_limits()
    {
        var configuration = BuildConfiguration(
            ("AI_PROVIDER_ENABLED", "true"),
            ("AI_PROVIDER_ACTIVATION_APPROVED", "true"),
            ("OPENAI_API_KEY", "synthetic-secret"),
            ("AI_OPENAI_MODEL", "gpt-test-snapshot"),
            ("AI_OPENAI_INPUT_USD_PER_MILLION_TOKENS", "1"),
            ("AI_OPENAI_CACHED_INPUT_USD_PER_MILLION_TOKENS", "0.5"),
            ("AI_OPENAI_CACHE_WRITE_INPUT_USD_PER_MILLION_TOKENS", "1.25"),
            ("AI_OPENAI_OUTPUT_USD_PER_MILLION_TOKENS", "2"),
            ("AI_MAX_REQUEST_COST_USD", "0.01"));

        var exception = Assert.Throws<PlatformConfigurationException>(() => AiProviderOptions.From(configuration));

        Assert.Contains("AI_MONTHLY_SPEND_LIMIT_USD", exception.Message, StringComparison.Ordinal);
        Assert.DoesNotContain("synthetic-secret", exception.Message, StringComparison.Ordinal);
    }

    [Fact]
    public void Enabled_provider_parses_bounded_cost_and_request_options_without_exposing_secrets()
    {
        var options = AiProviderOptions.From(EnabledConfiguration());

        Assert.True(options.Enabled);
        Assert.Equal("gpt-test-snapshot", options.Model);
        Assert.Equal(512, options.MaxOutputTokens);
        Assert.Equal(5, options.Timeout.TotalSeconds);
        Assert.Equal(0.01m, options.MaxRequestCostUsd);
        Assert.Equal(2m, options.MonthlySpendLimitUsd);
        Assert.Equal(0.01m, options.CachedInputUsdPerMillionTokens);
        Assert.Equal(1.25m, options.CacheWriteInputUsdPerMillionTokens);
        Assert.DoesNotContain("synthetic-secret", options.ToSafeString(), StringComparison.Ordinal);
    }

    [Theory]
    [InlineData("NaN")]
    [InlineData("-1")]
    [InlineData("0")]
    public void Enabled_provider_rejects_invalid_cost_rates(string value)
    {
        var exception = Assert.Throws<PlatformConfigurationException>(() =>
            AiProviderOptions.From(EnabledConfiguration(("AI_OPENAI_INPUT_USD_PER_MILLION_TOKENS", value))));

        Assert.Contains("AI_OPENAI_INPUT_USD_PER_MILLION_TOKENS", exception.Message, StringComparison.Ordinal);
    }

    [Fact]
    public void Enabled_provider_rejects_per_request_cost_above_monthly_ceiling()
    {
        var exception = Assert.Throws<PlatformConfigurationException>(() =>
            AiProviderOptions.From(EnabledConfiguration(("AI_MAX_REQUEST_COST_USD", "3"))));

        Assert.Contains("AI_MAX_REQUEST_COST_USD", exception.Message, StringComparison.Ordinal);
    }

    private static IConfiguration EnabledConfiguration(params (string Key, string Value)[] overrides)
    {
        var values = new Dictionary<string, string?>
        {
            ["AI_PROVIDER_ENABLED"] = "true",
            ["AI_PROVIDER_ACTIVATION_APPROVED"] = "true",
            ["OPENAI_API_KEY"] = "synthetic-secret",
            ["AI_OPENAI_MODEL"] = "gpt-test-snapshot",
            ["AI_OPENAI_INPUT_USD_PER_MILLION_TOKENS"] = "1",
            ["AI_OPENAI_CACHED_INPUT_USD_PER_MILLION_TOKENS"] = "0.01",
            ["AI_OPENAI_CACHE_WRITE_INPUT_USD_PER_MILLION_TOKENS"] = "1.25",
            ["AI_OPENAI_OUTPUT_USD_PER_MILLION_TOKENS"] = "2",
            ["AI_MAX_REQUEST_COST_USD"] = "0.01",
            ["AI_MONTHLY_SPEND_LIMIT_USD"] = "2",
        };
        foreach (var (key, value) in overrides)
            values[key] = value;
        return new ConfigurationBuilder().AddInMemoryCollection(values).Build();
    }

    private static IConfiguration BuildConfiguration(params (string Key, string Value)[] values) =>
        new ConfigurationBuilder()
            .AddInMemoryCollection(values.ToDictionary(item => item.Key, item => (string?)item.Value))
            .Build();
}
