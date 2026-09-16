-- Evidrilo P5, append-only recommendation interaction events.

create table if not exists public.recommendation_events (
    account_id uuid not null references auth.users(id) on delete cascade,
    client_event_id uuid not null,
    case_version_id text null references public.case_versions(case_version_id),
    interaction text not null check (interaction in ('shown', 'accepted', 'dismissed')),
    calculation_version text not null,
    reason_code text not null,
    created_at timestamptz not null default now(),
    primary key (account_id, client_event_id)
);

alter table public.recommendation_events enable row level security;

create policy recommendation_events_select_own on public.recommendation_events
    for select using (account_id = auth.uid());

create policy recommendation_events_insert_own on public.recommendation_events
    for insert with check (account_id = auth.uid());
