-- Disposable database only. Exact effective table grants and server RLS boundaries.
create temporary table expected_runtime_grants(role_name text, table_name text, operation text);
insert into expected_runtime_grants values
('evidrilo_api', 'account_deletion_requests', 'SELECT'),
('evidrilo_api', 'account_deletion_tombstones', 'SELECT'),
('evidrilo_api', 'account_export_jobs', 'DELETE'),
('evidrilo_api', 'account_export_jobs', 'INSERT'),
('evidrilo_api', 'account_export_jobs', 'SELECT'),
('evidrilo_api', 'account_export_jobs', 'UPDATE'),
('evidrilo_api', 'account_profiles', 'SELECT'),
('evidrilo_api', 'ai_audit_events', 'INSERT'),
('evidrilo_api', 'ai_audit_events', 'SELECT'),
('evidrilo_api', 'ai_conversation_sessions', 'DELETE'),
('evidrilo_api', 'ai_conversation_sessions', 'INSERT'),
('evidrilo_api', 'ai_conversation_sessions', 'SELECT'),
('evidrilo_api', 'ai_conversation_sessions', 'UPDATE'),
('evidrilo_api', 'ai_conversation_turn_requests', 'INSERT'),
('evidrilo_api', 'ai_conversation_turn_requests', 'SELECT'),
('evidrilo_api', 'ai_conversation_turn_requests', 'UPDATE'),
('evidrilo_api', 'ai_credit_consents', 'INSERT'),
('evidrilo_api', 'ai_credit_consents', 'SELECT'),
('evidrilo_api', 'ai_credit_consents', 'UPDATE'),
('evidrilo_api', 'ai_credit_grants', 'INSERT'),
('evidrilo_api', 'ai_credit_grants', 'SELECT'),
('evidrilo_api', 'ai_credit_grants', 'UPDATE'),
('evidrilo_api', 'ai_credit_reservation_allocations', 'INSERT'),
('evidrilo_api', 'ai_credit_reservation_allocations', 'SELECT'),
('evidrilo_api', 'ai_credit_reservation_allocations', 'UPDATE'),
('evidrilo_api', 'ai_credit_reservations', 'INSERT'),
('evidrilo_api', 'ai_credit_reservations', 'SELECT'),
('evidrilo_api', 'ai_credit_reservations', 'UPDATE'),
('evidrilo_api', 'ai_provider_monthly_spend', 'INSERT'),
('evidrilo_api', 'ai_provider_monthly_spend', 'SELECT'),
('evidrilo_api', 'ai_provider_monthly_spend', 'UPDATE'),
('evidrilo_api', 'ai_provider_spend_reservations', 'INSERT'),
('evidrilo_api', 'ai_provider_spend_reservations', 'SELECT'),
('evidrilo_api', 'ai_provider_spend_reservations', 'UPDATE'),
('evidrilo_api', 'analytics_events', 'INSERT'),
('evidrilo_api', 'analytics_events', 'SELECT'),
('evidrilo_api', 'attempt_commands', 'INSERT'),
('evidrilo_api', 'attempt_commands', 'SELECT'),
('evidrilo_api', 'case_lifecycle_audit_events', 'SELECT'),
('evidrilo_api', 'case_review_decisions', 'INSERT'),
('evidrilo_api', 'case_review_decisions', 'SELECT'),
('evidrilo_api', 'case_versions', 'INSERT'),
('evidrilo_api', 'case_versions', 'SELECT'),
('evidrilo_api', 'case_versions', 'UPDATE'),
('evidrilo_api', 'entitlement_events', 'INSERT'),
('evidrilo_api', 'entitlement_events', 'SELECT'),
('evidrilo_api', 'entitlement_events', 'UPDATE'),
('evidrilo_api', 'entitlements', 'INSERT'),
('evidrilo_api', 'entitlements', 'SELECT'),
('evidrilo_api', 'entitlements', 'UPDATE'),
('evidrilo_api', 'evidrilo_schema_migrations', 'SELECT'),
('evidrilo_api', 'membership_audit_events', 'INSERT'),
('evidrilo_api', 'membership_audit_events', 'SELECT'),
('evidrilo_api', 'notification_preferences', 'INSERT'),
('evidrilo_api', 'notification_preferences', 'SELECT'),
('evidrilo_api', 'notification_preferences', 'UPDATE'),
('evidrilo_api', 'organization_memberships', 'INSERT'),
('evidrilo_api', 'organization_memberships', 'SELECT'),
('evidrilo_api', 'organization_memberships', 'UPDATE'),
('evidrilo_api', 'organizations', 'SELECT'),
('evidrilo_api', 'progress_daily_projections', 'SELECT'),
('evidrilo_api', 'progress_projections', 'SELECT'),
('evidrilo_api', 'project_ai_activity', 'DELETE'),
('evidrilo_api', 'project_ai_activity', 'INSERT'),
('evidrilo_api', 'project_ai_activity', 'SELECT'),
('evidrilo_api', 'project_ai_activity', 'UPDATE'),
('evidrilo_api', 'project_ai_consent_events', 'INSERT'),
('evidrilo_api', 'project_ai_consent_events', 'SELECT'),
('evidrilo_api', 'project_ai_consents', 'INSERT'),
('evidrilo_api', 'project_ai_consents', 'SELECT'),
('evidrilo_api', 'project_ai_consents', 'UPDATE'),
('evidrilo_api', 'project_ai_local_contexts', 'DELETE'),
('evidrilo_api', 'project_ai_local_contexts', 'INSERT'),
('evidrilo_api', 'project_ai_local_contexts', 'SELECT'),
('evidrilo_api', 'project_ai_local_contexts', 'UPDATE'),
('evidrilo_api', 'project_template_review_decisions', 'INSERT'),
('evidrilo_api', 'project_template_review_decisions', 'SELECT'),
('evidrilo_api', 'project_template_versions', 'INSERT'),
('evidrilo_api', 'project_template_versions', 'SELECT'),
('evidrilo_api', 'project_template_versions', 'UPDATE'),
('evidrilo_api', 'recommendation_events', 'INSERT'),
('evidrilo_api', 'recommendation_events', 'SELECT'),
('evidrilo_api', 'student_project_cloud_consent_events', 'INSERT'),
('evidrilo_api', 'student_project_cloud_consent_events', 'SELECT'),
('evidrilo_api', 'student_project_cloud_consents', 'INSERT'),
('evidrilo_api', 'student_project_cloud_consents', 'SELECT'),
('evidrilo_api', 'student_project_cloud_consents', 'UPDATE'),
('evidrilo_api', 'student_project_commands', 'INSERT'),
('evidrilo_api', 'student_project_commands', 'SELECT'),
('evidrilo_api', 'student_project_commands', 'UPDATE'),
('evidrilo_api', 'student_project_revisions', 'INSERT'),
('evidrilo_api', 'student_project_revisions', 'SELECT'),
('evidrilo_api', 'student_projects', 'DELETE'),
('evidrilo_api', 'student_projects', 'INSERT'),
('evidrilo_api', 'student_projects', 'SELECT'),
('evidrilo_api', 'student_projects', 'UPDATE'),
('evidrilo_api', 'sync_changes', 'SELECT'),
('evidrilo_worker', 'account_auth_deletion_outbox', 'SELECT'),
('evidrilo_worker', 'account_auth_deletion_outbox', 'UPDATE'),
('evidrilo_worker', 'account_deletion_requests', 'SELECT'),
('evidrilo_worker', 'account_deletion_tombstones', 'SELECT'),
('evidrilo_worker', 'account_export_jobs', 'DELETE'),
('evidrilo_worker', 'account_export_jobs', 'SELECT'),
('evidrilo_worker', 'account_export_jobs', 'UPDATE'),
('evidrilo_worker', 'account_profiles', 'SELECT'),
('evidrilo_worker', 'ai_audit_events', 'SELECT'),
('evidrilo_worker', 'ai_conversation_sessions', 'SELECT'),
('evidrilo_worker', 'ai_conversation_turn_requests', 'SELECT'),
('evidrilo_worker', 'ai_credit_consents', 'SELECT'),
('evidrilo_worker', 'ai_credit_grants', 'SELECT'),
('evidrilo_worker', 'ai_credit_reservations', 'SELECT'),
('evidrilo_worker', 'analytics_events', 'SELECT'),
('evidrilo_worker', 'attempt_commands', 'SELECT'),
('evidrilo_worker', 'entitlement_events', 'SELECT'),
('evidrilo_worker', 'entitlements', 'SELECT'),
('evidrilo_worker', 'notification_preferences', 'SELECT'),
('evidrilo_worker', 'progress_daily_projections', 'DELETE'),
('evidrilo_worker', 'progress_daily_projections', 'INSERT'),
('evidrilo_worker', 'progress_daily_projections', 'SELECT'),
('evidrilo_worker', 'progress_projections', 'INSERT'),
('evidrilo_worker', 'progress_projections', 'SELECT'),
('evidrilo_worker', 'progress_projections', 'UPDATE'),
('evidrilo_worker', 'project_ai_activity', 'SELECT'),
('evidrilo_worker', 'project_ai_consent_events', 'SELECT'),
('evidrilo_worker', 'project_ai_consents', 'SELECT'),
('evidrilo_worker', 'recommendation_events', 'SELECT'),
('evidrilo_worker', 'student_project_cloud_consent_events', 'SELECT'),
('evidrilo_worker', 'student_project_cloud_consents', 'SELECT'),
('evidrilo_worker', 'student_project_revisions', 'SELECT'),
('evidrilo_worker', 'student_projects', 'SELECT'),
('evidrilo_worker', 'sync_changes', 'SELECT'),
('evidrilo_worker', 'worker_jobs', 'SELECT'),
('evidrilo_worker', 'worker_jobs', 'UPDATE');
do $$ declare r record; op text; expected boolean; actual boolean; begin
  for r in select roles.role_name, tables.tablename from
    (values ('evidrilo_api'), ('evidrilo_worker')) roles(role_name)
    cross join pg_tables tables where tables.schemaname='public'
  loop
    foreach op in array array['SELECT','INSERT','UPDATE','DELETE','TRUNCATE','REFERENCES','TRIGGER'] loop
      select exists(select 1 from expected_runtime_grants e where e.role_name=r.role_name and e.table_name=r.tablename and e.operation=op) into expected;
      actual := has_table_privilege(r.role_name, 'public.' || quote_ident(r.tablename), op);
      if actual <> expected then raise exception 'Unexpected grant %.% %: expected % got %', r.role_name,r.tablename,op,expected,actual; end if;
    end loop;
  end loop;
  if exists(select 1 from pg_roles where rolname in ('evidrilo_api','evidrilo_worker') and (rolsuper or rolcreatedb or rolcreaterole or rolreplication or rolbypassrls or rolinherit or rolcanlogin)) then raise exception 'Runtime attributes unsafe'; end if;
  if has_table_privilege('evidrilo_api','auth.users','SELECT') or has_table_privilege('evidrilo_worker','auth.users','SELECT') then raise exception 'Runtime role can read Auth users'; end if;
  if pg_has_role('authenticated','evidrilo_api','MEMBER') or pg_has_role('anon','evidrilo_worker','MEMBER') then raise exception 'Client inherited runtime role'; end if;
