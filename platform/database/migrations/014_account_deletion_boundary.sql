-- Evidrilo account lifecycle, explicit data-deletion boundary.
-- Managed Auth account removal remains an external admin operation. This
-- function removes platform-owned account data and anonymizes references in
-- shared authored content before that external operation is requested.

alter table public.case_review_decisions
    alter column reviewer_id drop not null;

create table if not exists public.account_deletion_requests (
    account_id uuid primary key references auth.users(id) on delete cascade,
    status text not null check (status in ('in_progress', 'completed')),
    requested_at timestamptz not null default now(),
    completed_at timestamptz null
);

alter table public.account_deletion_requests enable row level security;

-- No client read/write policy is granted. The API calls the server-owned
-- function after setting the verified request subject in its transaction.

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

    -- Keep shared case content available, but remove direct account identity.
    update public.case_versions
       set author_id = null,
           reviewer_id = null
     where author_id = requested_account_id
        or reviewer_id = requested_account_id;

    -- Review reasons may contain free-form personal context. Preserve the
    -- decision shape but remove the deleted account's identity and text.
    update public.case_review_decisions
       set reviewer_id = null,
           reason = '[ACCOUNT_DELETED]'
     where reviewer_id = requested_account_id;

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

