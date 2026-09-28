using Evidrilo.Api.Ai;

namespace Evidrilo.Api.Tests;

public sealed class AiOutputValidatorTests
{
    private static readonly string[] AllowedAnchors = ["OBS-01", "LIMIT-01"];

    [Fact]
    public void Accepts_output_that_cites_only_allowed_context_anchors()
    {
        var result = AiOutputValidator.Validate(
            new AiProviderResponse("explanation", "The conclusion is limited.", ["OBS-01"]),
            AllowedAnchors);

        Assert.True(result.IsValid);
        Assert.Equal(["OBS-01"], result.ReferencedAnchorIds);
    }

    [Fact]
    public void Rejects_output_without_structured_anchor_references()
    {
        var result = AiOutputValidator.Validate(
            new AiProviderResponse("explanation", "The conclusion is limited.", []),
            AllowedAnchors);

        Assert.False(result.IsValid);
        Assert.Equal("AI_OUTPUT_MISSING_ANCHORS", result.ReasonCode);
    }

    [Fact]
    public void Rejects_output_that_introduces_an_anchor_outside_the_context()
    {
        var result = AiOutputValidator.Validate(
            new AiProviderResponse("explanation", "The conclusion is limited.", ["OBS-99"]),
            AllowedAnchors);

        Assert.False(result.IsValid);
        Assert.Equal("AI_OUTPUT_UNKNOWN_ANCHOR", result.ReasonCode);
    }

    [Fact]
    public void Rejects_duplicate_anchor_references()
    {
        var result = AiOutputValidator.Validate(
            new AiProviderResponse("explanation", "The conclusion is limited.", ["OBS-01", "OBS-01"]),
            AllowedAnchors);

        Assert.False(result.IsValid);
        Assert.Equal("AI_OUTPUT_INVALID_ANCHORS", result.ReasonCode);
    }

    [Fact]
    public void Rejects_output_kind_that_does_not_match_the_requested_purpose()
    {
        var result = AiOutputValidator.Validate(
            AiAssistPurpose.ExplainFeedback,
            new AiProviderResponse("reflection_question", "What evidence supports this claim?", ["OBS-01"]),
            AllowedAnchors);

        Assert.False(result.IsValid);
        Assert.Equal("AI_OUTPUT_PURPOSE_MISMATCH", result.ReasonCode);
    }
}
