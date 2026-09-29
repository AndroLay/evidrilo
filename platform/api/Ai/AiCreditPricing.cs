namespace Evidrilo.Api.Ai;

public sealed record AiProviderTokenUsage(
    int InputTokens,
    int CachedInputTokens,
    int CacheWriteInputTokens,
    int OutputTokens,
    int ReasoningTokens)
{
    public bool IsValid =>
        InputTokens >= 0
        && CachedInputTokens >= 0
        && CacheWriteInputTokens >= 0
        && (long)CachedInputTokens + CacheWriteInputTokens <= InputTokens
        && OutputTokens >= 0
        && ReasoningTokens >= 0
        && ReasoningTokens <= OutputTokens
        && (InputTokens > 0 || OutputTokens > 0);
}

public static class AiCreditPricing
{
    public const decimal UsdPerCredit = 0.001m;
    public const int MaximumCreditsPerRequest = 200;

    public static int CreditsForCostUsd(decimal costUsd)
    {
        if (costUsd < 0 || costUsd > MaximumCreditsPerRequest * UsdPerCredit)
            throw new ArgumentOutOfRangeException(nameof(costUsd), "AI cost exceeds the per-request credit limit.");
        if (costUsd == 0)
            return 0;
        return Math.Max(1, decimal.ToInt32(decimal.Ceiling(costUsd / UsdPerCredit)));
    }
}
