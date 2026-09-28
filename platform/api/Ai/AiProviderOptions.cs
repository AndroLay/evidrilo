using System.Globalization;
using Evidrilo.Api.Configuration;
using Microsoft.Extensions.Configuration;

namespace Evidrilo.Api.Ai;

public sealed class AiProviderOptions
{
    private AiProviderOptions(
        bool enabled,
        bool activationApproved,
        string? apiKey,
        string? model,
        decimal? inputUsdPerMillionTokens,
        decimal? outputUsdPerMillionTokens,
        decimal? maxRequestCostUsd,
        decimal? monthlySpendLimitUsd,
        int maxOutputTokens,
        TimeSpan timeout)
    {
        Enabled = enabled;
        ActivationApproved = activationApproved;
        ApiKey = apiKey;
        Model = model;
        InputUsdPerMillionTokens = inputUsdPerMillionTokens;
        OutputUsdPerMillionTokens = outputUsdPerMillionTokens;
        MaxRequestCostUsd = maxRequestCostUsd;
        MonthlySpendLimitUsd = monthlySpendLimitUsd;
        MaxOutputTokens = maxOutputTokens;
        Timeout = timeout;
    }

    public bool Enabled { get; }

    public bool ActivationApproved { get; }

    public string? ApiKey { get; }

    public string? Model { get; }

    public decimal? InputUsdPerMillionTokens { get; }

    public decimal? OutputUsdPerMillionTokens { get; }

    public decimal? MaxRequestCostUsd { get; }

    public decimal? MonthlySpendLimitUsd { get; }

    public int MaxOutputTokens { get; }

    public TimeSpan Timeout { get; }

    public static AiProviderOptions From(IConfiguration configuration)
    {
        ArgumentNullException.ThrowIfNull(configuration);
        var enabled = ReadBoolean(configuration, "AI_PROVIDER_ENABLED", defaultValue: false);
        if (!enabled)
        {
            // Do not even retain an accidentally supplied key while the kill
            // switch is off. The default is safe in every environment.
            return new AiProviderOptions(
                false,
                false,
                null,
                null,
                null,
                null,
                null,
                null,
                512,
                TimeSpan.FromSeconds(5));
        }

        if (!ReadBoolean(configuration, "AI_PROVIDER_ACTIVATION_APPROVED", defaultValue: false))
        {
            throw new PlatformConfigurationException(
                "AI_PROVIDER_ACTIVATION_APPROVED must be true before enabling an AI provider.");
        }

        var apiKey = RequiredSecret(configuration, "OPENAI_API_KEY", maximumLength: 1024);
        var model = RequiredModel(configuration, "AI_OPENAI_MODEL");
        var inputRate = RequiredPositiveDecimal(configuration, "AI_OPENAI_INPUT_USD_PER_MILLION_TOKENS");
        var outputRate = RequiredPositiveDecimal(configuration, "AI_OPENAI_OUTPUT_USD_PER_MILLION_TOKENS");
        var requestCeiling = RequiredPositiveDecimal(configuration, "AI_MAX_REQUEST_COST_USD");
        var monthlyCeiling = RequiredPositiveDecimal(configuration, "AI_MONTHLY_SPEND_LIMIT_USD");
        if (requestCeiling > monthlyCeiling)
        {
            throw new PlatformConfigurationException(
                "AI_MAX_REQUEST_COST_USD cannot exceed AI_MONTHLY_SPEND_LIMIT_USD.");
        }

        var maxOutputTokens = ReadInteger(configuration, "AI_MAX_OUTPUT_TOKENS", 512, 64, 512);
        var timeoutSeconds = ReadInteger(configuration, "AI_PROVIDER_TIMEOUT_SECONDS", 5, 1, 5);
        return new AiProviderOptions(
            true,
            true,
            apiKey,
            model,
            inputRate,
            outputRate,
            requestCeiling,
            monthlyCeiling,
            maxOutputTokens,
            TimeSpan.FromSeconds(timeoutSeconds));
    }

