using System.Globalization;
using Evidrilo.Api.Configuration;
using Microsoft.Extensions.Configuration;

namespace Evidrilo.Api.Ai;

public sealed class AiProviderOptions
{
    private static readonly Uri DefaultResponsesEndpoint = new("https://api.openai.com/v1/responses");

    private AiProviderOptions(
        bool enabled,
        bool activationApproved,
        string? apiKey,
        string? model,
        Uri responsesEndpoint,
        decimal? inputUsdPerMillionTokens,
        decimal? cachedInputUsdPerMillionTokens,
        decimal? cacheWriteInputUsdPerMillionTokens,
        decimal? outputUsdPerMillionTokens,
        decimal? maxRequestCostUsd,
        decimal? monthlySpendLimitUsd,
        int maxOutputTokens,
        TimeSpan timeout,
        string? reasoningEffort = null)
    {
        Enabled = enabled;
        ActivationApproved = activationApproved;
        ApiKey = apiKey;
        Model = model;
        ResponsesEndpoint = responsesEndpoint;
        InputUsdPerMillionTokens = inputUsdPerMillionTokens;
        CachedInputUsdPerMillionTokens = cachedInputUsdPerMillionTokens;
        CacheWriteInputUsdPerMillionTokens = cacheWriteInputUsdPerMillionTokens;
        OutputUsdPerMillionTokens = outputUsdPerMillionTokens;
        MaxRequestCostUsd = maxRequestCostUsd;
        MonthlySpendLimitUsd = monthlySpendLimitUsd;
        MaxOutputTokens = maxOutputTokens;
        Timeout = timeout;
        ReasoningEffort = reasoningEffort;
    }

    public bool Enabled { get; }

    public bool ActivationApproved { get; }

    public string? ApiKey { get; }

    public string? Model { get; }

    public Uri ResponsesEndpoint { get; }

    public decimal? InputUsdPerMillionTokens { get; }

    public decimal? CachedInputUsdPerMillionTokens { get; }

    public decimal? CacheWriteInputUsdPerMillionTokens { get; }

    public decimal? OutputUsdPerMillionTokens { get; }

    public decimal? MaxRequestCostUsd { get; }

    public decimal? MonthlySpendLimitUsd { get; }

    public int MaxOutputTokens { get; }

    public TimeSpan Timeout { get; }

    public string? ReasoningEffort { get; }

