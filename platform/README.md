# Evidrilo platform lane

The checked-in [`Evidrilo.sln`](Evidrilo.sln) is the solution boundary for the
current API, worker, integration harness, and their tests. The projects remain
physically grouped by executable responsibility while deeper Application,
Domain, and Infrastructure extraction is deferred until dependency analysis
proves a safe move.

Current repository increment: E189 / KOTLIN_MODULE_BOUNDARIES_AND_VERIFICATION_ALIGNED / E188 / RELEASE_VERSION_SOURCE_ALIGNED / E187 / REPOSITORY_ARCHITECTURE_MIGRATION_VERIFIED / E186 / BACKEND_ENGINE_SYNC_BOUNDARY_HARDENED / E185 / REVENUECAT_OFFERING_MIGRATION_OBSERVED / E183 / NATIVE_CHOICE_ACCESSIBILITY_SEMANTICS_HARDENED / E182 / SYNC_CURSOR_CONTRACT_BOUNDARY_ALIGNED / E181 / CASE_TRANSITION_CONTRACT_BOUNDARY_HARDENED / E180 / MOBILE_RELEASE_CANDIDATE_PREPARATION / E179 / SYNC_CONSENT_CANCELLATION_BOUNDARY_HARDENED / E178 / API_INPUT_AND_STAGING_BOUNDARY_HARDENED / E177 / SYNC_PULL_PAGE_SIZE_BOUNDARY_HARDENED. The extracted Kotlin module boundaries and the local release-version regression are now verified alongside the backend engine, sync, API, and database checks; external runtime, provider, managed, staging, and human gates remain open.

The platform lane is optional infrastructure for accounts, cloud sync,
analytics, authoring, recommendations, AI assistance, teacher workflows, and
production operations. It must never make the bundled free mobile case require
an account, network, API, database, or AI provider.

## Current implementation

`api/` is an ASP.NET Core/.NET 10 LTS modular-monolith foundation. It currently
provides:

- dependency-free `GET /health/live`;
- safe `GET /health/ready` with degraded local configuration semantics;
- request-ID propagation with bounded input;
- redacted, versioned HTTP errors;
- a managed Supabase JWT bearer boundary for configured deployments;
- a test-only authentication scheme for local integration tests;
- authenticated `GET /v1/account/me` using only the verified subject claim.
- explicit-confirmation `DELETE /v1/account/me` for server-owned platform data
  deletion preparation; managed Auth deletion remains external.
- versioned sync command/pull contracts and `/v1/sync/commands` plus
  `/v1/sync/pull` routes;
- authenticated `/v1/progress/me`, `/v1/progress/daily`,
  `/v1/recommendations/next`, and
  `/v1/billing/entitlements` read boundaries;
- typed `/v1/authoring/cases` mutation/transition/audit read, teacher cohort aggregate,
  recommendation interaction, and `/v1/ai/assist` boundaries;
- an Npgsql repository boundary with idempotent command handling that fails
  closed when no database is configured;
- ordered Supabase PostgreSQL migrations through the case-version deletion
  exception and billing-event ordering guard, with account-scoped RLS policies
  and an append-only sync change feed;
- a local-only PostgreSQL integration harness that applies migrations `001`-
  `029` to a fresh container and asserts account isolation, sync/projection
  triggers, Auth account-profile provisioning, cohort suppression, lifecycle
  guards, and account deletion;
- a local-only PostgreSQL backup/restore rehearsal that applies the current
  ledger, dumps synthetic sentinel data in custom format, restores into a fresh
  database, and verifies recovery without claiming managed backup behavior;
- a checksum-guarded migration ledger runner at
  `database/migrations/apply-migrations.sh` that serializes ledger creation,
  checksum validation, and per-version application under one PostgreSQL
  advisory transaction lock and safely skips already-applied versions;
- a local worker process harness at
  `worker/integration/run-local-worker-smoke.sh` that reclaims an expired
  projection lease and verifies account/daily rebuilds against fresh
  PostgreSQL;
- consented, typed analytics ingestion for practice funnel, completion,
  premium-action, client-error, and recommendation signals, with client-event
  idempotency, a deterministic projection builder, authenticated progress
  reads, and a worker-owned account projection rebuild boundary;
