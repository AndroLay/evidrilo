-- Evidrilo P7, server-owned membership lifecycle and audit.
-- Invitations/email delivery are intentionally separate provider gates; this
-- migration covers authorized grant, role change, revoke, and leave commands.

create table if not exists public.membership_audit_events (
    membership_event_id uuid primary key default gen_random_uuid(),
    organization_id uuid not null references public.organizations(organization_id) on delete cascade,
    actor_account_id uuid null references auth.users(id) on delete set null,
    target_account_id uuid null references auth.users(id) on delete set null,
    event_type text not null check (event_type in ('granted', 'role_changed', 'revoked', 'left')),
    role text not null check (role in ('learner', 'author', 'teacher', 'reviewer', 'maintainer', 'owner')),
    reason text null check (reason is null or char_length(reason) between 1 and 2000),
    created_at timestamptz not null default now()
);

create index if not exists membership_audit_scope_idx
    on public.membership_audit_events (organization_id, created_at desc);

alter table public.membership_audit_events enable row level security;

-- No client policy is granted. Account deletion is completed by the managed
-- Auth deletion operation, which nulls these identity references via FK action.

create or replace function public.prepare_account_deletion(requested_account_id uuid)
returns text
language plpgsql
security definer
set search_path = public, auth, pg_temp
as $$
declare
    existing_status text;
begin
    if requested_account_id is null or auth.uid() is distinct from requested_account_id then
        raise exception 'account_scope_required' using errcode = '42501';
    end if;

    select status
      into existing_status
      from public.account_deletion_requests
     where account_id = requested_account_id
     for update;

    if existing_status = 'completed' then
        return 'already_completed';
    end if;

    insert into public.account_deletion_requests (account_id, status)
    values (requested_account_id, 'in_progress')
    on conflict (account_id) do update
        set status = 'in_progress', requested_at = now(), completed_at = null;

    update public.case_versions
       set author_id = null,
           reviewer_id = null
     where author_id = requested_account_id
        or reviewer_id = requested_account_id;

    update public.case_review_decisions
       set reviewer_id = null,
           reason = '[ACCOUNT_DELETED]'
     where reviewer_id = requested_account_id;

    update public.membership_audit_events
       set actor_account_id = null,
           target_account_id = null,
           reason = '[ACCOUNT_DELETED]'
     where actor_account_id = requested_account_id
        or target_account_id = requested_account_id;

    delete from public.recommendation_events where account_id = requested_account_id;
    delete from public.ai_audit_events where account_id = requested_account_id;
    delete from public.entitlement_events where account_id = requested_account_id;
    delete from public.entitlements where account_id = requested_account_id;
    delete from public.analytics_events where account_id = requested_account_id;
    delete from public.progress_projections where account_id = requested_account_id;
    delete from public.worker_jobs where account_id = requested_account_id;
    delete from public.attempt_commands where account_id = requested_account_id;
    delete from public.sync_changes where account_id = requested_account_id;
    delete from public.cohort_enrollments where account_id = requested_account_id;
    delete from public.organization_memberships where account_id = requested_account_id;

    update public.account_profiles
       set deleted_at = now()
     where account_id = requested_account_id;

    update public.account_deletion_requests
       set status = 'completed', completed_at = now()
     where account_id = requested_account_id;

    return 'accepted';
end;
$$;

revoke all on function public.prepare_account_deletion(uuid) from public;

