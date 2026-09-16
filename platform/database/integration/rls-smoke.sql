-- Local-only PostgreSQL integration assertions.
-- The test data is synthetic and must never be used as production fixtures.

\set ON_ERROR_STOP on

grant usage on schema public to authenticated;
grant select, update, delete on all tables in schema public to authenticated;
grant usage, select on all sequences in schema public to authenticated;

begin;
insert into auth.users (id) values
    ('11111111-1111-1111-1111-111111111111'),
    ('22222222-2222-2222-2222-222222222222'),
    ('33333333-3333-3333-3333-333333333333'),
    ('44444444-4444-4444-4444-444444444444'),
    ('55555555-5555-5555-5555-555555555555'),
    ('66666666-6666-6666-6666-666666666666'),
    ('77777777-7777-7777-7777-777777777777'),
    ('88888888-8888-8888-8888-888888888888'),
    ('99999999-9999-4999-8999-999999999999')
on conflict (id) do nothing;

insert into public.account_profiles (account_id) values
    ('11111111-1111-1111-1111-111111111111'),
    ('22222222-2222-2222-2222-222222222222')
on conflict (account_id) do nothing;

do $$
declare
    profile_count integer;
begin
    select count(*) into profile_count
      from public.account_profiles
     where account_id in (
         '11111111-1111-1111-1111-111111111111'::uuid,
         '22222222-2222-2222-2222-222222222222'::uuid,
         '33333333-3333-3333-3333-333333333333'::uuid
     );
    if profile_count <> 3 then
        raise exception 'new Auth accounts were not provisioned: %', profile_count;
    end if;
    raise notice 'ACCOUNT_PROFILE_PROVISIONING_PASS';
end;
$$;

do $$
begin
    if has_function_privilege('anon', 'public.append_sync_change()', 'execute')
       or has_function_privilege('authenticated', 'public.append_sync_change()', 'execute')
       or has_function_privilege('anon', 'public.enqueue_analytics_projection()', 'execute')
       or has_function_privilege('authenticated', 'public.enqueue_analytics_projection()', 'execute') then
        raise exception 'trigger SECURITY DEFINER functions remain executable by client roles';
    end if;
    raise notice 'TRIGGER_FUNCTION_PRIVILEGE_ASSERTION_PASS';
end;
$$;

-- Remove one generated row to retain a deterministic legacy-account fixture
-- for the deletion-ledger fallback assertion at the end of this script.
delete from public.account_profiles
 where account_id = '33333333-3333-3333-3333-333333333333';

insert into public.organizations (organization_id, name)
values ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'Evidrilo Integration Organization')
on conflict (organization_id) do nothing;

insert into public.organization_memberships (organization_id, account_id, role)
values
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', '11111111-1111-1111-1111-111111111111', 'teacher'),
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', '22222222-2222-2222-2222-222222222222', 'learner')
on conflict (organization_id, account_id) do nothing;

insert into public.organization_memberships (organization_id, account_id, role, active)
values
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', '44444444-4444-4444-4444-444444444444', 'learner', true),
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', '55555555-5555-5555-5555-555555555555', 'learner', true),
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', '66666666-6666-6666-6666-666666666666', 'learner', true),
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', '77777777-7777-7777-7777-777777777777', 'learner', true),
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', '88888888-8888-8888-8888-888888888888', 'learner', true),
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', '99999999-9999-4999-8999-999999999999', 'learner', false)
on conflict (organization_id, account_id) do update
    set role = excluded.role, active = excluded.active;

insert into public.cohorts (cohort_id, organization_id, name)
values ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'Integration cohort')
on conflict (cohort_id) do nothing;

insert into public.cohort_enrollments (cohort_id, account_id)
values ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', '22222222-2222-2222-2222-222222222222')
on conflict (cohort_id, account_id) do nothing;

insert into public.cohorts (cohort_id, organization_id, name)
values ('cccccccc-cccc-4ccc-8ccc-cccccccccccc', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'Membership boundary cohort')
on conflict (cohort_id) do nothing;

