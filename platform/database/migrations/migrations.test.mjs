import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import test from 'node:test';
import { fileURLToPath } from 'node:url';

const migrationPath = path.join(path.dirname(fileURLToPath(import.meta.url)), '001_platform_sync.sql');
const analyticsMigrationPath = path.join(path.dirname(fileURLToPath(import.meta.url)), '002_analytics.sql');
const recommendationMetadataMigrationPath = path.join(
  path.dirname(fileURLToPath(import.meta.url)),
  '009_case_recommendation_metadata.sql',
);
const accountExportPath = path.join(
  path.dirname(fileURLToPath(import.meta.url)),
  '..',
  '..',
  'api',
  'Account',
  'AccountExport.cs',
);
const postgresSmokePath = path.join(
  path.dirname(fileURLToPath(import.meta.url)),
  '..',
  'integration',
  'run-local-postgres-smoke.sh',
);

test('P2 migration contains account isolation and append-only sync boundaries', () => {
  const sql = fs.readFileSync(migrationPath, 'utf8');

  for (const table of ['account_profiles', 'case_versions', 'attempt_commands', 'sync_changes']) {
    assert.match(sql, new RegExp(`alter table public\\.${table} enable row level security`, 'i'));
  }
  assert.match(sql, /account_id\s*=\s*auth\.uid\(\)/i);
  assert.match(sql, /primary key \(account_id, command_id\)/i);
  assert.match(sql, /unique \(account_id, command_id\)/i);
  assert.match(sql, /on conflict \(account_id, command_id\) do nothing/i);
  assert.doesNotMatch(sql, /service[_ -]?role|password\s*=/i);
});

test('P3 migration contains consented append-only events and private projections', () => {
  const sql = fs.readFileSync(analyticsMigrationPath, 'utf8');

  assert.match(sql, /create table if not exists public\.analytics_events/i);
  assert.match(sql, /primary key \(account_id, client_event_id\)/i);
  assert.match(sql, /jsonb_typeof\(properties\)\s*=\s*'object'/i);
  assert.match(sql, /consent_version\s+text\s+not null/i);
  assert.match(sql, /alter table public\.analytics_events enable row level security/i);
  assert.match(sql, /alter table public\.progress_projections enable row level security/i);
  assert.match(sql, /account_id\s*=\s*auth\.uid\(\)/i);
  assert.match(sql, /create table if not exists public\.progress_projections/i);
  assert.doesNotMatch(sql, /rawDraftText|access[_ -]?token|refresh[_ -]?token|service[_ -]?role/i);
});

test('P3 funnel migration keeps premium products and event names bounded', () => {
  const sql = fs.readFileSync(
    path.join(path.dirname(fileURLToPath(import.meta.url)), '021_analytics_funnel_events.sql'),
    'utf8',
  );
  assert.match(sql, /drop constraint if exists analytics_events_event_name_check/i);
  for (const eventName of [
    'practice_started',
    'paywall_viewed',
    'premium_action',
    'client_error',
  ]) {
    assert.match(sql, new RegExp(`'${eventName}'`, 'i'));
  }
  assert.match(sql, /Renewal\/refund remains a provider-webhook concern/i);
  assert.doesNotMatch(sql, /lifetime/);
});

test('P5 migration isolates recommendation interaction history', () => {
  const sql = fs.readFileSync(
    path.join(path.dirname(fileURLToPath(import.meta.url)), '004_recommendation_events.sql'),
    'utf8',
  );
  assert.match(sql, /primary key \(account_id, client_event_id\)/i);
  assert.match(sql, /interaction\s+text\s+not null\s+check/i);
  assert.match(sql, /alter table public\.recommendation_events enable row level security/i);
  assert.match(sql, /account_id\s*=\s*auth\.uid\(\)/i);
});

test('P4 migration preserves approved state and review audit records', () => {
  const sql = fs.readFileSync(
    path.join(path.dirname(fileURLToPath(import.meta.url)), '003_content_authoring.sql'),
    'utf8',
  );
  assert.match(sql, /drop constraint if exists case_versions_status_check/i);
  assert.match(sql, /status in \('draft', 'review', 'approved', 'published', 'retired'\)/i);
  assert.match(sql, /create table if not exists public\.case_review_decisions/i);
  assert.match(sql, /alter table public\.case_review_decisions enable row level security/i);
});

test('P6 migration keeps AI audit metadata separate from prompts and responses', () => {
  const sql = fs.readFileSync(
    path.join(path.dirname(fileURLToPath(import.meta.url)), '005_ai_audit.sql'),
    'utf8',
  );
  assert.match(sql, /input_hash\s+text\s+not null/i);
  assert.match(sql, /alter table public\.ai_audit_events enable row level security/i);
  assert.match(sql, /account_id\s*=\s*auth\.uid\(\)/i);
  assert.doesNotMatch(sql, /prompt\s+text|response\s+text|access[_ -]?token|refresh[_ -]?token/i);
});

test('P7 migration creates scoped memberships and enables RLS', () => {
  const sql = fs.readFileSync(
    path.join(path.dirname(fileURLToPath(import.meta.url)), '006_organization_access.sql'),
    'utf8',
  );
  for (const table of ['organizations', 'organization_memberships', 'cohorts', 'cohort_enrollments']) {
    assert.match(sql, new RegExp(`create table if not exists public\\.${table}`, 'i'));
    assert.match(sql, new RegExp(`alter table public\\.${table} enable row level security`, 'i'));
  }
  assert.match(sql, /role\s+text\s+not null\s+check/i);
  assert.match(sql, /account_id\s*=\s*auth\.uid\(\)/i);
});

test('P8 migration bounds worker leases and retry state', () => {
  const sql = fs.readFileSync(
    path.join(path.dirname(fileURLToPath(import.meta.url)), '007_worker_operations.sql'),
    'utf8',
  );
  assert.match(sql, /unique \(job_type, idempotency_key\)/i);
  assert.match(sql, /attempts\s+integer\s+not null\s+default 0\s+check \(attempts between 0 and 10\)/i);
  assert.match(sql, /leased_until\s+timestamptz/i);
  assert.match(sql, /alter table public\.worker_jobs enable row level security/i);
  assert.doesNotMatch(sql, /service[_ -]?role|password\s*=/i);
});

test('billing migration makes entitlements server-owned and provider events idempotent', () => {
  const sql = fs.readFileSync(
    path.join(path.dirname(fileURLToPath(import.meta.url)), '008_billing_entitlements.sql'),
    'utf8',
  );
  assert.match(sql, /provider_event_id\s+text\s+primary key/i);
  assert.match(sql, /primary key \(account_id, entitlement\)/i);
  assert.match(sql, /alter table public\.entitlements enable row level security/i);
  assert.match(sql, /account_id\s*=\s*auth\.uid\(\)/i);
  assert.doesNotMatch(sql, /for insert with check|service[_ -]?role|password\s*=/i);
});

