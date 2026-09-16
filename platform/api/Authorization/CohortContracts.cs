using System.Text.Json.Serialization;

namespace Evidrilo.Api.Authorization;

public sealed record CohortSummaryData(
    Guid CohortId,
    int LearnerCount,
    double? CompletionRate,
    double? PassRate);

public sealed record CohortSummaryResponse(
    [property: JsonPropertyName("schema")] string Schema,
    [property: JsonPropertyName("version")] string Version,
    [property: JsonPropertyName("cohortId")] Guid CohortId,
    [property: JsonPropertyName("learnerCount")] int LearnerCount,
    [property: JsonPropertyName("suppressed")] bool Suppressed,
    [property: JsonPropertyName("completionRate")] double? CompletionRate,
    [property: JsonPropertyName("passRate")] double? PassRate,
    [property: JsonPropertyName("requestId")] string RequestId);

public sealed record CohortSummaryLookup(
    CohortSummaryData? Summary,
    string? DenialCode)
{
    public static CohortSummaryLookup Found(CohortSummaryData summary) => new(summary, null);

    public static CohortSummaryLookup Denied(string code) => new(null, code);
}
