-- Evidrilo P3 follow-up: typed funnel, premium, and client-error events.
-- Renewal/refund remains a provider-webhook concern and is not client-asserted.

alter table public.analytics_events
    drop constraint if exists analytics_events_event_name_check;

alter table public.analytics_events
    add constraint analytics_events_event_name_check
    check (event_name in (
        'practice_started',
        'attempt_completed',
        'revision_recorded',
        'paywall_viewed',
        'premium_action',
        'client_error',
        'recommendation_shown',
        'recommendation_accepted',
        'recommendation_dismissed'
    ));
