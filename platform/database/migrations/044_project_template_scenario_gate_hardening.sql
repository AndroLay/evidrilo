-- Preserve the 043 checksum and repair both upgrade and publication paths.
-- Existing immutable content can receive metadata-only maintenance, while new
-- content and new publication still require reviewed scenario coverage.

alter table public.project_template_versions
    drop constraint if exists project_template_requires_normal_and_edge_examples;

create or replace function public.enforce_project_template_example_kinds()
returns trigger
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    has_normal boolean;
    has_edge_or_conflicting boolean;
begin
    -- Account deletion and other metadata-only maintenance must not force an
    -- immutable pre-migration template to acquire newly invented semantics.
    if tg_op = 'UPDATE' and new.content is not distinct from old.content then
        return new;
    end if;

    select coalesce(bool_or(examples.value ->> 'kind' = 'normal'), false),
           coalesce(bool_or(examples.value ->> 'kind' = 'edge_or_conflicting'), false)
      into has_normal, has_edge_or_conflicting
      from jsonb_array_elements(coalesce(new.content -> 'examples', '[]'::jsonb)) examples(value);

    if not coalesce(has_normal, false) or not coalesce(has_edge_or_conflicting, false) then
        raise exception 'project_template_requires_normal_and_edge_examples' using errcode = 'check_violation';
    end if;

    return new;
end;
$$;
revoke all on function public.enforce_project_template_example_kinds() from public;
drop trigger if exists project_template_example_kinds_guard on public.project_template_versions;
create trigger project_template_example_kinds_guard
    before insert or update on public.project_template_versions
    for each row execute function public.enforce_project_template_example_kinds();

create or replace function public.enforce_project_template_publish_example_kinds()
returns trigger
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    reviewed_normal boolean;
    reviewed_edge_or_conflicting boolean;
begin
    if new.state <> 'published' or old.state <> 'approved' then
        return new;
    end if;

    select coalesce(bool_or(examples.value ->> 'kind' = 'normal'), false),
           coalesce(bool_or(examples.value ->> 'kind' = 'edge_or_conflicting'), false)
      into reviewed_normal, reviewed_edge_or_conflicting
      from (
            select reviewed_example_ids
              from public.project_template_review_decisions
             where template_id = new.template_id
               and template_version = new.template_version
               and decision = 'approved'
             order by created_at desc, review_decision_id desc
             limit 1
      ) decision
      cross join lateral unnest(decision.reviewed_example_ids) selected(example_id)
      cross join lateral jsonb_array_elements(coalesce(new.content -> 'examples', '[]'::jsonb)) examples(value)
     where examples.value ->> 'id' = selected.example_id;

    if not coalesce(reviewed_normal, false) or not coalesce(reviewed_edge_or_conflicting, false) then
        raise exception 'project_template_requires_reviewed_normal_and_edge_examples' using errcode = 'check_violation';
    end if;

    return new;
end;
$$;
revoke all on function public.enforce_project_template_publish_example_kinds() from public;
drop trigger if exists project_template_publish_example_kinds_guard on public.project_template_versions;
create trigger project_template_publish_example_kinds_guard
    before update of state on public.project_template_versions
    for each row execute function public.enforce_project_template_publish_example_kinds();
