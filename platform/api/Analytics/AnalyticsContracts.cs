using System.Text.Json.Serialization;
using System.Text.RegularExpressions;

namespace Evidrilo.Api.Analytics;

public enum AnalyticsEventName
{
    PracticeStarted,
    AttemptCompleted,
    RevisionRecorded,
    PaywallViewed,
    PremiumAction,
    ClientError,
    RecommendationShown,
    RecommendationAccepted,
    RecommendationDismissed,
}

public enum AnalyticsEventSource
{
    Mobile,
}

public sealed record AnalyticsEventProperties(
    [property: JsonPropertyName("attemptId")] Guid? AttemptId = null,
    [property: JsonPropertyName("caseVersionId")] string? CaseVersionId = null,
    [property: JsonPropertyName("outcome")] string? Outcome = null,
    [property: JsonPropertyName("skillId")] string? SkillId = null,
    [property: JsonPropertyName("revisionChanged")] bool? RevisionChanged = null,
    [property: JsonPropertyName("surfaceId")] string? SurfaceId = null,
    [property: JsonPropertyName("action")] string? Action = null,
    [property: JsonPropertyName("productId")] string? ProductId = null,
    [property: JsonPropertyName("errorCode")] string? ErrorCode = null);

public sealed record AnalyticsEventRequest(
    [property: JsonPropertyName("schema")] string Schema,
    [property: JsonPropertyName("version")] string Version,
    [property: JsonPropertyName("clientEventId")] Guid ClientEventId,
    [property: JsonRequired, JsonPropertyName("eventName")] AnalyticsEventName EventName,
    [property: JsonPropertyName("eventVersion")] int EventVersion,
    [property: JsonPropertyName("occurredAt")] DateTimeOffset OccurredAt,
    [property: JsonRequired, JsonPropertyName("source")] AnalyticsEventSource Source,
    [property: JsonPropertyName("consent")] string Consent,
    [property: JsonPropertyName("properties")] AnalyticsEventProperties Properties);

public sealed record StoredAnalyticsEvent(
    Guid AccountId,
    Guid ClientEventId,
    AnalyticsEventName EventName,
    DateTimeOffset OccurredAt,
    AnalyticsEventProperties Properties);

public sealed record AnalyticsAppendResult(string Outcome);

public sealed record AnalyticsEventResponse(
    [property: JsonPropertyName("schema")] string Schema,
    [property: JsonPropertyName("version")] string Version,
    [property: JsonPropertyName("outcome")] string Outcome,
    [property: JsonPropertyName("requestId")] string RequestId);

public sealed record ProgressSummary(
    [property: JsonPropertyName("schema")] string Schema,
    [property: JsonPropertyName("version")] string Version,
    [property: JsonPropertyName("calculationVersion")] string CalculationVersion,
    [property: JsonPropertyName("attemptsObserved")] int AttemptsObserved,
    [property: JsonPropertyName("completedAttempts")] int CompletedAttempts,
    [property: JsonPropertyName("revisionsObserved")] int RevisionsObserved,
    [property: JsonPropertyName("passCount")] int PassCount,
    [property: JsonPropertyName("actionRequiredCount")] int ActionRequiredCount,
    [property: JsonPropertyName("abstentionCount")] int AbstentionCount,
    [property: JsonPropertyName("coverage")] double Coverage,
    [property: JsonPropertyName("requestId")] string RequestId);

public sealed record DailyProgressProjection(
    DateOnly ProjectionDate,
    string CalculationVersion,
    int AttemptsObserved,
    int CompletedAttempts,
    int RevisionsObserved,
    int PassCount,
    int ActionRequiredCount,
    int AbstentionCount,
    double Coverage);

public sealed record DailyProgressSummary(
    [property: JsonPropertyName("projectionDate")] string ProjectionDate,
    [property: JsonPropertyName("calculationVersion")] string CalculationVersion,
    [property: JsonPropertyName("attemptsObserved")] int AttemptsObserved,
    [property: JsonPropertyName("completedAttempts")] int CompletedAttempts,
    [property: JsonPropertyName("revisionsObserved")] int RevisionsObserved,
    [property: JsonPropertyName("passCount")] int PassCount,
    [property: JsonPropertyName("actionRequiredCount")] int ActionRequiredCount,
    [property: JsonPropertyName("abstentionCount")] int AbstentionCount,
    [property: JsonPropertyName("coverage")] double Coverage);

