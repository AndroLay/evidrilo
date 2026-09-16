-- Evidrilo P6, metadata-only AI audit trail.

create table if not exists public.ai_audit_events (
    account_id uuid not null references auth.users(id) on delete cascade,
    request_id text not null,
    prompt_version text not null,
    input_hash text not null check (input_hash ~ '^[a-f0-9]{64}$' or input_hash = 'not-recorded'),
    provider text null,
    outcome text not null,
    reason_code text null,
    created_at timestamptz not null default now(),
    primary key (account_id, request_id)
);

alter table public.ai_audit_events enable row level security;

create policy ai_audit_events_select_own on public.ai_audit_events
    for select using (account_id = auth.uid());

-- No client insert policy: the gateway/worker owns audit writes.