test('P4/P5 migration stores explicit recommendation metadata without opening client writes', () => {
  const sql = fs.readFileSync(recommendationMetadataMigrationPath, 'utf8');
  for (const column of ['organization_id', 'objective', 'difficulty', 'evidence_references', 'content']) {
    assert.match(sql, new RegExp(`add column if not exists ${column}`, 'i'));
  }
  assert.match(sql, /status = 'published'/i);
  assert.doesNotMatch(sql, /create policy.*for (insert|update|delete)/is);
  assert.doesNotMatch(sql, /service[_ -]?role|password\s*=/i);
});

test('P4 author migration includes a distinct author membership role', () => {
  const sql = fs.readFileSync(
    path.join(path.dirname(fileURLToPath(import.meta.url)), '010_author_role.sql'),
    'utf8',
  );
  assert.match(sql, /organization_memberships_role_check/i);
  assert.match(sql, /'author'/i);
  assert.doesNotMatch(sql, /for (insert|update|delete)/i);
});

test('P3/P8 migration enqueues account-scoped projection jobs idempotently', () => {
  const sql = fs.readFileSync(
    path.join(path.dirname(fileURLToPath(import.meta.url)), '011_projection_job_enqueue.sql'),
    'utf8',
  );
  assert.match(sql, /add column if not exists account_id/i);
  assert.match(sql, /enqueue_analytics_projection/i);
  assert.match(sql, /analytics_events_enqueue_projection/i);
  assert.match(sql, /on conflict \(job_type, idempotency_key\)/i);
  assert.match(sql, /'queued'/i);
  assert.doesNotMatch(sql, /password\s*=|service[_ -]?role/i);
});

test('P7 aggregate function checks auth subject and keeps raw enrollment data inside the server boundary', () => {
  const sql = fs.readFileSync(
    path.join(path.dirname(fileURLToPath(import.meta.url)), '012_cohort_aggregate_function.sql'),
    'utf8',
  );
  assert.match(sql, /create or replace function public\.read_cohort_aggregate/i);
  assert.match(sql, /security definer/i);
  assert.match(sql, /auth\.uid\(\)/i);
  assert.match(sql, /m\.role in \('teacher', 'maintainer', 'owner'\)/i);
  assert.match(sql, /revoke all on function/i);
  assert.doesNotMatch(sql, /password\s*=|service[_ -]?role/i);
});

test('P7 aggregate migration excludes enrollments whose organization membership is inactive', () => {
  const sql = fs.readFileSync(
    path.join(path.dirname(fileURLToPath(import.meta.url)), '028_cohort_active_membership_boundary.sql'),
    'utf8',
  );
  assert.match(sql, /create or replace function public\.read_cohort_aggregate/i);
  assert.match(sql, /join public\.cohorts c\s+on c\.cohort_id = e\.cohort_id/is);
  assert.match(sql, /join public\.organization_memberships learner_membership/is);
  assert.match(sql, /learner_membership\.active = true/i);
  assert.doesNotMatch(sql, /password\s*=|service[_ -]?role/i);
});

test('P7 aggregate role migration excludes non-learner enrollments', () => {
  const sql = fs.readFileSync(
    path.join(path.dirname(fileURLToPath(import.meta.url)), '029_cohort_learner_role_boundary.sql'),
    'utf8',
  );
  assert.match(sql, /create or replace function public\.read_cohort_aggregate/i);
  assert.match(sql, /learner_membership\.role\s*=\s*'learner'/i);
  assert.match(sql, /revoke all on function/i);
  assert.doesNotMatch(sql, /password\s*=|service[_ -]?role/i);
});

test('P4 migration protects published and retired case versions at the database boundary', () => {
  const sql = fs.readFileSync(
    path.join(path.dirname(fileURLToPath(import.meta.url)), '013_case_version_immutability.sql'),
    'utf8',
  );
  assert.match(sql, /enforce_case_version_lifecycle/i);
  assert.match(sql, /published_case_immutable/i);
  assert.match(sql, /retired_case_immutable/i);
  assert.match(sql, /case_versions_lifecycle_guard/i);
  assert.match(sql, /new\.status not in \('published', 'retired'\)/i);
  assert.doesNotMatch(sql, /password\s*=|service[_ -]?role/i);
});

test('sync writes require a published case version at the database boundary', () => {
  const sql = fs.readFileSync(
    path.join(path.dirname(fileURLToPath(import.meta.url)), '030_sync_published_case_boundary.sql'),
    'utf8',
  );
  assert.match(sql, /require_published_attempt_case_version/i);
  assert.match(sql, /status\s*=\s+'published'/i);
  assert.match(sql, /attempt_commands_case_version_published/i);
  assert.match(sql, /before insert on public\.attempt_commands/i);
  assert.match(sql, /revoke all on function public\.require_published_attempt_case_version\(\) from public/i);
  assert.doesNotMatch(sql, /password\s*=|service[_ -]?role/i);
});

test('AI credit migration keeps consent, grants, and reservations server-owned', () => {
  const sql = fs.readFileSync(
    path.join(path.dirname(fileURLToPath(import.meta.url)), '031_ai_credit_ledger.sql'),
    'utf8',
  );
  for (const table of ['ai_credit_consents', 'ai_credit_grants', 'ai_credit_reservations']) {
    assert.match(sql, new RegExp(`create table if not exists public\\.${table}`, 'i'));
    assert.match(sql, new RegExp(`alter table public\\.${table} enable row level security`, 'i'));
    assert.match(sql, new RegExp(`revoke all on public\\.${table} from anon, authenticated`, 'i'));
  }
  assert.match(sql, /unique \(account_id, grant_kind, grant_key\)/i);
  assert.match(sql, /primary key \(account_id, request_id\)/i);
  assert.match(sql, /reserved_credits\s*\+\s*consumed_credits\s*<=\s*credits/i);
  assert.match(sql, /purge_ai_credit_data_on_account_deletion/i);
  assert.doesNotMatch(sql, /password\s*=|service[_ -]?role/i);
});

test('worker lease migration adds an explicit fencing token for concurrent workers', () => {
  const sql = fs.readFileSync(
    path.join(path.dirname(fileURLToPath(import.meta.url)), '032_worker_lease_fencing.sql'),
    'utf8',
  );
  assert.match(sql, /add column if not exists lease_token\s+uuid/i);
  assert.match(sql, /create index if not exists worker_jobs_lease_idx/i);
  assert.doesNotMatch(sql, /password\s*=|service[_ -]?role/i);
});

