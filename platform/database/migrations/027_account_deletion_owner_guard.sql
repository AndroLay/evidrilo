-- Evidrilo P7, preserve the organization ownership invariant during account deletion.
-- Account deletion is allowed only after ownership has been transferred when the
-- requested account is the sole active owner of any organization. Organization
-- rows are locked in stable order so this check is serialized with membership
-- lifecycle mutations.

create or replace function public.prepare_account_deletion(requested_account_id uuid)
returns text
language plpgsql
security definer
set search_path = public, auth, pg_temp
as $$
declare
    existing_status text;
    owner_organization_id uuid;
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

    -- MembershipStore serializes owner changes on the organization row. Take
    -- the same locks before counting owners so deletion cannot race a grant,
    -- revoke, or leave operation into an ownerless organization.
    for owner_organization_id in
        select m.organization_id
          from public.organization_memberships m
         where m.account_id = requested_account_id
           and m.role = 'owner'
           and m.active = true
         order by m.organization_id
    loop
        perform 1
          from public.organizations
         where organization_id = owner_organization_id
         for update;

        if not exists (
            select 1
              from public.organization_memberships other
             where other.organization_id = owner_organization_id
               and other.account_id <> requested_account_id
               and other.role = 'owner'
               and other.active = true
        ) then
            raise exception 'owner_transfer_required' using errcode = 'P0001';
        end if;
    end loop;

    insert into public.account_deletion_requests (account_id, status)
    values (requested_account_id, 'in_progress')
    on conflict (account_id) do update
        set status = 'in_progress', requested_at = now(), completed_at = null;

    perform set_config('evidrilo.account_deletion', 'on', true);

    update public.case_versions
       set author_id = case when author_id = requested_account_id then null else author_id end,
           reviewer_id = case when reviewer_id = requested_account_id then null else reviewer_id end
     where author_id = requested_account_id
        or reviewer_id = requested_account_id;

    update public.case_review_decisions
       set reviewer_id = null,
           reason = '[ACCOUNT_DELETED]'
     where reviewer_id = requested_account_id;

    update public.membership_audit_events
       set actor_account_id = case when actor_account_id = requested_account_id then null else actor_account_id end,
           target_account_id = case when target_account_id = requested_account_id then null else target_account_id end,
           reason = '[ACCOUNT_DELETED]'
     where actor_account_id = requested_account_id
        or target_account_id = requested_account_id;

    delete from public.recommendation_events where account_id = requested_account_id;
    delete from public.ai_audit_events where account_id = requested_account_id;
    delete from public.entitlement_events where account_id = requested_account_id;
    delete from public.entitlements where account_id = requested_account_id;
    delete from public.analytics_events where account_id = requested_account_id;
    delete from public.progress_daily_projections where account_id = requested_account_id;
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
