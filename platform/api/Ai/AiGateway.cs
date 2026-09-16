using System.Security.Cryptography;
using System.Text;
using System.Collections.Concurrent;
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
    bool OptedIn);

public sealed record AiProviderRequest(
    AiAssistPurpose Purpose,
    string RedactedInput,
    string Locale,
    string PromptVersion);

public sealed record AiProviderResponse(string Kind, string Text);

public interface IAiProvider
{
    Task<AiProviderResponse?> CompleteAsync(
        AiProviderRequest request,
        CancellationToken cancellationToken);
}

public interface IAiQuota
{
    bool TryConsume(Guid accountId);
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
    public Task<AiProviderResponse?> CompleteAsync(
        AiProviderRequest request,
        CancellationToken cancellationToken) =>
        Task.FromResult<AiProviderResponse?>(null);
}

public sealed class InMemoryAiQuota : IAiQuota
{
    private readonly ConcurrentDictionary<Guid, QuotaWindow> windows = new();
    private readonly int limit;
    private readonly TimeSpan window;

    public InMemoryAiQuota(int limit = 10, TimeSpan? window = null)
    {
        this.limit = limit;
        this.window = window ?? TimeSpan.FromHours(1);
    }

    public bool TryConsume(Guid accountId)
    {
        if (accountId == Guid.Empty) return false;
        var now = DateTimeOffset.UtcNow;
        while (true)
        {
            var current = windows.GetOrAdd(accountId, _ => new QuotaWindow(now, 0));
            if (now - current.Start >= window)
            {
                if (windows.TryUpdate(accountId, new QuotaWindow(now, 1), current)) return true;
                continue;
            }

            if (current.Count >= limit) return false;
            if (windows.TryUpdate(accountId, current with { Count = current.Count + 1 }, current)) return true;
        }
    }

    private sealed record QuotaWindow(DateTimeOffset Start, int Count);
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
    AiAuditMetadata Audit);

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
    private readonly IAiProvider provider;
    private readonly IAiQuota quota;
    private readonly TimeSpan timeout;

    public AiGateway(IAiProvider provider, IAiQuota quota, TimeSpan? timeout = null)
    {
        this.provider = provider;
        this.quota = quota;
        this.timeout = timeout ?? TimeSpan.FromSeconds(5);
    }

    public async Task<AiGatewayResult> GenerateAsync(
        Guid accountId,
        AiAssistRequest request,
        CancellationToken cancellationToken)
    {
        if (request is null
            || !Enum.IsDefined(request.Purpose)
            || string.IsNullOrWhiteSpace(request.Locale)
            || request.Locale.Length > 32)
            return Fallback("INVALID_AI_REQUEST", "invalid_request");
        if (!request.OptedIn)
            return Fallback("AI_OPT_IN_REQUIRED", "not_requested");
        if (accountId == Guid.Empty || string.IsNullOrWhiteSpace(request.Input) || request.Input.Length > 4000)
            return Fallback("INVALID_AI_REQUEST", "invalid_request");
        if (!quota.TryConsume(accountId))
            return Fallback("AI_QUOTA_EXCEEDED", "quota_exceeded");

        var redacted = AiRedactor.Redact(request.Input);
        using var timeoutSource = CancellationTokenSource.CreateLinkedTokenSource(cancellationToken);
        timeoutSource.CancelAfter(timeout);
        AiProviderResponse? response;
        try
        {
            response = await provider.CompleteAsync(
                new AiProviderRequest(request.Purpose, redacted, request.Locale, PromptVersion),
                timeoutSource.Token);
        }
        catch (OperationCanceledException) when (cancellationToken.IsCancellationRequested)
        {
            throw;
        }
        catch (OperationCanceledException) when (!cancellationToken.IsCancellationRequested)
        {
            return Fallback("AI_PROVIDER_TIMEOUT", "timeout", Hash(redacted));
        }
        catch (Exception)
        {
            return Fallback("AI_PROVIDER_UNAVAILABLE", "provider_error", Hash(redacted));
        }

        if (response is null
            || !AllowedKind(response.Kind)
            || string.IsNullOrWhiteSpace(response.Text)
            || response.Text.Length > 2000
            || AiRedactor.ContainsCredential(response.Text))
            return Fallback("AI_INVALID_RESPONSE", "invalid_response", Hash(redacted));

        return new AiGatewayResult(
            "success",
            response.Text,
            null,
            new AiAuditMetadata(PromptVersion, Hash(redacted), "configured-provider", "success"));
    }

    private static bool AllowedKind(string kind) => kind is
        "explanation" or "reflection_question" or "language_alternative";

    private static AiGatewayResult Fallback(string reason, string outcome, string? hash = null) =>
        new(
            "fallback",
            null,
            reason,
            new AiAuditMetadata(PromptVersion, hash ?? "not-recorded", null, outcome));

    private static string Hash(string value) =>
        Convert.ToHexString(SHA256.HashData(Encoding.UTF8.GetBytes(value))).ToLowerInvariant();
}