- published-case reads plus typed content authoring, server membership lookup,
  review reason/audit, and a review/publish state machine that freezes
  published versions;
- deterministic explainable recommendation rules with abstention, stable
  tie-breaks, server-built candidates, and consented idempotent interactions;
- a provider-neutral AI gateway with opt-in, redaction, quota, timeout,
  response-schema boundaries, default-disabled provider behavior, and
  metadata-only audit persistence;
- organization/cohort access policy with aggregate suppression, server-scoped
  summary queries, raw-draft denial, and server-owned membership lifecycle
  operations with audit records;
- a signed, idempotent RevenueCat billing-webhook boundary with official HMAC or
  configured Authorization validation, forward-compatible event handling,
  anonymous-event acknowledgement, and server-owned entitlement persistence;
- bounded API rate limiting and a separately buildable .NET worker host.

E147 records the bounded AI decision: the gateway may support only an optional
feedback explanation, one evidence-scope/limitation reflection question, or a
meaning-preserving language alternative. It must not grade, determine
evaluator truth, rewrite learner drafts as authoritative output, or unlock
premium access. The provider remains disabled until legal/privacy, cost,
managed-secret, mobile, and human gates are authorized.

E148 records the local-only PostgreSQL backup/restore rehearsal. It verifies a
custom-format dump restored into a fresh database with synthetic sentinel data
and the complete migration ledger; managed retention, IAM, point-in-time
recovery, staging, and production rollback remain external gates.

E149 records the server billing guard: only the exact `evidrilo_pro` entitlement
with webhook authentication can make billing configured. Other entitlement
IDs fail closed; dashboard, transaction, and production configuration remain
external gates.

E151 closes the server-side RevenueCat product boundary: active webhook grants
must carry exact `monthly` or `yearly` product IDs, so a signed `lifetime`
event is ignored before it reaches the entitlement store. Focused BillingTests
pass `10/10` and the full API suite passes `141/141`; the provider dashboard and
transaction evidence remain open.

E152 protects the last active organization owner during account deletion.
Migration `027_account_deletion_owner_guard` locks affected organizations and
returns `OWNER_TRANSFER_REQUIRED` before deletion when no other active owner
exists. Fresh PostgreSQL smoke observes the guard and the existing deletion
assertions; API tests remain `144/144` after the latest boundary regressions. Managed database, Auth deletion, and
deployment behavior remain external gates.

E153 hardens the client and request boundaries: mobile entitlement access is
canonical and product-aware, content/sync/analytics reject unverified stored
sessions, account deletion preserves `OWNER_TRANSFER_REQUIRED` for the UI,
chunked webhook input is bounded before deserialization, and caller
cancellation propagates through optional AI. The full local boundary passes
Kotlin `268/268`, Node `77/77`, and the full API suite passes `144/144`; worker
tests pass `5/5`, with shared iOS target compilation and fresh PostgreSQL/worker
smoke. Provider, native runtime,
managed, staging, production, human, and submission gates remain open.

E154 bounds custom-scheme auth callbacks to 8 KiB before parsing or pending
retention. The full local verifier and shared iOS target compilation pass, with
API `144/144`, Node `77/77`, and worker `5/5`; native callback runtime and
provider-backed account verification remain external gates.

E155 requires accepted authoring documents to contain one to 32 meaningful
challenge variants. Focused content/authoring coverage passes `22/22` and the
full API suite passes `145/145`; managed content publication, runtime,
provider, staging, human, and submission gates remain open. See the [E155
content boundary record](../audit/evidence/evidrilo-content-challenge-required-2026-09-14.md).

E156 revalidates stored authoring JSON under the transition row lock before
`approved` or `published`. Focused content/authoring coverage passes `23/23`
and the full API suite passes `146/146`; managed database replay, content
publication, runtime, provider, staging, human, and submission gates remain
open. See the [E156 content transition record](../audit/evidence/evidrilo-stored-content-transition-guard-2026-09-14.md).

