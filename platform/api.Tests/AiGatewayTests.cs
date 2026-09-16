using Evidrilo.Api.Ai;

namespace Evidrilo.Api.Tests;

public sealed class AiGatewayTests
{
    private static readonly Guid AccountId = Guid.Parse("123e4567-e89b-42d3-a456-426614174000");

    [Fact]
    public async Task Gateway_redacts_identifiers_before_provider_call()
    {
        var provider = new RecordingProvider(new AiProviderResponse("explanation", "A safe explanation."));
        var gateway = new AiGateway(provider, new AllowQuota());

        var result = await gateway.GenerateAsync(
            AccountId,
            new AiAssistRequest(
                AiAssistPurpose.ExplainFeedback,
                "Explain user@example.com for 123e4567-e89b-42d3-a456-426614174000 bearer abc.def token password=secret",
                "id-ID",
                true),
            CancellationToken.None);

        Assert.Equal("success", result.Status);
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
            new AllowQuota());
        var result = await malformed.GenerateAsync(AccountId, ValidRequest(), CancellationToken.None);
        Assert.Equal("AI_INVALID_RESPONSE", result.ReasonCode);

        var exhausted = new AiGateway(
            new RecordingProvider(new AiProviderResponse("explanation", "safe")),
            new DenyQuota());
        var quotaResult = await exhausted.GenerateAsync(AccountId, ValidRequest(), CancellationToken.None);
        Assert.Equal("AI_QUOTA_EXCEEDED", quotaResult.ReasonCode);
    }

    [Fact]
    public async Task Gateway_times_out_to_typed_fallback()
    {
        var provider = new DelayedProvider();
        var gateway = new AiGateway(provider, new AllowQuota(), TimeSpan.FromMilliseconds(10));

        var result = await gateway.GenerateAsync(AccountId, ValidRequest(), CancellationToken.None);

        Assert.Equal("fallback", result.Status);
        Assert.Equal("AI_PROVIDER_TIMEOUT", result.ReasonCode);
        Assert.Null(result.Text);
    }

    [Fact]
    public async Task Gateway_propagates_caller_cancellation_instead_of_turning_it_into_provider_fallback()
    {
        var provider = new CancellationProvider();
        var gateway = new AiGateway(provider, new AllowQuota());
        using var cancellation = new CancellationTokenSource();
        cancellation.Cancel();

        await Assert.ThrowsAnyAsync<OperationCanceledException>(() => gateway.GenerateAsync(
            AccountId,
            ValidRequest(),
            cancellation.Token));
    }

    private static AiAssistRequest ValidRequest() => new(
        AiAssistPurpose.ReflectionQuestion,
        "Give me one reflection question about evidence scope.",
        "id-ID",
        true);

    private sealed class AllowQuota : IAiQuota
    {
        public bool TryConsume(Guid accountId) => true;
    }

    private sealed class DenyQuota : IAiQuota
    {
        public bool TryConsume(Guid accountId) => false;
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
            return new AiProviderResponse("explanation", "late");
        }
    }

    private sealed class CancellationProvider : IAiProvider
    {
        public Task<AiProviderResponse?> CompleteAsync(AiProviderRequest request, CancellationToken cancellationToken) =>
            Task.FromCanceled<AiProviderResponse?>(cancellationToken);
    }
}
