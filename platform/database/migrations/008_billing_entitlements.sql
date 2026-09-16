-- Evidrilo billing boundary, provider-event idempotency and server-owned access.

create table if not exists public.entitlement_events (
    provider_event_id text primary key,
    account_id uuid not null references auth.users(id) on delete cascade,
    entitlement text not null check (char_length(entitlement) between 1 and 128),
    status text not null check (status in ('active', 'expired', 'revoked')),
    occurred_at timestamptz not null,
    received_at timestamptz not null default now()
);

create table if not exists public.entitlements (
    account_id uuid not null references auth.users(id) on delete cascade,
    entitlement text not null check (char_length(entitlement) between 1 and 128),
    status text not null check (status in ('active', 'expired', 'revoked')),
    updated_at timestamptz not null default now(),
    primary key (account_id, entitlement)
);

alter table public.entitlement_events enable row level security;
alter table public.entitlements enable row level security;

create policy entitlement_events_select_own on public.entitlement_events
    for select using (account_id = auth.uid());

create policy entitlements_select_own on public.entitlements
    for select using (account_id = auth.uid());

-- Client roles have no insert/update/delete policy. Webhook processing owns
-- entitlement writes after signature verification and provider-event dedupe.
