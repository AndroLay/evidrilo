-- New project templates must contain both a normal and an edge/conflicting
-- reviewed example. Existing immutable versions remain readable but are not
-- silently upgraded or re-labeled by this migration.

alter table public.project_template_versions
    add constraint project_template_requires_normal_and_edge_examples
    check (
        coalesce(content -> 'examples' @> '[{"kind":"normal"}]'::jsonb, false)
        and coalesce(content -> 'examples' @> '[{"kind":"edge_or_conflicting"}]'::jsonb, false)
    ) not valid;

create or replace function public.enforce_project_template_review_example_kinds()
returns trigger
language plpgsql
security definer
set search_path = public, auth, pg_temp
as $$
declare
    reviewed_normal boolean;
    reviewed_edge_or_conflicting boolean;
begin
    if new.decision <> 'approved' then
        return new;
    end if;

    select coalesce(bool_or(examples.value ->> 'kind' = 'normal'), false),
           coalesce(bool_or(examples.value ->> 'kind' = 'edge_or_conflicting'), false)
      into reviewed_normal, reviewed_edge_or_conflicting
      from public.project_template_versions template
      cross join lateral unnest(new.reviewed_example_ids) selected(example_id)
      cross join lateral jsonb_array_elements(template.content -> 'examples') examples(value)
     where template.template_id = new.template_id
       and template.template_version = new.template_version
       and examples.value ->> 'id' = selected.example_id;

    if not coalesce(reviewed_normal, false) or not coalesce(reviewed_edge_or_conflicting, false) then
        raise exception 'project_template_requires_normal_and_edge_examples' using errcode = 'check_violation';
    end if;

    return new;
end;
$$;
revoke all on function public.enforce_project_template_review_example_kinds() from public;

drop trigger if exists project_template_review_example_kinds_guard on public.project_template_review_decisions;
create trigger project_template_review_example_kinds_guard
    before insert on public.project_template_review_decisions
    for each row execute function public.enforce_project_template_review_example_kinds();
