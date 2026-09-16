-- Evidrilo P2, forward-only migration.
-- This migration is intended for Supabase PostgreSQL, where auth.users and
-- auth.uid() are provided by Supabase Auth. It contains no production values.

create sequence if not exists public.sync_server_sequence as bigint;

create table if not exists public.account_profiles (
    account_id uuid primary key references auth.users(id) on delete cascade,
    created_at timestamptz not null default now(),
    deleted_at timestamptz null
);

create table if not exists public.case_versions (
    case_version_id text primary key,
    content_hash text not null,
    status text not null check (status in ('draft', 'review', 'published', 'retired')),
    published_at timestamptz null,
    created_at timestamptz not null default now()
);

create table if not exists public.attempt_commands (
    account_id uuid not null references auth.users(id) on delete cascade,
    command_id uuid not null,
    attempt_id uuid not null,
    case_version_id text not null references public.case_versions(case_version_id),
    command_type text not null check (command_type in ('attempt_started', 'attempt_submitted', 'revision_recorded')),
    revision_number integer not null check (revision_number between 0 and 1),
    client_occurred_at timestamptz not null,
    snapshot_digest text not null check (snapshot_digest ~ '^[a-f0-9]{64}$'),
    received_at timestamptz not null default now(),
    primary key (account_id, command_id)
);

create unique index if not exists attempt_commands_attempt_revision_unique
    on public.attempt_commands (account_id, attempt_id, revision_number, command_type);

create table if not exists public.sync_changes (
    server_sequence bigint primary key default nextval('public.sync_server_sequence'),
    account_id uuid not null references auth.users(id) on delete cascade,
    command_id uuid not null,
    attempt_id uuid not null,
    case_version_id text not null,
    command_type text not null,
    revision_number integer not null,
    snapshot_digest text not null,
    created_at timestamptz not null default now(),
    unique (account_id, command_id)
);

create index if not exists sync_changes_account_sequence_idx
    on public.sync_changes (account_id, server_sequence);

alter table public.account_profiles enable row level security;
alter table public.case_versions enable row level security;
alter table public.attempt_commands enable row level security;
alter table public.sync_changes enable row level security;

create policy account_profiles_select_own on public.account_profiles
    for select using (account_id = auth.uid() and deleted_at is null);

create policy published_case_versions_select on public.case_versions
    for select using (auth.uid() is not null and status = 'published');

create policy attempt_commands_select_own on public.attempt_commands
    for select using (account_id = auth.uid());

create policy attempt_commands_insert_own on public.attempt_commands
    for insert with check (account_id = auth.uid());

create policy sync_changes_select_own on public.sync_changes
    for select using (account_id = auth.uid());

create or replace function public.append_sync_change()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
    insert into public.sync_changes (
        account_id, command_id, attempt_id, case_version_id,
        command_type, revision_number, snapshot_digest
    ) values (
        new.account_id, new.command_id, new.attempt_id, new.case_version_id,
        new.command_type, new.revision_number, new.snapshot_digest
    ) on conflict (account_id, command_id) do nothing;
    return new;
end;
$$;

drop trigger if exists attempt_commands_append_sync_change on public.attempt_commands;
create trigger attempt_commands_append_sync_change
    after insert on public.attempt_commands
    for each row execute function public.append_sync_change();
