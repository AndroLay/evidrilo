using Evidrilo.Api.Common;

namespace Evidrilo.Api.Ai;

public sealed record SubscriptionCreditPeriod(string Status, DateTimeOffset OccurredAt,
    DateTimeOffset? PeriodStartedAt, DateTimeOffset? PeriodExpiresAt);

/// <summary>Pure monthly schedule; the database owns idempotent 200-credit inserts.</summary>
public static class AiSubscriptionCreditSchedule
{
    public static IReadOnlyList<DateTimeOffset> GrantStarts(IEnumerable<SubscriptionCreditPeriod> periods, DateTimeOffset now)
    {
        const int maximumMonths = 1200;
        var events = periods.OrderBy(item => item.OccurredAt).ToArray();
        var nowUtc = now.ToUniversalTime();
        var grantStarts = new Dictionary<long, DateTimeOffset>();
        foreach (var activeEvent in events.Where(item => item.Status == "active" &&
                     item.PeriodStartedAt is not null && item.PeriodExpiresAt is not null && item.OccurredAt <= nowUtc))
        {
            var start = activeEvent.PeriodStartedAt!.Value.ToUniversalTime();
            var end = activeEvent.PeriodExpiresAt!.Value.ToUniversalTime();
            if (start > nowUtc || end <= start) continue;
            var activeUntil = end < nowUtc ? end : nowUtc;
            foreach (var terminal in events)
            {
                if (terminal.Status is not ("expired" or "revoked") || terminal.OccurredAt < activeEvent.OccurredAt ||
                    terminal.OccurredAt >= activeUntil) continue;
                activeUntil = terminal.OccurredAt.ToUniversalTime(); break;
            }
            if (activeUntil <= start) continue;
            var firstMonth = MonthOffset(start, activeEvent.OccurredAt.ToUniversalTime());
            if (firstMonth > maximumMonths) throw InvalidHistory();
            for (var month = firstMonth; month <= maximumMonths; month++)
            {
                var grantStart = start.AddMonths(month);
                if (grantStart > nowUtc || grantStart >= activeUntil) break;
                grantStarts.TryAdd(grantStart.ToUnixTimeMilliseconds(), grantStart);
                if (grantStarts.Count > maximumMonths) throw InvalidHistory();
            }
        }
        return grantStarts.Values.OrderBy(value => value).ToArray();
    }

    private static int MonthOffset(DateTimeOffset start, DateTimeOffset instant)
    {
        if (instant <= start) return 0;
        var month = (instant.Year - start.Year) * 12 + instant.Month - start.Month;
        if (start.AddMonths(month) > instant) month--;
        return Math.Max(0, month);
    }

    private static ApiException InvalidHistory() => new(StatusCodes.Status503ServiceUnavailable,
        "AI_CREDIT_LEDGER_CORRUPT", "The AI credit ledger could not reconcile the entitlement history.");
}
