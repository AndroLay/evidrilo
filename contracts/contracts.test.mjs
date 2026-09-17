import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import test from 'node:test';
import { fileURLToPath } from 'node:url';

const root = path.dirname(fileURLToPath(import.meta.url));
const repositoryRoot = path.resolve(root, '..');
const schemaRoot = path.join(root, 'schemas');
const schemaFiles = [
  'health.v1.json',
  'http-errors.v1.json',
  'account-summary.v1.json',
  'sync-command.v1.json',
  'sync-push-request.v1.json',
  'sync-push-result.v1.json',
  'sync-pull.v1.json',
  'analytics-event.v1.json',
  'analytics-event-result.v1.json',
  'billing-webhook-result.v1.json',
  'progress-summary.v1.json',
  'progress-daily.v1.json',
  'case-summary.v1.json',
  'recommendation.v1.json',
  'cohort-summary.v1.json',
  'ai-assist-result.v1.json',
  'case-authoring-result.v1.json',
  'case-lifecycle-audit.v1.json',
  'recommendation-interaction-result.v1.json',
  'entitlements.v1.json',
  'account-deletion-result.v1.json',
  'membership-operation-result.v1.json',
];

const fixtureFiles = [
  'account-summary-authenticated.json',
  'health-degraded.json',
  'health-live.json',
  'health-ready.json',
  'http-error-unauthorized.json',
  'sync-push-result.json',
  'sync-pull.json',
  'analytics-event.json',
  'progress-summary.json',
  'progress-daily.json',
  'published-case-summary.json',
  'case-lifecycle-audit.json',
];

function readJson(relativePath) {
  const filePath = path.join(root, relativePath);
  return JSON.parse(fs.readFileSync(filePath, 'utf8'));
}

function readSchema(relativePath) {
  const filePath = path.join(schemaRoot, relativePath);
  return JSON.parse(fs.readFileSync(filePath, 'utf8'));
}

function hasRepositoryPaths(...relativePaths) {
  return relativePaths.every((relativePath) =>
    fs.existsSync(path.join(repositoryRoot, relativePath)),
  );
}

function assertNoCredentialShapedFields(value, location = '$') {
  if (Array.isArray(value)) {
    value.forEach((item, index) => assertNoCredentialShapedFields(item, `${location}[${index}]`));
    return;
  }

  if (!value || typeof value !== 'object') return;

  for (const [key, nestedValue] of Object.entries(value)) {
    const normalizedKey = key.toLowerCase().replaceAll('-', '').replaceAll('_', '');
    assert.equal(
      /^(password|accesstoken|refreshtoken|servicerole|privatekey|apikey|rawproviderpayload|rawclaims)$/.test(normalizedKey),
      false,
      `${location}.${key} must not cross the public contract boundary`,
    );
    assertNoCredentialShapedFields(nestedValue, `${location}.${key}`);
  }
}

test('all versioned response schemas are present and closed at the root', () => {
  for (const file of schemaFiles) {
    const schema = readSchema(file);
    assert.equal(schema.$schema, 'https://json-schema.org/draft/2020-12/schema');
    assert.match(schema.$id, /^https:\/\/evidrilo\.dev\/contracts\/[a-z-]+\.v1\.json$/);
    assert.equal(schema.type, 'object');
    assert.equal(schema.additionalProperties, false);
    assert.match(schema.title, /Evidrilo/);
  }
});

test('synthetic fixtures identify their schema and version', () => {
  for (const file of fixtureFiles) {
    const fixture = readJson(path.join('fixtures', file));
    assert.equal(typeof fixture.schema, 'string', `${file} schema`);
    assert.equal(fixture.version, '1', `${file} version`);
    assert.match(fixture.requestId, /^[A-Za-z0-9_-]{8,128}$/, `${file} requestId`);
    assertNoCredentialShapedFields(fixture);
  }
});

test('health fixtures distinguish dependency-free liveness from readiness', () => {
  const live = readJson(path.join('fixtures', 'health-live.json'));
  const ready = readJson(path.join('fixtures', 'health-ready.json'));
  const degraded = readJson(path.join('fixtures', 'health-degraded.json'));

  assert.deepEqual(live, {
    schema: 'evidrilo.health',
    version: '1',
    check: 'live',
    status: 'ok',
    requestId: live.requestId,
  });
  assert.equal(ready.check, 'ready');
  assert.equal(ready.status, 'ready');
  assert.equal(ready.dependencies.config, 'ready');
  assert.equal(degraded.check, 'ready');
  assert.equal(degraded.status, 'degraded');
  assert.equal(degraded.dependencies.config, 'missing');
});

