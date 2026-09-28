using Evidrilo.Api.Ai;

namespace Evidrilo.Api.Tests;

public sealed class AiGatewayTests
{
    private static readonly Guid AccountId = Guid.Parse("123e4567-e89b-42d3-a456-426614174000");

    [Fact]
    public async Task Gateway_redacts_identifiers_before_provider_call()
    {
        var provider = new RecordingProvider(new AiProviderResponse("explanation", "A safe explanation.", ["OBS-01"]));
        var ledger = new AllowLedger();
        var gateway = new AiGateway(provider, ledger);

        var result = await gateway.GenerateAsync(
            AccountId,
            new AiAssistRequest(
                AiAssistPurpose.ExplainFeedback,
                "Explain user@example.com for 123e4567-e89b-42d3-a456-426614174000 bearer abc.def token password=secret",
                "id-ID",
                true,
                ValidContext()),
            "ai-redaction-001",
            CancellationToken.None);

        Assert.Equal("success", result.Status);
        Assert.True(ledger.ConsentEnsured);
        Assert.Equal([1], ledger.ReservedCreditCosts);
        Assert.Equal([true], ledger.Completions);
        Assert.DoesNotContain("user@example.com", provider.Request!.RedactedInput);
        Assert.DoesNotContain("123e4567-e89b-42d3-a456-426614174000", provider.Request.RedactedInput);
        Assert.DoesNotContain("bearer abc.def", provider.Request.RedactedInput, StringComparison.OrdinalIgnoreCase);
        Assert.DoesNotContain("password=secret", provider.Request.RedactedInput, StringComparison.OrdinalIgnoreCase);
    }

    [Fact]
    public async Task Gateway_rejects_malformed_provider_output_and_quota_exhaustion()
    {
        var malformed = new AiGateway(
            new RecordingProvider(new AiProviderResponse("evaluator_truth", "unsafe")),
            new AllowLedger());
        var result = await malformed.GenerateAsync(AccountId, ValidRequest(), CancellationToken.None);
        Assert.Equal("AI_INVALID_RESPONSE", result.ReasonCode);

        var exhausted = new AiGateway(
            new RecordingProvider(new AiProviderResponse("explanation", "safe", ["OBS-01"])),
            new DenyLedger());
        var quotaResult = await exhausted.GenerateAsync(AccountId, ValidRequest(), CancellationToken.None);
        Assert.Equal("AI_QUOTA_EXCEEDED", quotaResult.ReasonCode);
    }

    [Fact]
    public async Task Gateway_rejects_provider_output_that_does_not_match_the_requested_purpose()
    {
        var ledger = new AllowLedger();
        var gateway = new AiGateway(
            new RecordingProvider(new AiProviderResponse("reflection_question", "What evidence supports this?", ["OBS-01"])),
            ledger);

        var result = await gateway.GenerateAsync(
            AccountId,
            ValidRequest() with { Purpose = AiAssistPurpose.ExplainFeedback },
            CancellationToken.None);

        Assert.Equal("AI_OUTPUT_PURPOSE_MISMATCH", result.ReasonCode);
        Assert.Equal([false], ledger.Completions);
    }

    [Fact]
    public async Task Gateway_times_out_to_typed_fallback()
    {
        var provider = new DelayedProvider();
        var ledger = new AllowLedger();
        var gateway = new AiGateway(provider, ledger, TimeSpan.FromMilliseconds(10));

        var result = await gateway.GenerateAsync(AccountId, ValidRequest(), CancellationToken.None);

        Assert.Equal("fallback", result.Status);
        Assert.Equal("AI_PROVIDER_TIMEOUT", result.ReasonCode);
        Assert.Null(result.Text);
        Assert.Equal([false], ledger.Completions);
    }

    [Fact]
    public async Task Gateway_treats_a_disabled_provider_as_unavailable_and_releases_credit()
    {
        var ledger = new AllowLedger();
        var gateway = new AiGateway(new NullProvider(), ledger);

        var result = await gateway.GenerateAsync(AccountId, ValidRequest(), CancellationToken.None);

        Assert.Equal("AI_PROVIDER_UNAVAILABLE", result.ReasonCode);
        Assert.Equal([false], ledger.Completions);
    }

    [Fact]
    public async Task Gateway_does_not_return_provider_success_when_reservation_settlement_is_fenced()
    {
        var ledger = new AllowLedger(completionResult: false);
        var gateway = new AiGateway(
            new RecordingProvider(new AiProviderResponse("reflection_question", "What supports the claim?", ["OBS-01"])),
            ledger);

        var result = await gateway.GenerateAsync(AccountId, ValidRequest(), CancellationToken.None);

        Assert.Equal("fallback", result.Status);
        Assert.Equal("AI_RESERVATION_EXPIRED", result.ReasonCode);
        Assert.Null(result.Text);
        Assert.Equal([true], ledger.Completions);
    }