insert into public.cohort_enrollments (cohort_id, account_id)
values
    ('cccccccc-cccc-4ccc-8ccc-cccccccccccc', '22222222-2222-2222-2222-222222222222'),
    ('cccccccc-cccc-4ccc-8ccc-cccccccccccc', '11111111-1111-1111-1111-111111111111'),
    ('cccccccc-cccc-4ccc-8ccc-cccccccccccc', '44444444-4444-4444-4444-444444444444'),
    ('cccccccc-cccc-4ccc-8ccc-cccccccccccc', '55555555-5555-5555-5555-555555555555'),
    ('cccccccc-cccc-4ccc-8ccc-cccccccccccc', '66666666-6666-6666-6666-666666666666'),
    ('cccccccc-cccc-4ccc-8ccc-cccccccccccc', '77777777-7777-7777-7777-777777777777'),
    ('cccccccc-cccc-4ccc-8ccc-cccccccccccc', '99999999-9999-4999-8999-999999999999')
on conflict (cohort_id, account_id) do nothing;

select set_config('request.jwt.claim.sub', '11111111-1111-1111-1111-111111111111', true);

insert into public.case_versions (
    case_version_id, content_hash, status, published_at, case_id, title,
    evaluator_version, skill_tags, author_id, reviewer_id, organization_id,
    objective, difficulty, evidence_references, content
) values
    (
        'case-published-it', repeat('a', 64), 'published', now(), 'case-it',
        'Published integration case', 'evidrilo.v1', array['evidence'],
        '11111111-1111-1111-1111-111111111111',
        '33333333-3333-3333-3333-333333333333',
        'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'Compare supplied observations',
        2, array['OBS-WARM-01', 'OBS-ROOM-01'],
        $$
        {
          "content": {
            "caseId": "case-it",
            "caseVersionId": "case-published-it",
            "title": "Published integration case",
            "contentHash": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            "evaluatorVersion": "evidrilo.v1",
            "factAnchors": ["OBS-WARM-01", "OBS-ROOM-01"],
            "skillTags": ["evidence"]
          },
          "objective": "Compare supplied observations",
          "difficulty": 2,
          "evidenceReferences": ["OBS-WARM-01", "OBS-ROOM-01"],
          "facts": [
            { "id": "OBS-WARM-01", "type": "observation", "text": "Warm water: 32 seconds." },
            { "id": "OBS-ROOM-01", "type": "observation", "text": "Room temperature: 58 seconds." },
            { "id": "LIMIT-TRIAL-01", "type": "limitation", "text": "Each condition was measured once." }
          ],
          "rules": [
            { "id": "RULE-1", "outcome": "PASS", "anchorIds": ["OBS-WARM-01", "OBS-ROOM-01"] }
          ],
          "variants": [
            { "id": "CHALLENGE-1", "removedFactIds": ["OBS-ROOM-01"] }
          ]
        }
        $$::jsonb
    ),
    (
        'case-draft-it', repeat('b', 64), 'draft', null, 'case-it-draft',
        'Draft integration case', 'evidrilo.v1', array['evidence'], null, null,
        'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'Draft objective', 1,
        array['OBS-DRAFT-01'],
        $$
        {
          "content": {
            "caseId": "case-it-draft",
            "caseVersionId": "case-draft-it",
            "title": "Draft integration case",
            "contentHash": "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
            "evaluatorVersion": "evidrilo.v1",
            "factAnchors": ["OBS-DRAFT-01"],
            "skillTags": ["evidence"]
          },
          "objective": "Draft objective",
          "difficulty": 1,
          "evidenceReferences": ["OBS-DRAFT-01"],
          "facts": [
            { "id": "OBS-DRAFT-01", "type": "observation", "text": "Draft observation." }
          ],
          "rules": [
            { "id": "DRAFT-RULE-1", "outcome": "INCOMPLETE", "anchorIds": ["OBS-DRAFT-01"] }
          ],
          "variants": []
        }
        $$::jsonb
    )
on conflict (case_version_id) do nothing;

insert into public.case_review_decisions (
    decision_id, case_version_id, reviewer_id, decision, reason
) values (
    '99999999-9999-4999-8999-999999999999', 'case-published-it',
    '33333333-3333-3333-3333-333333333333', 'approved', 'Synthetic review reason.'
)
on conflict (decision_id) do nothing;

