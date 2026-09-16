-- Evidrilo P7, organization/cohort authorization primitives.

create table if not exists public.organizations (
    organization_id uuid primary key default gen_random_uuid(),
    name text not null check (char_length(name) between 1 and 200),
    created_at timestamptz not null default now()
);

create table if not exists public.organization_memberships (
    organization_id uuid not null references public.organizations(organization_id) on delete cascade,
    account_id uuid not null references auth.users(id) on delete cascade,
    role text not null check (role in ('learner', 'teacher', 'reviewer', 'maintainer', 'owner')),
    active boolean not null default true,
    created_at timestamptz not null default now(),
    primary key (organization_id, account_id)
);

create table if not exists public.cohorts (
    cohort_id uuid primary key default gen_random_uuid(),
    organization_id uuid not null references public.organizations(organization_id) on delete cascade,
    name text not null check (char_length(name) between 1 and 200),
    created_at timestamptz not null default now()
);

create table if not exists public.cohort_enrollments (
    cohort_id uuid not null references public.cohorts(cohort_id) on delete cascade,
    account_id uuid not null references auth.users(id) on delete cascade,
    active boolean not null default true,
    created_at timestamptz not null default now(),
    primary key (cohort_id, account_id)
);

alter table public.organizations enable row level security;
alter table public.organization_memberships enable row level security;
alter table public.cohorts enable row level security;
alter table public.cohort_enrollments enable row level security;

create policy membership_select_own on public.organization_memberships
    for select using (account_id = auth.uid());

create policy cohort_enrollment_select_own on public.cohort_enrollments
    for select using (account_id = auth.uid());

-- Aggregate dashboard policies are added only with server-side membership
-- functions and suppression queries; no raw cohort table is public by default.
