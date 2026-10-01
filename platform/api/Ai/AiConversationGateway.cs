namespace Evidrilo.Api.Ai;

public sealed record AiConversationGatewayRequest(
    AiAssistPurpose Purpose,
    string Prompt,
    string Locale,
    IReadOnlyCollection<string> AllowedAnchorIds,
    AiConversationDraftSnapshot CurrentDraft);

public sealed record AiConversationGatewayResult(
    string Status,
    string? Kind,
    string? Text,
    string? ReasonCode,
    IReadOnlyList<string> GroundedAnchorIds,
    AiDraftProposal? Proposal,
    AiAuditMetadata Audit)
{
    public int CreditCost { get; init; }
}

public sealed class AiConversationGateway
{
    public const string PromptVersion = "conversation.v1";

    private readonly IAiProvider provider;
    private readonly IAiCreditLedger creditLedger;
    private readonly AiProviderOptions pricing;
    private readonly TimeSpan timeout;

    public AiConversationGateway(
        IAiProvider provider,
        IAiCreditLedger creditLedger,
        TimeSpan? timeout = null,
        AiProviderOptions? pricing = null)
    {
        this.provider = provider;
        this.creditLedger = creditLedger;
        this.pricing = pricing ?? AiProviderOptions.DefaultPricing;
        this.timeout = timeout ?? this.pricing.Timeout;
    }