test('AI credit fingerprint migration binds a request key to one payload', () => {
  const sql = fs.readFileSync(
    path.join(path.dirname(fileURLToPath(import.meta.url)), '033_ai_credit_request_fingerprint.sql'),
    'utf8',
  );
  assert.match(sql, /add column if not exists request_hash\s+text/i);
  assert.match(sql, /alter column request_hash set not null/i);
  assert.match(sql, /ai_credit_reservations_request_hash_check/i);
  assert.match(sql, /repeat\('0', 64\)/i);
  assert.doesNotMatch(sql, /password\s*=|service[_ -]?role/i);
});

test('notification preferences migration is account-scoped and deletion-safe', () => {
  const sql = fs.readFileSync(
    path.join(path.dirname(fileURLToPath(import.meta.url)), '034_notification_preferences.sql'),
    'utf8',
  );
  assert.match(sql, /create table if not exists public\.notification_preferences/i);
  assert.match(sql, /account_id\s+uuid\s+primary key references auth\.users\(id\) on delete cascade/i);
  assert.match(sql, /cadence\s+text[^;]*check \(cadence in \('daily', 'weekly'\)\)/is);
  assert.match(sql, /local_hour\s+smallint[^;]*between 0 and 23/is);
  assert.match(sql, /local_minute\s+smallint[^;]*between 0 and 59/is);
  assert.match(sql, /revision\s+bigint\s+not null/i);
  assert.match(sql, /alter table public\.notification_preferences enable row level security/i);
  assert.match(sql, /account_id\s*=\s*auth\.uid\(\)/i);
  assert.match(sql, /purge_notification_preferences_on_account_deletion/i);
  assert.match(sql, /delete from public\.notification_preferences/i);
  assert.doesNotMatch(sql, /password\s*=|service[_ -]?role/i);
});

test('account export includes the server-owned notification preference mirror', () => {
  const source = fs.readFileSync(accountExportPath, 'utf8');

  assert.match(source, /["]notificationPreferences["]/i);
  assert.match(source, /from public\.notification_preferences preference/i);
  assert.match(source, /where preference\.account_id = @account_id/i);
  assert.match(source, /'continueUnfinishedEnabled'/i);
  assert.match(source, /'reviewCompletedEnabled'/i);
});

