-- Cloud storage of student-authored projects is opt-in and policy-versioned.
-- The event ledger contains no project content and is retained as the minimum
-- record of the policy and decision; retention duration remains an operator /
-- privacy-policy decision outside this migration.

create table if not exists public.student_project_cloud_consents (
    account_id uuid primary key references auth.users(id) on delete cascade,
    policy_version text not null check (
        char_length(policy_version) between 1 and 80
        and policy_version ~ '^student-project-cloud\.v[0-9]+$'
    ),
    granted boolean not null,
    granted_at timestamptz null,
    revoked_at timestamptz null,
    updated_at timestamptz not null default now(),
    constraint student_project_cloud_consent_state_check check (
        (granted = true and granted_at is not null and revoked_at is null)
        or (granted = false and granted_at is not null and revoked_at is not null)
    )
);

create table if not exists public.student_project_cloud_consent_events (
    event_id uuid primary key,
    account_id uuid not null,
    policy_version text not null check (
        char_length(policy_version) between 1 and 80
        and policy_version ~ '^student-project-cloud\.v[0-9]+$'
    ),
    decision text not null check (decision in ('grant', 'revoke')),
    decided_at timestamptz not null default now()
);

create index if not exists student_project_cloud_consent_events_account_time_idx
    on public.student_project_cloud_consent_events (account_id, decided_at desc, event_id desc);

alter table public.student_project_cloud_consents enable row level security;
alter table public.student_project_cloud_consent_events enable row level security;

drop policy if exists student_project_cloud_consents_owner_read
    on public.student_project_cloud_consents;
create policy student_project_cloud_consents_owner_read
    on public.student_project_cloud_consents
    for select to authenticated
    using (account_id = auth.uid());

drop policy if exists student_project_cloud_consent_events_owner_read
    on public.student_project_cloud_consent_events;
create policy student_project_cloud_consent_events_owner_read
    on public.student_project_cloud_consent_events
    for select to authenticated
    using (account_id = auth.uid());

revoke all on public.student_project_cloud_consents from public, anon, authenticated;
grant select on public.student_project_cloud_consents to authenticated;
revoke all on public.student_project_cloud_consent_events from public, anon, authenticated;
grant select on public.student_project_cloud_consent_events to authenticated;

create or replace function public.prevent_student_project_cloud_consent_event_rewrite()
returns trigger
language plpgsql
set search_path = public, pg_temp
as $$
begin
    raise exception 'student_project_cloud_consent_event_immutable'
        using errcode = 'check_violation';
end;
$$;

revoke all on function public.prevent_student_project_cloud_consent_event_rewrite() from public;
drop trigger if exists student_project_cloud_consent_events_immutable
    on public.student_project_cloud_consent_events;
create trigger student_project_cloud_consent_events_immutable
    before update or delete on public.student_project_cloud_consent_events
    for each row execute function public.prevent_student_project_cloud_consent_event_rewrite();

create or replace function public.purge_student_project_cloud_consent_on_account_deletion()
returns trigger
language plpgsql
security definer
set search_path = public, pg_temp
as $$
begin
    if new.status = 'completed'
       and old.status is distinct from new.status then
        delete from public.student_project_cloud_consents
         where account_id = new.account_id;
    end if;
    return new;
end;
$$;

revoke all on function public.purge_student_project_cloud_consent_on_account_deletion() from public;
drop trigger if exists account_deletion_purge_student_project_cloud_consent
    on public.account_deletion_requests;
create trigger account_deletion_purge_student_project_cloud_consent
    after update of status on public.account_deletion_requests
    for each row execute function public.purge_student_project_cloud_consent_on_account_deletion();
