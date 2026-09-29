using Evidrilo.Api.Ai;
using Evidrilo.Api.Configuration;
using Evidrilo.Api.ProjectAi;
using Microsoft.Extensions.Configuration;

namespace Evidrilo.Api.Tests;

public sealed class ProjectAiProviderOptionsTests
{
    [Fact]
    public void Project_provider_is_off_by_default_even_if_shared_provider_is_enabled()
    {
        var options = ProjectAiProviderOptions.From(EnabledConfiguration(), AiProviderOptions.From(EnabledConfiguration()));

        Assert.False(options.Enabled);
        Assert.False(options.GeneralChatEnabled);
        Assert.DoesNotContain("synthetic-secret", options.ToSafeString(), StringComparison.Ordinal);
    }

    [Fact]
    public void Enabling_project_provider_requires_its_own_activation_and_privacy_approvals()
    {
        var configuration = EnabledConfiguration(
            ("PROJECT_AI_PROVIDER_ENABLED", "true"),
            ("PROJECT_AI_PROVIDER_ACTIVATION_APPROVED", "true"),
            ("PROJECT_AI_PRIVACY_APPROVED", "false"));

        var exception = Assert.Throws<PlatformConfigurationException>(() =>
            ProjectAiProviderOptions.From(configuration, AiProviderOptions.From(configuration)));

        Assert.Contains("PROJECT_AI_PRIVACY_APPROVED", exception.Message, StringComparison.Ordinal);
        Assert.DoesNotContain("synthetic-secret", exception.Message, StringComparison.Ordinal);
    }

    [Fact]
    public void Project_provider_requires_approved_shared_provider_and_database_ready_pricing()
    {
        var configuration = EnabledConfiguration(
            ("PROJECT_AI_PROVIDER_ENABLED", "true"),
            ("PROJECT_AI_PROVIDER_ACTIVATION_APPROVED", "true"),
            ("PROJECT_AI_PRIVACY_APPROVED", "true"));
        var disabledSharedProvider = AiProviderOptions.From(BuildConfiguration());

        var exception = Assert.Throws<PlatformConfigurationException>(() =>
            ProjectAiProviderOptions.From(configuration, disabledSharedProvider));

        Assert.Contains("AI_PROVIDER_ENABLED", exception.Message, StringComparison.Ordinal);
    }

    [Fact]
    public void Project_provider_can_be_enabled_only_after_all_independent_approvals()
    {
        var configuration = EnabledConfiguration(
            ("PROJECT_AI_PROVIDER_ENABLED", "true"),
            ("PROJECT_AI_PROVIDER_ACTIVATION_APPROVED", "true"),
            ("PROJECT_AI_PRIVACY_APPROVED", "true"));

        var options = ProjectAiProviderOptions.From(configuration, AiProviderOptions.From(configuration));

        Assert.True(options.Enabled);
        Assert.False(options.GeneralChatEnabled);
        Assert.DoesNotContain("synthetic-secret", options.ToSafeString(), StringComparison.Ordinal);
    }

    [Fact]
    public void General_chat_needs_its_separate_policy_approval_even_when_project_ai_is_enabled()
    {
        var configuration = EnabledConfiguration(
            ("PROJECT_AI_PROVIDER_ENABLED", "true"),
            ("PROJECT_AI_PROVIDER_ACTIVATION_APPROVED", "true"),
            ("PROJECT_AI_PRIVACY_APPROVED", "true"));

        var options = ProjectAiProviderOptions.From(configuration, AiProviderOptions.From(configuration));

        Assert.True(options.Enabled);
        Assert.False(options.GeneralChatEnabled);
    }

    [Fact]
    public void General_chat_can_open_only_with_its_separate_policy_approval()
    {
        var configuration = EnabledConfiguration(
            ("PROJECT_AI_PROVIDER_ENABLED", "true"),
            ("PROJECT_AI_PROVIDER_ACTIVATION_APPROVED", "true"),
            ("PROJECT_AI_PRIVACY_APPROVED", "true"),
            ("PROJECT_AI_GENERAL_CHAT_POLICY_APPROVED", "true"));

        var options = ProjectAiProviderOptions.From(configuration, AiProviderOptions.From(configuration));

        Assert.True(options.GeneralChatEnabled);
        Assert.DoesNotContain("synthetic-secret", options.ToSafeString(), StringComparison.Ordinal);
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

    private static IConfiguration BuildConfiguration() =>
        new ConfigurationBuilder().AddInMemoryCollection().Build();
}