E157 makes the Kotlin published-case reader reject an empty challenge-variant
array, aligning the mobile response boundary with the API content lifecycle.
The focused reader regression passes `8/8`; the full Kotlin/JVM suite passes
`269/269`, JVM/Android compilation passes, and shared iOS targets compile.
Managed content publication, provider, device, staging, human, and submission
gates remain open. See the [E157 content reader record](../audit/evidence/evidrilo-mobile-content-challenge-required-2026-09-14.md).

E158 aligns the versioned published-case schema with the same invariant by
requiring at least one challenge variant and protecting it with the contract
test. The full local verifier passes Kotlin `269/269`, Node `78/78`, API
`146/146`, worker `5/5`, and deployment checks. Managed content publication,
provider, device, staging, human, and submission gates remain open. See the
[E158 schema boundary record](../audit/evidence/evidrilo-case-schema-challenge-boundary-2026-09-14.md).

E163 hardens local conclusion-session restoration. Unknown enum values,
malformed escaped lists, and encoded values over 8 KiB fail closed before
parsing. The E163 local verifier passed Kotlin/JVM `271/271`, Node `78/78`,
API `146/146`, worker `5/5`, and deployment checks; external provider,
device, managed, staging, human, and submission gates remain open. See the
[E163 local-session decoder record](../audit/evidence/evidrilo-local-session-decoder-hardening-2026-09-14.md).

E164 hardens the cohort aggregate boundary. Active enrollments are counted
only while the account has an active membership in the cohort's organization.
Migration 028, the `28/28` migration contract, focused API readiness `4/4`,
and fresh PostgreSQL/RLS smoke pass; the tracker remains `78/92` and managed
database, provider, device, staging, human, publication, and submission gates
remain open. See the [E164 cohort boundary record](../audit/evidence/evidrilo-cohort-active-membership-boundary-2026-09-14.md).

E165 hardens the role boundary on that aggregate. Only active memberships with
role `learner` count toward learner totals; non-learner enrollments cannot
inflate the result or bypass suppression. Migration 029, the role contract,
focused API readiness `4/4`, and fresh PostgreSQL/RLS smoke pass. See the [E165
cohort role-boundary record](../audit/evidence/evidrilo-cohort-learner-role-boundary-2026-09-14.md).

E166 closes the membership role-change owner invariant. A grant or role-change
cannot demote the last active organization owner to a non-owner role; the
organization row is locked before the active-owner count is checked. The full
API suite passes `147/147`; managed database, provider, device, staging,
human, publication, and submission gates remain open. See the [E166
last-owner role-change record](../audit/evidence/evidrilo-last-owner-role-change-2026-09-14.md).

E167 hardens the billing webhook identity boundary. A signed event with the
all-zero `app_user_id` is acknowledged as `ignored` before the billing store
is called. The focused billing suite passes `11/11` and the full API suite
passes `148/148`; provider, managed database, device, staging, human,
publication, and submission gates remain open. See the [E167 billing identity
record](../audit/evidence/evidrilo-billing-empty-account-guard-2026-09-14.md).

E168 synchronizes the active status headers in the backend and related current
records after stale E154-era labels were found during audit. The snapshot
contract passes `23/23`; implementation counts and external gates are
unchanged. See the [E168 snapshot synchronization record](../audit/evidence/evidrilo-current-status-snapshot-sync-2026-09-14.md).

E169 hardens the auth provider boundary so non-string confirmation metadata
cannot mark a local session as email-verified. Focused auth coverage passes
`13/13`, and Kotlin/JVM passes `271/271` with JVM, Android, and shared-iOS
compilation. Provider, native runtime, managed, staging, human, publication,
and submission gates remain open. See the [E169 auth provider type-boundary
record](../audit/evidence/evidrilo-auth-provider-type-boundary-2026-09-14.md).

E170 adds explicit regression coverage for learner, teacher, and reviewer
membership role-assignment attempts. The existing authorization policy denies
those actors; focused access-policy coverage passes `9/9` and the full API
suite passes `151/151` without changing authorization behavior. Provider,
native runtime, managed, staging, human, publication, and submission gates
remain open. See the [E170 membership role-assignment record](../audit/evidence/evidrilo-membership-role-assignment-coverage-2026-09-14.md).

