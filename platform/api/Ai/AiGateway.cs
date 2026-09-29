using System.Security.Cryptography;
using System.Text;
using System.Text.RegularExpressions;
using System.Text.Json.Serialization;

namespace Evidrilo.Api.Ai;

public enum AiAssistPurpose
{
    ExplainFeedback,
    ReflectionQuestion,
    LanguageAlternative,
}

public sealed record AiAssistRequest(
    [property: JsonRequired, JsonPropertyName("purpose")]
    AiAssistPurpose Purpose,
    [property: JsonPropertyName("input")]
    string Input,
    [property: JsonPropertyName("locale")]
    string Locale,
    [property: JsonPropertyName("optedIn")]
    bool OptedIn,
    [property: JsonPropertyName("context")]
    AiAssistContextRequest? Context = null);

public sealed record AiProviderRequest(
    Guid AccountId,
    string RequestId,
    AiAssistPurpose Purpose,
    string RedactedInput,
    string Locale,
    string PromptVersion,
    IReadOnlySet<string>? AllowedAnchorIds = null);

public sealed record AiProviderResponse(
    string Kind,
    string Text,
    IReadOnlyList<string>? ReferencedAnchorIds = null)
{
    public AiDraftProposal? Proposal { get; init; }

    public AiProviderTokenUsage? Usage { get; init; }
}

public interface IAiProvider
{
    int EstimateMaximumCreditCost(AiProviderRequest request);

    Task<AiProviderResponse?> CompleteAsync(
        AiProviderRequest request,
        CancellationToken cancellationToken);
}

public sealed record AiCreditReservation(
    string RequestId,
    bool IsReplay = false,
    string? ExistingStatus = null);

public sealed record AiCreditGrantBalance(
    string GrantKind,
    string GrantKey,
    int Granted,
    int Reserved,
    int Consumed,
    DateTimeOffset? ExpiresAt)
{
    public int Available => Math.Max(0, Granted - Reserved - Consumed);
}

public sealed record AiCreditBalance(
    bool ConsentRecorded,
    IReadOnlyList<AiCreditGrantBalance> Grants)
{
    public static AiCreditBalance Empty { get; } = new(false, Array.Empty<AiCreditGrantBalance>());

    public int Available => Grants.Sum(grant => grant.Available);
}

public interface IAiCreditLedger
{
    Task EnsureConsentAsync(
        Guid accountId,
        string consentVersion,
        CancellationToken cancellationToken);

    Task<AiCreditReservation?> TryReserveAsync(
        Guid accountId,
        string requestId,
        string requestHash,
        CancellationToken cancellationToken);

    // Legacy one-credit adapters remain valid; weighted adapters override this overload.
    Task<AiCreditReservation?> TryReserveAsync(
        Guid accountId,
        string requestId,
        string requestHash,
        int creditCost,
        CancellationToken cancellationToken) =>
        creditCost == 1
            ? TryReserveAsync(accountId, requestId, requestHash, cancellationToken)
            : throw new NotSupportedException("This AI credit ledger does not support weighted reservations.");

    Task<bool> CompleteAsync(
        Guid accountId,
        AiCreditReservation reservation,
        bool accepted,
        int settledCreditCost,
        CancellationToken cancellationToken);

    Task<AiCreditBalance> GetBalanceAsync(
        Guid accountId,
        CancellationToken cancellationToken);
}

public interface IAiAuditStore
{
    Task RecordAsync(
        Guid accountId,
        string requestId,
        AiAuditMetadata metadata,
        string? reasonCode,
        CancellationToken cancellationToken);
}

public sealed class DisabledAiProvider : IAiProvider
{
    public int EstimateMaximumCreditCost(AiProviderRequest request) => 1;

    public Task<AiProviderResponse?> CompleteAsync(
        AiProviderRequest request,
        CancellationToken cancellationToken) =>
        Task.FromResult<AiProviderResponse?>(null);
}

public sealed record AiAuditMetadata(
    string PromptVersion,
    string InputHash,
    string? Provider,
    string Outcome);

public sealed record AiGatewayResult(
    string Status,
    string? Text,
    string? ReasonCode,
    AiAuditMetadata Audit)
{
    public IReadOnlyList<string> GroundedAnchorIds { get; init; } = Array.Empty<string>();
}

public static partial class AiRedactor
{
    [GeneratedRegex(@"(?i)bearer\s+[a-z0-9._~+/-]+=*", RegexOptions.CultureInvariant)]
    private static partial Regex BearerTokenRegex();

    [GeneratedRegex(@"(?i)\b[\w.%+-]+@[\w.-]+\.[a-z]{2,}\b", RegexOptions.CultureInvariant)]
    private static partial Regex EmailRegex();

    [GeneratedRegex(@"\b[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}\b", RegexOptions.IgnoreCase | RegexOptions.CultureInvariant)]
    private static partial Regex GuidRegex();

    [GeneratedRegex(@"(?i)(password|secret|api[_-]?key|service[_-]?role)\s*[=:]\s*[^\s,;]+", RegexOptions.CultureInvariant)]
    private static partial Regex SecretAssignmentRegex();

    public static string Redact(string input)
    {
        var output = BearerTokenRegex().Replace(input, "[REDACTED_TOKEN]");
        output = EmailRegex().Replace(output, "[REDACTED_EMAIL]");
        output = GuidRegex().Replace(output, "[REDACTED_ID]");
        return SecretAssignmentRegex().Replace(output, "$1=[REDACTED]");
    }

