using Evidrilo.Api.Analytics;

namespace Evidrilo.Api.Tests;

public sealed class AnalyticsTests
{
    [Fact]
    public void Validator_rejects_unconsented_or_unknown_events()
    {
        var request = ValidRequest() with { Consent = "denied" };
        Assert.Equal("ANALYTICS_CONSENT_REQUIRED", AnalyticsEventValidator.Validate(request).Code);

        var unsupported = request with
        {
            Consent = "granted",
            EventName = (AnalyticsEventName)999,
        };
        Assert.False(AnalyticsEventValidator.Validate(unsupported).IsValid);
    }

    [Fact]
    public void Validator_requires_event_specific_typed_properties()
    {
        var valid = new AnalyticsEventRequest(
            "evidrilo.analytics-event",
            "1",
            Guid.NewGuid(),
            AnalyticsEventName.AttemptCompleted,
            1,
            DateTimeOffset.UtcNow,
            AnalyticsEventSource.Mobile,
            "granted",
            new(Guid.NewGuid(), "M0_T2:1", "PASS", "evidence-linking", null));

        Assert.True(AnalyticsEventValidator.Validate(valid).IsValid);
        Assert.Equal(
            "INVALID_ANALYTICS_EVENT",
            AnalyticsEventValidator.Validate(valid with
            {
                Properties = valid.Properties with { Outcome = null },
            }).Code);
        Assert.Equal(
            "INVALID_ANALYTICS_EVENT",
            AnalyticsEventValidator.Validate(valid with
            {
                Properties = valid.Properties with { CaseVersionId = "not valid/identifier" },
            }).Code);
        Assert.Equal(
            "INVALID_ANALYTICS_EVENT",
            AnalyticsEventValidator.Validate(valid with
            {
                Properties = valid.Properties with
                {
                    AttemptId = Guid.NewGuid(),
                    CaseVersionId = "M0_T2:1\n",
                },
            }).Code);
        Assert.Equal(
            "INVALID_ANALYTICS_EVENT",
            AnalyticsEventValidator.Validate(valid with
            {
                EventName = AnalyticsEventName.RevisionRecorded,
                Properties = valid.Properties with { RevisionChanged = null },
            }).Code);
    }

    [Fact]
    public void Validator_accepts_funnel_conversion_and_safe_error_events()
    {
        var started = ValidRequest() with
        {
            EventName = AnalyticsEventName.PracticeStarted,
            Properties = new(AttemptId: Guid.NewGuid(), CaseVersionId: "M0_T2:1"),
        };
        var paywall = ValidRequest() with
        {
            EventName = AnalyticsEventName.PaywallViewed,
            Properties = new(SurfaceId: "premium"),
        };
        var purchase = ValidRequest() with
        {
            EventName = AnalyticsEventName.PremiumAction,
            Properties = new(Action: "purchase_started", ProductId: "monthly"),
        };
        var error = ValidRequest() with
        {
            EventName = AnalyticsEventName.ClientError,
            Properties = new(ErrorCode: "BILLING_UNAVAILABLE", SurfaceId: "premium"),
        };

        Assert.True(AnalyticsEventValidator.Validate(started).IsValid);
        Assert.True(AnalyticsEventValidator.Validate(paywall).IsValid);
        Assert.True(AnalyticsEventValidator.Validate(purchase).IsValid);
        Assert.True(AnalyticsEventValidator.Validate(error).IsValid);
        Assert.False(AnalyticsEventValidator.Validate(purchase with
        {
            Properties = purchase.Properties with { ProductId = "lifetime" },
        }).IsValid);
        Assert.False(AnalyticsEventValidator.Validate(error with
        {
            Properties = error.Properties with { ErrorCode = "billing failed" },
        }).IsValid);
    }

    [Fact]
    public void Projection_is_deterministic_and_ignores_non_progress_events()
    {
        var accountId = Guid.Parse("123e4567-e89b-42d3-a456-426614174000");
        var events = new[]
        {
            new StoredAnalyticsEvent(accountId, Guid.Parse("123e4567-e89b-42d3-a456-426614174003"), AnalyticsEventName.RecommendationShown, DateTimeOffset.Parse("2026-09-10T09:01:00Z"), new()),
            new StoredAnalyticsEvent(accountId, Guid.Parse("123e4567-e89b-42d3-a456-426614174001"), AnalyticsEventName.AttemptCompleted, DateTimeOffset.Parse("2026-09-10T09:00:00Z"), new(Outcome: "PASS")),
            new StoredAnalyticsEvent(accountId, Guid.Parse("123e4567-e89b-42d3-a456-426614174002"), AnalyticsEventName.RevisionRecorded, DateTimeOffset.Parse("2026-09-10T09:02:00Z"), new()),
            new StoredAnalyticsEvent(accountId, Guid.Parse("123e4567-e89b-42d3-a456-426614174004"), AnalyticsEventName.AttemptCompleted, DateTimeOffset.Parse("2026-09-10T09:03:00Z"), new(Outcome: "CANNOT_ASSESS")),
        };

        var first = ProgressProjectionBuilder.Build(events);
        var second = ProgressProjectionBuilder.Build(events.Reverse());

        Assert.Equal("progress.v1", first.CalculationVersion);
        Assert.Equal(2, first.AttemptsObserved);
        Assert.Equal(1, first.PassCount);
        Assert.Equal(1, first.AbstentionCount);
        Assert.Equal(1, first.RevisionsObserved);
        Assert.Equal(1, first.Coverage);
        Assert.Equal(first.AttemptsObserved, second.AttemptsObserved);
        Assert.Equal(first.CompletedAttempts, second.CompletedAttempts);
        Assert.Equal(first.RevisionsObserved, second.RevisionsObserved);
        Assert.Equal(first.PassCount, second.PassCount);
        Assert.Equal(first.ActionRequiredCount, second.ActionRequiredCount);
        Assert.Equal(first.AbstentionCount, second.AbstentionCount);
        Assert.Equal(first.Coverage, second.Coverage);
    }

    [Fact]
    public void Projection_validator_rejects_inconsistent_or_unknown_versions()
    {
        Assert.True(ProgressProjectionValidator.IsSafe(new ProgressProjection()));
        Assert.False(ProgressProjectionValidator.IsSafe(new ProgressProjection
        {
            CalculationVersion = "progress.unknown",
        }));
        Assert.False(ProgressProjectionValidator.IsSafe(new ProgressProjection
        {
            AttemptsObserved = 1,
            CompletedAttempts = 2,
        }));
        Assert.False(ProgressProjectionValidator.IsSafe(new ProgressProjection
        {
            Coverage = double.NaN,
        }));
    }

    [Fact]
    public void Projection_validator_rejects_category_sum_that_overflows_int()
    {
        Assert.False(ProgressProjectionValidator.IsSafe(new ProgressProjection
        {
            AttemptsObserved = int.MaxValue,
            CompletedAttempts = int.MaxValue,
            PassCount = int.MaxValue,
            ActionRequiredCount = 1,
            AbstentionCount = 0,
            Coverage = 1,
        }));
    }

    private static AnalyticsEventRequest ValidRequest() => new(
        "evidrilo.analytics-event",
        "1",
        Guid.Parse("123e4567-e89b-42d3-a456-426614174010"),
        AnalyticsEventName.AttemptCompleted,
        1,
        DateTimeOffset.Parse("2026-09-10T09:00:00Z"),
        AnalyticsEventSource.Mobile,
        "granted",
        new(Outcome: "PASS"));
}