    public async Task<AiConversationGatewayResult> GenerateAsync(
        Guid accountId,
        AiConversationGatewayRequest request,
        string requestId,
        CancellationToken cancellationToken)
    {
        if (request is null
            || !Enum.IsDefined(request.Purpose)
            || string.IsNullOrWhiteSpace(request.Locale)
            || request.Locale.Length > 32
            || string.IsNullOrWhiteSpace(request.Prompt)
            || request.Prompt.Length > AiConversationPrompt.MaxPromptLength
            || accountId == Guid.Empty
            || !IsValidRequestId(requestId))
            return Fallback("INVALID_AI_CONVERSATION", "invalid_request");

        var redacted = AiRedactor.Redact(request.Prompt);
        var requestHash = AiConversationFingerprint.RequestHash(request.Purpose, request.Locale, redacted);
        var providerRequest = new AiProviderRequest(
            accountId,
            requestId,
            request.Purpose,
            redacted,
            request.Locale,
            PromptVersion,
            request.AllowedAnchorIds.ToHashSet(StringComparer.Ordinal));
        int maximumCreditCost;
        try
        {
            maximumCreditCost = provider.EstimateMaximumCreditCost(providerRequest);
            if (maximumCreditCost is < 1 or > AiCreditPricing.MaximumCreditsPerRequest)
                throw new AiProviderFailureException("AI_PROVIDER_COST_LIMIT", "request_cost_limit");
        }
        catch (AiProviderFailureException exception)
        {
            return Fallback(exception.ReasonCode, exception.Outcome, requestHash);
        }
        catch (Exception)
        {
            return Fallback("AI_PROVIDER_COST_LIMIT", "request_cost_limit", requestHash);
        }
        await creditLedger.EnsureConsentAsync(accountId, AiGateway.ConsentVersion, cancellationToken);
        var reservation = await creditLedger.TryReserveAsync(
            accountId,
            requestId,
            requestHash,
            maximumCreditCost,
            cancellationToken);
        if (reservation is null)
            return Fallback("AI_QUOTA_EXCEEDED", "quota_exceeded", requestHash);
        if (reservation.IsReplay)
        {
            return Fallback(
                reservation.ExistingStatus == "reserved" ? "AI_REQUEST_IN_PROGRESS" : "AI_IDEMPOTENCY_REPLAY",
                reservation.ExistingStatus == "reserved" ? "in_progress" : "replay",
                requestHash);
        }

        using var timeoutSource = CancellationTokenSource.CreateLinkedTokenSource(cancellationToken);
        timeoutSource.CancelAfter(timeout);
        AiProviderResponse? response;
        try
        {
            response = await provider.CompleteAsync(
                providerRequest,
                timeoutSource.Token);
        }
        catch (OperationCanceledException) when (cancellationToken.IsCancellationRequested)
        {
            await ReleaseAsync(accountId, reservation);
            throw;
        }
        catch (OperationCanceledException)
        {
            return await ReleaseAndFallbackAsync(accountId, reservation, "AI_PROVIDER_TIMEOUT", "timeout", requestHash);
        }
        catch (AiProviderFailureException exception)
        {
            return await ReleaseAndFallbackAsync(
                accountId,
                reservation,
                exception.ReasonCode,
                exception.Outcome,
                requestHash);
        }
        catch (Exception)
        {
            return await ReleaseAndFallbackAsync(accountId, reservation, "AI_PROVIDER_UNAVAILABLE", "provider_error", requestHash);
        }

        if (response is null)
            return await ReleaseAndFallbackAsync(accountId, reservation, "AI_PROVIDER_UNAVAILABLE", "provider_unavailable", requestHash);

        var validation = AiConversationOutputValidator.Validate(
            request.Purpose,
            response,
            request.AllowedAnchorIds,
            request.CurrentDraft);
        if (!validation.IsValid)
            return await ReleaseAndFallbackAsync(
                accountId,
                reservation,
                validation.ReasonCode ?? "AI_INVALID_RESPONSE",
                "invalid_response",
                requestHash);

        if (response.Usage is null || !response.Usage.IsValid)
            return await ReleaseAndFallbackAsync(
                accountId,
                reservation,
                "AI_PROVIDER_INVALID_USAGE",
                "usage_invalid",
                requestHash);

        int actualCreditCost;
        try
        {
            actualCreditCost = AiCreditPricing.CreditsForCostUsd(pricing.EstimateActualCostUsd(response.Usage));
        }
        catch (Exception)
        {
            return await ReleaseAndFallbackAsync(
                accountId,
                reservation,
                "AI_PROVIDER_INVALID_USAGE",
                "usage_invalid",
                requestHash);
        }
        if (actualCreditCost > maximumCreditCost)
            return await ReleaseAndFallbackAsync(
                accountId,
                reservation,
                "AI_PROVIDER_COST_LIMIT",
                "provider_usage_exceeded_reservation",
                requestHash);

        if (!await creditLedger.CompleteAsync(
                accountId,
                reservation,
                accepted: true,
                actualCreditCost,
                cancellationToken))
            return Fallback("AI_RESERVATION_EXPIRED", "reservation_expired", requestHash);

        return new AiConversationGatewayResult(
            "success",
            response.Kind,
            response.Text,
            null,
            validation.ReferencedAnchorIds,
            validation.Proposal,
            new AiAuditMetadata(PromptVersion, requestHash, "configured-provider", "success"))
        {
            CreditCost = actualCreditCost,
        };
    }

    private async Task<AiConversationGatewayResult> ReleaseAndFallbackAsync(
        Guid accountId,
        AiCreditReservation reservation,
        string reason,
        string outcome,
        string requestHash)
    {
        await ReleaseAsync(accountId, reservation);
        return Fallback(reason, outcome, requestHash);
    }

    private Task ReleaseAsync(Guid accountId, AiCreditReservation reservation) =>
        creditLedger.CompleteAsync(
            accountId,
            reservation,
            accepted: false,
            settledCreditCost: 0,
            CancellationToken.None);

    private static bool IsValidRequestId(string value) =>
        value.Length is >= 8 and <= 128
        && value.All(character =>
            character is >= 'A' and <= 'Z'
                or >= 'a' and <= 'z'
                or >= '0' and <= '9'
                or '_' or '-');

    private static AiConversationGatewayResult Fallback(string reason, string outcome, string? hash = null) =>
        new(
            "fallback",
            null,
            null,
            reason,
            Array.Empty<string>(),
            null,
            new AiAuditMetadata(PromptVersion, hash ?? "not-recorded", null, outcome));

}
