-- Evidrilo P4, append-only case lifecycle history.
-- Review decisions explain review outcomes, but they do not cover draft
-- creation or the complete publish/retire state machine. This migration adds
-- a server-owned event log without copying case content or storing extra PII.

create table if not exists public.case_lifecycle_audit_events (
    audit_event_id uuid primary key default gen_random_uuid(),
    case_version_id text not null,
    organization_id uuid null,
    actor_account_id uuid null,
    event_type text not null check (event_type in ('created', 'transitioned')),
    from_state text null check (from_state is null or from_state in ('draft', 'review', 'approved', 'published', 'retired')),
    to_state text not null check (to_state in ('draft', 'review', 'approved', 'published', 'retired')),
    reason text null check (reason is null or char_length(reason) between 1 and 2000),
    created_at timestamptz not null default now(),
    check (
        (event_type = 'created' and from_state is null)
        or (event_type = 'transitioned' and from_state is not null)
    ),
    check (from_state is null or from_state <> to_state)
);

create index if not exists case_lifecycle_audit_version_idx
    on public.case_lifecycle_audit_events (case_version_id, created_at desc);

create index if not exists case_lifecycle_audit_scope_idx
    on public.case_lifecycle_audit_events (organization_id, created_at desc);

alter table public.case_lifecycle_audit_events enable row level security;

-- The API/worker database owner writes through the trigger. Client roles have
-- no direct read or write path to lifecycle history.
revoke all on table public.case_lifecycle_audit_events from public;
revoke all on table public.case_lifecycle_audit_events from anon, authenticated;

create or replace function public.append_case_lifecycle_audit_event()
returns trigger
language plpgsql
security definer
set search_path = public, auth, pg_temp
as $$
declare
    actor_claim text;
    actor_id uuid;
    event_reason text;
begin
    if TG_OP = 'UPDATE' then
        if old.status is not distinct from new.status then
            return new;
        end if;
    end if;

    actor_claim := nullif(current_setting('request.jwt.claim.sub', true), '');
    if actor_claim is null
       or actor_claim !~ '^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$' then
        raise exception 'case_lifecycle_actor_required' using errcode = '42501';
    end if;
    actor_id := actor_claim::uuid;

    if not exists (select 1 from auth.users where id = actor_id) then
        raise exception 'case_lifecycle_actor_unknown' using errcode = '42501';
    end if;

    event_reason := nullif(btrim(coalesce(current_setting('evidrilo.case_lifecycle_reason', true), '')), '');
    if event_reason is not null and char_length(event_reason) > 2000 then
        raise exception 'case_lifecycle_reason_invalid' using errcode = '22023';
    end if;

    if TG_OP = 'INSERT' then
        insert into public.case_lifecycle_audit_events (
            case_version_id, organization_id, actor_account_id,
            event_type, from_state, to_state, reason
        ) values (
            new.case_version_id, new.organization_id, actor_id,
            'created', null, new.status, event_reason
        );
    else
        insert into public.case_lifecycle_audit_events (
            case_version_id, organization_id, actor_account_id,
            event_type, from_state, to_state, reason
        ) values (
            new.case_version_id, new.organization_id, actor_id,
            'transitioned', old.status, new.status, event_reason
        );
    end if;

    return new;
end;
$$;

revoke all on function public.append_case_lifecycle_audit_event() from public;

drop trigger if exists case_versions_lifecycle_audit on public.case_versions;
create trigger case_versions_lifecycle_audit
    after insert or update on public.case_versions
    for each row execute function public.append_case_lifecycle_audit_event();

create or replace function public.enforce_case_lifecycle_audit_append_only()
returns trigger
language plpgsql
set search_path = public, pg_temp
as $$
begin
    if TG_OP = 'UPDATE' then
        -- Account deletion is the sole privacy exception: remove the deleted
        -- actor UUID while preserving the event and every other field.
        if coalesce(current_setting('evidrilo.account_deletion', true), 'off') = 'on'
           and old.actor_account_id is not null
           and new.actor_account_id is null
           and new.audit_event_id is not distinct from old.audit_event_id
           and new.case_version_id is not distinct from old.case_version_id
           and new.organization_id is not distinct from old.organization_id
           and new.event_type is not distinct from old.event_type
           and new.from_state is not distinct from old.from_state
           and new.to_state is not distinct from old.to_state
           and new.reason is not distinct from old.reason
           and new.created_at is not distinct from old.created_at then
            return new;
        end if;
    end if;

    raise exception 'case_lifecycle_audit_append_only' using errcode = '42501';
end;
$$;

revoke all on function public.enforce_case_lifecycle_audit_append_only() from public;

drop trigger if exists case_lifecycle_audit_append_only_guard on public.case_lifecycle_audit_events;
create trigger case_lifecycle_audit_append_only_guard
    before update or delete on public.case_lifecycle_audit_events
    for each row execute function public.enforce_case_lifecycle_audit_append_only();

-- The existing account-deletion function keeps shared cases but anonymizes
-- identity references. Run the same privacy operation for this event log at
-- completion, while retaining the append-only event itself.
create or replace function public.anonymize_case_lifecycle_audit_actor()
returns trigger
language plpgsql
security definer
set search_path = public, pg_temp
as $$
begin
    if new.status = 'completed'
       and old.status is distinct from new.status
       and coalesce(current_setting('evidrilo.account_deletion', true), 'off') = 'on' then
        update public.case_lifecycle_audit_events
           set actor_account_id = null
         where actor_account_id = new.account_id;
    end if;
    return new;
end;
$$;

revoke all on function public.anonymize_case_lifecycle_audit_actor() from public;

drop trigger if exists account_deletion_case_lifecycle_audit on public.account_deletion_requests;
create trigger account_deletion_case_lifecycle_audit
    after update on public.account_deletion_requests
    for each row execute function public.anonymize_case_lifecycle_audit_actor();
