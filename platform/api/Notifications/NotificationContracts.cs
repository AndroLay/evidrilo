using System.Text.Json.Serialization;

namespace Evidrilo.Api.Notifications;

public sealed record NotificationPreferencesUpdateRequest(
    [property: JsonPropertyName("schema")] string Schema,
    [property: JsonPropertyName("version")] string Version,
    [property: JsonPropertyName("enabled")] bool Enabled,
    [property: JsonPropertyName("continueUnfinishedEnabled")] bool ContinueUnfinishedEnabled,
    [property: JsonPropertyName("reviewCompletedEnabled")] bool ReviewCompletedEnabled,
    [property: JsonPropertyName("cadence")] string Cadence,
    [property: JsonPropertyName("localHour")] int LocalHour,
    [property: JsonPropertyName("localMinute")] int LocalMinute,
    [property: JsonPropertyName("expectedRevision")] long? ExpectedRevision);

public sealed record NotificationPreferencesSnapshot(
    [property: JsonPropertyName("enabled")] bool Enabled,
    [property: JsonPropertyName("continueUnfinishedEnabled")] bool ContinueUnfinishedEnabled,
    [property: JsonPropertyName("reviewCompletedEnabled")] bool ReviewCompletedEnabled,
    [property: JsonPropertyName("cadence")] string Cadence,
    [property: JsonPropertyName("localHour")] int LocalHour,
    [property: JsonPropertyName("localMinute")] int LocalMinute,
    [property: JsonPropertyName("revision")] long Revision,
    [property: JsonPropertyName("updatedAt")] DateTimeOffset? UpdatedAt)
{
    public static NotificationPreferencesSnapshot Defaults() => new(
        Enabled: false,
        ContinueUnfinishedEnabled: false,
        ReviewCompletedEnabled: false,
        Cadence: "daily",
        LocalHour: 9,
        LocalMinute: 0,
        Revision: 0,
        UpdatedAt: null);

    public bool Matches(NotificationPreferencesUpdateRequest request) =>
        Enabled == request.Enabled
        && ContinueUnfinishedEnabled == request.ContinueUnfinishedEnabled
        && ReviewCompletedEnabled == request.ReviewCompletedEnabled
        && Cadence == request.Cadence
        && LocalHour == request.LocalHour
        && LocalMinute == request.LocalMinute;
}

public sealed record NotificationPreferencesResponse(
    [property: JsonPropertyName("schema")] string Schema,
    [property: JsonPropertyName("version")] string Version,
    [property: JsonPropertyName("enabled")] bool Enabled,
    [property: JsonPropertyName("continueUnfinishedEnabled")] bool ContinueUnfinishedEnabled,
    [property: JsonPropertyName("reviewCompletedEnabled")] bool ReviewCompletedEnabled,
    [property: JsonPropertyName("cadence")] string Cadence,
    [property: JsonPropertyName("localHour")] int LocalHour,
    [property: JsonPropertyName("localMinute")] int LocalMinute,
    [property: JsonPropertyName("revision")] long Revision,
    [property: JsonPropertyName("updatedAt"), JsonIgnore(Condition = JsonIgnoreCondition.Never)] DateTimeOffset? UpdatedAt,
    [property: JsonPropertyName("requestId")] string RequestId)
{
    public static NotificationPreferencesResponse From(
        NotificationPreferencesSnapshot snapshot,
        string requestId) => new(
            "evidrilo.notification-preferences",
            "1",
            snapshot.Enabled,
            snapshot.ContinueUnfinishedEnabled,
            snapshot.ReviewCompletedEnabled,
            snapshot.Cadence,
            snapshot.LocalHour,
            snapshot.LocalMinute,
            snapshot.Revision,
            snapshot.UpdatedAt,
            requestId);
}

public sealed record NotificationPreferencesUpdateResponse(
    [property: JsonPropertyName("schema")] string Schema,
    [property: JsonPropertyName("version")] string Version,
    [property: JsonPropertyName("outcome")] string Outcome,
    [property: JsonPropertyName("enabled")] bool Enabled,
    [property: JsonPropertyName("continueUnfinishedEnabled")] bool ContinueUnfinishedEnabled,
    [property: JsonPropertyName("reviewCompletedEnabled")] bool ReviewCompletedEnabled,
    [property: JsonPropertyName("cadence")] string Cadence,
    [property: JsonPropertyName("localHour")] int LocalHour,
    [property: JsonPropertyName("localMinute")] int LocalMinute,
    [property: JsonPropertyName("revision")] long Revision,
    [property: JsonPropertyName("updatedAt")] DateTimeOffset? UpdatedAt,
    [property: JsonPropertyName("requestId")] string RequestId)
{
    public static NotificationPreferencesUpdateResponse From(
        NotificationPreferencesWriteResult result,
        string requestId) => new(
            "evidrilo.notification-preferences-update-result",
            "1",
            result.Outcome,
            result.Preferences.Enabled,
            result.Preferences.ContinueUnfinishedEnabled,
            result.Preferences.ReviewCompletedEnabled,
            result.Preferences.Cadence,
            result.Preferences.LocalHour,
            result.Preferences.LocalMinute,
            result.Preferences.Revision,
            result.Preferences.UpdatedAt,
            requestId);
}

public sealed record NotificationPreferencesWriteResult(
    string Outcome,
    NotificationPreferencesSnapshot Preferences);

public static class NotificationPreferencesValidator
{
    public static string? Validate(NotificationPreferencesUpdateRequest? request)
    {
        if (request is null
            || request.Schema != "evidrilo.notification-preferences-update"
            || request.Version != "1"
            || request.ExpectedRevision is null or < 0
            || request.Cadence is not ("daily" or "weekly")
            || request.LocalHour is < 0 or > 23
            || request.LocalMinute is < 0 or > 59)
            return "INVALID_NOTIFICATION_PREFERENCES";

        return null;
    }
}
