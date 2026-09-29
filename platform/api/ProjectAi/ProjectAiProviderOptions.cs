using Evidrilo.Api.Ai;
using Evidrilo.Api.Configuration;
using Microsoft.Extensions.Configuration;

namespace Evidrilo.Api.ProjectAi;

public sealed class ProjectAiProviderOptions
{
    private ProjectAiProviderOptions(bool enabled, bool generalChatEnabled)
    {
        Enabled = enabled;
        GeneralChatEnabled = generalChatEnabled;
    }

    public bool Enabled { get; }

    public bool GeneralChatEnabled { get; }

    public static ProjectAiProviderOptions Disabled { get; } = new(false, false);

    public static ProjectAiProviderOptions From(
        IConfiguration configuration,
        AiProviderOptions sharedProviderOptions)
    {
        ArgumentNullException.ThrowIfNull(configuration);
        ArgumentNullException.ThrowIfNull(sharedProviderOptions);
        var generalChatPolicyApproved = ReadBoolean(
            configuration,
            "PROJECT_AI_GENERAL_CHAT_POLICY_APPROVED",
            defaultValue: false);
        if (!ReadBoolean(configuration, "PROJECT_AI_PROVIDER_ENABLED", defaultValue: false))
            return Disabled;

        if (!ReadBoolean(configuration, "PROJECT_AI_PROVIDER_ACTIVATION_APPROVED", defaultValue: false))
            throw new PlatformConfigurationException(
                "PROJECT_AI_PROVIDER_ACTIVATION_APPROVED must be true before enabling project AI.");
        if (!ReadBoolean(configuration, "PROJECT_AI_PRIVACY_APPROVED", defaultValue: false))
            throw new PlatformConfigurationException(
                "PROJECT_AI_PRIVACY_APPROVED must be true before enabling project AI.");
        if (!sharedProviderOptions.Enabled || !sharedProviderOptions.ActivationApproved)
            throw new PlatformConfigurationException(
                "AI_PROVIDER_ENABLED and AI_PROVIDER_ACTIVATION_APPROVED must be true before enabling project AI.");
        if (sharedProviderOptions.ApiKey is null
            || sharedProviderOptions.Model is null
            || sharedProviderOptions.MaxRequestCostUsd is null
            || sharedProviderOptions.MonthlySpendLimitUsd is null)
        {
            throw new PlatformConfigurationException(
                "The shared AI provider must have server credentials, pinned model, and spend limits before enabling project AI.");
        }

        return new ProjectAiProviderOptions(true, generalChatPolicyApproved);
    }

    public string ToSafeString() =>
        $"ProjectAiProviderOptions(enabled={Enabled}, generalChatEnabled={GeneralChatEnabled})";

    private static bool ReadBoolean(IConfiguration configuration, string key, bool defaultValue)
    {
        var raw = configuration[key];
        if (string.IsNullOrWhiteSpace(raw)) return defaultValue;
        if (bool.TryParse(raw, out var value)) return value;
        throw new PlatformConfigurationException($"{key} must be true or false.");
    }
}