test('PostgreSQL smoke waits for the target database, not only the server socket', () => {
  const source = fs.readFileSync(postgresSmokePath, 'utf8');

  assert.match(
    source,
    /pg_isready\s+-U postgres\s+-d evidrilo_it[\s\S]*&&\s*docker exec[\s\S]*psql[\s\S]*-d evidrilo_it[\s\S]*-c ['"]select 1['"]/i,
  );
});

test('database integrity hardening binds AI reservations to their account grant and protects new published cases', () => {
  const sql = fs.readFileSync(
    path.join(path.dirname(fileURLToPath(import.meta.url)), '035_database_integrity_hardening.sql'),
    'utf8',
  );
  assert.match(sql, /unique\s*\(account_id,\s*grant_id\)/i);
  assert.match(sql, /foreign key\s*\(account_id,\s*grant_id\)[\s\S]*references public\.ai_credit_grants\s*\(account_id,\s*grant_id\)/i);
  assert.match(sql, /case_versions_published_metadata_guard/i);
  assert.match(sql, /new\.status\s*=\s*'published'/i);
  assert.match(sql, /old\.status\s+is distinct from new\.status/i);
  assert.match(sql, /published_case_metadata_required/i);
  assert.match(sql, /published_at\s+is null/i);
  assert.match(sql, /organization_id\s+is null/i);
  assert.match(sql, /content\s+is null/i);
  assert.doesNotMatch(sql, /password\s*=|service[_ -]?role/i);
});

test('RLS smoke fixture supplies the required AI reservation fingerprint', () => {
  const sql = fs.readFileSync(
    path.join(path.dirname(fileURLToPath(import.meta.url)), '..', 'integration', 'rls-smoke.sql'),
    'utf8',
  );
  assert.match(sql, /ai_credit_reservations\s*\(\s*\n\s*account_id,\s*request_id,\s*grant_id,\s*status,\s*request_hash/i);
  assert.match(sql, /repeat\('a',\s*64\)/i);
});

test('account lifecycle migration removes owned data and anonymizes shared content references', () => {
  const sql = fs.readFileSync(
    path.join(path.dirname(fileURLToPath(import.meta.url)), '014_account_deletion_boundary.sql'),
    'utf8',
  );
  assert.match(sql, /prepare_account_deletion/i);
  assert.match(sql, /auth\.uid\(\)\s+is distinct from requested_account_id/i);
  assert.match(sql, /case_versions[\s\S]*author_id = null/i);
  assert.match(sql, /case_review_decisions[\s\S]*reviewer_id = null/i);
  for (const table of ['analytics_events', 'progress_projections', 'recommendation_events', 'ai_audit_events']) {
    assert.match(sql, new RegExp(`delete from public\\.${table}`, 'i'));
  }
  assert.match(sql, /revoke all on function/i);
  assert.doesNotMatch(sql, /password\s*=|service[_ -]?role/i);
});

test('P7 membership migration keeps lifecycle mutations server-owned and auditable', () => {
  const sql = fs.readFileSync(
    path.join(path.dirname(fileURLToPath(import.meta.url)), '015_membership_lifecycle.sql'),
    'utf8',
  );
  assert.match(sql, /create table if not exists public\.membership_audit_events/i);
  assert.match(sql, /event_type\s+text\s+not null\s+check/i);
  assert.match(sql, /on delete set null/i);
  assert.match(sql, /alter table public\.membership_audit_events enable row level security/i);
  assert.match(sql, /prepare_account_deletion/i);
  assert.doesNotMatch(sql, /create policy.*for (insert|update|delete)/is);
  assert.doesNotMatch(sql, /password\s*=|service[_ -]?role/i);
});

test('P3 daily projection migration is worker-owned and account-scoped', () => {
  const sql = fs.readFileSync(
    path.join(path.dirname(fileURLToPath(import.meta.url)), '016_analytics_daily_projection.sql'),
    'utf8',
  );
  assert.match(sql, /create table if not exists public\.progress_daily_projections/i);
  assert.match(sql, /primary key \(account_id, projection_date\)/i);
  assert.match(sql, /alter table public\.progress_daily_projections enable row level security/i);
  assert.match(sql, /account_id\s*=\s*auth\.uid\(\)/i);
  assert.doesNotMatch(sql, /for (insert|update|delete)/i);
  assert.doesNotMatch(sql, /password\s*=|service[_ -]?role/i);
});

test('account deletion can anonymize immutable cases and purge daily projections', () => {
  const sql = fs.readFileSync(
    path.join(path.dirname(fileURLToPath(import.meta.url)), '017_account_deletion_immutability.sql'),
    'utf8',
  );
  const ownerGuard = fs.readFileSync(
    path.join(path.dirname(fileURLToPath(import.meta.url)), '027_account_deletion_owner_guard.sql'),
    'utf8',
  );
  assert.match(sql, /evidrilo\.account_deletion/i);
  assert.match(sql, /old\.status\s+in\s*\('published',\s*'retired'\)/i);
  assert.match(sql, /new\.author_id\s+is\s+null/i);
  assert.match(sql, /new\.reviewer_id\s+is\s+null/i);
  assert.match(sql, /author_id\s*=\s*case\s+when\s+author_id\s*=\s*requested_account_id/i);
  assert.match(sql, /target_account_id\s*=\s*case\s+when\s+target_account_id\s*=\s*requested_account_id/i);
  assert.match(sql, /delete from public\.progress_daily_projections/i);
  assert.match(sql, /revoke all on function/i);
  assert.doesNotMatch(sql, /password\s*=|service[_ -]?role/i);
  assert.match(ownerGuard, /create or replace function public\.prepare_account_deletion/i);
  assert.match(ownerGuard, /owner_transfer_required/i);
  assert.match(ownerGuard, /role\s*=\s*'owner'[\s\S]*active\s*=\s*true/i);
  assert.match(ownerGuard, /for update/i);
  assert.match(ownerGuard, /revoke all on function/i);
  assert.doesNotMatch(ownerGuard, /password\s*=|service[_ -]?role/i);
});

test('billing migration preserves entitlement ordering and server ownership', () => {
  const sql = fs.readFileSync(
    path.join(path.dirname(fileURLToPath(import.meta.url)), '018_billing_event_ordering.sql'),
    'utf8',
  );
  assert.match(sql, /source_occurred_at\s+timestamptz/i);
  assert.match(sql, /alter table public\.entitlements[\s\S]*alter column source_occurred_at set not null/i);
  assert.doesNotMatch(sql, /for (insert|update|delete)/i);
  assert.doesNotMatch(sql, /password\s*=|service[_ -]?role/i);
});

test('review decisions remain server-owned and private from published case readers', () => {
  const sql = fs.readFileSync(
    path.join(path.dirname(fileURLToPath(import.meta.url)), '019_review_decision_privacy.sql'),
    'utf8',
  );
  assert.match(sql, /drop policy if exists published_case_review_select on public\.case_review_decisions/i);
  assert.doesNotMatch(sql, /create policy/i);
  assert.doesNotMatch(sql, /password\s*=|service[_ -]?role/i);
});

test('Supabase access tokens receive a server-derived email verification claim', () => {
  const sql = fs.readFileSync(
    path.join(path.dirname(fileURLToPath(import.meta.url)), '020_auth_email_verification_claim.sql'),
    'utf8',
  );
  assert.match(sql, /custom_access_token_hook/i);
  assert.match(sql, /auth\.users/i);
  assert.match(sql, /email_confirmed_at/i);
  assert.match(sql, /email_verified/i);
  assert.match(sql, /security definer/i);
  assert.match(sql, /supabase_auth_admin/i);
  assert.doesNotMatch(sql, /password\s*=|service[_ -]?role/i);
});

test('client roles cannot bypass server validation for analytics and sync writes', () => {
  const sql = fs.readFileSync(
    path.join(path.dirname(fileURLToPath(import.meta.url)), '022_server_owned_write_boundaries.sql'),
    'utf8',
  );
  assert.match(sql, /drop policy if exists analytics_events_insert_own on public\.analytics_events/i);
  assert.match(sql, /drop policy if exists attempt_commands_insert_own on public\.attempt_commands/i);
  assert.match(sql, /revoke insert on (table )?public\.analytics_events from anon, authenticated/i);
  assert.match(sql, /revoke insert on (table )?public\.attempt_commands from anon, authenticated/i);
  assert.doesNotMatch(sql, /password\s*=|service[_ -]?role/i);
});

test('client roles cannot bypass server validation for recommendation writes', () => {
  const sql = fs.readFileSync(
    path.join(path.dirname(fileURLToPath(import.meta.url)), '023_server_owned_recommendation_writes.sql'),
    'utf8',
  );
  assert.match(sql, /drop policy if exists recommendation_events_insert_own on public\.recommendation_events/i);
  assert.match(sql, /revoke insert on (table )?public\.recommendation_events from anon, authenticated/i);
  assert.doesNotMatch(sql, /password\s*=|service[_ -]?role/i);
});

test('account creation provisions a lifecycle profile through a server-owned trigger', () => {
  const sql = fs.readFileSync(
    path.join(path.dirname(fileURLToPath(import.meta.url)), '024_account_profile_provisioning.sql'),
    'utf8',
  );
  assert.match(sql, /create or replace function public\.ensure_account_profile/i);
  assert.match(sql, /security definer/i);
  assert.match(sql, /insert into public\.account_profiles/i);
  assert.match(sql, /on conflict\s*\(account_id\)\s*do nothing/i);
  assert.match(sql, /create trigger auth_users_create_account_profile/i);
  assert.match(sql, /after insert on auth\.users/i);
  assert.match(sql, /set search_path\s*=\s*public\s*,\s*auth\s*,\s*pg_temp/i);
  assert.match(sql, /revoke all on function public\.ensure_account_profile\(\) from public/i);
  assert.doesNotMatch(sql, /password\s*=|service[_ -]?role/i);
});

test('security-definer trigger functions are not executable by public roles', () => {
  const sql = fs.readFileSync(
    path.join(path.dirname(fileURLToPath(import.meta.url)), '025_trigger_function_privileges.sql'),
    'utf8',
  );
  assert.match(sql, /revoke all on function public\.append_sync_change\(\) from public/i);
  assert.match(sql, /revoke all on function public\.enqueue_analytics_projection\(\) from public/i);
  assert.match(sql, /alter function public\.append_sync_change\(\) set search_path\s*=\s*public\s*,\s*pg_temp/i);
  assert.match(sql, /alter function public\.enqueue_analytics_projection\(\) set search_path\s*=\s*public\s*,\s*pg_temp/i);
  assert.doesNotMatch(sql, /grant execute.*(anon|authenticated)/i);
  assert.doesNotMatch(sql, /password\s*=|service[_ -]?role/i);
});

test('entitlement periods and AI reservation leases preserve bounded server-owned credit grants', () => {
  const sql = fs.readFileSync(
    path.join(path.dirname(fileURLToPath(import.meta.url)), '036_entitlement_periods_and_ai_reservation_leases.sql'),
    'utf8',
  );

  assert.match(sql, /add column if not exists period_started_at\s+timestamptz/i);
  assert.match(sql, /add column if not exists period_expires_at\s+timestamptz/i);
  assert.match(sql, /alter table public\.entitlement_events[\s\S]*add column if not exists period_started_at/i);
  assert.match(sql, /entitlement_events_period_bounds_check/i);
  assert.match(sql, /period_expires_at\s*>\s*period_started_at/i);
  assert.match(sql, /lease_expires_at\s+timestamptz/i);
  assert.match(sql, /reserved_at\s*\+\s*interval\s+'2 minutes'/i);
  assert.match(sql, /release_reason\s+text/i);
  assert.match(sql, /where status\s*=\s*'reserved'/i);
  assert.match(sql, /\([^)]*lease_expires_at[^)]*\)/i);
  assert.doesNotMatch(sql, /drop table|delete from public\.ai_credit_(?:grants|reservations)/i);
});