end $$;
begin;
insert into auth.users(id) values ('55eee555-eeee-4555-8555-555555555555');
insert into public.worker_jobs(job_type,idempotency_key,status) values ('analytics_projection','runtime-role-smoke','queued');
set local role evidrilo_api;
insert into public.ai_credit_consents(account_id,consent_version) values ('55eee555-eeee-4555-8555-555555555555','runtime-role-smoke');
update public.ai_credit_consents set revoked_at=now() where account_id='55eee555-eeee-4555-8555-555555555555';
do $$ begin
 if not public.lock_runtime_account('55eee555-eeee-4555-8555-555555555555') then raise exception 'API cannot fence existing account'; end if;
 if public.lock_runtime_account('66eee666-eeee-4666-8666-666666666666') then raise exception 'Missing account accepted'; end if;
 if (select count(*) from public.ai_credit_consents where account_id='55eee555-eeee-4555-8555-555555555555') <> 1 then raise exception 'API RLS hid server write'; end if;
 begin execute 'select count(*) from public.worker_jobs'; raise exception 'API reached worker queue'; exception when insufficient_privilege then null; end;
 begin execute 'create table public.runtime_forbidden(id int)'; raise exception 'API can create tables'; exception when insufficient_privilege then null; end;
end $$;
reset role;
set local role evidrilo_worker;
update public.worker_jobs set status='running' where idempotency_key='runtime-role-smoke';
do $$ begin
 if (select count(*) from public.worker_jobs where idempotency_key='runtime-role-smoke' and status='running') <> 1 then raise exception 'Worker RLS hid lease update'; end if;
 begin execute 'select public.lock_runtime_account(''55eee555-eeee-4555-8555-555555555555'')'; raise exception 'Worker accessed API account lock'; exception when insufficient_privilege then null; end;
 begin execute 'update public.ai_credit_consents set revoked_at=null'; raise exception 'Worker altered AI consent'; exception when insufficient_privilege then null; end;
 begin execute 'insert into public.worker_jobs(job_type,idempotency_key,status) values (''analytics_projection'',''forbidden'',''queued'')'; raise exception 'Worker invented queue job'; exception when insufficient_privilege then null; end;
end $$;
reset role;
rollback;
select 'EVIDRILO_RUNTIME_ROLES_PASS';