test('Android draft persistence does not synchronously commit on the UI event path', () => {
  const source = fs.readFileSync(
    path.join(
      root,
      '..',
      'modules',
      'data',
      'src',
      'androidMain',
      'kotlin',
      'dev',
      'nextgen',
      'mobile',
      'storage',
      'ConclusionSessionStore.android.kt',
    ),
    'utf8',
  );
  const saveStart = source.indexOf('override fun save');
  const clearStart = source.indexOf('override fun clear');

  assert.ok(saveStart >= 0 && clearStart > saveStart, 'save and clear implementations must remain explicit');
  const saveBody = source.slice(saveStart, clearStart);
  assert.match(saveBody, /\.apply\(\)/);
  assert.doesNotMatch(saveBody, /\.commit\(\)/);
});

test('error and account fixtures expose only safe public fields', () => {
  const error = readJson(path.join('fixtures', 'http-error-unauthorized.json'));
  const account = readJson(path.join('fixtures', 'account-summary-authenticated.json'));

  assert.deepEqual(Object.keys(error).sort(), ['code', 'message', 'requestId', 'schema', 'version']);
  assert.match(error.code, /^[A-Z][A-Z0-9_]{2,63}$/);
  assert.equal(typeof error.message, 'string');
  assert.ok(error.message.length > 0 && error.message.length <= 240);

  assert.deepEqual(Object.keys(account).sort(), [
    'accountId',
    'emailVerified',
    'requestId',
    'schema',
    'serverTime',
    'version',
  ]);
  assert.match(account.accountId, /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/);
  assert.equal(account.emailVerified, true);
  assert.match(account.serverTime, /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z$/);
});

test('sync fixtures preserve idempotency and cursor boundaries', () => {
  const push = readJson(path.join('fixtures', 'sync-push-result.json'));
  const pull = readJson(path.join('fixtures', 'sync-pull.json'));

  assert.equal(push.schema, 'evidrilo.sync-push-result');
  assert.equal(push.results[0].outcome, 'accepted');
  assert.equal(push.results[1].outcome, 'duplicate');
  assert.equal(push.nextCursor, 41);
  assert.equal(pull.schema, 'evidrilo.sync-pull');
  assert.equal(pull.cursor, 40);
  assert.equal(pull.nextCursor, 41);
  assert.equal(pull.hasMore, false);
  assert.equal(pull.changes[0].commandType, 'attempt_submitted');
  assert.equal(pull.changes[0].snapshotDigest.length, 64);
});

test('analytics contracts are consented, typed, and free of raw learner text', () => {
  const event = readJson(path.join('fixtures', 'analytics-event.json'));
  const progress = readJson(path.join('fixtures', 'progress-summary.json'));
  const daily = readJson(path.join('fixtures', 'progress-daily.json'));

  assert.equal(event.consent, 'granted');
  assert.equal(event.source, 'mobile');
  assert.equal(event.eventVersion, 1);
  assert.equal(event.properties.revisionChanged, true);
  assert.equal('rawDraftText' in event.properties, false);
  assert.equal('email' in event.properties, false);
  assert.equal(progress.calculationVersion, 'progress.v1');
  assert.ok(progress.coverage >= 0 && progress.coverage <= 1);
  assert.equal(daily.schema, 'evidrilo.progress-daily');
  assert.equal(daily.items[0].projectionDate, '2026-09-10');
  assert.ok(daily.items[0].coverage >= 0 && daily.items[0].coverage <= 1);
});

test('analytics schema covers funnel, premium conversion, and safe client errors', () => {
  const schema = readSchema('analytics-event.v1.json');
  const eventNames = schema.properties.eventName.enum;
  for (const name of [
    'practice_started',
    'paywall_viewed',
    'premium_action',
    'client_error',
  ]) {
    assert.equal(eventNames.includes(name), true, `${name} must be versioned`);
  }

  const properties = schema.properties.properties;
  assert.equal(properties.additionalProperties, false);
  assert.equal('errorMessage' in properties.properties, false);
  assert.match(properties.properties.surfaceId.pattern, /\^\[A-Za-z0-9._:-\]/);
  assert.match(properties.properties.errorCode.pattern, /\^\[A-Z\]/);
  assert.deepEqual(properties.properties.productId.enum, ['monthly', 'yearly', null]);
});