test('AI conversation migration stores only bounded account-scoped session metadata', () => {
  const sql = fs.readFileSync(
    path.join(path.dirname(fileURLToPath(import.meta.url)), '037_ai_contextual_conversations.sql'),
    'utf8',
  );
  for (const table of ['ai_conversation_sessions', 'ai_conversation_turn_requests']) {
    assert.match(sql, new RegExp(`create table if not exists public\\.${table}`, 'i'));
    assert.match(sql, new RegExp(`alter table public\\.${table} enable row level security`, 'i'));
  }
  assert.match(sql, /account_id\s*=\s*auth\.uid\(\)/i);
  assert.match(sql, /turn_count smallint not null default 0 check \(turn_count between 0 and 5\)/i);
  assert.match(sql, /unique \(account_id, creation_request_id\)/i);
  assert.match(sql, /primary key \(account_id, request_id\)/i);
  assert.match(sql, /active_lease_expires_at/i);
  assert.match(sql, /on delete cascade/i);
  assert.match(sql, /purge_ai_conversations_on_account_deletion/i);
  assert.match(sql, /revoke all on public\.ai_conversation_sessions from anon, authenticated/i);
  assert.match(sql, /revoke all on public\.ai_conversation_turn_requests from anon, authenticated/i);
  assert.doesNotMatch(sql, /prompt\s+text|response\s+text|transcript\s+text|message\s+text|service[_ -]?role|password\s*=/i);
});

test('AI provider spend migration enforces monthly budget reservations and private metadata-only usage', () => {
  const sql = fs.readFileSync(
    path.join(path.dirname(fileURLToPath(import.meta.url)), '038_ai_provider_spend_budget.sql'),
    'utf8',
  );
  for (const table of ['ai_provider_monthly_spend', 'ai_provider_spend_reservations']) {
    assert.match(sql, new RegExp(`create table if not exists public\\.${table}`, 'i'));
    assert.match(sql, new RegExp(`alter table public\\.${table} enable row level security`, 'i'));
    assert.match(sql, new RegExp(`revoke all on public\\.${table} from public, anon, authenticated`, 'i'));
  }
  assert.match(sql, /primary key \(provider, period_start\)/i);
  assert.match(sql, /primary key \(provider, account_id, request_id\)/i);
  assert.match(sql, /reserved_usd[^;]*spent_usd/is);
  assert.match(sql, /status in \('reserved', 'settled', 'uncertain', 'released'\)/i);
  assert.match(sql, /lease_expires_at/i);
  assert.match(sql, /input_tokens integer/i);
  assert.match(sql, /output_tokens integer/i);
  assert.match(sql, /on delete cascade/i);
  assert.match(sql, /purge_ai_provider_spend_on_account_deletion/i);
  assert.match(sql, /status = 'uncertain'[\s\S]*actual_cost_usd = reserved_cost_usd/i);
  assert.doesNotMatch(sql, /prompt\s+text|response\s+text|transcript\s+text|message\s+text|service[_ -]?role|password\s*=/i);
});

test('student project migration enforces private versioned storage and idempotent account deletion', () => {
  const sql = fs.readFileSync(
    path.join(path.dirname(fileURLToPath(import.meta.url)), '039_student_owned_projects.sql'),
    'utf8',
  );
  for (const table of ['student_projects', 'student_project_revisions', 'student_project_commands']) {
    assert.match(sql, new RegExp(`create table if not exists public\\.${table}`, 'i'));
    assert.match(sql, new RegExp(`alter table public\\.${table} enable row level security`, 'i'));
  }
  assert.match(sql, /unique \(account_id, project_id, version\)/i);
  assert.match(sql, /primary key \(account_id, idempotency_key\)/i);
  assert.match(sql, /request_fingerprint text not null check/i);
  assert.match(sql, /student_project_revisions_immutable/i);
  assert.match(sql, /pg_trigger_depth\(\) > 1/i);
  assert.match(sql, /student_projects_owner_read[\s\S]*auth\.uid\(\)/i);
  assert.match(sql, /purge_student_projects_on_account_deletion[\s\S]*new\.status = 'completed'/i);
  assert.doesNotMatch(sql, /for insert with check|for update using|service[_ -]?role|password\s*=/i);
});