    public static AiProviderOptions DefaultPricing { get; } = new(
        false,
        false,
        null,
        null,
        DefaultResponsesEndpoint,
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

        var apiKey = RequiredProviderSecret(configuration);
        var model = RequiredModel(configuration, "AI_OPENAI_MODEL");
        var responsesEndpoint = RequiredResponsesEndpoint(configuration);
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

        var effort = configuration["AI_REASONING_EFFORT"]?.Trim();
        if (string.IsNullOrEmpty(effort)) effort = null;
        if (effort is not null && effort is not ("none" or "minimal" or "low" or "medium" or "high" or "xhigh" or "max"))
            throw new PlatformConfigurationException("AI_REASONING_EFFORT must be a supported exact effort or omitted.");
        var maxOutputTokens = ReadInteger(configuration, "AI_MAX_OUTPUT_TOKENS", 512, 64, 8192);
        var timeoutSeconds = ReadInteger(configuration, "AI_PROVIDER_TIMEOUT_SECONDS", 5, 1, 90);
        return new AiProviderOptions(
            true,
            true,
            apiKey,
            model,
            responsesEndpoint,
            inputRate,
            cachedInputRate,
            cacheWriteInputRate,
            outputRate,
            requestCeiling,
            monthlyCeiling,
            maxOutputTokens,
            TimeSpan.FromSeconds(timeoutSeconds),
            effort);
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

    private static string RequiredProviderSecret(IConfiguration configuration)
    {
        var secretFile = configuration["AI_PROVIDER_API_KEY_FILE"]?.Trim();
        var environmentSecret = configuration["OPENAI_API_KEY"];
        var hasSecretFile = !string.IsNullOrWhiteSpace(secretFile);
        var hasEnvironmentSecret = !string.IsNullOrWhiteSpace(environmentSecret);
        if (hasSecretFile && hasEnvironmentSecret)
            throw new PlatformConfigurationException(
                "Configure only one of AI_PROVIDER_API_KEY_FILE or OPENAI_API_KEY.");

        if (!hasSecretFile)
            return RequiredSecret(configuration, "OPENAI_API_KEY", maximumLength: 1024);

        if (!Path.IsPathFullyQualified(secretFile!))
            throw new PlatformConfigurationException(
                "AI_PROVIDER_API_KEY_FILE must point to an absolute, owner-only secret file.");

        try
        {
            var file = new FileInfo(secretFile!);
            if (!file.Exists || file.LinkTarget is not null || (File.GetAttributes(secretFile!) & FileAttributes.Directory) != 0)
                throw new PlatformConfigurationException(
                    "AI_PROVIDER_API_KEY_FILE must point to a regular, owner-only secret file.");

            if (OperatingSystem.IsLinux() || OperatingSystem.IsMacOS())
            {
                var mode = File.GetUnixFileMode(secretFile!);
                const UnixFileMode nonOwnerPermissions = UnixFileMode.GroupRead
                    | UnixFileMode.GroupWrite
                    | UnixFileMode.GroupExecute
                    | UnixFileMode.OtherRead
                    | UnixFileMode.OtherWrite
                    | UnixFileMode.OtherExecute;
                if ((mode & UnixFileMode.UserRead) == 0 || (mode & nonOwnerPermissions) != 0)
                    throw new PlatformConfigurationException(
                        "AI_PROVIDER_API_KEY_FILE must be readable only by its owner (for example, mode 0600).");
            }

            var value = File.ReadAllText(secretFile!).Trim();
            if (string.IsNullOrWhiteSpace(value)
                || value.Length > 1024
                || value.Any(char.IsWhiteSpace))
                throw new PlatformConfigurationException(
                    "AI_PROVIDER_API_KEY_FILE must contain one non-empty API key line.");
            return value;
        }
        catch (PlatformConfigurationException)
        {
            throw;
        }
        catch (Exception exception) when (exception is IOException
            or UnauthorizedAccessException
            or ArgumentException
            or NotSupportedException
            or System.Security.SecurityException)
        {
            throw new PlatformConfigurationException(
                "AI_PROVIDER_API_KEY_FILE could not be read as a secure server-side secret.");
        }
    }

    private static Uri RequiredResponsesEndpoint(IConfiguration configuration)
    {
        var raw = configuration["AI_PROVIDER_BASE_URL"]?.Trim();
        if (string.IsNullOrWhiteSpace(raw)) return DefaultResponsesEndpoint;

        if (!Uri.TryCreate(raw, UriKind.Absolute, out var baseUri)
            || !string.Equals(baseUri.Scheme, Uri.UriSchemeHttps, StringComparison.OrdinalIgnoreCase)
            || !baseUri.IsDefaultPort
            || !string.IsNullOrEmpty(baseUri.UserInfo)
            || !string.IsNullOrEmpty(baseUri.Query)
            || !string.IsNullOrEmpty(baseUri.Fragment)
            || !string.Equals(baseUri.AbsolutePath.TrimEnd('/'), "/v1", StringComparison.Ordinal)
            || !(string.Equals(baseUri.Host, "api.openai.com", StringComparison.OrdinalIgnoreCase)
                || string.Equals(baseUri.Host, "api.experientiallabs.ai", StringComparison.OrdinalIgnoreCase)))
        {
            throw new PlatformConfigurationException(
                "AI_PROVIDER_BASE_URL must be the HTTPS v1 endpoint for OpenAI or Experiential Labs.");
        }

        return new Uri($"{baseUri.GetLeftPart(UriPartial.Authority)}/v1/responses", UriKind.Absolute);
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