E171 records the repository-owned offline-audio implementation and its local
verification boundary. Five bounded interaction effects, offline-only native
TTS adapters, accessible controls, lifecycle handling, and manifest validation
are present; focused audio tests pass `28/28`. Reviewed natural narration,
device playback, accessibility-service, and human pronunciation evidence remain
open. See the [E171 audio record](../audit/evidence/evidrilo-offline-audio-implementation-2026-09-14.md).

E172 hardens local restoration boundaries. Session phases are bound to their
supported main/challenge case IDs, and secure-session records reject unsafe
account identifiers before billing, sync, or request use. Focused coverage
passes `5/5` for each boundary; the full local boundary passes Kotlin/JVM
`301/301`, Node `87/87`, API `151/151`, worker `5/5`, Android compilation,
shared iOS target compilation, and deployment checks. Provider, native runtime,
managed, staging, human, publication, and submission gates remain open. See
the [E172 local-session boundary record](../audit/evidence/evidrilo-local-session-boundary-2026-09-14.md).

E173 hardens asynchronous state boundaries. Stale sync responses cannot restore
cleared or newer queue state, visible results are tied to account/consent
state, account restoration preserves a persisted queue, and the approved
monthly/yearly billing catalog is retained. Focused coverage passes `18/18`;
the full local boundary passes Kotlin/JVM `306/306`, Node `87/87`, API
`151/151`, worker `5/5`, Android compilation, shared iOS compilation, and
deployment checks. Provider, runtime, managed, staging, human, publication,
and submission gates remain open. See the [E173 record](../audit/evidence/evidrilo-async-state-boundaries-2026-09-14.md).

E174 closes fail-closed response, refresh, and presentation gaps. Sync rejects
non-monotonic or skipping cursors and unrelated push results; auth refresh
clears a stale secure session when email confirmation is withdrawn; and direct
billing presentations enforce the approved monthly/yearly allowlist. Focused
sync, auth, and billing suites pass `18/18`, `14/14`, and `9/9`; the latest full
local boundary passes Kotlin/JVM `311/311`, Node `87/87`, API `151/151`, worker
`5/5`, Android and shared-iOS target compilation, deployment checks, and audio
asset checks. Runtime, provider, managed, staging, human, publication, and
submission gates remain open. See the [E174 record](../audit/evidence/evidrilo-fail-closed-response-and-refresh-boundaries-2026-09-14.md).

E175 tightens the sync pull boundary left open by that response validation:
returned changes must be strictly newer than the requested cursor and bounded by
`nextCursor`. The focused regression and full local boundary are green at
`29/29` sync tests, Kotlin/JVM `312/312`, Node `87/87`, API `151/151`, and
worker `5/5`, with Android/shared-iOS compilation, deployment, and audio checks
passing. Runtime, provider, managed, staging, human, publication, and
submission gates remain open. See the [E175 record](../audit/evidence/evidrilo-sync-cursor-lower-bound-2026-09-14.md).

E176 hardens request lifecycle and input boundaries. Mobile HTTP cancellation
aborts platform work and ignores late callbacks; malformed JSON uses the stable
API error envelope; duplicate sync command IDs are rejected before storage; and
cancelled recommendation/auth flows restore retryable state. The local boundary
passes Kotlin/JVM `317/317`, Node `87/87`, API `153/153`, worker `5/5`,
Android/shared-iOS compilation, deployment, and audio checks. Runtime, provider,
managed, staging, human, publication, and submission gates remain open. See the
[E176 record](../audit/evidence/evidrilo-request-lifecycle-and-input-boundaries-2026-09-14.md).

E177 hardens sync pull pagination by validating each response against the
requested page size. Smaller pages with `hasMore=true` are accepted and pages
larger than the request are rejected. The local boundary passes Kotlin/JVM
`319/319`, Node `87/87`, API `153/153`, worker `5/5`, Android/shared-iOS
compilation, deployment, and audio checks. Runtime, provider, managed,
staging, human, publication, and submission gates remain open. See the
[E177 record](../audit/evidence/evidrilo-sync-page-size-boundary-2026-09-14.md).

E178 hardens API full-match validation for identifiers, digests, actions, reason
codes, case-version IDs, and request IDs, and accepts the conventional
`Staging` host environment as canonical `staging`. The API suite passes
`158/158`; provider, managed staging, runtime, human, publication, and
submission gates remain open. See the [E178 record](../audit/evidence/evidrilo-api-input-and-staging-boundary-2026-09-14.md).

