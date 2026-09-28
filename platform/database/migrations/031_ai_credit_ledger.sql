-- Evidrilo AI credits, server-owned consent, grants, reservations, and audit-safe settlement.
-- Credits are never granted or mutated by the mobile client.

create table if not exists public.ai_credit_consents (
    account_id uuid primary key references auth.users(id) on delete cascade,
    consent_version text not null check (char_length(consent_version) between 1 and 64),
    consented_at timestamptz not null default now(),
    revoked_at timestamptz null
);

create table if not exists public.ai_credit_grants (
    grant_id uuid primary key default gen_random_uuid(),
    account_id uuid not null references auth.users(id) on delete cascade,
    grant_kind text not null check (grant_kind in ('free_once', 'subscription_month')),
    grant_key text not null check (char_length(grant_key) between 1 and 64),
    credits integer not null check (credits > 0 and credits <= 100),
    reserved_credits integer not null default 0 check (reserved_credits >= 0),
    consumed_credits integer not null default 0 check (consumed_credits >= 0),
    starts_at timestamptz not null,
    expires_at timestamptz null,
    created_at timestamptz not null default now(),
    check (reserved_credits + consumed_credits <= credits),
    check (expires_at is null or expires_at > starts_at),
    unique (account_id, grant_kind, grant_key)
);

create table if not exists public.ai_credit_reservations (
    account_id uuid not null references auth.users(id) on delete cascade,
    request_id text not null check (char_length(request_id) between 8 and 128),
    grant_id uuid not null references public.ai_credit_grants(grant_id) on delete cascade,
    status text not null check (status in ('reserved', 'consumed', 'released')),
    reserved_at timestamptz not null default now(),
    completed_at timestamptz null,
    primary key (account_id, request_id)
);

create index if not exists ai_credit_grants_account_expiry_idx
    on public.ai_credit_grants (account_id, expires_at, grant_kind, grant_key);

create index if not exists ai_credit_reservations_grant_status_idx
    on public.ai_credit_reservations (grant_id, status);

alter table public.ai_credit_consents enable row level security;
alter table public.ai_credit_grants enable row level security;
alter table public.ai_credit_reservations enable row level security;

create policy ai_credit_consents_select_own on public.ai_credit_consents
    for select using (account_id = auth.uid());

create policy ai_credit_grants_select_own on public.ai_credit_grants
    for select using (account_id = auth.uid());

create policy ai_credit_reservations_select_own on public.ai_credit_reservations
    for select using (account_id = auth.uid());

-- The API/worker owns all credit mutations. Client roles may only read their
-- own balance through a validated API projection.
revoke all on public.ai_credit_consents from anon, authenticated;
revoke all on public.ai_credit_grants from anon, authenticated;
revoke all on public.ai_credit_reservations from anon, authenticated;
grant select on public.ai_credit_consents, public.ai_credit_grants,
    public.ai_credit_reservations to authenticated;

-- Account deletion must remove AI consent, grants, and reservations in the
-- same transaction as the existing platform-owned deletion boundary.
create or replace function public.purge_ai_credit_data_on_account_deletion()
returns trigger
language plpgsql
security definer
set search_path = public, pg_temp
as $$
begin
    if new.status = 'completed'
       and old.status is distinct from new.status then
        delete from public.ai_credit_reservations
         where account_id = new.account_id;
        delete from public.ai_credit_grants
         where account_id = new.account_id;
        delete from public.ai_credit_consents
         where account_id = new.account_id;
    end if;
    return new;
end;
$$;

drop trigger if exists account_deletion_purge_ai_credit_data
    on public.account_deletion_requests;
create trigger account_deletion_purge_ai_credit_data
    after update of status on public.account_deletion_requests
    for each row execute function public.purge_ai_credit_data_on_account_deletion();

revoke all on function public.purge_ai_credit_data_on_account_deletion() from public;
