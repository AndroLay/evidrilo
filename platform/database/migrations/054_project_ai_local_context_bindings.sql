-- Metadata-only owner/revision binding for local-first project AI.
-- Student-authored text is sent only in the explicitly selected AI request and
-- is never persisted in this table.

alter table public.project_ai_activity
    add column if not exists uses_local_project_context boolean not null default false,
    add column if not exists base_project_binding_generation bigint null check (
        base_project_binding_generation is null or base_project_binding_generation >= 1
    );

create table if not exists public.project_ai_local_contexts (
    account_id uuid not null references auth.users(id) on delete cascade,
    project_id uuid not null,
    current_revision integer not null check (current_revision >= 1),
    binding_generation bigint not null default 1 check (binding_generation >= 1),
    template_id text null check (
        template_id is null or (
            char_length(template_id) between 1 and 96
            and template_id ~ '^[a-z0-9]+([._-][a-z0-9]+)*$'
        )
    ),
    template_version integer null check (template_version is null or template_version >= 1),
    available_evidence_ids jsonb not null default '[]'::jsonb check (
        jsonb_typeof(available_evidence_ids) = 'array'
        and jsonb_array_length(available_evidence_ids) <= 2048
    ),
    updated_at timestamptz not null default clock_timestamp(),
    primary key (account_id, project_id),
    constraint project_ai_local_context_template_pair_check check (
        (template_id is null and template_version is null)
        or (template_id is not null and template_version is not null)
    )
);

-- Preserve existing account-owned project history before moving its FK from
-- cloud project storage to this metadata-only project identity registry.
insert into public.project_ai_local_contexts (
    account_id,
    project_id,
    current_revision,
    available_evidence_ids
)
select
    account_id,
    project_id,
    max(greatest(coalesce(base_project_revision, 1), coalesce(result_project_revision, 1))),
    '[]'::jsonb
from public.project_ai_activity
where project_id is not null
group by account_id, project_id
on conflict (account_id, project_id) do update
set current_revision = greatest(
        public.project_ai_local_contexts.current_revision,
        excluded.current_revision
    ),
    updated_at = clock_timestamp();

do $$
declare
    constraint_name text;
begin
    for constraint_name in
        select conname
        from pg_constraint
        where conrelid = 'public.project_ai_activity'::regclass
          and confrelid = 'public.student_projects'::regclass
          and contype = 'f'
    loop
        execute format('alter table public.project_ai_activity drop constraint %I', constraint_name);
    end loop;
end;
$$;

alter table public.project_ai_activity
    add constraint project_ai_activity_local_context_owner_fk
    foreign key (account_id, project_id)
    references public.project_ai_local_contexts (account_id, project_id)
    on delete cascade;

create index if not exists project_ai_local_contexts_updated_idx
    on public.project_ai_local_contexts (account_id, updated_at desc, project_id);

alter table public.project_ai_local_contexts enable row level security;
drop policy if exists project_ai_local_contexts_owner_read on public.project_ai_local_contexts;
create policy project_ai_local_contexts_owner_read on public.project_ai_local_contexts
    for select to authenticated using (account_id = auth.uid());
revoke all on public.project_ai_local_contexts from public, anon, authenticated;
grant select on public.project_ai_local_contexts to authenticated;

create or replace function public.purge_project_ai_local_context_on_student_project_delete()
returns trigger
language plpgsql
security definer
set search_path = public, pg_temp
as $$
begin
    delete from public.project_ai_local_contexts
    where account_id = old.account_id and project_id = old.project_id;
    return old;
end;
$$;

revoke all on function public.purge_project_ai_local_context_on_student_project_delete() from public;
drop trigger if exists student_project_purge_project_ai_local_context
    on public.student_projects;
create trigger student_project_purge_project_ai_local_context
    after delete on public.student_projects
    for each row execute function public.purge_project_ai_local_context_on_student_project_delete();

create or replace function public.purge_project_ai_activity_on_account_deletion()
returns trigger
language plpgsql
security definer
set search_path = public, pg_temp
as $$
begin
    if new.status = 'completed'
       and old.status is distinct from new.status then
        perform set_config('evidrilo.account_deletion', 'on', true);
        delete from public.project_ai_activity where account_id = new.account_id;
        delete from public.project_ai_local_contexts where account_id = new.account_id;
    end if;
    return new;
end;
$$;

revoke all on function public.purge_project_ai_activity_on_account_deletion() from public;
drop trigger if exists account_deletion_purge_project_ai_local_context
    on public.account_deletion_requests;
create trigger account_deletion_purge_project_ai_local_context
    after update of status on public.account_deletion_requests
    for each row execute function public.purge_project_ai_activity_on_account_deletion();
