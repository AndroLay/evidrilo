using Evidrilo.Api.Ai;

namespace Evidrilo.Api.Tests;

public sealed class AiConversationGatewayTests
{
    private static readonly Guid AccountId = Guid.Parse("123e4567-e89b-42d3-a456-426614174000");

    [Fact]
    public async Task Gateway_accepts_a_grounded_typed_proposal_without_applying_it()
    {
        var ledger = new RecordingLedger();
        var gateway = new AiConversationGateway(
            new FixedProvider(new AiProviderResponse(
                "draft_proposal",
                "Narrow this wording to the observed comparison.",
                ["OBS-WARM-01"])
            {
                Proposal = new AiDraftProposal(
                    "claim_scope",
                    "GENERAL_CAUSAL",
                    "OBSERVED_COMPARISON_ONLY",
                    ["OBS-WARM-01"]),
                Usage = new AiProviderTokenUsage(10_000, 0, 0, 5_000, 0),
            }),
            ledger);

        var result = await gateway.GenerateAsync(AccountId, ValidRequest(), "chat_req_0001", CancellationToken.None);

        Assert.Equal("success", result.Status);
        Assert.Equal("draft_proposal", result.Kind);
        Assert.Equal("GENERAL_CAUSAL", result.Proposal?.BeforeValue);
        Assert.Equal("OBSERVED_COMPARISON_ONLY", result.Proposal?.SuggestedValue);
        Assert.Equal("conversation.v1", result.Audit.PromptVersion);
        Assert.Equal([10], ledger.ReservedCreditCosts);
        Assert.Equal([true], ledger.Settlements);
        Assert.Equal([4], ledger.SettledCreditCosts);
    }

    [Fact]
    public async Task Gateway_releases_credit_when_provider_proposes_a_stale_or_ungrounded_patch()
    {
        var ledger = new RecordingLedger();
        var gateway = new AiConversationGateway(
            new FixedProvider(new AiProviderResponse(
                "draft_proposal",
                "Apply this wording.",
                ["OBS-WARM-01"])
            {
                Proposal = new AiDraftProposal(
                    "claim_scope",
                    "OLD_SCOPE",
                    "OBSERVED_COMPARISON_ONLY",
                    ["OBS-WARM-01"]),
            }),
            ledger);

        var result = await gateway.GenerateAsync(AccountId, ValidRequest(), "chat_req_0002", CancellationToken.None);

        Assert.Equal("fallback", result.Status);
        Assert.Equal("AI_PROPOSAL_STALE_FIELD", result.ReasonCode);
        Assert.Null(result.Proposal);
        Assert.Equal([false], ledger.Settlements);
    }

    [Fact]
    public async Task Disabled_provider_returns_a_safe_fallback_and_releases_the_reservation()
    {
        var ledger = new RecordingLedger();
        var gateway = new AiConversationGateway(new DisabledAiProvider(), ledger);

        var result = await gateway.GenerateAsync(AccountId, ValidRequest(), "chat_req_0003", CancellationToken.None);

        Assert.Equal("fallback", result.Status);
        Assert.Equal("AI_PROVIDER_UNAVAILABLE", result.ReasonCode);
        Assert.Null(result.Text);
        Assert.Null(result.Proposal);
        Assert.Equal([false], ledger.Settlements);
    }

    [Fact]
    public async Task Gateway_redacts_conversation_text_and_releases_credit_on_provider_timeout()
    {
        var ledger = new RecordingLedger();
        var provider = new CapturingDelayedProvider();
        var gateway = new AiConversationGateway(provider, ledger, TimeSpan.FromMilliseconds(10));
        var request = ValidRequest() with
        {
            Prompt = "Learner draft user@example.com; bearer abc.def; api_key=supersecret.",
        };

        var result = await gateway.GenerateAsync(AccountId, request, "chat_req_0004", CancellationToken.None);

        Assert.Equal("fallback", result.Status);
        Assert.Equal("AI_PROVIDER_TIMEOUT", result.ReasonCode);
        Assert.NotNull(provider.Request);
        Assert.DoesNotContain("user@example.com", provider.Request!.RedactedInput);
        Assert.DoesNotContain("bearer abc.def", provider.Request.RedactedInput, StringComparison.OrdinalIgnoreCase);
        Assert.DoesNotContain("api_key=supersecret", provider.Request.RedactedInput, StringComparison.OrdinalIgnoreCase);
        Assert.Equal([false], ledger.Settlements);
    }

    private static AiConversationGatewayRequest ValidRequest() => new(
        AiAssistPurpose.LanguageAlternative,
        "Grounded project context and a request for a bounded wording suggestion.",
        "en-US",
        ["OBS-WARM-01", "LIMIT-TRIAL-01"],
        new AiConversationDraftSnapshot(
            "Warm water causes faster dissolution.",
            "GENERAL_CAUSAL",
            "Each condition was measured once.",
            "Repeat each condition."));

    private sealed class RecordingLedger : IAiCreditLedger
    {
        public List<bool> Settlements { get; } = [];

        public List<int> ReservedCreditCosts { get; } = [];

        public List<int> SettledCreditCosts { get; } = [];

        public Task EnsureConsentAsync(Guid accountId, string consentVersion, CancellationToken cancellationToken) =>
            Task.CompletedTask;

        public Task<AiCreditReservation?> TryReserveAsync(
            Guid accountId,
            string requestId,
            string requestHash,
            CancellationToken cancellationToken) =>
            TryReserveAsync(accountId, requestId, requestHash, 1, cancellationToken);

        public Task<AiCreditReservation?> TryReserveAsync(
            Guid accountId,
            string requestId,
            string requestHash,
            int creditCost,
            CancellationToken cancellationToken)
        {
            ReservedCreditCosts.Add(creditCost);
            return Task.FromResult<AiCreditReservation?>(new AiCreditReservation(requestId));
        }

        public Task<bool> CompleteAsync(
            Guid accountId,
            AiCreditReservation reservation,
            bool accepted,
            int settledCreditCost,
            CancellationToken cancellationToken)
        {
            Settlements.Add(accepted);
            SettledCreditCosts.Add(settledCreditCost);
            return Task.FromResult(true);
        }

        public Task<AiCreditBalance> GetBalanceAsync(Guid accountId, CancellationToken cancellationToken) =>
            Task.FromResult(AiCreditBalance.Empty);
    }

    private sealed class FixedProvider(AiProviderResponse? response) : IAiProvider
    {
        public int EstimateMaximumCreditCost(AiProviderRequest request) => 10;

        public Task<AiProviderResponse?> CompleteAsync(AiProviderRequest request, CancellationToken cancellationToken) =>
            Task.FromResult(response);
    }

    private sealed class CapturingDelayedProvider : IAiProvider
    {
        public AiProviderRequest? Request { get; private set; }

        public int EstimateMaximumCreditCost(AiProviderRequest request) => 10;

        public async Task<AiProviderResponse?> CompleteAsync(
            AiProviderRequest request,
            CancellationToken cancellationToken)
        {
            Request = request;
            await Task.Delay(TimeSpan.FromSeconds(1), cancellationToken);
            return new AiProviderResponse("language_alternative", "Bounded wording.", ["OBS-WARM-01"]);
        }
    }
}