E179 hardens consent-bound sync orchestration. Failed or invalidated pulls do
not start a push, active sync jobs are cancelled on account/consent changes,
enablement has one state-driven launch owner, and failed queue clears preserve
the visible pending count and error message. Kotlin/JVM `321/321` and
Android/shared-iOS compilation pass; provider, managed, runtime, human,
publication, and submission gates remain open. See the [E179 record](../audit/evidence/evidrilo-sync-consent-cancellation-boundary-2026-09-14.md).

E180 prepares Android and iOS release boundaries. Android uses R8/resource
shrinking, explicit version inputs, manifest security defaults, and a
fail-closed upload-signing task; iOS uses an owner-supplied distribution team
through an ignored Release xcconfig template. Kotlin/JVM `324/324`, Node
`93/93`, API `161/161`, worker `5/5`, Android release packaging, shared iOS
target compilation, and release checks pass. Device runtime, archive/export,
signing, store, provider, managed, staging, human, and submission evidence
remain open. See the [E180 record](../audit/evidence/evidrilo-mobile-release-candidate-preparation-2026-09-14.md).

E181 hardens the case-authoring transition boundary. The endpoint now rejects
an unknown contract version, schema, or target state before invoking the store;
focused transition coverage passes `2/2` and the full local verifier passes
Kotlin/Android build, Node `93/93`, API `162/162`, worker `5/5`, deployment, and
asset checks. Runtime, provider, managed, staging, human, publication, and
submission evidence remain open. See the [E181 record](../audit/evidence/evidrilo-case-transition-contract-boundary-2026-09-15.md).

E183 hardens the shared Compose choice control. Radio-style conclusion choices
use `Modifier.selectable`, multi-select evidence facts use `Modifier.toggleable`,
and the card merges its label and state for assistive technology. Direct
contracts pass `25/25`; the full local verifier passes Node `95/95`, API
`163/163`, worker `5/5`, the current Kotlin/Android build boundary, Android
release packaging, deployment, and asset checks. This is repository and build
evidence only; TalkBack, VoiceOver, device runtime, human, provider, managed,
staging, publication, and submission gates remain open. See the [E183 record](../audit/evidence/evidrilo-choice-accessibility-semantics-2026-09-15.md).

E186 hardens the engine and sync identity boundary. Local case IDs remain stable
for offline practice while the bundled case maps to canonical server version
`M0_T2:1`; unknown mappings fail closed. Invalid conclusion cases and invalid
or oversized local snapshots are rejected before evaluator/storage success is
reported. New API sync commands require a published case version, idempotent
replays remain readable, and migration 030 enforces the same rule for direct
database writes. The latest local boundary passes Kotlin/JVM `332/332`, Node
`96/96`, API `172/172`, worker `5/5`, fresh PostgreSQL/RLS smoke, and worker
process smoke. This does not prove device runtime, managed deployment,
provider, accessibility service, human, or submission evidence.

E182 aligns the server sync-pull cursor boundary with the mobile parser. Cursors
outside `0..1_000_000_000_000_000` are rejected before storage; the focused
regression passes `1/1` and the full API suite passes `163/163`. Runtime,
provider, managed, staging, human, publication, and submission evidence remain
open. See the [E182 record](../audit/evidence/evidrilo-sync-cursor-contract-boundary-2026-09-15.md).

E150 synchronizes the RevenueCat Test Store runbook, backend execution
register, and current status page with the E149 canonical entitlement boundary,
Node `77/77`, and API `140/140` result. The local contract passes `22/22`;
provider, runtime, managed, human, and submission evidence remain separate
gates.

No production Supabase project, database, email provider, or account data is
required by the local tests. The publishable key is configuration, not a
replacement for server-side token validation. Service-role keys and database
passwords are never part of this repository.

