-- Evidrilo P3/P8, account-scoped projection jobs and enqueue trigger.

alter table public.worker_jobs add column if not exists account_id uuid
    references auth.users(id) on delete cascade;

create index if not exists worker_jobs_projection_account_idx
    on public.worker_jobs (job_type, account_id, status, available_at);

create or replace function public.enqueue_analytics_projection()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
    insert into public.worker_jobs as jobs (
        job_type, account_id, idempotency_key, status, available_at
    ) values (
        'analytics_projection',
        new.account_id,
        new.account_id::text || ':' || to_char(now() at time zone 'UTC', 'YYYY-MM-DD'),
        'queued',
        now()
    ) on conflict (job_type, idempotency_key) do update
        set status = case
                when jobs.status in ('succeeded', 'failed') then 'queued'
                else jobs.status
            end,
            attempts = case
                when jobs.status in ('succeeded', 'failed') then 0
                else jobs.attempts
            end,
            available_at = case
                when jobs.status in ('queued', 'succeeded', 'failed') then now()
                else jobs.available_at
            end;
    return new;
end;
$$;

drop trigger if exists analytics_events_enqueue_projection on public.analytics_events;
create trigger analytics_events_enqueue_projection
    after insert on public.analytics_events
    for each row execute function public.enqueue_analytics_projection();

-- The worker uses a narrowly scoped server connection. Client roles still have
-- no worker_jobs write/read policy.
