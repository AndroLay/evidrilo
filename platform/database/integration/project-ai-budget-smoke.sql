-- Runtime assertions for migrations 045-049. This file runs only against the
-- disposable PostgreSQL database created by run-local-postgres-smoke.sh.

insert into auth.users (id) values
    ('d1450000-0000-4000-8000-000000000001'),
    ('d1450000-0000-4000-8000-000000000002'),
    ('d1450000-0000-4000-8000-000000000003'),
    ('d1450000-0000-4000-8000-000000000004'),
    ('d1470000-0000-4000-8000-000000000001')
on conflict (id) do nothing;

insert into public.project_ai_activity (
    activity_id, account_id, installation_id, request_id, mode, outcome
) values
    ('d1490000-0000-4000-8000-000000000001', 'd1450000-0000-4000-8000-000000000001', 'a1490000-0000-4000-8000-000000000001', 'activity-owner-row-01', 'GENERAL', 'PENDING'),
    ('d1490000-0000-4000-8000-000000000002', 'd1450000-0000-4000-8000-000000000002', 'a1490000-0000-4000-8000-000000000002', 'activity-foreign-row-01', 'GENERAL', 'PENDING');

insert into public.project_ai_consents (
    account_id, policy_version, granted, granted_at, revoked_at, consent_generation
) values
    ('d1450000-0000-4000-8000-000000000001', 'project-ai-data.v1', true, now(), null, 1),
    ('d1450000-0000-4000-8000-000000000002', 'project-ai-data.v1', true, now(), null, 1),
    ('d1450000-0000-4000-8000-000000000003', 'project-ai-data.v1', true, now(), null, 1);

insert into public.project_ai_consent_events (
    account_id, policy_version, decision, consent_generation
) values
    ('d1450000-0000-4000-8000-000000000001', 'project-ai-data.v1', 'grant', 1),
    ('d1450000-0000-4000-8000-000000000002', 'project-ai-data.v1', 'grant', 1),
    ('d1450000-0000-4000-8000-000000000003', 'project-ai-data.v1', 'grant', 1);

begin;
set local role authenticated;
select set_config('request.jwt.claim.sub', 'd1450000-0000-4000-8000-000000000001', true);
do $$
declare
    own_consent_count integer;
    foreign_consent_count integer;
    own_event_count integer;
    foreign_event_count integer;
    own_activity_count integer;
    foreign_activity_count integer;
begin
    select count(*) into own_consent_count from public.project_ai_consents;
    select count(*) into foreign_consent_count
      from public.project_ai_consents
     where account_id = 'd1450000-0000-4000-8000-000000000002';
    select count(*) into own_event_count from public.project_ai_consent_events;
    select count(*) into foreign_event_count
      from public.project_ai_consent_events
     where account_id = 'd1450000-0000-4000-8000-000000000002';
    select count(*) into own_activity_count from public.project_ai_activity;
    select count(*) into foreign_activity_count
      from public.project_ai_activity
     where account_id = 'd1450000-0000-4000-8000-000000000002';
    if own_consent_count <> 1 or foreign_consent_count <> 0
       or own_event_count <> 1 or foreign_event_count <> 0
       or own_activity_count <> 1 or foreign_activity_count <> 0 then
        raise exception 'project_ai_consent_rls_scope_failed';
    end if;
end;
$$;
rollback;
do $$ begin raise notice 'PROJECT_AI_CONSENT_RLS_PASS'; end $$;
do $$ begin raise notice 'PROJECT_AI_ACTIVITY_RLS_PASS'; end $$;

do $$
begin
    begin
        update public.project_ai_consent_events
           set policy_version = 'project-ai-data.v2'
         where account_id = 'd1450000-0000-4000-8000-000000000001'
           and consent_generation = 1;
        raise exception 'project AI consent event update was allowed';
    exception when check_violation then
        if position('project_ai_consent_event_immutable' in sqlerrm) = 0 then raise; end if;
    end;
    begin
        delete from public.project_ai_consent_events
         where account_id = 'd1450000-0000-4000-8000-000000000001'
           and consent_generation = 1;
        raise exception 'project AI consent event delete was allowed';
    exception when check_violation then
        if position('project_ai_consent_event_immutable' in sqlerrm) = 0 then raise; end if;
    end;
