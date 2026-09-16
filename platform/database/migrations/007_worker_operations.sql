-- Evidrilo P8, bounded worker job lease and audit primitives.

create table if not exists public.worker_jobs (
    job_id uuid primary key default gen_random_uuid(),
    job_type text not null check (job_type in ('analytics_projection', 'sync_reconciliation', 'notification')),
    idempotency_key text not null,
    status text not null check (status in ('queued', 'running', 'succeeded', 'failed')),
    attempts integer not null default 0 check (attempts between 0 and 10),
    available_at timestamptz not null default now(),
    leased_until timestamptz null,
    last_error_code text null,
    created_at timestamptz not null default now(),
    unique (job_type, idempotency_key)
);

create index if not exists worker_jobs_available_idx
    on public.worker_jobs (status, available_at);

alter table public.worker_jobs enable row level security;

-- Worker jobs are not readable or writable by client roles. The worker uses a
-- narrowly scoped server role outside the mobile API boundary.
