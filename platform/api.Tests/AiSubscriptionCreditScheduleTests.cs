using Evidrilo.Api.Ai;

namespace Evidrilo.Api.Tests;

public sealed class AiSubscriptionCreditScheduleTests
{
    private static readonly DateTimeOffset Start = DateTimeOffset.Parse("2026-01-01T12:00:00Z");
    private static SubscriptionCreditPeriod Active(DateTimeOffset end) => new("active", Start, Start, end);

    [Fact]
    public void First_purchase_has_one_200_credit_grant_and_retries_do_not_duplicate_it()
    {
        var period = Active(Start.AddMonths(1));
        Assert.Equal(new[] { Start }, AiSubscriptionCreditSchedule.GrantStarts([period, period], Start.AddSeconds(1)));
    }

    [Fact]
    public void Annual_subscription_earns_monthly_instead_of_all_credits_up_front()
    {
        var period = Active(Start.AddYears(1));
        Assert.Single(AiSubscriptionCreditSchedule.GrantStarts([period], Start.AddDays(1)));
        Assert.Equal(new[] { Start, Start.AddMonths(1), Start.AddMonths(2) },
            AiSubscriptionCreditSchedule.GrantStarts([period], Start.AddMonths(2).AddSeconds(1)));
    }

    [Fact]
    public void Renewed_monthly_periods_add_exactly_one_grant_each()
    {
        var first = Active(Start.AddMonths(1));
        var second = new SubscriptionCreditPeriod("active", Start.AddMonths(1), Start.AddMonths(1), Start.AddMonths(2));
        Assert.Equal(new[] { Start, Start.AddMonths(1) },
            AiSubscriptionCreditSchedule.GrantStarts([first, second, second], Start.AddMonths(1).AddSeconds(1)));
    }

    [Theory]
    [InlineData("expired")]
    [InlineData("revoked")]
    public void Terminal_status_stops_new_months_and_preserves_earned_grants(string status)
    {
        var terminal = new SubscriptionCreditPeriod(status, Start.AddDays(20), null, null);
        Assert.Equal(new[] { Start }, AiSubscriptionCreditSchedule.GrantStarts(
            [Active(Start.AddYears(1)), terminal], Start.AddMonths(2)));
    }

    [Fact]
    public void No_grants_are_added_after_a_nonrenewed_period_ends()
    {
        Assert.Equal(new[] { Start }, AiSubscriptionCreditSchedule.GrantStarts([Active(Start.AddMonths(1))], Start.AddMonths(3)));
    }

    [Fact]
    public void Month_end_anniversaries_handle_february_without_early_grants()
    {
        var start = DateTimeOffset.Parse("2026-01-31T12:00:00Z");
        var period = new SubscriptionCreditPeriod("active", start, start, start.AddYears(1));
        Assert.Single(AiSubscriptionCreditSchedule.GrantStarts([period], DateTimeOffset.Parse("2026-02-28T11:59:59Z")));
        Assert.Equal(2, AiSubscriptionCreditSchedule.GrantStarts([period], DateTimeOffset.Parse("2026-02-28T12:00:01Z")).Count);
    }
}
