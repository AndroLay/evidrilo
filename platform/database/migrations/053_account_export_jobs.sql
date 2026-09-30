-- Temporary, private account-export artifacts. Only the API and worker
-- connection use this table; client roles have no direct table access.

create table if not exists public.account_export_jobs (
    export_id uuid primary key default gen_random_uuid(),
    account_id uuid not null references auth.users(id) on delete cascade,
    idempotency_key_hash text not null check (idempotency_key_hash ~ '^[a-f0-9]{64}$'),
    request_id text not null check (char_length(request_id) between 8 and 128),
    status text not null default 'queued'
        check (status in ('queued', 'running', 'ready', 'failed', 'cancelled')),
    attempts smallint not null default 0 check (attempts between 0 and 3),
    lease_token uuid null,
    lease_expires_at timestamptz null,
    payload bytea null,
    artifact_bytes bigint null,
    last_error_code text null check (
        last_error_code is null or last_error_code ~ '^[A-Z0-9_]{1,64}$'
    ),
    created_at timestamptz not null default now(),
    started_at timestamptz null,
    completed_at timestamptz null,
    expires_at timestamptz not null default now() + interval '24 hours',
    constraint account_export_job_lease_state_check check (
        (status = 'running' and lease_token is not null and lease_expires_at is not null)
        or (status <> 'running' and lease_token is null and lease_expires_at is null)
    ),
    constraint account_export_job_payload_state_check check (
        (status = 'ready'
            and payload is not null
            and artifact_bytes = octet_length(payload)
            and artifact_bytes between 1 and 67108864
            and completed_at is not null
            and expires_at > completed_at)
        or (status <> 'ready' and payload is null and artifact_bytes is null)
    ),
    constraint account_export_job_expiry_check check (expires_at > created_at),
    unique (account_id, idempotency_key_hash)
);

create unique index if not exists account_export_jobs_one_active_per_account_idx
    on public.account_export_jobs (account_id)
    where status in ('queued', 'running', 'ready');
create index if not exists account_export_jobs_queue_idx
    on public.account_export_jobs (created_at, export_id)
    where status = 'queued';
create index if not exists account_export_jobs_expiry_idx
    on public.account_export_jobs (expires_at, export_id);

alter table public.account_export_jobs enable row level security;
revoke all on public.account_export_jobs from public, anon, authenticated;

create or replace function public.purge_account_exports_on_account_deletion()
returns trigger
language plpgsql
security definer
set search_path = public, pg_temp
as $$
begin
    if new.status = 'completed'
       and old.status is distinct from new.status then
        delete from public.account_export_jobs where account_id = new.account_id;
    end if;
    return new;
end;
$$;

revoke all on function public.purge_account_exports_on_account_deletion() from public;
drop trigger if exists account_deletion_purge_account_exports
    on public.account_deletion_requests;
create trigger account_deletion_purge_account_exports
    after update of status on public.account_deletion_requests
    for each row execute function public.purge_account_exports_on_account_deletion();
