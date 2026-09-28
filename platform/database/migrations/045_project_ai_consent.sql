-- Project AI data consent is distinct from AI-credit consent and cloud-sync consent.
-- Store only the account decision/policy revision; never store project text here.

create table if not exists public.project_ai_consents (
    account_id uuid primary key references auth.users(id) on delete cascade,
    policy_version text not null check (
        char_length(policy_version) between 1 and 80
        and policy_version ~ '^project-ai-data\.v[0-9]+$'
    ),
    granted boolean not null,
    granted_at timestamptz null,
    revoked_at timestamptz null,
    consent_generation integer not null check (consent_generation >= 1),
    updated_at timestamptz not null default now(),
    constraint project_ai_consent_state_check check (
        (granted = true and granted_at is not null and revoked_at is null)
        or (granted = false and granted_at is not null and revoked_at is not null)
    )
);

create table if not exists public.project_ai_consent_events (
    event_id uuid primary key default gen_random_uuid(),
    account_id uuid not null references auth.users(id) on delete cascade,
    policy_version text not null check (
        char_length(policy_version) between 1 and 80
        and policy_version ~ '^project-ai-data\.v[0-9]+$'
    ),
    decision text not null check (decision in ('grant', 'revoke')),
    consent_generation integer not null check (consent_generation >= 1),
    decided_at timestamptz not null default now(),
    unique (account_id, consent_generation)
);

create index if not exists project_ai_consent_events_account_time_idx
    on public.project_ai_consent_events (account_id, decided_at desc, event_id desc);

alter table public.project_ai_consents enable row level security;
alter table public.project_ai_consent_events enable row level security;

drop policy if exists project_ai_consents_owner_read on public.project_ai_consents;
create policy project_ai_consents_owner_read on public.project_ai_consents
    for select to authenticated using (account_id = auth.uid());

drop policy if exists project_ai_consent_events_owner_read on public.project_ai_consent_events;
create policy project_ai_consent_events_owner_read on public.project_ai_consent_events
    for select to authenticated using (account_id = auth.uid());

revoke all on public.project_ai_consents from public, anon, authenticated;
grant select on public.project_ai_consents to authenticated;
revoke all on public.project_ai_consent_events from public, anon, authenticated;
grant select on public.project_ai_consent_events to authenticated;

create or replace function public.prevent_project_ai_consent_event_rewrite()
returns trigger
language plpgsql
set search_path = public, pg_temp
as $$
begin
    if tg_op = 'DELETE'
       and (
           pg_trigger_depth() > 1
           or coalesce(current_setting('evidrilo.account_deletion', true), 'off') = 'on'
       ) then
        return old;
    end if;

    raise exception 'project_ai_consent_event_immutable'
        using errcode = 'check_violation';
end;
$$;

revoke all on function public.prevent_project_ai_consent_event_rewrite() from public;
drop trigger if exists project_ai_consent_events_append_only on public.project_ai_consent_events;
create trigger project_ai_consent_events_append_only
    before update or delete on public.project_ai_consent_events
    for each row execute function public.prevent_project_ai_consent_event_rewrite();

create or replace function public.purge_project_ai_consent_on_account_deletion()
returns trigger
language plpgsql
security definer
set search_path = public, pg_temp
as $$
begin
    if new.status = 'completed'
       and old.status is distinct from new.status then
        perform set_config('evidrilo.account_deletion', 'on', true);
        delete from public.project_ai_consent_events where account_id = new.account_id;
        delete from public.project_ai_consents where account_id = new.account_id;
    end if;
    return new;
end;
$$;

revoke all on function public.purge_project_ai_consent_on_account_deletion() from public;
drop trigger if exists account_deletion_purge_project_ai_consent
    on public.account_deletion_requests;
create trigger account_deletion_purge_project_ai_consent
    after update of status on public.account_deletion_requests
    for each row execute function public.purge_project_ai_consent_on_account_deletion();
