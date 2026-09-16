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