end;
$$;
do $$ begin raise notice 'PROJECT_AI_CONSENT_APPEND_ONLY_PASS'; end $$;

do $$
declare
    trigger_enabled boolean;
    function_definition text;
begin
    select tgenabled in ('O', 'A')
      into trigger_enabled
      from pg_trigger
     where tgrelid = 'public.account_deletion_requests'::regclass
       and tgname = 'account_deletion_lock_project_ai_consent'
       and not tgisinternal;
    select pg_get_functiondef('public.lock_project_ai_consent_on_account_deletion()'::regprocedure)
      into function_definition;
    if trigger_enabled is not true
       or position('pg_advisory_xact_lock' in function_definition) = 0
       or position('hashtextextended(new.account_id::text, 0)' in function_definition) = 0 then
        raise exception 'project_ai_consent_deletion_fence_not_installed';
    end if;
end;
$$;
do $$ begin raise notice 'PROJECT_AI_CONSENT_DELETION_FENCE_PASS'; end $$;

insert into public.account_deletion_requests (account_id, status)
values ('d1450000-0000-4000-8000-000000000003', 'in_progress');
insert into public.student_projects (project_id, account_id, version, document)
values ('d1490000-0000-4000-8000-000000000003', 'd1450000-0000-4000-8000-000000000003', 1, '{}'::jsonb);
insert into public.project_ai_activity (
    activity_id, account_id, installation_id, request_id, mode, project_id,
    stage_id, operation_id, base_project_revision, consent_generation, outcome
) values
    ('d1490000-0000-4000-8000-000000000003', 'd1450000-0000-4000-8000-000000000003', 'a1490000-0000-4000-8000-000000000003', 'activity-account-delete-01', 'PROJECT', 'd1490000-0000-4000-8000-000000000003', 'frame', 'explain_template_step', 1, 1, 'PENDING'),
    ('d1490000-0000-4000-8000-000000000004', 'd1450000-0000-4000-8000-000000000003', 'a1490000-0000-4000-8000-000000000003', 'activity-account-delete-02', 'GENERAL', 'PENDING');
update public.account_deletion_requests
   set status = 'completed', completed_at = now()
 where account_id = 'd1450000-0000-4000-8000-000000000003';
do $$
begin
    if exists (select 1 from public.project_ai_consents where account_id = 'd1450000-0000-4000-8000-000000000003')
       or exists (select 1 from public.project_ai_consent_events where account_id = 'd1450000-0000-4000-8000-000000000003')
       or exists (select 1 from public.project_ai_activity where account_id = 'd1450000-0000-4000-8000-000000000003')
       or not exists (select 1 from public.account_deletion_tombstones where account_id = 'd1450000-0000-4000-8000-000000000003') then
        raise exception 'project_ai_consent_account_deletion_purge_failed';
    end if;
end;
$$;
do $$ begin raise notice 'PROJECT_AI_CONSENT_DELETION_PURGE_PASS'; end $$;
do $$ begin raise notice 'PROJECT_AI_ACTIVITY_ACCOUNT_DELETION_PURGE_PASS'; end $$;

do $$
declare
    grant_id uuid;
    default_cost integer;
