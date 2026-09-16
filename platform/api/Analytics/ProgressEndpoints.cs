using System.Globalization;
using Evidrilo.Api.Auth;
using Evidrilo.Api.Common;

namespace Evidrilo.Api.Analytics;

public static class ProgressEndpoints
{
    public static IEndpointRouteBuilder MapProgressEndpoints(this IEndpointRouteBuilder endpoints)
    {
        endpoints.MapGet(
            "/v1/progress/me",
            async (HttpContext context, IProgressStore store, CancellationToken cancellationToken) =>
            {
                if (!AuthenticatedUser.TryGetAccountId(context.User, out var accountId))
                {
                    return Results.Json(
                        ApiErrors.Create(context, "AUTH_REQUIRED", "Authentication is required."),
                        statusCode: StatusCodes.Status401Unauthorized);
                }

                if (!AuthenticatedUser.IsEmailVerified(context.User))
                {
                    return Results.Json(
                        ApiErrors.Create(context, "FORBIDDEN", "You are not allowed to access progress."),
                        statusCode: StatusCodes.Status403Forbidden);
                }

                var projection = await store.GetAsync(accountId, cancellationToken)
                    ?? new ProgressProjection
                    {
                        CalculationVersion = ProgressProjectionBuilder.CalculationVersion,
                    };
                if (!ProgressProjectionValidator.IsSafe(projection))
                {
                    throw new ApiException(
                        StatusCodes.Status503ServiceUnavailable,
                        "PROJECTION_UNAVAILABLE",
                        "Progress is temporarily unavailable.");
                }

                return Results.Ok(new ProgressSummary(
                    "evidrilo.progress-summary",
                    "1",
                    projection.CalculationVersion,
                    projection.AttemptsObserved,
                    projection.CompletedAttempts,
                    projection.RevisionsObserved,
                    projection.PassCount,
                    projection.ActionRequiredCount,
                    projection.AbstentionCount,
                    projection.Coverage,
                    RequestIdMiddleware.Get(context)));
            })
            .RequireAuthorization()
            .RequireRateLimiting("api");

        endpoints.MapGet(
            "/v1/progress/daily",
            async (HttpContext context, IProgressStore store, CancellationToken cancellationToken) =>
            {
                if (!AuthenticatedUser.TryGetAccountId(context.User, out var accountId))
                {
                    return Results.Json(
                        ApiErrors.Create(context, "AUTH_REQUIRED", "Authentication is required."),
                        statusCode: StatusCodes.Status401Unauthorized);
                }

                if (!AuthenticatedUser.IsEmailVerified(context.User))
                {
                    return Results.Json(
                        ApiErrors.Create(context, "FORBIDDEN", "You are not allowed to access progress."),
                        statusCode: StatusCodes.Status403Forbidden);
                }

                if (!TryReadRange(context, out var from, out var to))
                {
                    return Results.Json(
                        ApiErrors.Create(context, "INVALID_PROGRESS_RANGE", "The progress date range is invalid."),
                        statusCode: StatusCodes.Status400BadRequest);
                }

                var projections = await store.GetDailyAsync(accountId, from, to, cancellationToken);
                var items = new List<DailyProgressSummary>(projections.Count);
                foreach (var projection in projections)
                {
                    var safe = new ProgressProjection
                    {
                        CalculationVersion = projection.CalculationVersion,
                        AttemptsObserved = projection.AttemptsObserved,
                        CompletedAttempts = projection.CompletedAttempts,
                        RevisionsObserved = projection.RevisionsObserved,
                        PassCount = projection.PassCount,
                        ActionRequiredCount = projection.ActionRequiredCount,
                        AbstentionCount = projection.AbstentionCount,
                        Coverage = projection.Coverage,
                    };
                    if (!ProgressProjectionValidator.IsSafe(safe))
                    {
                        throw new ApiException(
                            StatusCodes.Status503ServiceUnavailable,
                            "PROJECTION_UNAVAILABLE",
                            "Daily progress is temporarily unavailable.");
                    }

                    items.Add(new DailyProgressSummary(
                        projection.ProjectionDate.ToString("yyyy-MM-dd", CultureInfo.InvariantCulture),
                        projection.CalculationVersion,
                        projection.AttemptsObserved,
                        projection.CompletedAttempts,
                        projection.RevisionsObserved,
                        projection.PassCount,
                        projection.ActionRequiredCount,
                        projection.AbstentionCount,
                        projection.Coverage));
                }

                return Results.Ok(new DailyProgressResponse(
                    "evidrilo.progress-daily",
                    "1",
                    ProgressProjectionBuilder.CalculationVersion,
                    items,
                    RequestIdMiddleware.Get(context)));
            })
            .RequireAuthorization()
            .RequireRateLimiting("api");

        return endpoints;
    }

    private static bool TryReadRange(
        HttpContext context,
        out DateOnly from,
        out DateOnly to)
    {
        var today = DateOnly.FromDateTime(DateTime.UtcNow);
        from = today.AddDays(-30);
        to = today;

        if (!TryReadDate(context.Request.Query["from"].FirstOrDefault(), from, out from)
            || !TryReadDate(context.Request.Query["to"].FirstOrDefault(), to, out to))
            return false;

        return from <= to && to.DayNumber - from.DayNumber <= 365;
    }

    private static bool TryReadDate(string? value, DateOnly fallback, out DateOnly result)
    {
        if (value is null)
        {
            result = fallback;
            return true;
        }

        return DateOnly.TryParseExact(
            value,
            "yyyy-MM-dd",
            CultureInfo.InvariantCulture,
            DateTimeStyles.None,
            out result);
    }
}
