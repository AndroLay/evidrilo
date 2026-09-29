using Evidrilo.Api.Ai;
using Microsoft.Extensions.Configuration;

namespace Evidrilo.Api.Tests;

public sealed class AiCreditPricingTests
{
    [Fact]
    public void Cached_input_and_reasoning_tokens_use_their_billed_categories_once()
    {
        var options = EnabledOptions();
        var usage = new AiProviderTokenUsage(
            InputTokens: 10_000,
            CachedInputTokens: 4_000,
            CacheWriteInputTokens: 1_000,
            OutputTokens: 5_000,
            ReasoningTokens: 3_000);

        var usd = options.EstimateActualCostUsd(usage);

        // 5,000 uncached input at $0.10/M, 4,000 cached at $0.01/M,
        // 1,000 cache-write input at $0.125/M,
        // and 5,000 output at $0.50/M. Reasoning is already in output_tokens.
        Assert.Equal(0.003165m, usd);
        Assert.Equal(4, AiCreditPricing.CreditsForCostUsd(usd));
    }

    [Fact]
    public void Maximum_cost_reserves_the_highest_input_category_and_output_cap()
    {
        var options = EnabledOptions();

        var maximumCost = options.EstimateMaximumRequestCostUsd(10_000);

        Assert.Equal(0.001506m, maximumCost);
        Assert.Equal(2, AiCreditPricing.CreditsForCostUsd(maximumCost));
    }

    [Theory]
    [InlineData(0, 0)]
    [InlineData(1_000, 1)]
    [InlineData(1_001, 2)]
    [InlineData(20_000, 20)]
    [InlineData(200_000, 200)]
    public void Credits_round_up_each_started_millidollar(int costMicrosUsd, int expectedCredits)
    {
        var costUsd = costMicrosUsd / 1_000_000m;
        Assert.Equal(expectedCredits, AiCreditPricing.CreditsForCostUsd(costUsd));
    }

    private static AiProviderOptions EnabledOptions()
    {
        var values = new Dictionary<string, string?>
        {
            ["AI_PROVIDER_ENABLED"] = "true",
            ["AI_PROVIDER_ACTIVATION_APPROVED"] = "true",
            ["OPENAI_API_KEY"] = "synthetic-secret",
            ["AI_OPENAI_MODEL"] = "gpt-6-luna",
            ["AI_OPENAI_INPUT_USD_PER_MILLION_TOKENS"] = "0.10",
            ["AI_OPENAI_CACHED_INPUT_USD_PER_MILLION_TOKENS"] = "0.01",
            ["AI_OPENAI_CACHE_WRITE_INPUT_USD_PER_MILLION_TOKENS"] = "0.125",
            ["AI_OPENAI_OUTPUT_USD_PER_MILLION_TOKENS"] = "0.50",
            ["AI_MAX_REQUEST_COST_USD"] = "0.20",
            ["AI_MONTHLY_SPEND_LIMIT_USD"] = "2",
        };
        return AiProviderOptions.From(new ConfigurationBuilder().AddInMemoryCollection(values).Build());
    }
}