insert into public.progress_projections (
    account_id, calculation_version, attempts_observed, completed_attempts,
    revisions_observed, pass_count, action_required_count, abstention_count,
    coverage
) values (
    '22222222-2222-2222-2222-222222222222', 'progress.v1', 4, 4, 1, 3, 1, 0, 1.0
)
on conflict (account_id) do nothing;

insert into public.progress_daily_projections (
    account_id, projection_date, calculation_version, attempts_observed,
    completed_attempts, revisions_observed, pass_count, action_required_count,
    abstention_count, coverage
) values (
    '22222222-2222-2222-2222-222222222222', current_date, 'daily.v1', 4, 4, 1, 3, 1, 0, 1.0
)
on conflict (account_id, projection_date) do nothing;

insert into public.analytics_events (
    account_id, client_event_id, event_name, event_version, occurred_at,
    source, consent_version, properties
) values (
    '22222222-2222-2222-2222-222222222222',
    '44444444-4444-4444-4444-444444444444', 'attempt_completed', 1, now(),
    'mobile', 'analytics.v1', '{"passed": true}'::jsonb
)
on conflict (account_id, client_event_id) do nothing;

insert into public.attempt_commands (
    account_id, command_id, attempt_id, case_version_id, command_type,
    revision_number, client_occurred_at, snapshot_digest
) values (
    '11111111-1111-1111-1111-111111111111',
    '66666666-6666-6666-6666-666666666666',
    '77777777-7777-7777-7777-777777777777', 'case-published-it',
    'attempt_started', 0, now(), repeat('c', 64)
)
on conflict (account_id, command_id) do nothing;

insert into public.analytics_events (
    account_id, client_event_id, event_name, event_version, occurred_at,
    source, consent_version, properties
) values (
    '11111111-1111-1111-1111-111111111111',
    '55555555-5555-5555-5555-555555555555', 'attempt_completed', 1, now(),
    'mobile', 'analytics.v1', '{"passed": true}'::jsonb
)
on conflict (account_id, client_event_id) do nothing;

insert into public.entitlement_events (
    provider_event_id, account_id, entitlement, status, occurred_at
) values (
    'provider-event-b', '22222222-2222-2222-2222-222222222222',
    'evidrilo.pro', 'active', now()
)
on conflict (provider_event_id) do nothing;

insert into public.entitlements (
    account_id, entitlement, status, source_occurred_at
) values (
    '22222222-2222-2222-2222-222222222222', 'evidrilo.pro', 'active', now()
)
on conflict (account_id, entitlement) do nothing;
commit;

do $$
declare
    n integer;
begin
    select count(*) into n from public.case_lifecycle_audit_events;
    if n <> 2 then raise exception 'case creation audit count failed: %', n; end if;
    if (
        select count(*)
          from public.case_lifecycle_audit_events
         where event_type = 'created'
           and from_state is null
           and to_state in ('draft', 'published')
           and actor_account_id = '11111111-1111-1111-1111-111111111111'::uuid
    ) <> 2 then
        raise exception 'case creation audit shape failed';
    end if;
    raise notice 'CASE_CREATION_AUDIT_ASSERTION_PASS';
end;
$$;

do $$
declare
    token jsonb;
begin
    select public.custom_access_token_hook(jsonb_build_object(
        'user_id', '11111111-1111-1111-1111-111111111111',
        'claims', jsonb_build_object('sub', '11111111-1111-1111-1111-111111111111')
    )) into token;
    if token #>> '{claims,email_verified}' <> 'false' then
        raise exception 'unconfirmed email claim was not false: %', token;
    end if;

    update auth.users
       set email_confirmed_at = now()
     where id = '11111111-1111-1111-1111-111111111111';
    select public.custom_access_token_hook(jsonb_build_object(
        'user_id', '11111111-1111-1111-1111-111111111111',
        'claims', jsonb_build_object('sub', '11111111-1111-1111-1111-111111111111')
    )) into token;
    if token #>> '{claims,email_verified}' <> 'true' then
        raise exception 'confirmed email claim was not true: %', token;
    end if;
    raise notice 'AUTH_VERIFICATION_CLAIM_ASSERTION_PASS';
end;
$$;

set role authenticated;
set row_security = on;
select set_config('request.jwt.claim.sub', '11111111-1111-1111-1111-111111111111', false);

