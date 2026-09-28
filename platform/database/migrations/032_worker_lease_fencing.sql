-- Evidrilo: explicit worker lease fencing for concurrent/restarted workers.
-- The attempt counter remains useful for retry accounting; the lease token is
-- the per-claim ownership credential and must be presented for every mutation.

alter table public.worker_jobs
    add column if not exists lease_token uuid;

update public.worker_jobs
set lease_token = gen_random_uuid()
where status = 'running'
  and lease_token is null;

create index if not exists worker_jobs_lease_idx
    on public.worker_jobs (job_id, lease_token)
    where status = 'running';