    public static bool ContainsCredential(string value) =>
        BearerTokenRegex().IsMatch(value)
        || EmailRegex().IsMatch(value)
        || SecretAssignmentRegex().IsMatch(value);
}

public sealed class AiGateway
{
    public const string PromptVersion = "assist.v1";
    public const string ConsentVersion = "ai.v1";
    private readonly IAiProvider provider;
    private readonly IAiCreditLedger creditLedger;
    private readonly AiProviderOptions pricing;
    private readonly TimeSpan timeout;

    public AiGateway(
        IAiProvider provider,
        IAiCreditLedger creditLedger,
        TimeSpan? timeout = null,
        AiProviderOptions? pricing = null)
    {
        this.provider = provider;
        this.creditLedger = creditLedger;
        this.pricing = pricing ?? AiProviderOptions.DefaultPricing;
        this.timeout = timeout ?? TimeSpan.FromSeconds(5);
    }

    public Task<AiGatewayResult> GenerateAsync(
        Guid accountId,
        AiAssistRequest request,
        CancellationToken cancellationToken) => GenerateAsync(
            accountId,
            request,
            Guid.NewGuid().ToString("N"),
            cancellationToken);

    public async Task<AiGatewayResult> GenerateAsync(
        Guid accountId,
        AiAssistRequest request,
        string requestId,
        CancellationToken cancellationToken)
    {
        if (request is null
            || !Enum.IsDefined(request.Purpose)
            || string.IsNullOrWhiteSpace(request.Locale)
            || request.Locale.Length > 32
            || !IsValidRequestId(requestId))
            return Fallback("INVALID_AI_REQUEST", "invalid_request");
        if (!request.OptedIn)
            return Fallback("AI_OPT_IN_REQUIRED", "not_requested");
        if (accountId == Guid.Empty || string.IsNullOrWhiteSpace(request.Input) || request.Input.Length > 4000)
            return Fallback("INVALID_AI_REQUEST", "invalid_request");
        if (request.Context is null
            || request.Context.AnchorIds is null
            || request.Context.AnchorIds.Count is < 1 or > 32)
            return Fallback("INVALID_AI_CONTEXT", "invalid_context");

        var redacted = AiRedactor.Redact(request.Input);
        var contextFingerprint = string.Join(
            "|",
            request.Context.CaseVersionId,
            request.Context.FeedbackCode,
            request.Context.FeedbackStatus,
            string.Join(",", request.Context.AnchorIds),
            string.Join(",", request.Context.LimitationIds));
        var requestHash = Hash($"{request.Purpose}|{request.Locale}|{contextFingerprint}|{redacted}");
        var providerRequest = new AiProviderRequest(
            accountId,
            requestId,
            request.Purpose,
            redacted,
            request.Locale,
            PromptVersion,
            request.Context.AnchorIds
                .Concat(request.Context.LimitationIds)
                .ToHashSet(StringComparer.Ordinal));
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
        await creditLedger.EnsureConsentAsync(accountId, ConsentVersion, cancellationToken);
        var reservation = await creditLedger.TryReserveAsync(
            accountId,
            requestId,
            requestHash,
            maximumCreditCost,
            cancellationToken);
        if (reservation is null)
            return Fallback("AI_QUOTA_EXCEEDED", "quota_exceeded");
        if (reservation.IsReplay)
        {
            return Fallback(
                reservation.ExistingStatus == "reserved"
                    ? "AI_REQUEST_IN_PROGRESS"
                    : "AI_IDEMPOTENCY_REPLAY",
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
        catch (OperationCanceledException) when (!cancellationToken.IsCancellationRequested)
        {
            return await ReleaseAndFallbackAsync(
                accountId,
                reservation,
                "AI_PROVIDER_TIMEOUT",
                "timeout",
                requestHash);
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
            return await ReleaseAndFallbackAsync(
                accountId,
                reservation,
                "AI_PROVIDER_UNAVAILABLE",
                "provider_error",
                requestHash);
        }

        if (response is null)
            return await ReleaseAndFallbackAsync(
                accountId,
                reservation,
                "AI_PROVIDER_UNAVAILABLE",
                "provider_unavailable",
                requestHash);

        var validation = AiOutputValidator.Validate(
            request.Purpose,
            response,
            request.Context.AnchorIds
                .Concat(request.Context.LimitationIds)
                .ToHashSet(StringComparer.Ordinal));
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

        var settled = await creditLedger.CompleteAsync(
            accountId,
            reservation,
            accepted: true,
            actualCreditCost,
            cancellationToken);
        if (!settled)
            return Fallback("AI_RESERVATION_EXPIRED", "reservation_expired", requestHash);

        return new AiGatewayResult(
            "success",
            response.Text,
            null,
            new AiAuditMetadata(PromptVersion, requestHash, "configured-provider", "success"))
        {
            GroundedAnchorIds = validation.ReferencedAnchorIds,
        };
    }

    private async Task<AiGatewayResult> ReleaseAndFallbackAsync(
        Guid accountId,
        AiCreditReservation reservation,
        string reason,
        string outcome,
        string inputHash)
    {
        await ReleaseAsync(accountId, reservation);
        return Fallback(reason, outcome, inputHash);
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

    private static AiGatewayResult Fallback(string reason, string outcome, string? hash = null) =>
        new(
            "fallback",
            null,
            reason,
            new AiAuditMetadata(PromptVersion, hash ?? "not-recorded", null, outcome));

    private static string Hash(string value) =>
        Convert.ToHexString(SHA256.HashData(Encoding.UTF8.GetBytes(value))).ToLowerInvariant();
}
