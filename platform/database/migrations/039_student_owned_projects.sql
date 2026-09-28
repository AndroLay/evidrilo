-- Student-owned project workspace foundation.
-- Project content is manually entered, account-private, versioned, and never
-- treated as a grade or an authoritative assessment of academic merit.

create table if not exists public.student_projects (
    project_id uuid primary key,
    account_id uuid not null references auth.users(id) on delete cascade,
    version integer not null check (version >= 1),
    document jsonb not null check (
        jsonb_typeof(document) = 'object'
        and octet_length(document::text) <= 81920
    ),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique (account_id, project_id)
);

create index if not exists student_projects_account_created_idx
    on public.student_projects (account_id, created_at desc, project_id desc);

create table if not exists public.student_project_revisions (
    revision_id uuid primary key,
    project_id uuid not null,
    account_id uuid not null references auth.users(id) on delete cascade,
    version integer not null check (version >= 1),
    document jsonb not null check (
        jsonb_typeof(document) = 'object'
        and octet_length(document::text) <= 81920
    ),
    created_at timestamptz not null default now(),
    unique (account_id, project_id, version),
    foreign key (account_id, project_id)
        references public.student_projects (account_id, project_id)
        on delete cascade
);

create index if not exists student_project_revisions_history_idx
    on public.student_project_revisions (account_id, project_id, version desc);

-- This account-scoped receipt table lets the API replay a successful command
-- even after a later edit or project deletion, without storing student text a
-- second time. Failed commands roll the reservation back with the transaction.
create table if not exists public.student_project_commands (
    account_id uuid not null references auth.users(id) on delete cascade,
    idempotency_key text not null check (char_length(idempotency_key) between 8 and 128),
    operation text not null check (operation in ('create', 'save', 'delete')),
    request_fingerprint text not null check (request_fingerprint ~ '^[a-f0-9]{64}$'),
    result_project_id uuid null,
    result_version integer null check (result_version is null or result_version >= 1),
    created_at timestamptz not null default now(),
    primary key (account_id, idempotency_key),
    constraint student_project_command_result_pair_check check (
        (result_project_id is null and result_version is null)
        or (result_project_id is not null and result_version is not null)
    )
);

alter table public.student_projects enable row level security;
alter table public.student_project_revisions enable row level security;
alter table public.student_project_commands enable row level security;

drop policy if exists student_projects_owner_read on public.student_projects;
create policy student_projects_owner_read
    on public.student_projects
    for select to authenticated
    using (account_id = auth.uid());
grant select on public.student_projects to authenticated;

drop policy if exists student_project_revisions_owner_read on public.student_project_revisions;
create policy student_project_revisions_owner_read
    on public.student_project_revisions
    for select to authenticated
    using (account_id = auth.uid());
grant select on public.student_project_revisions to authenticated;

-- Clients cannot mutate projects, revisions, or API idempotency receipts
-- directly. All writes pass through the authenticated API transaction.
revoke all on public.student_projects from public, anon, authenticated;
grant select on public.student_projects to authenticated;
revoke all on public.student_project_revisions from public, anon, authenticated;
grant select on public.student_project_revisions to authenticated;
revoke all on public.student_project_commands from public, anon, authenticated;

create or replace function public.prevent_student_project_revision_rewrite()
returns trigger
language plpgsql
set search_path = public, pg_temp
as $$
begin
    if tg_op = 'DELETE'
       and (
           pg_trigger_depth() > 1
           or
           coalesce(current_setting('evidrilo.project_deletion', true), 'off') = 'on'
           or coalesce(current_setting('evidrilo.account_deletion', true), 'off') = 'on'
       ) then
        return old;
    end if;

    raise exception 'student_project_revision_immutable'
        using errcode = 'check_violation';
end;
$$;

revoke all on function public.prevent_student_project_revision_rewrite() from public;
drop trigger if exists student_project_revisions_immutable on public.student_project_revisions;
create trigger student_project_revisions_immutable
    before update or delete on public.student_project_revisions
    for each row execute function public.prevent_student_project_revision_rewrite();

create or replace function public.purge_student_projects_on_account_deletion()
returns trigger
language plpgsql
security definer
set search_path = public, pg_temp
as $$
begin
    if new.status = 'completed'
       and old.status is distinct from new.status then
        perform set_config('evidrilo.account_deletion', 'on', true);
        delete from public.student_projects where account_id = new.account_id;
        delete from public.student_project_commands where account_id = new.account_id;
    end if;
    return new;
end;
$$;

revoke all on function public.purge_student_projects_on_account_deletion() from public;
drop trigger if exists account_deletion_purge_student_projects on public.account_deletion_requests;
create trigger account_deletion_purge_student_projects
    after update of status on public.account_deletion_requests
    for each row execute function public.purge_student_projects_on_account_deletion();
