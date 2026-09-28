-- Keep the platform access tombstone and provider deletion job independent of
-- auth.users: Supabase Auth deletion cascades auth-owned rows, but an unexpired
-- signed JWT must remain blocked and provider deletion must remain replayable.

create table if not exists public.account_deletion_tombstones (
    account_id uuid primary key,
    deleted_at timestamptz not null default now()
);

create table if not exists public.account_auth_deletion_outbox (
    outbox_id uuid primary key default gen_random_uuid(),
    account_id uuid null unique,
    status text not null default 'queued'
        check (status in ('queued', 'running', 'completed', 'dead_letter')),
    attempts integer not null default 0 check (attempts between 0 and 8),
    available_at timestamptz not null default now(),
    lease_token uuid null,
    leased_until timestamptz null,
    last_error_code text null check (
        last_error_code is null or last_error_code ~ '^[A-Z0-9_]{1,64}$'
    ),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    completed_at timestamptz null,
    constraint account_auth_deletion_outbox_lease_state_check check (
        (status = 'running' and lease_token is not null and leased_until is not null)
        or (status <> 'running' and lease_token is null and leased_until is null)
    ),
    constraint account_auth_deletion_outbox_completion_state_check check (
        (status = 'completed' and completed_at is not null)
        or (status <> 'completed' and completed_at is null)
    )
);

create index if not exists account_auth_deletion_outbox_ready_idx
    on public.account_auth_deletion_outbox (available_at, created_at, outbox_id)
    where status in ('queued', 'running');

alter table public.account_deletion_tombstones enable row level security;
alter table public.account_auth_deletion_outbox enable row level security;
revoke all on public.account_deletion_tombstones from public, anon, authenticated;
revoke all on public.account_auth_deletion_outbox from public, anon, authenticated;

create or replace function public.prevent_account_deletion_tombstone_rewrite()
returns trigger
language plpgsql
set search_path = public, pg_temp
as $$
begin
    raise exception 'account_deletion_tombstone_immutable'
        using errcode = 'check_violation';
end;
$$;

revoke all on function public.prevent_account_deletion_tombstone_rewrite() from public;
drop trigger if exists account_deletion_tombstones_immutable
    on public.account_deletion_tombstones;
create trigger account_deletion_tombstones_immutable
    before update or delete on public.account_deletion_tombstones
    for each row execute function public.prevent_account_deletion_tombstone_rewrite();

create or replace function public.enqueue_account_auth_deletion_on_completion()
returns trigger
language plpgsql
security definer
set search_path = public, pg_temp
as $$
begin
    if new.status = 'completed'
       and old.status is distinct from new.status then
        insert into public.account_deletion_tombstones (account_id, deleted_at)
        values (new.account_id, coalesce(new.completed_at, now()))
        on conflict (account_id) do nothing;

        insert into public.account_auth_deletion_outbox (account_id)
        values (new.account_id)
        on conflict (account_id) do nothing;
    end if;
    return new;
end;
$$;

revoke all on function public.enqueue_account_auth_deletion_on_completion() from public;
drop trigger if exists account_deletion_enqueue_auth_provider_delete
    on public.account_deletion_requests;
create trigger account_deletion_enqueue_auth_provider_delete
    after update of status on public.account_deletion_requests
    for each row execute function public.enqueue_account_auth_deletion_on_completion();

-- Preserve and finish any platform deletions that completed before this
-- provider outbox existed. Rows already removed by an older Auth cascade are
-- intentionally unknowable here and are not recreated.
insert into public.account_deletion_tombstones (account_id, deleted_at)
select account_id, coalesce(completed_at, now())
  from public.account_deletion_requests
 where status = 'completed'
on conflict (account_id) do nothing;

insert into public.account_auth_deletion_outbox (account_id)
select account_id
  from public.account_deletion_requests
 where status = 'completed'
on conflict (account_id) do nothing;