The mobile lane now has an optional account boundary with explicit session
states and platform secure-storage adapters: Android Keystore-backed encrypted
storage and iOS Keychain storage. The repository-owned adapter supports
email/password sign-in and creation, non-enumerating password-reset requests,
Google OAuth authorization-code PKCE, callback validation, refresh rotation,
local sign-out, and an explicit account-deletion preparation call. The free
evaluator and local history do not instantiate or require a signed-in account.
Empty configuration fails closed, and a real provider login is not claimed
until the managed Supabase/Google/email/runtime gates are verified.

Mobile account configuration uses local-only values named
`SUPABASE_URL`, `SUPABASE_PUBLISHABLE_KEY`, `SUPABASE_AUTH_REDIRECT_URL`, and
`EVIDRILO_API_BASE_URL`. Android receives them through Gradle properties;
iOS receives them through an ignored xcconfig. The registered callback is
`evidrilo://auth/callback`. Passwords never pass through the Evidrilo API, and
service-role or secret-shaped keys are rejected from the mobile client.

The P3-P8 modules now include local API/worker implementations. API and worker
compile/test suites pass with the repository-local .NET 10 SDK. E145 records
the public-package exporter preflight and its fail-closed Git metadata limit;
E144 records
the explainable recommendation candidate validation; E143 records the
fail-closed recommendation projection validation; E142 records the
lifecycle audit read boundary; E139 records the canonical published content
payload/reader; E138 records the
platform-scoped RevenueCat managed UI adapter; E137 records
the synchronized backend execution register; E136 records the
provider-observation documentation boundary; E135 records the
migration-aware database readiness boundary; E134 records the RevenueCat
runbook catalog alignment; E133 records the production CORS
fail-closed boundary; E132 records
the migration-runner concurrency hardening; E131 records the generated-output
publication boundary; E130 records the trigger privilege hardening; E129
records the account-bound asynchronous
billing callback guard; E128 records the Auth
account-profile provisioning boundary; E125 records the latest
mobile host/network verification; E124 records the authenticated transport
verification; E123 records the billing presentation boundary and E122 records
the API configuration correction. The
latest local API/worker harness has API `172/172`, Node `96/96`, worker `5/5`,
deployment checks, and a fresh PostgreSQL/worker smoke with the Auth
compatibility shim and checksum ledger through migration 030. Non-root image
builds and Compose
startup remain separately documented preparation checks. Managed
Supabase grants, managed projection replay, provider adapter, invitation
delivery, and deployment remain staging/production gates.

## Local commands

From the repository root, with the .NET 10 SDK available:

```bash
dotnet restore platform/api.Tests/Evidrilo.Api.Tests.csproj
dotnet test platform/api.Tests/Evidrilo.Api.Tests.csproj
dotnet build platform/api.Tests/Evidrilo.Api.Tests.csproj --configuration Release
node --test contracts/contracts.test.mjs
node --test platform/database/migrations/migrations.test.mjs
bash platform/database/integration/run-local-postgres-smoke.sh
dotnet build platform/worker/Evidrilo.Worker.csproj --configuration Release
dotnet test platform/worker.Tests/Evidrilo.Worker.Tests.csproj --configuration Release
bash platform/worker/integration/run-local-worker-smoke.sh
```

Run the API locally with synthetic/degraded configuration:

```bash
dotnet run --project platform/api/Evidrilo.Api.csproj --environment Development
```

The API reports degraded readiness when Supabase configuration is absent. Do
not place a real URL, key, token, password, or production data in `.env`, test
fixtures, logs, screenshots, or source control.

## Planned follow-up boundaries

Account linking, managed Auth account deletion, managed database/RLS
integration, managed projection execution, AI provider configuration,
invitation delivery, billing provider delivery, and deployment operations each
require their own external gate. Fresh local PostgreSQL migration/RLS and
worker process boundaries are covered by the integration harnesses above.
Billing webhook processing also remains disabled until a managed secret and
database are configured. The local API deliberately returns safe unavailable
responses when the database is not configured; it never returns fake sync,
  cloud, authoring, projection, audit, or entitlement success.

Container preparation for the API and worker is in [`../infra/`](../infra/).
Run `bash scripts/verification/check-deployment.sh .` and
`docker compose -f infra/environments/local/docker-compose.yml config --quiet` before a
local stack run. The local Compose database is development-only; managed
secrets, TLS, backups, alerts, rollback, and load verification remain external
deployment gates.