do $$
begin
    begin
        insert into public.analytics_events (
            account_id, client_event_id, event_name, event_version, occurred_at,
            source, consent_version, properties
        ) values (
            '11111111-1111-1111-1111-111111111111',
            '55555555-5555-5555-5555-555555555555', 'attempt_completed', 1, now(),
            'mobile', 'analytics.v1', '{"passed": true}'::jsonb
        );
        raise exception 'client analytics insert was allowed';
    exception when insufficient_privilege then
        null;
    end;

    begin
        insert into public.attempt_commands (
            account_id, command_id, attempt_id, case_version_id, command_type,
            revision_number, client_occurred_at, snapshot_digest
        ) values (
            '11111111-1111-1111-1111-111111111111',
            '66666666-6666-6666-6666-666666666666',
            '77777777-7777-7777-7777-777777777777', 'case-published-it',
            'attempt_started', 0, now(), repeat('c', 64)
        );
        raise exception 'client sync insert was allowed';
    exception when insufficient_privilege then
        null;
    end;

    begin
        insert into public.recommendation_events (
            account_id, client_event_id, case_version_id, interaction,
            calculation_version, reason_code
        ) values (
            '11111111-1111-1111-1111-111111111111',
            '99999999-9999-4999-8999-999999999998', 'case-published-it',
            'shown', 'recommendation.v1', 'synthetic'
        );
        raise exception 'client recommendation insert was allowed';
    exception when insufficient_privilege then
        null;
    end;
    raise notice 'SERVER_OWNED_WRITE_ASSERTION_PASS';
end;
$$;

reset role;
do $$
declare
    n integer;
begin
    select count(*) into n
      from public.worker_jobs
     where job_type = 'analytics_projection'
       and account_id in (
           '11111111-1111-1111-1111-111111111111'::uuid,
           '22222222-2222-2222-2222-222222222222'::uuid
       );
    if n <> 2 then raise exception 'projection enqueue trigger failed: %', n; end if;
    raise notice 'PROJECTION_ENQUEUE_ASSERTION_PASS';
end;
$$;
set role authenticated;
set row_security = on;

do $$
declare
    n integer;
    role_name text;
