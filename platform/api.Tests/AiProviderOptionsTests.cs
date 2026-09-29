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

    [Fact]
    public void Enabled_provider_reads_an_owner_only_secret_file_and_allows_the_experiential_endpoint()
    {
        var secretPath = Path.Combine(Path.GetTempPath(), $"evidrilo-ai-key-{Guid.NewGuid():N}");
        try
        {
            File.WriteAllText(secretPath, "synthetic-file-secret\n");
            if (!OperatingSystem.IsWindows())
            {
                File.SetUnixFileMode(secretPath, UnixFileMode.UserRead | UnixFileMode.UserWrite);
            }

            var options = AiProviderOptions.From(EnabledConfiguration(
                ("OPENAI_API_KEY", ""),
                ("AI_PROVIDER_API_KEY_FILE", secretPath),
                ("AI_PROVIDER_BASE_URL", "https://api.experientiallabs.ai/v1"),
                ("AI_OPENAI_MODEL", "gpt-6-luna")));

            Assert.Equal("synthetic-file-secret", options.ApiKey);
            Assert.Equal("gpt-6-luna", options.Model);
            Assert.Equal("https://api.experientiallabs.ai/v1/responses", options.ResponsesEndpoint.AbsoluteUri);
            Assert.DoesNotContain("synthetic-file-secret", options.ToSafeString(), StringComparison.Ordinal);
        }
        finally
        {
            File.Delete(secretPath);
        }
    }

    [Fact]
    public void Enabled_provider_rejects_secret_files_readable_by_group_or_others()
    {
        if (OperatingSystem.IsWindows()) return;

        var secretPath = Path.Combine(Path.GetTempPath(), $"evidrilo-ai-key-{Guid.NewGuid():N}");
        try
        {
            File.WriteAllText(secretPath, "synthetic-file-secret");
            File.SetUnixFileMode(secretPath, UnixFileMode.UserRead | UnixFileMode.UserWrite | UnixFileMode.OtherRead);

            var exception = Assert.Throws<PlatformConfigurationException>(() =>
                AiProviderOptions.From(EnabledConfiguration(
                    ("OPENAI_API_KEY", ""),
                    ("AI_PROVIDER_API_KEY_FILE", secretPath))));

            Assert.Contains("AI_PROVIDER_API_KEY_FILE", exception.Message, StringComparison.Ordinal);
            Assert.DoesNotContain("synthetic-file-secret", exception.Message, StringComparison.Ordinal);
        }
        finally
        {
            File.Delete(secretPath);
        }
    }

    [Theory]
    [InlineData("http://api.experientiallabs.ai/v1")]
    [InlineData("https://example.invalid/v1")]
    [InlineData("https://api.experientiallabs.ai/v1?redirect=elsewhere")]
    public void Enabled_provider_rejects_untrusted_base_urls(string baseUrl)
    {
        var exception = Assert.Throws<PlatformConfigurationException>(() =>
            AiProviderOptions.From(EnabledConfiguration(("AI_PROVIDER_BASE_URL", baseUrl))));

        Assert.Contains("AI_PROVIDER_BASE_URL", exception.Message, StringComparison.Ordinal);
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