    public decimal EstimateMaximumRequestCostUsd(int inputUtf8ByteCount)
    {
        if (!Enabled
            || inputUtf8ByteCount < 0
            || InputUsdPerMillionTokens is null
            || OutputUsdPerMillionTokens is null)
            throw new InvalidOperationException("AI provider cost estimation is unavailable.");

        // UTF-8 bytes are used as a conservative upper bound for input tokens.
        // Owner-configured rates must be the highest applicable rates for the
        // pinned model, including any reasoning/output token category.
        var rawCost = ((inputUtf8ByteCount * InputUsdPerMillionTokens.Value)
            + (MaxOutputTokens * OutputUsdPerMillionTokens.Value)) / 1_000_000m;
        return RoundUsdUp(rawCost);
    }

    public decimal EstimateActualCostUsd(int inputTokens, int outputTokens)
    {
        if (!Enabled
            || inputTokens < 0
            || outputTokens < 0
            || InputUsdPerMillionTokens is null
            || OutputUsdPerMillionTokens is null)
            throw new InvalidOperationException("AI provider cost estimation is unavailable.");

        var rawCost = ((inputTokens * InputUsdPerMillionTokens.Value)
            + (outputTokens * OutputUsdPerMillionTokens.Value)) / 1_000_000m;
        return RoundUsdUp(rawCost);
    }

    public string ToSafeString() =>
        $"AiProviderOptions(enabled={Enabled}, activationApproved={ActivationApproved}, modelConfigured={!string.IsNullOrWhiteSpace(Model)}, costLimitsConfigured={MaxRequestCostUsd.HasValue && MonthlySpendLimitUsd.HasValue}, maxOutputTokens={MaxOutputTokens})";

    private static decimal RoundUsdUp(decimal amount) =>
        amount == 0 ? 0 : Math.Ceiling(amount * 1_000_000m) / 1_000_000m;

    private static bool ReadBoolean(IConfiguration configuration, string key, bool defaultValue)
    {
        var raw = configuration[key];
        if (string.IsNullOrWhiteSpace(raw)) return defaultValue;
        if (bool.TryParse(raw, out var value)) return value;
        throw new PlatformConfigurationException($"{key} must be true or false.");
    }

    private static int ReadInteger(IConfiguration configuration, string key, int defaultValue, int minimum, int maximum)
    {
        var raw = configuration[key];
        if (string.IsNullOrWhiteSpace(raw)) return defaultValue;
        if (int.TryParse(raw, NumberStyles.None, CultureInfo.InvariantCulture, out var value)
            && value >= minimum
            && value <= maximum)
            return value;
        throw new PlatformConfigurationException($"{key} must be an integer from {minimum} through {maximum}.");
    }

    private static decimal RequiredPositiveDecimal(IConfiguration configuration, string key)
    {
        var raw = configuration[key];
        if (decimal.TryParse(raw, NumberStyles.AllowDecimalPoint, CultureInfo.InvariantCulture, out var value)
            && value > 0
            && value <= 1_000_000m)
            return value;
        throw new PlatformConfigurationException($"{key} must be a positive USD amount no greater than 1000000.");
    }

    private static string RequiredSecret(IConfiguration configuration, string key, int maximumLength)
    {
        var value = configuration[key]?.Trim();
        if (!string.IsNullOrWhiteSpace(value)
            && value.Length <= maximumLength
            && !value.Any(char.IsWhiteSpace))
            return value;
        throw new PlatformConfigurationException($"{key} must be configured as a server-side secret.");
    }

    private static string RequiredModel(IConfiguration configuration, string key)
    {
        var value = configuration[key]?.Trim();
        if (string.IsNullOrWhiteSpace(value)
            || value.Length > 128
            || !char.IsAsciiLetterOrDigit(value[0])
            || !char.IsAsciiLetterOrDigit(value[^1])
            || value.Any(character => !char.IsAsciiLetterOrDigit(character) && character is not ('.' or '_' or '-'))
            || value.Contains("latest", StringComparison.OrdinalIgnoreCase))
            throw new PlatformConfigurationException($"{key} must be an exact, pinned model identifier.");
        return value;
    }
}
