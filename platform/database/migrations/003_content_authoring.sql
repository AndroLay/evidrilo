-- Evidrilo P4, immutable case metadata and review decisions.

alter table public.case_versions add column if not exists case_id text;
alter table public.case_versions add column if not exists title text;
alter table public.case_versions add column if not exists evaluator_version text;
alter table public.case_versions add column if not exists skill_tags text[];
alter table public.case_versions add column if not exists author_id uuid references auth.users(id);
alter table public.case_versions add column if not exists reviewer_id uuid references auth.users(id);

alter table public.case_versions drop constraint if exists case_versions_status_check;
alter table public.case_versions add constraint case_versions_status_check
    check (status in ('draft', 'review', 'approved', 'published', 'retired'));

create table if not exists public.case_review_decisions (
    decision_id uuid primary key default gen_random_uuid(),
    case_version_id text not null references public.case_versions(case_version_id),
    reviewer_id uuid not null references auth.users(id),
    decision text not null check (decision in ('approved', 'rejected')),
    reason text not null check (char_length(reason) between 1 and 2000),
    created_at timestamptz not null default now()
);

create index if not exists case_review_decisions_version_idx
    on public.case_review_decisions (case_version_id, created_at desc);

alter table public.case_review_decisions enable row level security;

-- Reviewer/maintainer role checks are added by the organization membership
-- migration. Until then, no public client insert policy is granted.
create policy published_case_review_select on public.case_review_decisions
    for select using (
        exists (
            select 1 from public.case_versions version
            where version.case_version_id = case_review_decisions.case_version_id
              and version.status = 'published'
        )
    );