begin
    perform set_config('request.jwt.claim.sub', '11111111-1111-1111-1111-111111111111', false);
    select count(*) into n from public.account_profiles;
    if n <> 1 then raise exception 'account profile isolation failed: %', n; end if;
    select count(*) into n from public.case_versions where status = 'published';
    if n <> 1 then raise exception 'published case visibility failed: %', n; end if;
    if (
        select content #>> '{facts,0,type}'
          from public.case_versions
         where case_version_id = 'case-published-it'
    ) <> 'observation'
       or (
        select content #>> '{facts,2,type}'
          from public.case_versions
         where case_version_id = 'case-published-it'
    ) <> 'limitation'
       or (
        select content #>> '{rules,0,outcome}'
          from public.case_versions
         where case_version_id = 'case-published-it'
    ) <> 'PASS'
       or (
        select content #>> '{variants,0,id}'
          from public.case_versions
         where case_version_id = 'case-published-it'
    ) <> 'CHALLENGE-1' then
        raise exception 'published canonical content was not readable';
    end if;
    select count(*) into n from public.case_versions where status = 'draft';
    if n <> 0 then raise exception 'draft case leaked: %', n; end if;
    select count(*) into n from public.case_review_decisions;
    if n <> 0 then raise exception 'review decision leaked to authenticated client: %', n; end if;
    select count(*) into n from public.analytics_events;
    if n <> 1 then raise exception 'analytics own visibility failed: %', n; end if;
    select count(*) into n from public.attempt_commands;
    if n <> 1 then raise exception 'attempt own visibility failed: %', n; end if;
    select count(*) into n from public.sync_changes;
    if n <> 1 then raise exception 'sync trigger/visibility failed: %', n; end if;
    select count(*) into n from public.worker_jobs;
    if n <> 0 then raise exception 'worker job leaked to client: %', n; end if;
    select count(*) into n from public.case_lifecycle_audit_events;
    if n <> 0 then raise exception 'case lifecycle audit leaked to client: %', n; end if;
    select role into role_name
      from public.read_cohort_aggregate('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb');
    if role_name <> 'teacher' then raise exception 'teacher aggregate failed: %', role_name; end if;
    select learner_count into n
      from public.read_cohort_aggregate('cccccccc-cccc-4ccc-8ccc-cccccccccccc');
    if n <> 5 then
        raise exception 'cohort aggregate counted a non-learner enrollment: %', n;
    end if;
    raise notice 'COHORT_ACTIVE_MEMBERSHIP_ASSERTION_PASS';

    perform set_config('request.jwt.claim.sub', '22222222-2222-2222-2222-222222222222', false);
    select count(*) into n from public.account_profiles;
    if n <> 1 then raise exception 'second account profile isolation failed: %', n; end if;
    select count(*) into n from public.analytics_events;
    if n <> 1 then raise exception 'second account analytics isolation failed: %', n; end if;
    select count(*) into n from public.attempt_commands;
    if n <> 0 then raise exception 'second account saw first attempt: %', n; end if;
    select count(*) into n from public.sync_changes;
    if n <> 0 then raise exception 'second account saw first sync: %', n; end if;
    select count(*) into n from public.progress_projections;
    if n <> 1 then raise exception 'second account projection visibility failed: %', n; end if;
    select count(*) into n from public.progress_daily_projections;
    if n <> 1 then raise exception 'second account daily visibility failed: %', n; end if;
    select count(*) into n from public.read_cohort_aggregate('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb');
    if n <> 0 then raise exception 'learner aggregate disclosure failed: %', n; end if;

    perform set_config('request.jwt.claim.sub', '11111111-1111-1111-1111-111111111111', false);
    begin
        insert into public.analytics_events (
            account_id, client_event_id, event_name, event_version, occurred_at,
            source, consent_version, properties
        ) values (
            '22222222-2222-2222-2222-222222222222',
            '88888888-8888-8888-8888-888888888888', 'attempt_completed', 1, now(),
            'mobile', 'analytics.v1', '{}'::jsonb
        );
        raise exception 'cross-account analytics insert was allowed';
    exception when insufficient_privilege then
        null;
    end;
    perform set_config('request.jwt.claim.sub', '22222222-2222-2222-2222-222222222222', false);
    select count(*) into n from public.analytics_events
      where client_event_id = '88888888-8888-8888-8888-888888888888';
    if n <> 0 then raise exception 'cross-account row was persisted'; end if;
    raise notice 'RLS_ASSERTIONS_PASS';
end;
$$;

reset role;
select set_config('request.jwt.claim.sub', '11111111-1111-1111-1111-111111111111', false);

do $$
begin
    begin
        update public.case_versions
           set title = 'tampered published title'
         where case_version_id = 'case-published-it';
        raise exception 'published case mutation was allowed';
    exception when check_violation then
        if position('published_case_immutable' in sqlerrm) = 0 then raise; end if;
    end;
    begin
        update public.case_versions
           set status = 'published'
         where case_version_id = 'case-draft-it';
        raise exception 'invalid draft publication was allowed';
    exception when check_violation then
        if position('invalid_case_transition' in sqlerrm) = 0 then raise; end if;
    end;
    raise notice 'LIFECYCLE_ASSERTIONS_PASS';
end;
$$;

do $$
begin
    begin
        insert into public.attempt_commands (
            account_id, command_id, attempt_id, case_version_id, command_type,
            revision_number, client_occurred_at, snapshot_digest
        ) values (
            '22222222-2222-2222-2222-222222222222',
            'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaab',
            'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbc', 'case-draft-it',
            'attempt_started', 0, now(), repeat('d', 64)
        );
        raise exception 'server sync accepted an unpublished case version';
    exception when check_violation then
        if position('Attempt commands require a published case version.' in sqlerrm) = 0 then raise; end if;
    end;
    raise notice 'SYNC_PUBLISHED_CASE_BOUNDARY_ASSERTION_PASS';
end;
$$;

do $$
declare
    n integer;
begin
    select count(*) into n from public.case_lifecycle_audit_events;
    if n <> 2 then raise exception 'failed transitions created audit rows: %', n; end if;
    raise notice 'FAILED_TRANSITION_AUDIT_ASSERTION_PASS';
end;
$$;

begin;
select set_config('request.jwt.claim.sub', '22222222-2222-2222-2222-222222222222', true);
select set_config('evidrilo.case_lifecycle_reason', 'draft_to_review', true);
update public.case_versions
   set status = 'review'
 where case_version_id = 'case-draft-it';