    [Fact]
    public async Task Gateway_propagates_caller_cancellation_instead_of_turning_it_into_provider_fallback()
    {
        var provider = new CancellationProvider();
        var ledger = new AllowLedger();
        var gateway = new AiGateway(provider, ledger);
        using var cancellation = new CancellationTokenSource();
        cancellation.Cancel();

        await Assert.ThrowsAnyAsync<OperationCanceledException>(() => gateway.GenerateAsync(
            AccountId,
            ValidRequest(),
            cancellation.Token));
        Assert.Equal([false], ledger.Completions);
    }

    private static AiAssistRequest ValidRequest() => new(
        AiAssistPurpose.ReflectionQuestion,
        "Give me one reflection question about evidence scope.",
        "id-ID",
        true,
        ValidContext());

    private static AiAssistContextRequest ValidContext() => new(
        "M0_T2:1",
        "MISSING_EVIDENCE",
        "ACTION_REQUIRED",
        ["OBS-01"],
        ["LIMIT-01"],
        "The claim stays bounded.",
        "LIMITED_COMPARISON",
        "State the limitation.");

    private sealed class AllowLedger(bool completionResult = true) : IAiCreditLedger
    {
        public bool ConsentEnsured { get; private set; }

        public List<bool> Completions { get; } = [];

        public List<int> ReservedCreditCosts { get; } = [];

        public Task EnsureConsentAsync(
            Guid accountId,
            string consentVersion,
            CancellationToken cancellationToken)
        {
            ConsentEnsured = true;
            return Task.CompletedTask;
        }

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
            CancellationToken cancellationToken) =>
            ReserveAsync(requestId, creditCost);

        private Task<AiCreditReservation?> ReserveAsync(string requestId, int creditCost)
        {
            ReservedCreditCosts.Add(creditCost);
            return Task.FromResult<AiCreditReservation?>(new AiCreditReservation(requestId));
        }

        public Task<bool> CompleteAsync(
            Guid accountId,
            AiCreditReservation reservation,
            bool accepted,
            CancellationToken cancellationToken)
        {
            Completions.Add(accepted);
            return Task.FromResult(completionResult);
        }

        public Task<AiCreditBalance> GetBalanceAsync(
            Guid accountId,
            CancellationToken cancellationToken) =>
            Task.FromResult(AiCreditBalance.Empty);
    }

    private sealed class DenyLedger : IAiCreditLedger
    {
        public Task EnsureConsentAsync(
            Guid accountId,
            string consentVersion,
            CancellationToken cancellationToken) => Task.CompletedTask;

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
            CancellationToken cancellationToken) =>
            Task.FromResult<AiCreditReservation?>(null);

        public Task<bool> CompleteAsync(
            Guid accountId,
            AiCreditReservation reservation,
            bool accepted,
            CancellationToken cancellationToken) => Task.FromResult(false);

        public Task<AiCreditBalance> GetBalanceAsync(
            Guid accountId,
            CancellationToken cancellationToken) =>
            Task.FromResult(AiCreditBalance.Empty);
    }

    private sealed class RecordingProvider : IAiProvider
    {
        private readonly AiProviderResponse response;

        public RecordingProvider(AiProviderResponse response)
        {
            this.response = response;
        }

        public AiProviderRequest? Request { get; private set; }

        public Task<AiProviderResponse?> CompleteAsync(AiProviderRequest request, CancellationToken cancellationToken)
        {
            Request = request;
            return Task.FromResult<AiProviderResponse?>(response);
        }
    }

    private sealed class DelayedProvider : IAiProvider
    {
        public async Task<AiProviderResponse?> CompleteAsync(AiProviderRequest request, CancellationToken cancellationToken)
        {
            await Task.Delay(TimeSpan.FromSeconds(1), cancellationToken);
            return new AiProviderResponse("explanation", "late", ["OBS-01"]);
        }
    }

    private sealed class NullProvider : IAiProvider
    {
        public Task<AiProviderResponse?> CompleteAsync(AiProviderRequest request, CancellationToken cancellationToken) =>
            Task.FromResult<AiProviderResponse?>(null);
    }

    private sealed class CancellationProvider : IAiProvider
    {
        public Task<AiProviderResponse?> CompleteAsync(AiProviderRequest request, CancellationToken cancellationToken) =>
            Task.FromCanceled<AiProviderResponse?>(cancellationToken);
    }
}
