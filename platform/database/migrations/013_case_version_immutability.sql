-- Evidrilo P4, database-side enforcement for the published case boundary.
-- The API remains the managed mutation path, but direct database writes must
-- not be able to bypass review or rewrite a version already consumed by a
-- learner.

create or replace function public.enforce_case_version_lifecycle()
returns trigger
language plpgsql
set search_path = public
as $$
begin
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

drop trigger if exists case_versions_lifecycle_guard on public.case_versions;
create trigger case_versions_lifecycle_guard
    before update on public.case_versions
    for each row execute function public.enforce_case_version_lifecycle();