select set_config('evidrilo.case_lifecycle_reason', 'review_to_approved', true);
update public.case_versions
   set status = 'approved'
 where case_version_id = 'case-draft-it';
select set_config('evidrilo.case_lifecycle_reason', 'approved_to_published', true);
update public.case_versions
   set status = 'published', published_at = now()
 where case_version_id = 'case-draft-it';
select set_config('evidrilo.case_lifecycle_reason', 'published_to_retired', true);
update public.case_versions
   set status = 'retired'
 where case_version_id = 'case-draft-it';
commit;

do $$
declare
    n integer;
begin
    select count(*) into n from public.case_lifecycle_audit_events;
    if n <> 6 then raise exception 'case transition audit count failed: %', n; end if;
    select count(*) into n
      from public.case_lifecycle_audit_events
     where case_version_id = 'case-draft-it'
       and event_type = 'transitioned'
       and actor_account_id = '22222222-2222-2222-2222-222222222222'::uuid;
    if n <> 4 then raise exception 'case transition audit actor/count failed: %', n; end if;
    if (
        select count(*)
          from public.case_lifecycle_audit_events
         where case_version_id = 'case-draft-it'
           and reason in ('draft_to_review', 'review_to_approved', 'approved_to_published', 'published_to_retired')
    ) <> 4 then
        raise exception 'case transition audit reasons failed';
    end if;
    if (
        select count(*)
          from public.case_lifecycle_audit_events
         where case_version_id = 'case-draft-it'
           and from_state is not null
           and to_state is not null
           and from_state <> to_state
    ) <> 4 then
        raise exception 'case transition audit state pairs failed';
    end if;
    raise notice 'CASE_TRANSITION_AUDIT_ASSERTIONS_PASS';
end;
$$;

do $$
declare
    event_id uuid;
begin
    select audit_event_id into event_id
      from public.case_lifecycle_audit_events
     order by created_at
     limit 1;
    begin
        update public.case_lifecycle_audit_events
           set reason = 'tampered'
         where audit_event_id = event_id;
        raise exception 'case lifecycle audit update was allowed';
    exception when insufficient_privilege then
        if position('case_lifecycle_audit_append_only' in sqlerrm) = 0 then raise; end if;
    end;
    begin
        delete from public.case_lifecycle_audit_events
         where audit_event_id = event_id;
        raise exception 'case lifecycle audit delete was allowed';
    exception when insufficient_privilege then
        if position('case_lifecycle_audit_append_only' in sqlerrm) = 0 then raise; end if;
    end;
    raise notice 'CASE_AUDIT_APPEND_ONLY_ASSERTION_PASS';
end;
$$;

-- A sole active organization owner must transfer ownership before deleting the
-- account. This is a disposable fixture for the database boundary only.
insert into public.organizations (organization_id, name)
values ('cccccccc-cccc-cccc-cccc-cccccccccccc', 'Evidrilo owner guard fixture')
on conflict (organization_id) do nothing;

insert into public.organization_memberships (organization_id, account_id, role)
values (
    'cccccccc-cccc-cccc-cccc-cccccccccccc',
    '11111111-1111-1111-1111-111111111111',
    'owner'
)
on conflict (organization_id, account_id) do nothing;

select set_config('request.jwt.claim.sub', '11111111-1111-1111-1111-111111111111', false);
do $$
begin
    begin
        perform public.prepare_account_deletion('11111111-1111-1111-1111-111111111111');
        raise exception 'sole owner deletion was allowed';
    exception when others then
        if sqlstate <> 'P0001' or sqlerrm <> 'owner_transfer_required' then
            raise;
        end if;
    end;
    raise notice 'LAST_OWNER_DELETION_GUARD_PASS';
end;
$$;

delete from public.organizations
 where organization_id = 'cccccccc-cccc-cccc-cccc-cccccccccccc';

select set_config('request.jwt.claim.sub', '22222222-2222-2222-2222-222222222222', false);
do $$
begin
    begin
        perform public.prepare_account_deletion('11111111-1111-1111-1111-111111111111');
        raise exception 'cross-account deletion was allowed';
    exception when insufficient_privilege then
        null;
    end;
    raise notice 'DELETION_SCOPE_ASSERTION_PASS';
