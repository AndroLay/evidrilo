-- Evidrilo P3, worker-owned daily projection rows.
-- Raw analytics remain the immutable source; these rows are disposable and
-- rebuilt atomically with the account-level projection.

create table if not exists public.progress_daily_projections (
    account_id uuid not null references auth.users(id) on delete cascade,
    projection_date date not null,
    calculation_version text not null,
    attempts_observed integer not null check (attempts_observed >= 0),
    completed_attempts integer not null check (completed_attempts >= 0),
    revisions_observed integer not null check (revisions_observed >= 0),
    pass_count integer not null check (pass_count >= 0),
    action_required_count integer not null check (action_required_count >= 0),
    abstention_count integer not null check (abstention_count >= 0),
    coverage double precision not null check (coverage between 0 and 1),
    rebuilt_at timestamptz not null default now(),
    primary key (account_id, projection_date)
);

create index if not exists progress_daily_projections_account_date_idx
    on public.progress_daily_projections (account_id, projection_date);

alter table public.progress_daily_projections enable row level security;

create policy progress_daily_projections_select_own on public.progress_daily_projections
    for select using (account_id = auth.uid());

-- No client write policy is granted. The projection worker owns rebuilds.

