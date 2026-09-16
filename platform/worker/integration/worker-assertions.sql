\set ON_ERROR_STOP on

do $$
declare
    job_status text;
    job_attempts integer;
    job_lease timestamptz;
    attempts_observed integer;
    completed_attempts integer;
    revisions_observed integer;
    pass_count integer;
    action_required_count integer;
    abstention_count integer;
    coverage double precision;
    daily_rows integer;
begin
    select status, attempts, leased_until
      into job_status, job_attempts, job_lease
      from public.worker_jobs
     where job_type = 'analytics_projection'
       and account_id = '99999999-9999-9999-9999-999999999999';

    if job_status <> 'succeeded' or job_attempts <> 2 or job_lease is not null then
        raise exception 'worker completion assertion failed: status %, attempts %, lease %',
            job_status, job_attempts, job_lease;
    end if;

    select p.attempts_observed, p.completed_attempts, p.revisions_observed,
           p.pass_count, p.action_required_count, p.abstention_count, p.coverage
      into attempts_observed, completed_attempts, revisions_observed,
           pass_count, action_required_count, abstention_count, coverage
      from public.progress_projections p
     where p.account_id = '99999999-9999-9999-9999-999999999999';

    if attempts_observed <> 3
       or completed_attempts <> 3
       or revisions_observed <> 1
       or pass_count <> 1
       or action_required_count <> 1
       or abstention_count <> 1
       or coverage <> 1 then
        raise exception 'worker aggregate assertion failed: %, %, %, %, %, %, %',
            attempts_observed, completed_attempts, revisions_observed,
            pass_count, action_required_count, abstention_count, coverage;
    end if;

    select count(*) into daily_rows
      from public.progress_daily_projections
     where account_id = '99999999-9999-9999-9999-999999999999';

    if daily_rows <> 2 then
        raise exception 'worker daily projection assertion failed: %', daily_rows;
    end if;

    raise notice 'WORKER_LIVE_ASSERTIONS_PASS';
end;
$$;
