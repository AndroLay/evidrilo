using System.Text.Json.Serialization;

namespace Evidrilo.Api.Sync;

public enum SyncCommandType
{
    AttemptStarted,
    AttemptSubmitted,
    RevisionRecorded,
}

public sealed record SyncCommand(
    [property: JsonPropertyName("commandId")] Guid CommandId,
    [property: JsonPropertyName("attemptId")] Guid AttemptId,
    [property: JsonPropertyName("caseVersionId")] string CaseVersionId,
    [property: JsonRequired, JsonPropertyName("commandType")] SyncCommandType CommandType,
    [property: JsonPropertyName("revisionNumber")] int RevisionNumber,
    [property: JsonPropertyName("clientOccurredAt")] DateTimeOffset ClientOccurredAt,
    [property: JsonPropertyName("snapshotDigest")] string SnapshotDigest);

public sealed record SyncPushRequest(
    [property: JsonPropertyName("schema")] string Schema,
    [property: JsonPropertyName("version")] string Version,
    [property: JsonPropertyName("commands")] IReadOnlyList<SyncCommand> Commands);

public sealed record SyncCommandResult(
    [property: JsonPropertyName("commandId")] Guid CommandId,
    [property: JsonPropertyName("outcome")] string Outcome,
    [property: JsonPropertyName("serverSequence")] long? ServerSequence = null,
    [property: JsonPropertyName("reasonCode")] string? ReasonCode = null);

public sealed record SyncPushResponse(
    [property: JsonPropertyName("schema")] string Schema,
    [property: JsonPropertyName("version")] string Version,
    [property: JsonPropertyName("results")] IReadOnlyList<SyncCommandResult> Results,
    [property: JsonPropertyName("nextCursor")] long NextCursor,
    [property: JsonPropertyName("requestId")] string RequestId);

public sealed record SyncChange(
    [property: JsonPropertyName("serverSequence")] long ServerSequence,
    [property: JsonPropertyName("attemptId")] Guid AttemptId,
    [property: JsonPropertyName("caseVersionId")] string CaseVersionId,
    [property: JsonPropertyName("commandType")] SyncCommandType CommandType,
    [property: JsonPropertyName("revisionNumber")] int RevisionNumber,
    [property: JsonPropertyName("snapshotDigest")] string SnapshotDigest);

public sealed record SyncPullResponse(
    [property: JsonPropertyName("schema")] string Schema,
    [property: JsonPropertyName("version")] string Version,
    [property: JsonPropertyName("cursor")] long Cursor,
    [property: JsonPropertyName("nextCursor")] long NextCursor,
    [property: JsonPropertyName("hasMore")] bool HasMore,
    [property: JsonPropertyName("changes")] IReadOnlyList<SyncChange> Changes,
    [property: JsonPropertyName("requestId")] string RequestId);

public sealed record SyncValidationResult(bool IsValid, string? ErrorCode, string? Message)
{
    public static SyncValidationResult Valid { get; } = new(true, null, null);

    public override string ToString() => IsValid
        ? "SyncValidationResult(valid)"
        : $"SyncValidationResult(invalid:{ErrorCode})";
}

public static class SyncCommandValidator
{
    private static readonly System.Text.RegularExpressions.Regex CaseVersionPattern =
        new("\\A[A-Za-z0-9._:-]{1,128}\\z", System.Text.RegularExpressions.RegexOptions.CultureInvariant);

    private static readonly System.Text.RegularExpressions.Regex DigestPattern =
        new("\\A[a-f0-9]{64}\\z", System.Text.RegularExpressions.RegexOptions.CultureInvariant);

    public static SyncValidationResult Validate(SyncCommand? command)
    {
        if (command is null)
        {
            return Invalid("INVALID_SYNC_COMMAND", "A sync command is invalid.");
        }

        if (command.CommandId == Guid.Empty || command.AttemptId == Guid.Empty)
        {
            return Invalid("INVALID_ID", "A sync identifier is invalid.");
        }

        if (string.IsNullOrWhiteSpace(command.CaseVersionId)
            || !CaseVersionPattern.IsMatch(command.CaseVersionId))
        {
            return Invalid("INVALID_CASE_VERSION", "The case version is invalid.");
        }

        if (!Enum.IsDefined(command.CommandType))
        {
            return Invalid("INVALID_COMMAND_TYPE", "The sync command type is invalid.");
        }

        if (command.RevisionNumber is < 0 or > 1)
        {
            return Invalid("INVALID_REVISION", "The revision number is invalid.");
        }

        if (command.ClientOccurredAt == default)
        {
            return Invalid("INVALID_CLIENT_OCCURRED_AT", "The client occurrence time is invalid.");
        }

        if (string.IsNullOrWhiteSpace(command.SnapshotDigest)
            || !DigestPattern.IsMatch(command.SnapshotDigest))
        {
            return Invalid("INVALID_SNAPSHOT_DIGEST", "The snapshot digest is invalid.");
        }

        return SyncValidationResult.Valid;
    }

    private static SyncValidationResult Invalid(string code, string message) =>
        new(false, code, message);
}

internal static class SyncCaseVersionPolicy
{
    public static string? RejectionReason(string? status) => status switch
    {
        null => "CASE_VERSION_NOT_FOUND",
        "published" => null,
        _ => "CASE_VERSION_NOT_PUBLISHED",
    };
}