end;
$$;

select set_config('request.jwt.claim.sub', '11111111-1111-1111-1111-111111111111', false);
select public.prepare_account_deletion('11111111-1111-1111-1111-111111111111') as deletion_outcome;
select public.prepare_account_deletion('11111111-1111-1111-1111-111111111111') as idempotent_outcome;

do $$
declare
    n integer;
    tombstoned boolean;
    author uuid;
    reviewer uuid;
    case_status text;
    deletion_status text;
begin
    select deleted_at is not null into tombstoned from public.account_profiles
      where account_id = '11111111-1111-1111-1111-111111111111';
    if tombstoned is distinct from true then raise exception 'account profile was not tombstoned'; end if;
    select count(*) into n from public.analytics_events
      where account_id = '11111111-1111-1111-1111-111111111111';
    if n <> 0 then raise exception 'analytics deletion failed: %', n; end if;
    select count(*) into n from public.attempt_commands
      where account_id = '11111111-1111-1111-1111-111111111111';
    if n <> 0 then raise exception 'attempt deletion failed: %', n; end if;
    select count(*) into n from public.sync_changes
      where account_id = '11111111-1111-1111-1111-111111111111';
    if n <> 0 then raise exception 'sync deletion failed: %', n; end if;
    select author_id, reviewer_id, status into author, reviewer, case_status
      from public.case_versions where case_version_id = 'case-published-it';
    if author is not null
       or reviewer is distinct from '33333333-3333-3333-3333-333333333333'::uuid
       or case_status <> 'published' then
        raise exception 'published case anonymization/immutability failed';
    end if;
    select status into deletion_status from public.account_deletion_requests
      where account_id = '11111111-1111-1111-1111-111111111111';
    if deletion_status <> 'completed' then raise exception 'deletion status failed: %', deletion_status; end if;
    select count(*) into n from public.organization_memberships
      where account_id = '22222222-2222-2222-2222-222222222222';
    if n <> 1 then raise exception 'other account membership was changed'; end if;
    select count(*) into n from public.worker_jobs
      where account_id = '22222222-2222-2222-2222-222222222222';
    if n <> 1 then raise exception 'other account worker job was changed'; end if;
    select count(*) into n
      from public.case_lifecycle_audit_events
     where actor_account_id = '11111111-1111-1111-1111-111111111111'::uuid;
    if n <> 0 then raise exception 'deleted actor remained in case lifecycle audit: %', n; end if;
    select count(*) into n from public.case_lifecycle_audit_events;
    if n <> 6 then raise exception 'case lifecycle audit history was removed: %', n; end if;
    select count(*) into n from auth.users
      where id = '11111111-1111-1111-1111-111111111111';
    if n <> 1 then raise exception 'managed auth identity was removed by platform function'; end if;
    raise notice 'DELETION_ASSERTIONS_PASS';
end;
$$;

-- A legacy Auth account may predate its account_profiles row. The deletion
-- ledger must still become the authoritative access tombstone in that case.
select set_config('request.jwt.claim.sub', '33333333-3333-3333-3333-333333333333', false);
do $$
declare
    profile_count integer;
    deletion_request_completed boolean;
    profile_tombstoned boolean;
begin
    select count(*) into profile_count
      from public.account_profiles
     where account_id = '33333333-3333-3333-3333-333333333333';
    if profile_count <> 0 then
        raise exception 'missing-profile fixture is not missing its profile: %', profile_count;
    end if;

    perform public.prepare_account_deletion('33333333-3333-3333-3333-333333333333');
    select exists (
               select 1
                 from public.account_deletion_requests
                where account_id = '33333333-3333-3333-3333-333333333333'
                  and status = 'completed'
           ),
           exists (
               select 1
                 from public.account_profiles
                where account_id = '33333333-3333-3333-3333-333333333333'
                  and deleted_at is not null
           )
      into deletion_request_completed, profile_tombstoned;
    if deletion_request_completed is not true and profile_tombstoned is not true then
        raise exception 'deletion status was not observable without a profile row';
    end if;
    raise notice 'DELETION_WITHOUT_PROFILE_ASSERTION_PASS';
end;
$$;