public sealed record DailyProgressResponse(
    [property: JsonPropertyName("schema")] string Schema,
    [property: JsonPropertyName("version")] string Version,
    [property: JsonPropertyName("calculationVersion")] string CalculationVersion,
    [property: JsonPropertyName("items")] IReadOnlyList<DailyProgressSummary> Items,
    [property: JsonPropertyName("requestId")] string RequestId);

public sealed record AnalyticsValidationResult(bool IsValid, string? Code)
{
    public static AnalyticsValidationResult Valid { get; } = new(true, null);
}

public static class AnalyticsEventValidator
{
    private static readonly Regex IdentifierPattern =
        new("\\A[A-Za-z0-9._:-]{1,128}\\z", RegexOptions.CultureInvariant);
    private static readonly Regex ActionPattern =
        new("\\A[a-z][a-z0-9_]{2,63}\\z", RegexOptions.CultureInvariant);
    private static readonly Regex ErrorCodePattern =
        new("\\A[A-Z][A-Z0-9_]{2,63}\\z", RegexOptions.CultureInvariant);
    private static readonly HashSet<string> Outcomes =
    ["PASS", "ACTION_REQUIRED", "INCOMPLETE", "CANNOT_ASSESS"];
    private static readonly HashSet<string> Products = ["monthly", "yearly"];
    private static readonly HashSet<string> PremiumActions =
    [
        "purchase_started",
        "purchase_completed",
        "purchase_cancelled",
        "purchase_failed",
        "purchase_pending",
        "purchase_unknown",
        "restore_started",
        "restore_completed",
        "restore_cancelled",
        "restore_failed",
        "restore_pending",
        "restore_unknown",
    ];
    private static readonly HashSet<AnalyticsEventName> SupportedNames =
    [
        AnalyticsEventName.PracticeStarted,
        AnalyticsEventName.AttemptCompleted,
        AnalyticsEventName.RevisionRecorded,
        AnalyticsEventName.PaywallViewed,
        AnalyticsEventName.PremiumAction,
        AnalyticsEventName.ClientError,
        AnalyticsEventName.RecommendationShown,
        AnalyticsEventName.RecommendationAccepted,
        AnalyticsEventName.RecommendationDismissed,
    ];

    public static AnalyticsValidationResult Validate(AnalyticsEventRequest? request)
    {
        if (request is null) return Invalid("INVALID_ANALYTICS_EVENT");
        if (request.Schema != "evidrilo.analytics-event" || request.Version != "1")
            return Invalid("INVALID_ANALYTICS_EVENT");
        if (request.ClientEventId == Guid.Empty || request.EventVersion != 1)
            return Invalid("INVALID_ANALYTICS_EVENT");
        if (!SupportedNames.Contains(request.EventName) || request.Source != AnalyticsEventSource.Mobile)
            return Invalid("INVALID_ANALYTICS_EVENT");
        if (request.OccurredAt == default)
            return Invalid("INVALID_ANALYTICS_EVENT");
        if (!string.Equals(request.Consent, "granted", StringComparison.Ordinal))
            return Invalid("ANALYTICS_CONSENT_REQUIRED");
        if (request.Properties is null) return Invalid("INVALID_ANALYTICS_EVENT");
        if ((request.Properties.CaseVersionId is { } caseVersionId
                && !IdentifierPattern.IsMatch(caseVersionId))
            || (request.Properties.SkillId is { } skillId
                && !IdentifierPattern.IsMatch(skillId))
            || (request.Properties.SurfaceId is { } surfaceId
                && !IdentifierPattern.IsMatch(surfaceId))
            || (request.Properties.Action is { } action
                && !ActionPattern.IsMatch(action))
            || (request.Properties.ProductId is { } productId
                && !Products.Contains(productId))
            || (request.Properties.ErrorCode is { } errorCode
                && !ErrorCodePattern.IsMatch(errorCode))
            || (request.Properties.Outcome is { } outcome
                && !Outcomes.Contains(outcome)))
            return Invalid("INVALID_ANALYTICS_EVENT");

        var properties = request.Properties;
        if ((request.EventName is AnalyticsEventName.PracticeStarted
                or AnalyticsEventName.AttemptCompleted
                or AnalyticsEventName.RevisionRecorded)
            && (properties.AttemptId is null || properties.CaseVersionId is null))
            return Invalid("INVALID_ANALYTICS_EVENT");
        if (request.EventName == AnalyticsEventName.AttemptCompleted
            && properties.Outcome is null)
            return Invalid("INVALID_ANALYTICS_EVENT");
        if (request.EventName == AnalyticsEventName.RevisionRecorded
            && properties.RevisionChanged is null)
            return Invalid("INVALID_ANALYTICS_EVENT");
        if (request.EventName == AnalyticsEventName.PaywallViewed
            && properties.SurfaceId is null)
            return Invalid("INVALID_ANALYTICS_EVENT");
        if (request.EventName == AnalyticsEventName.PremiumAction
            && (properties.Action is null || !PremiumActions.Contains(properties.Action)))
            return Invalid("INVALID_ANALYTICS_EVENT");
        if (request.EventName == AnalyticsEventName.ClientError
            && properties.ErrorCode is null)
            return Invalid("INVALID_ANALYTICS_EVENT");
        if ((request.EventName is AnalyticsEventName.RecommendationShown
                or AnalyticsEventName.RecommendationAccepted
                or AnalyticsEventName.RecommendationDismissed)
            && properties.CaseVersionId is null)
            return Invalid("INVALID_ANALYTICS_EVENT");

        return AnalyticsValidationResult.Valid;
    }

