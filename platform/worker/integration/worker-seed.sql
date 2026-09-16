\set ON_ERROR_STOP on

begin;
insert into auth.users (id)
values ('99999999-9999-9999-9999-999999999999')
on conflict (id) do nothing;

insert into public.analytics_events (
    account_id, client_event_id, event_name, event_version, occurred_at,
    source, consent_version, properties
) values
    (
        '99999999-9999-9999-9999-999999999999',
        'aaaaaaaa-0000-0000-0000-000000000001',
        'attempt_completed', 1, '2026-01-01 12:00:00+00',
        'mobile', 'analytics.v1',
        '{"attemptId": "99999999-0000-0000-0000-000000000001", "caseVersionId": "worker-case", "outcome": "PASS"}'::jsonb
    ),
    (
        '99999999-9999-9999-9999-999999999999',
        'aaaaaaaa-0000-0000-0000-000000000002',
        'attempt_completed', 1, '2026-01-01 13:00:00+00',
        'mobile', 'analytics.v1',
        '{"attemptId": "99999999-0000-0000-0000-000000000002", "caseVersionId": "worker-case", "outcome": "ACTION_REQUIRED"}'::jsonb
    ),
    (
        '99999999-9999-9999-9999-999999999999',
        'aaaaaaaa-0000-0000-0000-000000000003',
        'attempt_completed', 1, '2026-01-02 12:00:00+00',
        'mobile', 'analytics.v1',
        '{"attemptId": "99999999-0000-0000-0000-000000000003", "caseVersionId": "worker-case", "outcome": "CANNOT_ASSESS"}'::jsonb
    ),
    (
        '99999999-9999-9999-9999-999999999999',
        'aaaaaaaa-0000-0000-0000-000000000004',
        'revision_recorded', 1, '2026-01-02 13:00:00+00',
        'mobile', 'analytics.v1',
        '{"attemptId": "99999999-0000-0000-0000-000000000003", "caseVersionId": "worker-case", "revisionChanged": true}'::jsonb
    );

update public.worker_jobs
set status = 'running',
    attempts = 1,
    leased_until = now() - interval '1 second',
    last_error_code = null
where job_type = 'analytics_projection'
  and account_id = '99999999-9999-9999-9999-999999999999';

commit;

do $$
declare
    n integer;
begin
    select count(*) into n
      from public.worker_jobs
     where job_type = 'analytics_projection'
       and account_id = '99999999-9999-9999-9999-999999999999'
       and status = 'running'
       and attempts = 1
       and leased_until < now();
    if n <> 1 then
        raise exception 'worker seed lease assertion failed: %', n;
    end if;
    raise notice 'WORKER_SEED_ASSERTION_PASS';
end;
$$;
