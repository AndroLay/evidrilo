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
        decimal? cachedInputUsdPerMillionTokens,
        decimal? cacheWriteInputUsdPerMillionTokens,
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
        CachedInputUsdPerMillionTokens = cachedInputUsdPerMillionTokens;
        CacheWriteInputUsdPerMillionTokens = cacheWriteInputUsdPerMillionTokens;
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

    public decimal? CachedInputUsdPerMillionTokens { get; }

    public decimal? CacheWriteInputUsdPerMillionTokens { get; }

    public decimal? OutputUsdPerMillionTokens { get; }

    public decimal? MaxRequestCostUsd { get; }

    public decimal? MonthlySpendLimitUsd { get; }

    public int MaxOutputTokens { get; }

    public TimeSpan Timeout { get; }

    public static AiProviderOptions DefaultPricing { get; } = new(
        false,
        false,
        null,
        null,
        0.10m,
        0.01m,
        0.125m,
        0.50m,
        null,
        null,
        512,
        TimeSpan.FromSeconds(5));

    public static AiProviderOptions From(IConfiguration configuration)
    {
        ArgumentNullException.ThrowIfNull(configuration);
        if (!ReadBoolean(configuration, "AI_PROVIDER_ENABLED", defaultValue: false))
        {
            // Pricing remains available to gateways for usage settlement, but
            // provider credentials and activation settings are discarded.
            return DefaultPricing;
        }

        if (!ReadBoolean(configuration, "AI_PROVIDER_ACTIVATION_APPROVED", defaultValue: false))
        {
            throw new PlatformConfigurationException(
                "AI_PROVIDER_ACTIVATION_APPROVED must be true before enabling an AI provider.");
        }

        var apiKey = RequiredSecret(configuration, "OPENAI_API_KEY", maximumLength: 1024);
        var model = RequiredModel(configuration, "AI_OPENAI_MODEL");
        var inputRate = RequiredPositiveDecimal(configuration, "AI_OPENAI_INPUT_USD_PER_MILLION_TOKENS");
        var cachedInputRate = RequiredPositiveDecimal(configuration, "AI_OPENAI_CACHED_INPUT_USD_PER_MILLION_TOKENS");
        var cacheWriteInputRate = RequiredPositiveDecimal(configuration, "AI_OPENAI_CACHE_WRITE_INPUT_USD_PER_MILLION_TOKENS");
        var outputRate = RequiredPositiveDecimal(configuration, "AI_OPENAI_OUTPUT_USD_PER_MILLION_TOKENS");
        var requestCeiling = RequiredPositiveDecimal(configuration, "AI_MAX_REQUEST_COST_USD");
        var monthlyCeiling = RequiredPositiveDecimal(configuration, "AI_MONTHLY_SPEND_LIMIT_USD");
        if (requestCeiling > monthlyCeiling)
        {
            throw new PlatformConfigurationException(
                "AI_MAX_REQUEST_COST_USD cannot exceed AI_MONTHLY_SPEND_LIMIT_USD.");
        }
        if (requestCeiling > AiCreditPricing.MaximumCreditsPerRequest * AiCreditPricing.UsdPerCredit)
        {
            throw new PlatformConfigurationException(
                "AI_MAX_REQUEST_COST_USD cannot exceed the 200-credit per-request limit.");
        }

        var maxOutputTokens = ReadInteger(configuration, "AI_MAX_OUTPUT_TOKENS", 512, 64, 512);
        var timeoutSeconds = ReadInteger(configuration, "AI_PROVIDER_TIMEOUT_SECONDS", 5, 1, 5);
        return new AiProviderOptions(
            true,
            true,
            apiKey,
            model,
            inputRate,
            cachedInputRate,
            cacheWriteInputRate,
            outputRate,
            requestCeiling,
            monthlyCeiling,
            maxOutputTokens,
            TimeSpan.FromSeconds(timeoutSeconds));
    }

    public decimal EstimateMaximumRequestCostUsd(int inputUtf8ByteCount)
    {
        if (inputUtf8ByteCount < 0
            || InputUsdPerMillionTokens is null
            || CachedInputUsdPerMillionTokens is null
            || CacheWriteInputUsdPerMillionTokens is null
            || OutputUsdPerMillionTokens is null)
            throw new InvalidOperationException("AI provider cost estimation is unavailable.");

        // UTF-8 bytes bound input tokens conservatively. Price all reserved
        // input as uncached and include the complete output cap.
        var maximumInputRate = Math.Max(
            Math.Max(InputUsdPerMillionTokens.Value, CachedInputUsdPerMillionTokens.Value),
            CacheWriteInputUsdPerMillionTokens.Value);
        var rawCost = ((inputUtf8ByteCount * maximumInputRate)
            + (MaxOutputTokens * OutputUsdPerMillionTokens.Value)) / 1_000_000m;
        return RoundUsdUp(rawCost);
    }

    public decimal EstimateActualCostUsd(AiProviderTokenUsage usage)
    {
        ArgumentNullException.ThrowIfNull(usage);
        if (!usage.IsValid
            || InputUsdPerMillionTokens is null
            || CachedInputUsdPerMillionTokens is null
            || CacheWriteInputUsdPerMillionTokens is null
            || OutputUsdPerMillionTokens is null)
            throw new InvalidOperationException("AI provider usage or pricing is invalid.");

        var uncachedInputTokens = usage.InputTokens
            - usage.CachedInputTokens
            - usage.CacheWriteInputTokens;
        var rawCost = ((uncachedInputTokens * InputUsdPerMillionTokens.Value)
            + (usage.CachedInputTokens * CachedInputUsdPerMillionTokens.Value)
            + (usage.CacheWriteInputTokens * CacheWriteInputUsdPerMillionTokens.Value)
            + (usage.OutputTokens * OutputUsdPerMillionTokens.Value)) / 1_000_000m;
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