    private static AnalyticsValidationResult Invalid(string code) => new(false, code);
}

public static class ProgressProjectionBuilder
{
    public const string CalculationVersion = "progress.v1";

    public static ProgressProjection Build(IEnumerable<StoredAnalyticsEvent> events)
    {
        var projection = new ProgressProjection
        {
            CalculationVersion = CalculationVersion,
        };
        // The projection is a commutative count aggregate. Avoid sorting the
        // full event stream when its order cannot affect the result.
        foreach (var item in events)
        {
            switch (item.EventName)
            {
                case AnalyticsEventName.AttemptCompleted:
                    projection.AttemptsObserved++;
                    projection.CompletedAttempts++;
                    switch (item.Properties.Outcome)
                    {
                        case "PASS":
                            projection.PassCount++;
                            break;
                        case "ACTION_REQUIRED":
                            projection.ActionRequiredCount++;
                            break;
                        case "CANNOT_ASSESS":
                        case "INCOMPLETE":
                            projection.AbstentionCount++;
                            break;
                    }

                    break;
                case AnalyticsEventName.RevisionRecorded:
                    projection.RevisionsObserved++;
                    break;
            }
        }

        projection.Coverage = projection.AttemptsObserved == 0
            ? 0
            : Math.Clamp((double)projection.CompletedAttempts / projection.AttemptsObserved, 0, 1);
        return projection;
    }
}

public sealed class ProgressProjection
{
    public string CalculationVersion { get; set; } = ProgressProjectionBuilder.CalculationVersion;
    public int AttemptsObserved { get; set; }
    public int CompletedAttempts { get; set; }
    public int RevisionsObserved { get; set; }
    public int PassCount { get; set; }
    public int ActionRequiredCount { get; set; }
    public int AbstentionCount { get; set; }
    public double Coverage { get; set; }
}

public static class ProgressProjectionValidator
{
    public static bool IsSafe(ProgressProjection? projection)
    {
        return projection is not null
            && projection.CalculationVersion == ProgressProjectionBuilder.CalculationVersion
            && projection.AttemptsObserved >= 0
            && projection.CompletedAttempts >= 0
            && projection.CompletedAttempts <= projection.AttemptsObserved
            && projection.RevisionsObserved >= 0
            && projection.PassCount >= 0
            && projection.ActionRequiredCount >= 0
            && projection.AbstentionCount >= 0
            && (long)projection.PassCount + projection.ActionRequiredCount + projection.AbstentionCount
                <= projection.CompletedAttempts
            && double.IsFinite(projection.Coverage)
            && projection.Coverage is >= 0 and <= 1;
    }
}
