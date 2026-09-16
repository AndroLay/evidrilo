using System.Text.Json.Serialization;

namespace Evidrilo.Api.Authorization;

public sealed record MembershipCommand(
    [property: JsonPropertyName("schema")] string Schema,
    [property: JsonPropertyName("version")] string Version,
    [property: JsonPropertyName("targetAccountId")] Guid TargetAccountId,
    [property: JsonRequired, JsonPropertyName("role")] PlatformRole Role,
    [property: JsonPropertyName("reason")] string? Reason);

public sealed record MembershipOperation(
    string Outcome,
    Guid OrganizationId,
    Guid TargetAccountId,
    PlatformRole? Role);

public sealed record MembershipOperationResponse(
    [property: JsonPropertyName("schema")] string Schema,
    [property: JsonPropertyName("version")] string Version,
    [property: JsonPropertyName("outcome")] string Outcome,
    [property: JsonPropertyName("organizationId")] Guid OrganizationId,
    [property: JsonPropertyName("targetAccountId")] Guid TargetAccountId,
    [property: JsonPropertyName("role")] PlatformRole? Role,
    [property: JsonPropertyName("requestId")] string RequestId);

public static class MembershipCommandValidator
{
    public static string? Validate(MembershipCommand? request)
    {
        if (request is null
            || request.Schema != "evidrilo.membership-command"
            || request.Version != "1"
            || request.TargetAccountId == Guid.Empty
            || !Enum.IsDefined(request.Role)
            || request.Reason is not null && string.IsNullOrWhiteSpace(request.Reason)
            || request.Reason is { Length: > 2000 })
            return "INVALID_MEMBERSHIP_COMMAND";
        return null;
    }
}

public static class MembershipWireExtensions
{
    public static string ToWire(this PlatformRole value) => value switch
    {
        PlatformRole.Learner => "learner",
        PlatformRole.Author => "author",
        PlatformRole.Teacher => "teacher",
        PlatformRole.Reviewer => "reviewer",
        PlatformRole.Maintainer => "maintainer",
        PlatformRole.Owner => "owner",
        _ => throw new ArgumentOutOfRangeException(nameof(value)),
    };

    public static PlatformRole FromWire(string value) => value switch
    {
        "learner" => PlatformRole.Learner,
        "author" => PlatformRole.Author,
        "teacher" => PlatformRole.Teacher,
        "reviewer" => PlatformRole.Reviewer,
        "maintainer" => PlatformRole.Maintainer,
        "owner" => PlatformRole.Owner,
        _ => throw new InvalidOperationException("Unknown membership role."),
    };
}
