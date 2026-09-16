-- Evidrilo P3/P4, forward fix for account deletion across immutable content.
-- Published and retired case versions remain immutable for normal writes. The
-- server-owned deletion function may only clear identity references and must
-- also remove disposable daily projections before managed Auth removal.

create or replace function public.enforce_case_version_lifecycle()
returns trigger
language plpgsql
set search_path = public
as $$
begin
    -- Account deletion is the only operation allowed to anonymize identity
    -- references on an immutable version. All content and lifecycle fields
    -- must remain byte-for-byte equivalent, and identities may only become
    -- null, never be replaced with another account.
    if old.status in ('published', 'retired')
       and new.status = old.status
       and coalesce(current_setting('evidrilo.account_deletion', true), 'off') = 'on'
       and new.case_version_id is not distinct from old.case_version_id
       and new.content_hash is not distinct from old.content_hash
       and new.published_at is not distinct from old.published_at
       and new.created_at is not distinct from old.created_at
       and new.case_id is not distinct from old.case_id
       and new.title is not distinct from old.title
       and new.evaluator_version is not distinct from old.evaluator_version
       and new.skill_tags is not distinct from old.skill_tags
       and new.organization_id is not distinct from old.organization_id
       and new.objective is not distinct from old.objective
       and new.difficulty is not distinct from old.difficulty
       and new.evidence_references is not distinct from old.evidence_references
       and new.content is not distinct from old.content
       and (new.author_id is null or new.author_id is not distinct from old.author_id)
       and (new.reviewer_id is null or new.reviewer_id is not distinct from old.reviewer_id) then
        return new;
    end if;

    if old.status = 'draft' and new.status not in ('draft', 'review', 'retired') then
        raise exception 'invalid_case_transition' using errcode = 'check_violation';
    end if;
    if old.status = 'review' and new.status not in ('review', 'draft', 'approved', 'retired') then
        raise exception 'invalid_case_transition' using errcode = 'check_violation';
    end if;
    if old.status = 'approved' and new.status not in ('approved', 'published', 'retired') then
        raise exception 'invalid_case_transition' using errcode = 'check_violation';
    end if;

    if old.status = 'published' then
        if new.status not in ('published', 'retired')
            or new.case_version_id is distinct from old.case_version_id
            or new.content_hash is distinct from old.content_hash
            or new.published_at is distinct from old.published_at
            or new.created_at is distinct from old.created_at
            or new.case_id is distinct from old.case_id
            or new.title is distinct from old.title
            or new.evaluator_version is distinct from old.evaluator_version
            or new.skill_tags is distinct from old.skill_tags
            or new.author_id is distinct from old.author_id
            or new.reviewer_id is distinct from old.reviewer_id
            or new.organization_id is distinct from old.organization_id
            or new.objective is distinct from old.objective
            or new.difficulty is distinct from old.difficulty
            or new.evidence_references is distinct from old.evidence_references
            or new.content is distinct from old.content then
            raise exception 'published_case_immutable' using errcode = 'check_violation';
        end if;
    elsif old.status = 'retired' and new is distinct from old then
        raise exception 'retired_case_immutable' using errcode = 'check_violation';
    end if;

    return new;
end;
$$;

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
