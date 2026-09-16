using Evidrilo.Api.Sync;

namespace Evidrilo.Api.Tests;

public sealed class SyncCommandTests
{
    [Theory]
    [InlineData(null, "CASE_VERSION_NOT_FOUND")]
    [InlineData("draft", "CASE_VERSION_NOT_PUBLISHED")]
    [InlineData("review", "CASE_VERSION_NOT_PUBLISHED")]
    [InlineData("retired", "CASE_VERSION_NOT_PUBLISHED")]
    public void Non_published_case_versions_are_rejected_by_the_sync_policy(string? status, string reason)
    {
        Assert.Equal(reason, SyncCaseVersionPolicy.RejectionReason(status));
    }

    [Fact]
    public void Published_case_versions_are_accepted_by_the_sync_policy()
    {
        Assert.Null(SyncCaseVersionPolicy.RejectionReason("published"));
    }

    [Fact]
    public void Valid_command_is_accepted_without_exposing_snapshot_content()
    {
        var command = new SyncCommand(
            Guid.Parse("123e4567-e89b-42d3-a456-426614174000"),
            Guid.Parse("123e4567-e89b-42d3-a456-426614174001"),
            "M0_T2:1",
            SyncCommandType.AttemptSubmitted,
            0,
            DateTimeOffset.Parse("2026-09-10T09:00:00Z"),
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");

        var result = SyncCommandValidator.Validate(command);

        Assert.True(result.IsValid);
        Assert.Null(result.ErrorCode);
        Assert.DoesNotContain("claim", result.ToString(), StringComparison.OrdinalIgnoreCase);
    }

    [Theory]
    [InlineData("bad-digest", "INVALID_SNAPSHOT_DIGEST")]
    public void Invalid_digest_is_rejected_without_echoing_the_value(string digest, string code)
    {
        var command = ValidCommand() with { SnapshotDigest = digest };

        var result = SyncCommandValidator.Validate(command);

        Assert.False(result.IsValid);
        Assert.Equal(code, result.ErrorCode);
        Assert.DoesNotContain(digest, result.Message, StringComparison.Ordinal);
    }

    [Fact]
    public void Digest_length_is_bounded_without_echoing_the_value()
    {
        var digest = new string('a', 63);

        var result = SyncCommandValidator.Validate(ValidCommand() with { SnapshotDigest = digest });

        Assert.False(result.IsValid);
        Assert.Equal("INVALID_SNAPSHOT_DIGEST", result.ErrorCode);
        Assert.DoesNotContain(digest, result.Message, StringComparison.Ordinal);
    }

    [Fact]
    public void Revision_number_and_case_version_are_bounded()
    {
        var tooManyRevisions = SyncCommandValidator.Validate(ValidCommand() with { RevisionNumber = 2 });
        var invalidCaseVersion = SyncCommandValidator.Validate(ValidCommand() with { CaseVersionId = "case with spaces" });

        Assert.Equal("INVALID_REVISION", tooManyRevisions.ErrorCode);
        Assert.Equal("INVALID_CASE_VERSION", invalidCaseVersion.ErrorCode);
    }

    [Fact]
    public void Case_version_trailing_newline_is_rejected()
    {
        var result = SyncCommandValidator.Validate(ValidCommand() with { CaseVersionId = "M0_T2:1\n" });

        Assert.False(result.IsValid);
        Assert.Equal("INVALID_CASE_VERSION", result.ErrorCode);
    }

    [Fact]
    public void Digest_trailing_newline_is_rejected()
    {
        var result = SyncCommandValidator.Validate(ValidCommand() with
        {
            SnapshotDigest = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef\n",
        });

        Assert.False(result.IsValid);
        Assert.Equal("INVALID_SNAPSHOT_DIGEST", result.ErrorCode);
    }

    [Fact]
    public void Missing_client_occurrence_time_is_rejected()
    {
        var result = SyncCommandValidator.Validate(ValidCommand() with
        {
            ClientOccurredAt = default,
        });

        Assert.False(result.IsValid);
        Assert.Equal("INVALID_CLIENT_OCCURRED_AT", result.ErrorCode);
    }

    private static SyncCommand ValidCommand() => new(
        Guid.Parse("123e4567-e89b-42d3-a456-426614174000"),
        Guid.Parse("123e4567-e89b-42d3-a456-426614174001"),
        "M0_T2:1",
        SyncCommandType.AttemptSubmitted,
        0,
        DateTimeOffset.UtcNow,
        "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
}
