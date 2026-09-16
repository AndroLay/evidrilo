-- Evidrilo P7, count only learner memberships in cohort aggregates.
-- A non-learner enrollment must not inflate learner counts or bypass
-- aggregate suppression.

create or replace function public.read_cohort_aggregate(requested_cohort_id uuid)
returns table (
    organization_id uuid,
    cohort_id uuid,
    role text,
    learner_count integer,
    completion_rate double precision,
    pass_rate double precision
)
language sql
stable
security definer
set search_path = public, auth, pg_temp
as $$
    with scoped as (
        select c.organization_id, c.cohort_id, m.role
        from public.cohorts c
        join public.organization_memberships m
          on m.organization_id = c.organization_id
         and m.account_id = auth.uid()
         and m.active = true
         and m.role in ('teacher', 'maintainer', 'owner')
        where c.cohort_id = requested_cohort_id
    ), aggregate as (
        select
            e.cohort_id,
            count(*)::integer as learner_count,
            avg(case when coalesce(p.completed_attempts, 0) > 0 then 1.0 else 0.0 end)::double precision as completion_rate,
            case when coalesce(sum(p.completed_attempts), 0) = 0 then null
                 else sum(p.pass_count)::double precision / sum(p.completed_attempts)
            end as pass_rate
        from public.cohort_enrollments e
        join public.cohorts c
          on c.cohort_id = e.cohort_id
        join public.organization_memberships learner_membership
          on learner_membership.organization_id = c.organization_id
         and learner_membership.account_id = e.account_id
         and learner_membership.active = true
         and learner_membership.role = 'learner'
        left join public.progress_projections p on p.account_id = e.account_id
        where e.cohort_id = requested_cohort_id
          and e.active = true
        group by e.cohort_id
    )
    select
        scoped.organization_id,
        scoped.cohort_id,
        scoped.role,
        coalesce(aggregate.learner_count, 0),
        aggregate.completion_rate,
        aggregate.pass_rate
    from scoped
    left join aggregate on aggregate.cohort_id = scoped.cohort_id;
$$;

revoke all on function public.read_cohort_aggregate(uuid) from public;
grant execute on function public.read_cohort_aggregate(uuid) to authenticated;