test('analytics idempotency normalizes nullable properties across app versions', () => {
  const repositoryRoot = path.resolve(root, '..');
  const store = fs.readFileSync(
    path.join(repositoryRoot, 'platform', 'api', 'Analytics', 'AnalyticsStore.cs'),
    'utf8',
  );
  assert.equal((store.match(/jsonb_strip_nulls/g) ?? []).length, 2);
  assert.match(store, /jsonb_strip_nulls\(jsonb_build_object\(/);
});

test('the repository verification harness is documented and non-secret', () => {
  const repositoryRoot = path.resolve(root, '..');
  const harnessPath = path.join(repositoryRoot, 'scripts', 'ci', 'verify-local.sh');
  assert.equal(fs.existsSync(harnessPath), true, 'scripts/ci/verify-local.sh must exist');
  const harness = fs.readFileSync(harnessPath, 'utf8');
  assert.match(harness, /:composeApp:jvmTest/);
  assert.match(harness, /contracts\/contracts\.test\.mjs/);
  assert.match(harness, /platform\/database\/migrations\/migrations\.test\.mjs/);
  assert.match(harness, /toolchain-paths\.sh/);
  assert.match(harness, /evidrilo_dotnet_root/);
  assert.match(harness, /platform\/api\.Tests\/Evidrilo\.Api\.Tests\.csproj/);
  assert.match(harness, /javac -version/);
  assert.match(harness, /version \"21/);
  assert.match(harness, /gradle_available/);
  assert.doesNotMatch(harness, /(?:sk_|service_role|access_token|refresh_token|password\s*=)/i);

  const testingGuide = fs.readFileSync(path.join(repositoryRoot, 'docs', 'testing.md'), 'utf8');
  assert.match(testingGuide, /UNAVAILABLE/);
  const roadmap = fs.readFileSync(path.join(repositoryRoot, 'docs', 'roadmap.md'), 'utf8');
  assert.match(roadmap, /repository-owned preparation boundary/);
  assert.match(
    roadmap,
    /E175 closes the remaining sync pull lower-bound gap:[\s\S]*?E176 hardens request lifecycle and input boundaries\.[\s\S]*?E177 hardens sync pull pagination:/,
  );
  assert.match(
    roadmap,
    /\[E176\]\(\.\.\/\.\.\/audit\/evidence\/evidrilo-request-lifecycle-and-input-boundaries-2026-09-14\.md\)/,
  );
});

test('billing webhook and forwarded headers have explicit boundaries', () => {
  const repositoryRoot = path.resolve(root, '..');
  const billingEndpoints = fs.readFileSync(
    path.join(repositoryRoot, 'platform', 'api', 'Billing', 'BillingEndpoints.cs'),
    'utf8',
  );
  const program = fs.readFileSync(
    path.join(repositoryRoot, 'platform', 'api', 'Program.cs'),
    'utf8',
  );
  const options = fs.readFileSync(
    path.join(repositoryRoot, 'platform', 'api', 'Configuration', 'PlatformOptions.cs'),
    'utf8',
  );

  assert.match(billingEndpoints, /RequireRateLimiting\("billing-webhook"\)/);
  assert.match(program, /AddPolicy\("billing-webhook"/);
  assert.match(program, /TrustedProxyAddresses/);
  assert.match(program, /UseForwardedHeaders\(\)/);
  assert.match(options, /TRUSTED_PROXY_ADDRESSES/);
});

test('Android local billing configuration uses the documented ignored properties', () => {
  const repositoryRoot = path.resolve(root, '..');
  const composeBuild = fs.readFileSync(
    path.join(repositoryRoot, 'apps', 'mobile-shared', 'build.gradle.kts'),
    'utf8',
  );

  assert.match(composeBuild, /local\.properties/);
  assert.match(composeBuild, /providers\.fileContents/);
  assert.match(composeBuild, /revenuecatAndroidApiKey/);
  assert.match(composeBuild, /revenuecatEntitlementId/);
  assert.match(composeBuild, /revenuecatProductIds/);
  assert.doesNotMatch(composeBuild, /test_[A-Za-z0-9]{20,}/);
});

test('RevenueCat Test Store runbook keeps the approved monthly/yearly catalog', {
  skip: !hasRepositoryPaths('docs/operations/revenuecat-test-store-runbook.md'),
}, () => {
  const repositoryRoot = path.resolve(root, '..');
  const runbook = fs.readFileSync(
    path.join(repositoryRoot, 'docs', 'operations', 'revenuecat-test-store-runbook.md'),
    'utf8',
  );

  assert.match(runbook, /`evidrilo_pro` with only `monthly` and\s+`yearly`/);
  assert.match(runbook, /Lifetime\s+is not part\s+of the approved catalog/i);
  assert.doesNotMatch(runbook, /`monthly`, `yearly`, and `lifetime`/);
  assert.doesNotMatch(runbook, /monthly,yearly,lifetime/);
});

test('RevenueCat Test Store runbook does not overclaim dashboard configuration', {
  skip: !hasRepositoryPaths('docs/operations/revenuecat-test-store-runbook.md'),
}, () => {
  const repositoryRoot = path.resolve(root, '..');
  const runbook = fs.readFileSync(
    path.join(repositoryRoot, 'docs', 'operations', 'revenuecat-test-store-runbook.md'),
    'utf8',
  );

  assert.match(runbook, /(?:approved )?price replacement and transaction matrix remain\s+owner\s+gates/i);
  assert.doesNotMatch(runbook, /catalog is now configured in the owner-authorized dashboard/i);
});

test('current operational snapshots point to the latest local evidence', {
  skip: !hasRepositoryPaths(
    'docs/operations/revenuecat-test-store-runbook.md',
    'docs/operations/evidrilo-backend-execution.md',
    'internal/research/next-gen/STATUS.md',
    'audit/evidence/evidrilo-all-areas-audit-2026-09-13.md',
  ),
}, () => {
  const repositoryRoot = path.resolve(root, '..');
  const revenueCatRunbook = fs.readFileSync(
    path.join(repositoryRoot, 'docs', 'operations', 'revenuecat-test-store-runbook.md'),
    'utf8',
  );
  const backendRegister = fs.readFileSync(
    path.join(repositoryRoot, 'docs', 'operations', 'evidrilo-backend-execution.md'),
    'utf8',
  );
  const platformReadme = fs.readFileSync(
    path.join(repositoryRoot, 'platform', 'README.md'),
    'utf8',
  );
  const rootReadme = fs.readFileSync(path.join(repositoryRoot, 'README.md'), 'utf8');
  const contentAuthoringRunbook = fs.readFileSync(
    path.join(repositoryRoot, 'docs', 'operations', 'content-authoring-runbook.md'),
    'utf8',
  );
  const researchIndex = fs.readFileSync(
    path.join(repositoryRoot, 'internal', 'research', 'next-gen', 'README.md'),
    'utf8',
  );
  const databaseReadiness = fs.readFileSync(
    path.join(repositoryRoot, 'platform', 'api', 'Health', 'DatabaseSchemaReadiness.cs'),
    'utf8',
  );
  const completionPlan = fs.readFileSync(
    path.join(repositoryRoot, 'docs', 'superpowers', 'plans', '2026-09-13-evidrilo-all-areas-completion.md'),
    'utf8',
  );
  const currentSnapshotPaths = [
    path.join(repositoryRoot, 'docs', 'operations', 'evidrilo-backend-execution.md'),
    path.join(repositoryRoot, 'docs', 'architecture', 'platform-decision.md'),
    path.join(repositoryRoot, 'docs', 'architecture', 'repository-structure.md'),
    path.join(repositoryRoot, 'docs', 'architecture', 'revenuecat.md'),
    path.join(repositoryRoot, 'platform', 'security-boundaries.md'),
    path.join(repositoryRoot, 'audit', 'evidence', 'evidrilo-all-areas-audit-2026-09-13.md'),
  ];

  assert.match(revenueCatRunbook, /^Status: `E185 \/ REVENUECAT_OFFERING_MIGRATION_OBSERVED \/.*PRICE_MIGRATION_OPEN`$/m);
  assert.match(revenueCatRunbook, /E149 canonical entitlement guard/);
  assert.match(revenueCatRunbook, /E151 server product allowlist/);
  assert.match(revenueCatRunbook, /E152 account-deletion owner guard/);
  assert.match(revenueCatRunbook, /E153 client\/request boundary hardening/);
  assert.match(revenueCatRunbook, /E154 auth callback boundary|E154/);
  assert.match(backendRegister, /^Last synchronized: 2026-09-16 \(E186\)$/m);
  assert.match(backendRegister, /Node boundary `93\/93`/);
  assert.match(backendRegister, /E172 binds restored session phases[\s\S]*?full API\s+suite passes `151\/151`/);
  assert.match(backendRegister, /E173 protects the optional sync queue[\s\S]*?full API suite `151\/151`/);
  assert.match(backendRegister, /E174 rejects inconsistent sync cursors[\s\S]*?API `151\/151`/);
  assert.match(backendRegister, /E175 requires every returned pull change[\s\S]*?sync suite[\s\S]*?`29\/29`/);
  assert.match(backendRegister, /E151 requires exact `monthly` or `yearly`/);
  assert.match(backendRegister, /E152 protects the last active organization owner/);
  assert.match(platformReadme, /E151 closes the server-side RevenueCat product boundary/);
  assert.match(platformReadme, /E152 protects the last active organization owner/);
  assert.match(platformReadme, /full API suite passes `145\/145`/);
  assert.match(platformReadme, /E150 synchronizes[\s\S]*Node `77\/77`[\s\S]*API `140\/140`/);
  assert.match(platformReadme, /local contract passes `22\/22`/);
  assert.match(platformReadme, /latest local API\/worker harness has API `172\/172`/);
  assert.match(platformReadme, /E164 hardens the cohort aggregate boundary/);
  assert.match(platformReadme, /E165 hardens the role boundary/);
  assert.match(platformReadme, /E166 closes the membership role-change owner invariant/);
  assert.match(platformReadme, /E167 hardens the billing webhook identity boundary/);
  assert.match(platformReadme, /E169 hardens the auth provider boundary/);
  assert.match(platformReadme, /E170 adds explicit regression coverage/);
  assert.match(backendRegister, /E169 hardens the auth provider type boundary/);
  assert.match(backendRegister, /E170 adds explicit regression coverage/);
  assert.match(rootReadme, /Current structural baseline: `f2d532e`/);
  assert.match(rootReadme, /`modules\/domain`/);
  assert.doesNotMatch(rootReadme, /Current repository increment: E186/);
  assert.doesNotMatch(rootReadme, /Kotlin\/JVM 332\/332, Node 96\/96/);
  assert.doesNotMatch(rootReadme, /231\/231 JVM tests, 74\/74 Node checks, API 139\/139/);
  assert.match(databaseReadiness, /CurrentMigrationVersion = "030_sync_published_case_boundary"/);
  assert.match(completionPlan, /Tracker snapshot after E187 backend persistence and sync boundary hardening:\s+`85\/100`/);
  assert.match(completionPlan, /\| Public boundary \|[\s\S]*?\| `96\/96` checks across 9 files pass/);
  assert.match(completionPlan, /\| Kotlin \|[\s\S]*?\| `332\/332` JVM tests/);
  assert.match(completionPlan, /\| Database\/worker integration \|[\s\S]*?migration 030/);
  assert.match(completionPlan, /Preserve the 30-migration order\/checksum ledger/);
  assert.match(researchIndex, /^Latest increment: E186 \/ BACKEND_ENGINE_SYNC_BOUNDARY_HARDENED \/ E185 \/ REVENUECAT_OFFERING_MIGRATION_OBSERVED \/ E183 \/ NATIVE_CHOICE_ACCESSIBILITY_SEMANTICS_HARDENED \/ E182 \/ SYNC_CURSOR_CONTRACT_BOUNDARY_ALIGNED \/ E181 \/ CASE_TRANSITION_CONTRACT_BOUNDARY_HARDENED \/ E180 \/ MOBILE_RELEASE_CANDIDATE_PREPARATION \/ E179 \/ SYNC_CONSENT_CANCELLATION_BOUNDARY_HARDENED \/ E178 \/ API_INPUT_AND_STAGING_BOUNDARY_HARDENED \/ E177 \/ SYNC_PULL_PAGE_SIZE_BOUNDARY_HARDENED(?: \/|\.)/m);
  assert.match(researchIndex, /^Status: CURRENT \/ E186 \/ BACKEND_ENGINE_SYNC_BOUNDARY_HARDENED \/ E185 \/ REVENUECAT_OFFERING_MIGRATION_OBSERVED /m);
  assert.match(researchIndex, /latest repository-owned increment is \[E186\]/);
  assert.match(researchIndex, /strict API input matching/);
  assert.match(researchIndex, /requested page size/);
  assert.match(researchIndex, /focused audio tests pass `28\/28`/);
  assert.match(researchIndex, /focused access-policy coverage `9\/9`/);
  assert.match(contentAuthoringRunbook, /^Status: `E150 \/.*MANAGED_STAGING_NOT_RUN`$/m);
  assert.match(contentAuthoringRunbook, /E150 runbook synchronization/);
  assert.match(contentAuthoringRunbook, /one to 32 meaningful challenge variants/);
  assert.doesNotMatch(contentAuthoringRunbook, /optional challenge variants/i);
  assert.doesNotMatch(contentAuthoringRunbook, /^Status: `E144 \//m);
  for (const snapshotPath of currentSnapshotPaths) {
    const snapshot = fs.readFileSync(snapshotPath, 'utf8');
    assert.match(snapshot, /^Status: `?E186(?:\s|\/)/m, snapshotPath);
  }
});

test('current status page points to the latest evidence record', {
  skip: !hasRepositoryPaths('internal/research/next-gen/STATUS.md'),
}, () => {
  const repositoryRoot = path.resolve(root, '..');
  const status = fs.readFileSync(
    path.join(repositoryRoot, 'internal', 'research', 'next-gen', 'STATUS.md'),
    'utf8',
  );

  assert.match(status, /recorded through\s+E188/i);
  assert.doesNotMatch(status, /recorded through E151/i);
});

test('runtime matrix separates the current boundary from historical Android evidence', {
  skip: !hasRepositoryPaths('audit/runtime-matrix.md'),
}, () => {
  const repositoryRoot = path.resolve(root, '..');
  const runtimeMatrix = fs.readFileSync(
    path.join(repositoryRoot, 'audit', 'runtime-matrix.md'),
    'utf8',
  );

  assert.match(runtimeMatrix, /^Last documentation check: 15 September 2026 \(E184 Android runtime probe\)$/m);
  assert.match(runtimeMatrix, /^## Current Evidrilo evidence boundary — E183$/m);
  assert.doesNotMatch(runtimeMatrix, /^## Current Evidrilo status — E107$/m);
  assert.match(runtimeMatrix, /Android runtime[\s\S]*?package` service[\s\S]*?APK installation failed/i);
  assert.match(runtimeMatrix, /RevenueCat Test Store.*UNRUN/i);
});

test('conclusion choices use native radio and checkbox semantics', () => {
  const repositoryRoot = path.resolve(root, '..');
  const source = fs.readFileSync(
    path.join(repositoryRoot, 'apps', 'mobile-shared', 'src', 'commonMain', 'kotlin', 'dev', 'nextgen', 'mobile', 'EvidriloApp.kt'),
    'utf8',
  );
  const choiceButton = source.slice(
    source.indexOf('private fun EvidriloChoiceButton'),
    source.indexOf('private fun EvidriloFeedbackCard'),
  );

  assert.match(choiceButton, /Modifier\.toggleable\(/);
  assert.match(choiceButton, /Modifier\.selectable\(/);
  assert.match(choiceButton, /mergeDescendants = true/);
  assert.doesNotMatch(choiceButton, /Modifier\.clickable\(/);
});

test('current all-area audit records the latest local verification boundary', {
  skip: !hasRepositoryPaths('audit/evidence/evidrilo-all-areas-audit-2026-09-13.md'),
}, () => {
  const repositoryRoot = path.resolve(root, '..');
  const audit = fs.readFileSync(
    path.join(repositoryRoot, 'audit', 'evidence', 'evidrilo-all-areas-audit-2026-09-13.md'),
    'utf8',
  );

  assert.match(audit, /^# Evidrilo All-Area Capability Audit$/m);
  assert.match(audit, /^Latest increment: E186 \/ BACKEND_ENGINE_SYNC_BOUNDARY_HARDENED \/ E185 \/ REVENUECAT_OFFERING_MIGRATION_OBSERVED \/ E183 \/ NATIVE_CHOICE_ACCESSIBILITY_SEMANTICS_HARDENED \/ E182 \/ SYNC_CURSOR_CONTRACT_BOUNDARY_ALIGNED \/ E181 \/ CASE_TRANSITION_CONTRACT_BOUNDARY_HARDENED \/ E180 \/ MOBILE_RELEASE_CANDIDATE_PREPARATION \/ E179 \/ SYNC_CONSENT_CANCELLATION_BOUNDARY_HARDENED \/ E178 \/ API_INPUT_AND_STAGING_BOUNDARY_HARDENED \/ E177 \/ SYNC_PULL_PAGE_SIZE_BOUNDARY_HARDENED(?: \/|$)/m);
  assert.match(audit, /Node contract,[\s\S]*?`96\/96`/);
  assert.match(audit, /API[\s\S]*?`172\/172`/);
  assert.match(audit, /E186 hardens the backend-first engine[\s\S]*?Kotlin\/JVM `332\/332`/);
  assert.match(audit, /E176 records request lifecycle and input-boundary hardening/);
  assert.match(audit, /E177 records sync pull page-size hardening/);
  assert.match(audit, /E178 records API full-match input hardening/);
  assert.match(audit, /E179 records consent-bound sync orchestration/);
  assert.match(audit, /E174 records fail-closed response and refresh hardening/);
  assert.match(audit, /E151[^\n]*product allowlist/i);
  assert.match(audit, /E152[^\n]*last active owner/i);
  assert.match(audit, /E153[^\n]*client\/request boundary/i);
  assert.match(audit, /E154[^\n]*auth callback/i);
  assert.doesNotMatch(audit, /Status: `E147 \/ AI_SCOPE_DECISION_LOCKED/);
});

test('active backend execution register points to the current verification boundary', {
  skip: !hasRepositoryPaths('docs/operations/evidrilo-backend-execution.md'),
}, () => {
  const repositoryRoot = path.resolve(root, '..');
  const register = fs.readFileSync(
    path.join(repositoryRoot, 'docs', 'operations', 'evidrilo-backend-execution.md'),
    'utf8',
  );

  assert.match(register, /Last synchronized: 2026-09-16 \(E186\)/);
  assert.match(register, /E178 replaces helper-level/);
  assert.match(register, /E179 prevents a deferred or invalidated pull/);
  assert.match(register, /Node boundary `93\/93`/);
  assert.match(register, /E186 backend engine and sync boundary hardening[\s\S]*?Node `96\/96`/);
  assert.match(register, /E172 binds restored session phases[\s\S]*?full API\s+suite passes `151\/151`/);
  assert.match(register, /E173 protects the optional sync queue[\s\S]*?full API suite `151\/151`/);
  assert.match(register, /Release worker tests `5\/5`/);
  assert.match(register, /E152 protects the last active organization owner/);
  assert.match(register, /E153 makes mobile entitlement access canonical/);
  assert.match(register, /E154 bounds custom-scheme authentication callback/);
  assert.match(register, /E155 closes a content-integrity gap/);
  assert.match(register, /E157 makes the Kotlin published-case reader reject/);
  assert.match(register, /E158 adds `minItems: 1` to the versioned published-case schema/);
  assert.match(register, /E159 adds the shared identifier pattern/);
  assert.match(register, /E160 corrects the stale instruction that challenge variants are optional/);
  assert.match(register, /E161 aligns `recommendation.v1.json` with the mobile\/API status-specific rules/);
  assert.match(register, /E162 synchronizes the current Node verification snapshot/);
  assert.match(register, /E163 hardens the local conclusion-session decoder/);
  assert.match(register, /E164 makes teacher-facing cohort aggregates count only active enrollments/);
  assert.match(register, /E165 makes teacher-facing cohort aggregates count only active enrollments whose/);
  assert.match(register, /E166 prevents a membership grant or role change from demoting the last active/);
  assert.match(register, /E167 prevents a signed RevenueCat event with the all-zero/);
  assert.match(register, /E170 adds explicit regression coverage/);
  assert.doesNotMatch(register, /Last synchronized: 2026-09-10 \(E110\)/);
  assert.doesNotMatch(register, /Last synchronized: 2026-09-13 \(E137\)/);
  assert.doesNotMatch(register, /Last synchronized: 2026-09-13 \(E138\)/);
  assert.doesNotMatch(register, /Last synchronized: 2026-09-13 \(E139\)/);
  assert.doesNotMatch(register, /Last synchronized: 2026-09-13 \(E140\)/);
  assert.doesNotMatch(register, /Last synchronized: 2026-09-13 \(E141\)/);
  assert.doesNotMatch(register, /Last synchronized: 2026-09-13 \(E142\)/);
  assert.doesNotMatch(register, /Last synchronized: 2026-09-13 \(E143\)/);
  assert.doesNotMatch(register, /API boundary `139\/139`/);
  assert.doesNotMatch(register, /API 137\/137/);
  assert.doesNotMatch(register, /API 138\/138/);
  assert.doesNotMatch(register, /API 87\/87/);
});

test('RevenueCat managed UI stays platform-scoped and keeps a local fallback', () => {
  const repositoryRoot = path.resolve(root, '..');
  const catalog = fs.readFileSync(
    path.join(repositoryRoot, 'gradle', 'libs.versions.toml'),
    'utf8',
  );
  const build = fs.readFileSync(
    path.join(repositoryRoot, 'apps', 'mobile-shared', 'build.gradle.kts'),
    'utf8',
  );
  const commonUi = fs.readFileSync(
    path.join(
      repositoryRoot,
      'apps',
      'mobile-shared',
      'src',
      'commonMain',
      'kotlin',
      'dev',
      'nextgen',
      'mobile',
      'billing',
      'RevenueCatUi.kt',
    ),
    'utf8',
  );
  const androidUi = fs.readFileSync(
    path.join(
      repositoryRoot,
      'apps',
      'mobile-shared',
      'src',
      'androidMain',
      'kotlin',
      'dev',
      'nextgen',
      'mobile',
      'billing',
      'RevenueCatUi.android.kt',
    ),
    'utf8',
  );
  const iosUi = fs.readFileSync(
    path.join(
      repositoryRoot,
      'apps',
      'mobile-shared',
      'src',
      'iosMain',
      'kotlin',
      'dev',
      'nextgen',
      'mobile',
      'billing',
      'RevenueCatUi.ios.kt',
    ),
    'utf8',
  );
  const jvmUi = fs.readFileSync(
    path.join(
      repositoryRoot,
      'apps',
      'mobile-shared',
      'src',
      'jvmMain',
      'kotlin',
      'dev',
      'nextgen',
      'mobile',
      'billing',
      'RevenueCatUi.jvm.kt',
    ),
    'utf8',
  );

  assert.match(catalog, /revenuecat-kmp-ui = \{ module = "com\.revenuecat\.purchases:purchases-kmp-ui"/);
  assert.equal((build.match(/libs\.revenuecat\.kmp\.ui/g) ?? []).length, 2);
  assert.match(commonUi, /expect fun RevenueCatManagedPaywall/);
  assert.match(commonUi, /expect fun RevenueCatCustomerCenter/);
  assert.match(androidUi, /com\.revenuecat\.purchases\.kmp\.ui\.revenuecatui\.Paywall/);
  assert.match(androidUi, /com\.revenuecat\.purchases\.kmp\.ui\.revenuecatui\.CustomerCenter/);
  assert.match(iosUi, /com\.revenuecat\.purchases\.kmp\.ui\.revenuecatui\.Paywall/);
  assert.match(iosUi, /com\.revenuecat\.purchases\.kmp\.ui\.revenuecatui\.CustomerCenter/);
  assert.match(jvmUi, /RevenueCatUiAvailability\(canPresent = false\)/);
});

test('published case contract carries canonical learning content', () => {
  const schema = readSchema('case-summary.v1.json');
  const properties = schema.properties;

  for (const name of ['objective', 'difficulty', 'evidenceReferences', 'facts', 'rules', 'variants']) {
    assert.equal(name in properties, true, `${name} must be part of the published case contract`);
  }

  assert.equal(properties.facts.items.additionalProperties, false);
  assert.equal(properties.rules.items.additionalProperties, false);
  assert.equal(properties.variants.items.additionalProperties, false);
  assert.equal(properties.caseId.pattern, '^[A-Za-z0-9._:-]{1,128}$');
  assert.equal(properties.caseVersionId.pattern, '^[A-Za-z0-9._:-]{1,128}$');
  assert.equal(properties.evaluatorVersion.pattern, '^[A-Za-z0-9._:-]{1,128}$');
  assert.equal(properties.title.minLength, 1);
  assert.equal(properties.skillTags.minItems, 1);
  assert.equal(properties.skillTags.maxItems, 32);
  assert.equal(properties.skillTags.items.pattern, '^[A-Za-z0-9._:-]{1,128}$');
  assert.equal(properties.variants.minItems, 1);
  assert.deepEqual(properties.facts.items.properties.type.enum, [
    'aim',
    'context',
    'observation',
    'limitation',
    'boundary',
  ]);
  assert.deepEqual(properties.rules.items.properties.outcome.enum, [
    'PASS',
    'ACTION_REQUIRED',
    'CANNOT_ASSESS',
    'INCOMPLETE',
  ]);

  const fixture = readJson(path.join('fixtures', 'published-case-summary.json'));
  assert.equal(fixture.schema, 'evidrilo.case-summary');
  assert.equal(fixture.facts[0].type, 'observation');
  assert.equal(fixture.facts[1].type, 'limitation');
  assert.equal(fixture.rules[0].outcome, 'PASS');
  assert.equal(fixture.variants[0].id, 'CHALLENGE-1');
});

test('recommendation contract preserves status-specific response invariants', () => {
  const schema = readSchema('recommendation.v1.json');

  assert.deepEqual(schema.required, [
    'schema',
    'version',
    'status',
    'calculationVersion',
    'caseVersionId',
    'objective',
    'reasonCode',
    'evidenceReferences',
    'requestId',
  ]);
  assert.equal(schema.properties.calculationVersion.const, 'recommendation.v1');
  assert.deepEqual(schema.properties.status.enum, ['recommended', 'abstain']);
  assert.deepEqual(schema.properties.reasonCode.enum, [
    'START_HERE',
    'PRACTICE_ACTION_REQUIRED',
    'NEXT_PRACTICE',
    'NO_ELIGIBLE_CASE',
    'INSUFFICIENT_PROJECTION',
  ]);
  assert.equal(schema.allOf.length, 2);
});

test('case lifecycle audit contract is closed, bounded, and deletion-safe', () => {
  const schema = readSchema('case-lifecycle-audit.v1.json');
  const fixture = readJson(path.join('fixtures', 'case-lifecycle-audit.json'));

  assert.equal(schema.additionalProperties, false);
  assert.deepEqual(
    schema.required,
    ['schema', 'version', 'caseVersionId', 'events', 'truncated', 'requestId'],
  );
  assert.equal(schema.properties.events.maxItems, 128);
  assert.equal(schema.properties.events.items.additionalProperties, false);
  assert.equal(fixture.schema, 'evidrilo.case-lifecycle-audit');
  assert.equal(fixture.events[0].eventType, 'created');
  assert.equal(fixture.events[0].fromState, null);
  assert.equal(fixture.events[0].actorAccountId, null);
  assert.equal(fixture.events[1].eventType, 'transitioned');
  assert.equal(fixture.events[1].fromState, 'draft');
  assert.equal(fixture.events[1].toState, 'review');
  assert.equal(fixture.truncated, false);
  assertNoCredentialShapedFields(fixture);
});

test('RevenueCat architecture documentation does not overclaim dashboard state', () => {
  const repositoryRoot = path.resolve(root, '..');
  const architecture = fs.readFileSync(
    path.join(repositoryRoot, 'docs', 'architecture', 'revenuecat.md'),
    'utf8',
  );

  assert.match(architecture, /remaining dashboard price migration and purchase\/restore\/revoke matrix remain\s+owner gates/i);
  assert.match(architecture, /historical Test Store\s+observation is recorded/i);
  assert.match(architecture, /approved\s+replacement prices remain open/i);
  assert.doesNotMatch(architecture, /authorized dashboard contains the Test Store catalog/i);
  assert.doesNotMatch(architecture, /The Test Store catalog is configured/i);
  assert.doesNotMatch(architecture, /The current Test Store observation is recorded/i);
});
