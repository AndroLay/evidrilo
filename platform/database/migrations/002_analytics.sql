-- Evidrilo P3, append-only consented analytics and projection source.
-- Projection rows are worker-owned and can be rebuilt from analytics_events.

create table if not exists public.analytics_events (
    account_id uuid not null references auth.users(id) on delete cascade,
    client_event_id uuid not null,
    event_name text not null check (event_name in (
        'attempt_completed',
        'revision_recorded',
        'recommendation_shown',
        'recommendation_accepted',
        'recommendation_dismissed'
    )),
    event_version integer not null check (event_version = 1),
    occurred_at timestamptz not null,
    source text not null check (source = 'mobile'),
    consent_version text not null default 'analytics.v1' check (consent_version = 'analytics.v1'),
    properties jsonb not null check (jsonb_typeof(properties) = 'object'),
    received_at timestamptz not null default now(),
    primary key (account_id, client_event_id)
);

create index if not exists analytics_events_account_occurred_idx
    on public.analytics_events (account_id, occurred_at, client_event_id);

create table if not exists public.progress_projections (
    account_id uuid primary key references auth.users(id) on delete cascade,
    calculation_version text not null,
    attempts_observed integer not null check (attempts_observed >= 0),
    completed_attempts integer not null check (completed_attempts >= 0),
    revisions_observed integer not null check (revisions_observed >= 0),
    pass_count integer not null check (pass_count >= 0),
    action_required_count integer not null check (action_required_count >= 0),
    abstention_count integer not null check (abstention_count >= 0),
    coverage double precision not null check (coverage between 0 and 1),
    rebuilt_at timestamptz not null default now()
);

alter table public.analytics_events enable row level security;
alter table public.progress_projections enable row level security;

create policy analytics_events_select_own on public.analytics_events
    for select using (account_id = auth.uid());

create policy analytics_events_insert_own on public.analytics_events
    for insert with check (account_id = auth.uid());

create policy progress_projections_select_own on public.progress_projections
    for select using (account_id = auth.uid());

-- No update/delete policy is granted to client roles. Projection writes belong
-- to the separately scoped worker role and are intentionally not client APIs.