begin
    insert into public.ai_credit_grants (
        account_id, grant_kind, grant_key, credits, starts_at
    ) values (
        'd1470000-0000-4000-8000-000000000001', 'free_once', 'project-cost-smoke', 100, now()
    ) returning ai_credit_grants.grant_id into grant_id;

    insert into public.ai_credit_reservations (
        account_id, request_id, grant_id, status, request_hash
    ) values (
        'd1470000-0000-4000-8000-000000000001',
        'credit-cost-default-001', grant_id, 'reserved', repeat('a', 64)
    );
    select credit_cost into default_cost
      from public.ai_credit_reservations
     where account_id = 'd1470000-0000-4000-8000-000000000001'
       and request_id = 'credit-cost-default-001';
    if default_cost <> 1 then raise exception 'ai_credit_reservation_default_cost_failed'; end if;

    insert into public.ai_credit_reservations (
        account_id, request_id, grant_id, status, request_hash, credit_cost
    ) values (
        'd1470000-0000-4000-8000-000000000001',
        'credit-cost-maximum-001', grant_id, 'reserved', repeat('b', 64), 100
    );

    begin
        insert into public.ai_credit_reservations (
            account_id, request_id, grant_id, status, request_hash, credit_cost
        ) values (
            'd1470000-0000-4000-8000-000000000001',
            'credit-cost-zero-0001', grant_id, 'reserved', repeat('c', 64), 0
        );
        raise exception 'zero AI reservation cost was allowed';
    exception when check_violation then
        if position('ai_credit_reservations_credit_cost_check' in sqlerrm) = 0 then raise; end if;
    end;
    begin
        insert into public.ai_credit_reservations (
            account_id, request_id, grant_id, status, request_hash, credit_cost
        ) values (
            'd1470000-0000-4000-8000-000000000001',
            'credit-cost-overmax-001', grant_id, 'reserved', repeat('d', 64), 101
        );
        raise exception 'over-limit AI reservation cost was allowed';
    exception when check_violation then
        if position('ai_credit_reservations_credit_cost_check' in sqlerrm) = 0 then raise; end if;
    end;
end;
$$;
do $$ begin raise notice 'AI_CREDIT_RESERVATION_COST_PASS'; end $$;

do $$
declare
    project_id uuid := 'd1480000-0000-4000-8000-000000000001';
    valid_document jsonb := jsonb_build_object('payload', repeat('x', 3100000));
    oversized_document jsonb := jsonb_build_object('payload', repeat('x', 3145800));
begin
    insert into public.student_projects (project_id, account_id, version, document)
    values (project_id, 'd1450000-0000-4000-8000-000000000001', 1, valid_document);
    insert into public.student_project_revisions (revision_id, project_id, account_id, version, document)
    values (
        'd1480000-0000-4000-8000-000000000002',
        project_id,
        'd1450000-0000-4000-8000-000000000001',
        1,
        valid_document
    );
    insert into public.project_ai_activity (
        activity_id, account_id, installation_id, request_id, mode, project_id,
        stage_id, operation_id, base_project_revision, consent_generation, outcome
    ) values (
        'd1490000-0000-4000-8000-000000000005',
        'd1450000-0000-4000-8000-000000000001',
        'a1490000-0000-4000-8000-000000000001',
        'activity-project-delete-01',
        'PROJECT',
        project_id,
        'frame',
        'explain_template_step',
        1,
        1,
        'PENDING'
    );

    begin
        insert into public.student_projects (project_id, account_id, version, document)
        values (
            'd1480000-0000-4000-8000-000000000003',
            'd1450000-0000-4000-8000-000000000001',
            1,
            oversized_document
        );
        raise exception 'oversized student project document was allowed';
    exception when check_violation then
        if position('student_projects_document_check' in sqlerrm) = 0 then raise; end if;
    end;
    begin
        insert into public.student_project_revisions (revision_id, project_id, account_id, version, document)
        values (
            'd1480000-0000-4000-8000-000000000004',
            project_id,
            'd1450000-0000-4000-8000-000000000001',
            2,
            oversized_document
        );
        raise exception 'oversized student project revision was allowed';
    exception when check_violation then
        if position('student_project_revisions_document_check' in sqlerrm) = 0 then raise; end if;
    end;
end;
$$;
do $$ begin raise notice 'STUDENT_PROJECT_DOCUMENT_BUDGET_PASS'; end $$;

delete from public.student_projects
 where account_id = 'd1450000-0000-4000-8000-000000000001'
   and project_id = 'd1480000-0000-4000-8000-000000000001';
do $$
begin
    if exists (
        select 1 from public.project_ai_activity
        where account_id = 'd1450000-0000-4000-8000-000000000001'
          and project_id = 'd1480000-0000-4000-8000-000000000001'
    ) then
        raise exception 'project_ai_activity_project_delete_cascade_failed';
    end if;
end;
$$;
do $$ begin raise notice 'PROJECT_AI_ACTIVITY_PROJECT_DELETION_CASCADE_PASS'; end $$;