test('040 gates project writes on versioned consent while retaining owner-scoped reads after revocation', () => {
  const sql = fs.readFileSync(
    path.join(path.dirname(fileURLToPath(import.meta.url)), '040_project_cloud_consent.sql'),
    'utf8',
  );
  const projectStore = fs.readFileSync(
    path.join(path.dirname(fileURLToPath(import.meta.url)), '..', '..', 'api', 'Projects', 'NpgsqlStudentProjectStore.cs'),
    'utf8',
  );
  assert.match(sql, /create table if not exists public\.student_project_cloud_consents/i);
  assert.match(sql, /policy_version\s+text\s+not null/i);
  assert.match(sql, /granted\s+boolean\s+not null/i);
  assert.match(sql, /create table if not exists public\.student_project_cloud_consent_events/i);
  assert.match(sql, /decision\s+text\s+not null\s+check\s*\(decision in \('grant', 'revoke'\)\)/i);
  assert.match(sql, /alter table public\.student_project_cloud_consents enable row level security/i);
  assert.match(sql, /alter table public\.student_project_cloud_consent_events enable row level security/i);
  assert.match(sql, /account_id\s*=\s*auth\.uid\(\)/i);
  assert.match(projectStore, /student_project_cloud_consents[\s\S]*?for update/i);
  assert.match(projectStore, /and granted = true[\s\S]*?for share/i);
  assert.match(projectStore, /pg_advisory_xact_lock\(hashtextextended\(@account_id::text, 0\)\)/i);
  for (const method of ['CreateOwnAsync', 'SaveOwnAsync']) {
    const methodStart = projectStore.indexOf(`> ${method}(`);
    assert.notEqual(methodStart, -1, `${method} must exist in the project store`);
    const nextMethod = projectStore.indexOf('\n    public ', methodStart + 1);
    const methodBody = projectStore.slice(methodStart, nextMethod < 0 ? undefined : nextMethod);
    assert.match(
      methodBody,
      /await RequireCloudConsentAsync\(/,
      `${method} must enforce current account consent inside its transaction`,
    );
  }
  for (const method of ['ListOwnAsync', 'ReadOwnAsync', 'ReadRevisionsOwnAsync']) {
    const methodStart = projectStore.indexOf(`> ${method}(`);
    assert.notEqual(methodStart, -1, `${method} must exist in the project store`);
    const nextMethod = projectStore.indexOf('\n    public ', methodStart + 1);
    const methodBody = projectStore.slice(methodStart, nextMethod < 0 ? undefined : nextMethod);
    assert.doesNotMatch(methodBody, /await RequireCloudConsentAsync\(/);
    assert.match(methodBody, /where account_id = @account_id/i);
  }
  assert.match(
    projectStore,
    /select 1\s+from public\.student_projects\s+where account_id = @account_id and project_id = @project_id\s+for key share/i,
    'project-ID reads must verify ownership and serialize with permanent deletion',
  );
  assert.doesNotMatch(sql, /project_text|student_project_document|service[_ -]?role|password\s*=/i);
});

test('041 migration preserves account tombstones and a fenced provider deletion outbox', () => {
  const sql = fs.readFileSync(
    path.join(path.dirname(fileURLToPath(import.meta.url)), '041_account_auth_deletion_outbox.sql'),
    'utf8',
  );
  const outboxStore = fs.readFileSync(
    path.join(path.dirname(fileURLToPath(import.meta.url)), '..', '..', 'worker', 'AccountAuthDeletionOutboxStore.cs'),
    'utf8',
  );
  assert.match(sql, /create table if not exists public\.account_deletion_tombstones/i);
  assert.match(sql, /create table if not exists public\.account_auth_deletion_outbox/i);
  assert.match(sql, /lease_token\s+uuid/i);
  assert.match(sql, /leased_until\s+timestamptz/i);
  assert.match(sql, /account_deletion_requests[\s\S]*status = 'completed'[\s\S]*account_auth_deletion_outbox/i);
  assert.match(outboxStore, /for update skip locked/i);
  assert.match(outboxStore, /lease_token = @lease_token/i);
  assert.doesNotMatch(sql, /references auth\.users/i);
  assert.doesNotMatch(sql, /service[_ -]?role|password\s*=|access[_ -]?token|refresh[_ -]?token/i);
});

test('042 migration publishes only reviewed, versioned project templates and anonymizes authoring actors', () => {
  const sql = fs.readFileSync(
    path.join(path.dirname(fileURLToPath(import.meta.url)), '042_project_template_catalog.sql'),
    'utf8',
  );
  const smoke = fs.readFileSync(
    path.join(path.dirname(fileURLToPath(import.meta.url)), '..', 'integration', 'rls-smoke.sql'),
    'utf8',
  );
  for (const table of [
    'project_template_versions',
    'project_template_review_decisions',
    'project_template_lifecycle_events',
  ]) {
    assert.match(sql, new RegExp(`create table if not exists public\\.${table}`, 'i'));
    assert.match(sql, new RegExp(`alter table public\\.${table} enable row level security`, 'i'));
  }
  for (const family of [
    'experimental_laboratory',
    'observational_survey',
    'literature_review',
    'qualitative_interview_field_study',
    'design_engineering',
  ]) assert.match(sql, new RegExp(`'${family}'`));
  assert.match(sql, /project_template_published_read[\s\S]*state = 'published'[\s\S]*is_current_published = true/i);
  assert.match(sql, /grant select \([\s\S]*template_id, template_version, family, content/i);
  assert.ok(
    sql.lastIndexOf('grant select (') > sql.lastIndexOf('revoke all on table public.project_template_versions'),
    'the public column grant must be applied after the table-wide revoke',
  );
  assert.match(sql, /revoke all on table public\.project_template_review_decisions from public, anon, authenticated/i);
  assert.match(sql, /pg_advisory_xact_lock\(hashtextextended\(new\.template_id, 0\)\)/i);
  assert.match(sql, /project_template_content_immutable/i);
  assert.match(sql, /project_template_independent_reviewer_required/i);
  assert.match(sql, /project_template_reviewed_example_invalid/i);
  assert.match(sql, /project_template_review_metadata_server_owned/i);
  assert.match(sql, /project_template_lifecycle_append_only/i);
  assert.match(sql, /project_template_version_append_only/i);
  assert.match(sql, /account_deletion_project_template_actors/i);
  assert.match(sql, /set reviewer_id = null, reason = '\[ACCOUNT_DELETED\]'/i);
  assert.doesNotMatch(sql, /student_project_document|rawDraftText|service[_ -]?role|password\s*=/i);
  for (const marker of [
    'PROJECT_TEMPLATE_DRAFT_PUBLICATION_GUARD_PASS',
    'PROJECT_TEMPLATE_SELF_REVIEW_GUARD_PASS',
    'PROJECT_TEMPLATE_REVIEW_ANCHOR_GUARD_PASS',
    'PROJECT_TEMPLATE_SCENARIO_KIND_INSERT_GUARD_PASS',
    'PROJECT_TEMPLATE_LEGACY_METADATA_UPDATE_PASS',
    'PROJECT_TEMPLATE_PREEXISTING_APPROVAL_PUBLISH_BLOCK_PASS',
    'PROJECT_TEMPLATE_CONTENT_IMMUTABLE_PASS',
    'PROJECT_TEMPLATE_AUDIT_APPEND_ONLY_PASS',
    'PROJECT_TEMPLATE_DELETION_ANONYMIZATION_PASS',
  ]) assert.ok(smoke.includes(marker), `PostgreSQL smoke must cover ${marker}`);
});

test('043 migration requires reviewed normal and edge or conflicting template examples', () => {
  const sql = fs.readFileSync(
    path.join(path.dirname(fileURLToPath(import.meta.url)), '043_project_template_example_scenarios.sql'),
    'utf8',
  );
  assert.match(sql, /add constraint project_template_requires_normal_and_edge_examples/i);
  assert.match(sql, /coalesce\(content\s*->\s*'examples'\s*@>\s*'\[\{"kind":\s*"normal"\}\]'::jsonb,\s*false\)/i);
  assert.match(sql, /coalesce\(content\s*->\s*'examples'\s*@>\s*'\[\{"kind":\s*"edge_or_conflicting"\}\]'::jsonb,\s*false\)/i);
  assert.match(sql, /reviewed_example_ids/i);
  assert.match(sql, /project_template_review_example_kinds_guard/i);
  assert.match(sql, /project_template_requires_normal_and_edge_examples/i);
  assert.doesNotMatch(sql, /service[_ -]?role|password\s*=|access[_ -]?token|refresh[_ -]?token/i);
});

test('044 scenario hardening preserves immutable legacy rows and rechecks approval at publication', () => {
  const sql = fs.readFileSync(
    path.join(path.dirname(fileURLToPath(import.meta.url)), '044_project_template_scenario_gate_hardening.sql'),
    'utf8',
  );
  assert.match(sql, /drop constraint if exists project_template_requires_normal_and_edge_examples/i);
  assert.match(sql, /new\.content is not distinct from old\.content/i);
  assert.match(sql, /before insert or update on public\.project_template_versions/i);
  assert.match(sql, /create or replace function public\.enforce_project_template_publish_example_kinds/i);
  assert.match(sql, /before update of state on public\.project_template_versions/i);
  assert.match(sql, /order by created_at desc, review_decision_id desc/i);
  assert.match(sql, /project_template_requires_reviewed_normal_and_edge_examples/i);
  assert.doesNotMatch(sql, /service[_ -]?role|password\s*=|access[_ -]?token|refresh[_ -]?token/i);
});

test('045 stores revocable account-scoped Project AI consent separately from cloud consent', () => {
  const sql = fs.readFileSync(
    path.join(path.dirname(fileURLToPath(import.meta.url)), '045_project_ai_consent.sql'),
    'utf8',
  );
  assert.match(sql, /create table if not exists public\.project_ai_consents/i);
  assert.match(sql, /account_id\s+uuid\s+primary key references auth\.users\(id\) on delete cascade/i);
  assert.match(sql, /policy_version\s+text\s+not null/i);
  assert.match(sql, /granted\s+boolean\s+not null/i);
  assert.match(sql, /consent_generation\s+integer\s+not null/i);
  assert.match(sql, /unique \(account_id, consent_generation\)/i);
  assert.match(sql, /create table if not exists public\.project_ai_consent_events/i);
  assert.match(sql, /decision\s+text\s+not null\s+check\s*\(decision in \('grant', 'revoke'\)\)/i);
  assert.match(sql, /alter table public\.project_ai_consents enable row level security/i);
  assert.match(sql, /alter table public\.project_ai_consent_events enable row level security/i);
  assert.match(sql, /account_id\s*=\s*auth\.uid\(\)/i);
  assert.match(sql, /project_ai_consent_events_append_only/i);
  assert.match(sql, /purge_project_ai_consent_on_account_deletion/i);
  assert.doesNotMatch(sql, /assignment_brief|student_question|prompt\s+text|response\s+text|project_document/i);
  assert.doesNotMatch(sql, /service[_ -]?role|password\s*=|access[_ -]?token|refresh[_ -]?token/i);
});

test('046 serializes Project AI consent grants with account deletion completion', () => {
  const sql = fs.readFileSync(
    path.join(path.dirname(fileURLToPath(import.meta.url)), '046_project_ai_consent_deletion_fence.sql'),
    'utf8',
  );
  assert.match(sql, /lock_project_ai_consent_on_account_deletion/i);
  assert.match(sql, /before update of status on public\.account_deletion_requests/i);
  assert.match(sql, /new\.status = 'completed'[\s\S]*old\.status is distinct from new\.status/i);
  assert.match(sql, /pg_advisory_xact_lock\(hashtextextended\(new\.account_id::text, 0\)\)/i);
  assert.match(sql, /revoke all on function public\.lock_project_ai_consent_on_account_deletion\(\) from public/i);
  assert.doesNotMatch(sql, /service[_ -]?role|password\s*=|access[_ -]?token|refresh[_ -]?token/i);
});

test('047 adds a bounded positive credit cost to AI reservations with a one-credit default', () => {
  const migrationPath = path.join(path.dirname(fileURLToPath(import.meta.url)), '047_ai_credit_reservation_cost.sql');
  assert.equal(fs.existsSync(migrationPath), true, '047 must be a new forward-only migration');
  const sql = fs.readFileSync(migrationPath, 'utf8');

  assert.match(sql, /add column if not exists credit_cost\s+integer\s+not null\s+default\s+1/i);
  assert.match(sql, /add constraint ai_credit_reservations_credit_cost_check/i);
  assert.match(sql, /check\s*\(\s*credit_cost between 1 and 100\s*\)/i);
  assert.doesNotMatch(sql, /drop column|drop constraint|delete from public\.ai_credit_reservations/i);
});

test('048 aligns student project JSONB storage limits with the validated API document budget', () => {
  const migrationPath = path.join(path.dirname(fileURLToPath(import.meta.url)), '048_student_project_document_budget.sql');
  assert.equal(fs.existsSync(migrationPath), true, '048 must be a new forward-only migration');
  const sql = fs.readFileSync(migrationPath, 'utf8');

  for (const table of ['student_projects', 'student_project_revisions']) {
    assert.match(sql, new RegExp(`alter table public\\.${table}[\\s\\S]*?octet_length\\(document::text\\) <= 3145728`, 'i'));
    assert.match(sql, new RegExp(`jsonb_typeof\\(document\\) = 'object'`, 'i'));
  }
  assert.doesNotMatch(sql, /delete from|truncate|drop table|drop column|service[_ -]?role|password\s*=/i);
});

test('049 adds metadata-only per-installation Project AI history with deletion cleanup', () => {
  const migrationPath = path.join(path.dirname(fileURLToPath(import.meta.url)), '049_project_ai_activity_history.sql');
  assert.equal(fs.existsSync(migrationPath), true, '049 must be a new forward-only migration');
  const sql = fs.readFileSync(migrationPath, 'utf8');

  assert.match(sql, /create table if not exists public\.project_ai_activity/i);
  assert.match(sql, /unique \(account_id, request_id\)/i);
  assert.match(sql, /on delete cascade/i);
  assert.match(sql, /mode = 'GENERAL'[\s\S]*?project_id is null[\s\S]*?stage_id is null/i);
  assert.match(sql, /consent_generation integer/i);
  assert.match(sql, /requested_settlement_outcome[\s\S]*?settlement_hash/i);
  assert.match(sql, /alter table public\.project_ai_activity enable row level security/i);
  assert.match(sql, /account_id = auth\.uid\(\)/i);
  assert.match(sql, /purge_project_ai_activity_on_account_deletion[\s\S]*?new\.status = 'completed'[\s\S]*?delete from public\.project_ai_activity/i);
  assert.doesNotMatch(sql, /prompt\s+text|response\s+text|transcript|source_text/i);
  assert.doesNotMatch(sql, /truncate|drop table|service[_ -]?role|password\s*=/i);
});

test('Project AI consent, activity, credit cost, and document budgets have disposable PostgreSQL runtime coverage', () => {
  const integrationDir = path.join(path.dirname(fileURLToPath(import.meta.url)), '..', 'integration');
  const smokePath = path.join(integrationDir, 'project-ai-budget-smoke.sql');
  const smoke = fs.readFileSync(smokePath, 'utf8');
  const runner = fs.readFileSync(path.join(integrationDir, 'run-local-postgres-smoke.sh'), 'utf8');
  const documentBudgetMigration = fs.readFileSync(
    path.join(path.dirname(fileURLToPath(import.meta.url)), '048_student_project_document_budget.sql'),
    'utf8',
  );
  const activityMigration = fs.readFileSync(
    path.join(path.dirname(fileURLToPath(import.meta.url)), '049_project_ai_activity_history.sql'),
    'utf8',
  );

  assert.match(runner, /project-ai-budget-smoke\.sql/);
  for (const marker of [
    'PROJECT_AI_CONSENT_RLS_PASS',
    'PROJECT_AI_CONSENT_APPEND_ONLY_PASS',
    'PROJECT_AI_CONSENT_DELETION_FENCE_PASS',
    'PROJECT_AI_CONSENT_DELETION_PURGE_PASS',
    'AI_CREDIT_RESERVATION_COST_PASS',
    'STUDENT_PROJECT_DOCUMENT_BUDGET_PASS',
    'PROJECT_AI_ACTIVITY_RLS_PASS',
    'PROJECT_AI_ACTIVITY_ACCOUNT_DELETION_PURGE_PASS',
    'PROJECT_AI_ACTIVITY_PROJECT_DELETION_CASCADE_PASS',
  ]) assert.match(smoke, new RegExp(marker));
  assert.match(documentBudgetMigration, /octet_length\(document::text\)/i);
  assert.match(smoke, /repeat\('x',\s*3100000\)/i);
  assert.match(smoke, /repeat\('x',\s*3145800\)/i);
  assert.match(smoke, /request\.jwt\.claim\.sub/i);
  assert.match(smoke, /project_ai_consent_events/i);
  assert.match(smoke, /project_ai_activity/i);
  assert.match(activityMigration, /requested_settlement_outcome/i);
  assert.doesNotMatch(smoke, /service[_ -]?role|password\s*=|access[_ -]?token|refresh[_ -]?token/i);
});

test('AI credit ledger reserves, settles, and recovers each persisted reservation cost', () => {
  const ledger = fs.readFileSync(
    path.join(path.dirname(fileURLToPath(import.meta.url)), '..', '..', 'api', 'Ai', 'AiCreditLedger.cs'),
    'utf8',
  );

  assert.match(ledger, /string requestHash,\s+int creditCost,\s+CancellationToken cancellationToken/);
  assert.match(ledger, /select request_hash, status, credit_cost/i);
  assert.match(ledger, /existingCreditCost != creditCost/);
  assert.match(ledger, /credits >= reserved_credits \+ consumed_credits \+ @credit_cost/i);
  assert.match(ledger, /request_hash, grant_id, credit_cost,[\s\S]*?@request_hash, @grant_id, @credit_cost/i);
  assert.match(ledger, /returning grant_id, credit_cost/i);
  assert.match(ledger, /reserved_credits = reserved_credits - @credit_cost,\s+consumed_credits = consumed_credits \+ @credit_cost/i);
  assert.match(ledger, /sum\(credit_cost\)::integer as released_credit_cost/i);
  assert.match(ledger, /reserved_credits - grant_costs\.released_credit_cost/i);
});

test('case lifecycle migration records actor-bound append-only history', () => {
  const sql = fs.readFileSync(
    path.join(path.dirname(fileURLToPath(import.meta.url)), '026_case_lifecycle_audit.sql'),
    'utf8',
  );
  assert.match(sql, /create table if not exists public\.case_lifecycle_audit_events/i);
  for (const column of ['case_version_id', 'organization_id', 'actor_account_id', 'from_state', 'to_state', 'event_type', 'reason']) {
    assert.match(sql, new RegExp(`\\b${column}\\b`, 'i'));
  }
  assert.match(sql, /alter table public\.case_lifecycle_audit_events enable row level security/i);
  assert.match(sql, /revoke all on table public\.case_lifecycle_audit_events from anon, authenticated/i);
  assert.match(sql, /create or replace function public\.append_case_lifecycle_audit_event/i);
  assert.match(sql, /current_setting\('request\.jwt\.claim\.sub', true\)/i);
  assert.match(sql, /case_lifecycle_actor_required/i);
  assert.match(sql, /after insert or update on public\.case_versions/i);
  assert.match(sql, /old\.status is distinct from new\.status/i);
  assert.match(sql, /case_lifecycle_audit_append_only/i);
  assert.match(sql, /evidrilo\.account_deletion/i);
  assert.match(sql, /update public\.case_lifecycle_audit_events/i);
  assert.doesNotMatch(sql, /password\s*=|service[_ -]?role/i);
});

test('migration runner locks before checking and applying each version', () => {
  const runner = fs.readFileSync(
    path.join(path.dirname(fileURLToPath(import.meta.url)), 'apply-migrations.sh'),
    'utf8',
  );
  const migrationLoopIndex = runner.indexOf('for migration in');
  const lockIndex = runner.indexOf('select pg_advisory_xact_lock(814235);', migrationLoopIndex);
  const checksumQueryIndex = runner.indexOf('select checksum from public.evidrilo_schema_migrations', migrationLoopIndex);

  assert.ok(migrationLoopIndex >= 0, 'migration runner must iterate numbered versions');
  assert.ok(lockIndex >= 0, 'migration runner must acquire the advisory lock');
  assert.ok(checksumQueryIndex >= 0, 'migration runner must check the checksum ledger');
  assert.ok(
    lockIndex < checksumQueryIndex,
    'the checksum check must be inside the transaction that holds the migration lock',
  );
  assert.match(runner, /--single-transaction/);
  assert.match(runner, /migration_already_applied/);
  assert.match(runner, /migration_checksum_ok/);
  assert.match(runner, /\[0-9\]\[0-9\]\[0-9\]_\[A-Za-z0-9_\]\*\)/);
});
