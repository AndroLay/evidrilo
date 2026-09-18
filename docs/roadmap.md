# Evidrilo Delivery Roadmap

Latest increment: E191 / TARGET_SURFACES_AND_CASE_CATALOGUE_INTEGRATED / E190 / EVIDENCE_GRAPH_ANCHOR_CLOSURE_HARDENED / E189 / KOTLIN_MODULE_BOUNDARIES_AND_VERIFICATION_ALIGNED / E188 / RELEASE_VERSION_SOURCE_ALIGNED / E187 / REPOSITORY_ARCHITECTURE_MIGRATION_VERIFIED / E186 / BACKEND_ENGINE_SYNC_BOUNDARY_HARDENED / E185 / REVENUECAT_OFFERING_MIGRATION_OBSERVED / E183 / NATIVE_CHOICE_ACCESSIBILITY_SEMANTICS_HARDENED / E182 / SYNC_CURSOR_CONTRACT_BOUNDARY_ALIGNED / E181 / CASE_TRANSITION_CONTRACT_BOUNDARY_HARDENED / E180 / MOBILE_RELEASE_CANDIDATE_PREPARATION / E179 / SYNC_CONSENT_CANCELLATION_BOUNDARY_HARDENED / E178 / API_INPUT_AND_STAGING_BOUNDARY_HARDENED / E177 / SYNC_PULL_PAGE_SIZE_BOUNDARY_HARDENED / E176 / REQUEST_LIFECYCLE_AND_INPUT_BOUNDARIES_HARDENED / E175 / SYNC_PULL_CURSOR_LOWER_BOUND_GUARDED / E174 / FAIL_CLOSED_RESPONSE_REFRESH_BOUNDARIES / E173 / ASYNC_STATE_BOUNDARIES_HARDENED / E172 / LOCAL_SESSION_BOUNDARIES_HARDENED / E171 / OFFLINE_AUDIO_REPOSITORY_IMPLEMENTATION / E170 / MEMBERSHIP_ROLE_ASSIGNMENT_POLICY_COVERAGE / E169 / AUTH_PROVIDER_CONFIRMATION_TYPE_BOUNDED / E168 / CURRENT_STATUS_SNAPSHOTS_SYNCHRONIZED.

Status: CURRENT / E191 / TARGET_SURFACES_AND_CASE_CATALOGUE_INTEGRATED / E190 / EVIDENCE_GRAPH_ANCHOR_CLOSURE_HARDENED / E189 / KOTLIN_MODULE_BOUNDARIES_AND_VERIFICATION_ALIGNED / E188 / RELEASE_VERSION_SOURCE_ALIGNED / E187 / REPOSITORY_ARCHITECTURE_MIGRATION_VERIFIED / E186 / BACKEND_ENGINE_SYNC_BOUNDARY_HARDENED / E185 / REVENUECAT_OFFERING_MIGRATION_OBSERVED / E183 / NATIVE_CHOICE_ACCESSIBILITY_SEMANTICS_HARDENED / E182 / SYNC_CURSOR_CONTRACT_BOUNDARY_ALIGNED / E181 / CASE_TRANSITION_CONTRACT_BOUNDARY_HARDENED / E180 / MOBILE_RELEASE_CANDIDATE_PREPARATION / E179 / SYNC_CONSENT_CANCELLATION_BOUNDARY_HARDENED / E178 / API_INPUT_AND_STAGING_BOUNDARY_HARDENED / E177 / SYNC_PULL_PAGE_SIZE_BOUNDARY_HARDENED / E176 / REQUEST_LIFECYCLE_AND_INPUT_BOUNDARIES_HARDENED / E175 / SYNC_PULL_CURSOR_LOWER_BOUND_GUARDED / E174 / FAIL_CLOSED_RESPONSE_REFRESH_BOUNDARIES / E173 / ASYNC_STATE_BOUNDARIES_HARDENED / E172 / LOCAL_SESSION_BOUNDARIES_HARDENED / E171 / OFFLINE_AUDIO_REPOSITORY_IMPLEMENTATION / E170 / MEMBERSHIP_ROLE_ASSIGNMENT_POLICY_COVERAGE / E169 / AUTH_PROVIDER_CONFIRMATION_TYPE_BOUNDED / E168 / CURRENT_STATUS_SNAPSHOTS_SYNCHRONIZED / RUNTIME_GATES_OPEN / EXTERNAL_GATES_OPEN / NOT_SUBMISSION_READY

Legacy tracker status retained below for provenance:
Status: CURRENT / E172 / LOCAL_SESSION_BOUNDARIES_HARDENED / E171 / OFFLINE_AUDIO_REPOSITORY_IMPLEMENTATION / E170 / MEMBERSHIP_ROLE_ASSIGNMENT_POLICY_COVERAGE / E169 / AUTH_PROVIDER_CONFIRMATION_TYPE_BOUNDED / E168 / CURRENT_STATUS_SNAPSHOTS_SYNCHRONIZED / E167 / BILLING_EMPTY_ACCOUNT_GUARDED / E166 / LAST_OWNER_ROLE_CHANGE_GUARDED / E165 / COHORT_LEARNER_ROLE_BOUNDARY_HARDENED / E164 / COHORT_MEMBERSHIP_BOUNDARY_HARDENED / E154 / AUTH_CALLBACK_INPUT_BOUNDED / E153 / CLIENT_BOUNDARIES_HARDENED / WEBHOOK_BODY_BOUNDED / AI_CANCELLATION_PROPAGATED / VERIFIED_SESSION_GATES_ALIGNED / E152 / ACCOUNT_DELETION_OWNER_GUARDED / E151 / REVENUECAT_PRODUCT_ALLOWLIST_GUARDED / E150 / RUNBOOK_STATUS_SYNCHRONIZED / E149 / CANONICAL_ENTITLEMENT_GUARDED / E148 / LOCAL_BACKUP_RESTORE_SMOKE_VERIFIED / E147 / AI_SCOPE_DECISION_LOCKED / E146 / RECOMMENDATION_MOBILE_INTEGRATED / PUBLIC_PACKAGE_EXPORT_VERIFIED / GIT_WORKSPACE_CHECK_BLOCKED / REPOSITORY_OWNED_CLOSURE_VERIFIED / CASE_LIFECYCLE_AUDIT_VERIFIED / CASE_LIFECYCLE_AUDIT_READ_VERIFIED / RECOMMENDATION_PROJECTION_VALIDATED / BACKEND_REGISTER_SYNCHRONIZED / CANONICAL_CONTENT_READER_VERIFIED / CLAIM_AUDIT_CURRENT / DATABASE_READINESS_LEDGER_GUARDED / RUNBOOK_OBSERVATION_CLAIMS_GUARDED / REVENUECAT_UI_BOUNDARY_COMPILED / REVENUECAT_RUNBOOK_ALIGNED / PRODUCTION_CORS_EXPLICIT / GENERATED_OUTPUT_BOUNDARY_HARDENED / MIGRATION_RUNNER_CONCURRENCY_GUARDED / TRIGGER_FUNCTION_PRIVILEGES_HARDENED / BILLING_CALLBACK_IDENTITY_GUARDED / BILLING_CONFIG_STATUS_TRUTHFUL / BILLING_UNKNOWN_STATE_ALLOWLISTED / AUTH_REDIRECTS_REJECTED / ANDROID_NETWORK_PERMISSION_DECLARED / ACCOUNT_PROFILE_PROVISIONED / ACCOUNT_DELETION_ACCESS_GUARDED / ACCOUNT_DELETION_LEDGER_FALLBACK / ACCOUNT_AUTH_REPO_IMPLEMENTED / SHARED_UI_IMPLEMENTED / MOBILE_METADATA_SYNC_IMPLEMENTED / SERVER_OWNED_WRITE_BOUNDARIES / CONTENT_READER_VERIFIED / DESKTOP_UI_OBSERVED / MOBILE_SHARED_CHECKS_PASS / DOTNET_LOCAL_CHECKS_PASS / REVENUECAT_APP_ALLOWLIST_VERIFIED / DEPLOYMENT_PREPARATION_PRESENT / PUBLIC_PACKAGE_GUARD_PRESENT / ACCESSIBILITY_SEMANTICS_HARDENED / M2_ANDROID_SEMANTICS_PARTIAL / BACKEND_POSTGRES_LOCAL_VERIFIED / MIGRATION_LEDGER_VERIFIED / ANALYTICS_MIGRATION_023 / ACCOUNT_PROFILE_MIGRATION_024 / TRIGGER_PRIVILEGE_MIGRATION_025 / CASE_LIFECYCLE_MIGRATION_026 / ACCOUNT_DELETION_OWNER_GUARD_MIGRATION_027 / WORKER_LOCAL_VERIFIED / EXTERNAL_GATES_OPEN / NOT_SUBMISSION_READY

Current authority: [Evidrilo Source of Truth](../internal/research/next-gen/SOURCE_OF_TRUTH.md).
This document only orders implementation and verification work; it does not
create a second product scope or evidence status.

The E191 increment passes the focused Kotlin/Android target tasks, full API
`187/187`, and the versioned contract suite. This is repository-only evidence;
device runtime, provider, managed, human, and submission gates remain separate.

The E192 semantic-closure increment extends that bridge with an Evidence Lens, a
multi-check Conflict Detail projection, honest draft-completeness versus
evidence-support metrics, active-case selection for the evidence-change state,
and Evidence Delta propagation for assessment, gap, claim-boundary, and action
state. These changes are implemented in the committed repository but remain
**pending focused Kotlin verification** until the Gradle environment is available;
they do not close
the device, provider, managed, human, or submission gates.

## Outcome

Deliver one reproducible Android/iOS Evidrilo experience for Shipaton Next Gen:

1. supplied case and structured evidence;
2. learner-authored bounded conclusion;
3. transparent fact-anchored feedback;
4. exactly one revision and before/after comparison;
5. fresh evidence-change challenge and local comparison history; and
6. a meaningful two-case premium pack controlled by RevenueCat.

The free core must remain usable without an account, backend, AI, network after
bundled content is available, or billing key.

The optional repository account lane supports email/password, password
recovery, Google authorization-code PKCE, secure session refresh/sign-out, and
account-deletion preparation. It is additive infrastructure and never becomes
a prerequisite for the free core.

## Gate model

Each milestone moves through:

NOT_STARTED → IN_PROGRESS → REVIEW_REQUIRED → VERIFIED → LOCKED

A gate can return to REVISE or STOP. A plan, compile, mock, forecast, or
synthetic fixture cannot replace the runtime, billing, human, or submission
evidence required by that gate. Work on one unmet milestone at a time.

## Milestone map

| Goal | Boundary | Exit evidence | State |
| --- | --- | --- | --- |
| G0 | Repository and governance | Private development repo, explicit public allowlist, no internal material | Verified |
| G1 | M0 product contract | Approved case, evaluator, fairness, privacy, and access boundaries | Verified |
| G2 | M1 deterministic domain | Pure Kotlin tests and independent fixtures | Verified |
| G3 | M2 free core | Android/iOS runtime, persistence, challenge/history, accessibility, offline checks | Repository verified; runtime/accessibility gates open |
| G4 | M3 RevenueCat | Test Store purchase, entitlement, restore, failure, and free fallback | App allowlist verified; dashboard/transaction gates open |
| G5 | M4 human validation | Two reviewer preflights, then at least six consented adult sessions | 0/2 reviewers; 0 participants |
| G6 | M5 release readiness | Clean clone, final build/assets, accessibility, and claim audit | In progress; repository preparation present, release gates open |
| G7 | M6 submission | Eligibility, public source, English copy, video, and owner approval | Not started |
| G8 | Archive | Frozen submission record and artifact hashes | Not started |

## G3 — Free core completion

The implementation must demonstrate this single flow:

Welcome → case facts → evidence/relationship selection → claim, scope,
limitation, and next action → fact-anchored feedback → one revision →
before/after summary → evidence-change notice → fresh challenge draft →
changed feedback → comparison summary/history.

Close G3 only when all of these are observed on the claimed targets:

- free case completes without account or billing;
- incomplete and unsupported input show the correct bounded status;
- the initial draft remains immutable and a second revision is rejected;
- the challenge removes OBS-COLD-01 and never carries it into a fresh draft;
- active-case facts/literals drive evaluation and stale references abstain;
- local persistence survives the approved relaunch path;
- one comparison-history entry is visible and explicitly clearable;
- text scaling, semantics, focus, contrast, tap targets, and recovery are
  reviewed on each available target;
- offline behavior is checked after bundled content is available.

Current evidence: M1 is verified; the base Android flow, persistence, and the
complete challenge/history path are observed on the local Android AVD,
including offline completion and relaunch restoration on the unchanged
behavioral boundary. The latest APK exposes checked choice semantics and the
130% text-scale hierarchy remains scrollable. E112 implements the
owner-approved V8 Home/Guide/Settings direction and the V7
practice/history/packs/about surfaces in shared Compose UI; the desktop
walkthrough observed those routes, practice draft preservation, and the
  premium-unavailable state. The 15 September Android probe found ADB and the
  local AVD, but the emulator framework did not expose a usable `package`
  service and APK installation failed before app launch; see the [E184 probe
  record](../audit/evidence/evidrilo-android-runtime-probe-2026-09-15.md).
  Full assistive-technology, contrast, focus, switch-access, and iOS
  host/runtime evidence remain open.

E117 closes the repository-owned preparation boundary: non-root API/worker
images, local Compose health/auth-shim/migration ordering, deployment checks,
an allowlisted public-package exporter, submission asset validation, and
disclosure semantics are implemented and tested. The final local harness
passes `186/186` JVM tests, `52/52` Node checks, API `108/108`, worker `5/5`,
Android debug assembly, and both shared iOS Kotlin/Native targets. Docker
builds and a local Compose smoke pass; managed deployment, device/runtime,
billing, human, and submission gates remain open.

E118 completes the repository-owned all-area foundation: persisted first-run
onboarding and unlimited free replay coverage, app-side monthly/yearly billing
eligibility, consent-gated metadata sync, exact cursor/version checks,
consented mobile analytics, support states, and content/sync/support/recovery
runbooks. The fresh verification is recorded in the [E118 all-area closure
record](../audit/evidence/evidrilo-all-areas-closure-2026-09-13.md); provider,
device, managed deployment, human, publication, and submission gates remain
open.

E119 closes the analytics product-signal gap at the repository boundary. It
adds typed practice-start, paywall-view, premium purchase/restore, and bounded
client-error events, keeps product IDs limited to monthly/yearly, and extends
the append-only PostgreSQL constraint through migration 021. Renewal, refund,
and reversal remain server-owned RevenueCat webhook signals. Evidence is in
the [E119 analytics closure record](../audit/evidence/evidrilo-analytics-funnel-closure-2026-09-13.md).

E120 closes the direct client-write bypass. Migration 022 removes client INSERT
policies for analytics and sync, the fresh RLS smoke asserts rejection, and the
analytics store normalizes nullable properties so retries from older app
versions remain idempotent. The current verification passes `221/221` JVM,
`56/56` Node, API `110/110`, worker `5/5`, shared iOS target compilation, and
PostgreSQL/RLS smoke through migration 022. See the
[E120 security boundary record](../audit/evidence/evidrilo-server-owned-write-boundary-2026-09-13.md).

E121 removes the remaining direct client INSERT path for recommendation
interactions through migration 023. The fresh RLS smoke verifies rejection and
the complete local verification passes `221/221` JVM, `57/57` Node, API
`110/110`, worker `5/5`, shared iOS target compilation, and PostgreSQL/RLS smoke
through migration 023. See the
[E121 recommendation boundary record](../audit/evidence/evidrilo-server-owned-recommendation-boundary-2026-09-13.md).

E122 fixes the billing configuration truth boundary: `BillingConfigured` now
requires webhook authentication plus the configured `evidrilo_pro` entitlement,
matching the webhook service's actual prerequisites. The full API suite now
passes `111/111`. See the
[E122 configuration record](../audit/evidence/evidrilo-billing-config-readiness-2026-09-13.md).

E123 closes the billing presentation-policy escape in the unknown transaction
fallback. The shared UI now applies the monthly/yearly allowlist before it
displays or selects an offer, even while provider state is unresolved; the
state remains non-purchasable until reconciliation. Verification passes
`222/222` JVM, `57/57` Node, API `111/111`, worker `5/5`, shared iOS target
compilation, fresh PostgreSQL/worker smoke, deployment checks, and public
export. See the [E123 offer allowlist record](../audit/evidence/evidrilo-billing-offer-allowlist-2026-09-13.md).

E124 closes automatic redirects on authenticated Android/JVM/iOS transports.
E125 closes the missing Android `INTERNET` permission and adds a regression
check for the Android/iOS auth callback host wiring. E126 closes the
post-deletion access gap with a configured API tombstone guard while retaining
idempotent deletion retry. E127 makes the completed deletion ledger
authoritative for legacy accounts without a profile row. The current local
boundary passes `223/223` JVM, `60/60` Node, API `120/120`, worker `5/5`,
shared iOS target compilation, Android debug assembly, fresh PostgreSQL/worker
smoke, deployment checks, and public export. See the [E124](../audit/evidence/evidrilo-auth-redirect-safety-2026-09-13.md),
[E125](../audit/evidence/evidrilo-android-network-permission-2026-09-13.md),
[E126](../audit/evidence/evidrilo-account-deletion-access-2026-09-13.md), and
[E127](../audit/evidence/evidrilo-account-deletion-ledger-fallback-2026-09-13.md)
records for boundary-specific evidence.

E128 provisions `account_profiles` from new `auth.users` rows with an
idempotent, fixed-search-path server-owned trigger in migration 024, while
retaining E127's legacy deletion-ledger fallback. The current local boundary
passes `223/223` JVM, `61/61` Node, API `120/120`, worker `5/5`, shared iOS
target compilation, Android debug assembly, fresh PostgreSQL/worker smoke,
deployment checks, and public export. Managed Supabase trigger behavior,
provider, device, deployment, human, and publication gates remain open. See
the [E128 record](../audit/evidence/evidrilo-account-profile-provisioning-2026-09-13.md).

E129 binds asynchronous RevenueCat identity, refresh/offer, purchase, and
restore callbacks to both their request generation and initiating account ID.
The focused billing suite passes `9/9`; the fresh local boundary passes
`226/226` JVM, `61/61` Node, API `120/120`, worker `5/5`, shared iOS target
compilation, Android debug assembly, deployment checks, and public export.
Provider transactions and native account-switch runtime remain open. See the
[E129 record](../audit/evidence/evidrilo-billing-callback-identity-2026-09-13.md).

E130 closes the public-execution privilege gap for the two security-definer
trigger functions used by sync and analytics projection. Migration 025 revokes
their `PUBLIC` privileges while the fresh PostgreSQL smoke confirms triggers,
RLS, lifecycle, deletion, and provisioning still work through the checksum
ledger. Managed role ACLs and deployment replay remain open. See the
[E130 record](../audit/evidence/evidrilo-trigger-function-privileges-2026-09-13.md).

E131 closes the generated-output publication boundary by excluding root `.tmp`
compiler artifacts from Git staging, Docker contexts, public export, and public
candidate checks. E132 closes the migration-runner race by taking the advisory
lock before ledger creation and keeping checksum validation and version
application in the same transaction. See the [E131 record](../audit/evidence/evidrilo-generated-output-boundary-2026-09-13.md)
and [E132 record](../audit/evidence/evidrilo-migration-runner-concurrency-2026-09-13.md).

E133 makes production CORS fail closed when the operator has not supplied an
explicit origin list, while preserving the localhost default for development
and test. The focused configuration suite and full API suite pass. Staging
origin, proxy, TLS, and browser verification remain open. See the
[E133 record](../audit/evidence/evidrilo-production-cors-boundary-2026-09-13.md).

E134 aligns the RevenueCat Test Store runbook with the approved monthly/yearly-
only catalog and adds a catalog inspection before transaction testing. The
dashboard state and Test Store matrix remain owner gates. See the [E134 record](../audit/evidence/evidrilo-revenuecat-runbook-catalog-boundary-2026-09-13.md).

E135 makes API database readiness migration-aware: the API reports the database
as ready only when the migration ledger exists and includes
`025_trigger_function_privileges`. Missing or older schemas remain degraded or
missing, preventing a false-ready deployment. API, PostgreSQL, and temporary
Compose health checks pass; managed deployment evidence remains open. See the
[E135 record](../audit/evidence/evidrilo-database-readiness-ledger-2026-09-13.md).

E136 removes provider-observation language that overclaimed RevenueCat dashboard
configuration. The runbook now distinguishes the approved intended catalog
from owner-observed dashboard and transaction evidence; the focused public
contract boundary passes `14/14`. See the [E136 record](../audit/evidence/evidrilo-runbook-observation-boundary-2026-09-13.md).

E137 synchronizes the active backend execution register with the current local
verification boundary and adds a contract against stale E110 status/counts.
The focused contract passes `15/15` and the full Node boundary passes `69/69`;
external database, provider, runtime, and deployment gates remain unchanged.
See the [E137 record](../audit/evidence/evidrilo-backend-register-synchronization-2026-09-13.md).

E138 adds platform-scoped RevenueCat managed Paywall and Customer Center
adapters, a key/catalog/platform availability guard, and a JVM fallback. The
common suite passes `229/229`, the full Node boundary passes `70/70`, and
Android/shared-iOS compilation passes. Provider dashboard, transaction, and
real-device UI evidence remain open. See the [E138 record](../audit/evidence/evidrilo-revenuecat-managed-ui-2026-09-13.md).

E139 carries the canonical objective, observations, limitations, feedback
rules, and non-empty challenge variants through the published API contract and
mobile reader. Local PostgreSQL seed/read, API `130/130`, Kotlin `231/231`,
Node `71/71`, and shared-iOS compilation pass. Managed content operation and
editorial ownership remain open. See the [E139 record](../audit/evidence/evidrilo-canonical-content-reader-2026-09-13.md).

E140 removes an unobserved RevenueCat dashboard assertion from the architecture
note and protects the wording with a contract. The full Node boundary passes
`72/72`; provider dashboard and transaction evidence remain unobserved. See the
[E140 record](../audit/evidence/evidrilo-revenuecat-documentation-claim-boundary-2026-09-13.md).

E141 closes the content lifecycle audit gap. Migration `026_case_lifecycle_audit`
records actor-bound append-only creation and successful state transitions,
rejects audit mutation/deletion, and anonymizes deleted actors without removing
history. Migration checks pass `27/27`, API `130/130` passes, and fresh
PostgreSQL smoke covers valid/failed transitions, RLS, append-only enforcement,
and deletion retention. Pre-migration history, managed replay, admin UI, and
editorial operation remain open. See the [E141 record](../audit/evidence/evidrilo-case-lifecycle-audit-2026-09-13.md).

E142 exposes a closed, bounded lifecycle-audit read endpoint for verified
authors, reviewers, maintainers, and owners in the case organization. It keeps
deletion-anonymized fields explicit and caps responses at 128 events; API
`137/137`, Node `74/74`, and migration `27/27` checks pass. Managed replay,
admin UI, and editorial operation remain open. See the [E142 record](../audit/evidence/evidrilo-case-lifecycle-audit-read-2026-09-13.md).

E143 makes recommendation projection selection fail closed for negative or
inconsistent counters and uses overflow-safe arithmetic. The focused
recommendation suite passes `12/12` and the full API suite passes `138/138`;
mobile recommendation integration remains pending explicit design approval.
See the [E143 record](../audit/evidence/evidrilo-recommendation-projection-validation-2026-09-13.md).

E144 requires a non-empty skill identifier on every selected recommendation
candidate, preserving explainability. The focused recommendation suite passes
`13/13` and the full API suite passes `139/139`; managed content and mobile
integration remain external or pending. See the [E144 record](../audit/evidence/evidrilo-recommendation-candidate-validation-2026-09-13.md).

E145 verifies the complete local verifier and public-package exporter. The
current run passes Gradle, Node `74/74`, API `139/139`, worker `5/5`,
deployment, and the exported candidate checks, but the current workspace Git
checker remains blocked by unusable Git metadata. Push, remote visibility,
final assets, and submission remain open. See the
[E145 record](../audit/evidence/evidrilo-public-package-preflight-2026-09-13.md).

E146 integrates the explainable recommendation into the shared mobile Home
flow with strict consent/auth/session gating, an exact `M0_T2:1` local registry,
safe fixed copy, typed analytics, and additive local launch behavior. Final
review found and fixed the server idempotency collision by using a distinct
UUID for each logical `shown`, `accepted`, or `dismissed` event while reusing
that UUID across retries. Focused recommendation tests pass `28/28`, the full
Kotlin/JVM suite passes `261/261`, and the available shared targets compile.
Native runtime, provider, managed deployment/content, human, and submission
gates remain open. See the [E146 record](../audit/evidence/evidrilo-recommendation-mobile-integration-2026-09-13.md).

E147 locks the optional AI scope to non-grading assistance: explaining one
deterministic feedback anchor, asking one evidence-scope/limitation reflection
question, or producing a meaning-preserving language alternative. The existing
boundary preserves verified identity, explicit opt-in, redaction, bounded
input/output, quota, timeout, typed fallback, and metadata-only audit. Focused
AI API tests pass `39/39` and the full API suite passes `139/139`; the provider
remains disabled pending legal/privacy, cost, managed, mobile, and human gates.
See the [E147 record](../audit/evidence/evidrilo-ai-bounded-assistance-2026-09-13.md).

E148 verifies the repository-owned local recovery rehearsal: a disposable
PostgreSQL container applies the current ledger, creates a custom-format dump,
restores it into a fresh database, and recovers synthetic sentinel data. This
does not close managed backup, staging, load, or production rollback gates.
See the [E148 record](../audit/evidence/evidrilo-local-backup-restore-2026-09-13.md).

E149 fixes the server billing configuration gap by requiring the exact
`evidrilo_pro` entitlement in addition to webhook authentication. A
noncanonical entitlement now fails closed; focused configuration tests pass
`11/11` and the full API suite passes `140/140`. Provider dashboard,
transaction, and production configuration gates remain open. See the [E149
record](../audit/evidence/evidrilo-billing-entitlement-allowlist-2026-09-13.md).

E150 corrects stale operational snapshots: the RevenueCat Test Store runbook,
backend execution register, current status page, platform README, root README,
and all-area audit now point to the latest E149 billing boundary and API `140/140` result.
The focused contract passes `22/22` and the final Node boundary passes `77/77`; this does not change provider, transaction,
runtime, managed, human, or submission evidence.
See the [E150 record](../audit/evidence/evidrilo-runbook-status-synchronization-2026-09-13.md).

E151 closes the server-side RevenueCat product-policy gap. Active signed
webhook events now require an exact `monthly` or `yearly` `product_id`, so a
canonical-entitlement `lifetime` event is ignored before persistence. Focused
billing tests pass `10/10` and the full API suite passes `141/141`; provider,
runtime, managed, human, and submission evidence remain open. See the
[E151 record](../audit/evidence/evidrilo-billing-product-allowlist-2026-09-14.md).

E152 closes a local account-deletion integrity gap. Migration
`027_account_deletion_owner_guard` locks affected organizations and rejects
deletion when the requested account is their sole active owner; the API maps
the boundary to `409 OWNER_TRANSFER_REQUIRED`. Fresh PostgreSQL smoke observes
the owner guard and the existing deletion assertions; provider, managed,
deployment, runtime, human, and submission gates remain open. See the
[E152 record](../audit/evidence/evidrilo-account-deletion-owner-guard-2026-09-14.md).

E153 hardens local client/request boundaries: the mobile billing entitlement is
canonical and product-aware; content, sync, and analytics reject unverified
stored sessions; account deletion preserves the actionable owner-transfer
state; chunked billing bodies are bounded before deserialization; and caller
cancellation propagates through optional AI. The current local boundary passes
Kotlin `267/267`, API `144/144`, Node `77/77`, worker `5/5`, shared iOS target
compilation, and fresh PostgreSQL/worker smoke. External provider, native
runtime, managed, staging, production, human, and submission gates remain
open. See the [E153 record](../audit/evidence/evidrilo-client-boundary-hardening-2026-09-14.md).

E154 bounds the custom-scheme auth callback to 8 KiB before parsing or pending
retention. The callback parser/bus regression, full local verifier, and shared
iOS target compilation pass; the current verifier records API `144/144`, Node
`77/77`, and worker `5/5`. Native callback runtime and provider-backed account
verification remain open. See the [E154 record](../audit/evidence/evidrilo-auth-callback-boundary-2026-09-14.md).

E155 requires each accepted authoring document to contain one to 32 meaningful
challenge variants. Focused content/authoring coverage passes `22/22`; the final
local verifier passes Kotlin/JVM `268/268`, Node `77/77`, API `145/145`, worker
`5/5`, and deployment checks. Managed content publication and provider, runtime,
staging, human, and submission gates remain open. See the [E155
record](../audit/evidence/evidrilo-content-challenge-required-2026-09-14.md).

E156 revalidates stored authoring JSON under the transition row lock before
`approved` or `published`. Focused content/authoring coverage passes `23/23`
and the full API suite passes `146/146`; managed database replay, content
publication, provider, runtime, staging, human, and submission gates remain
open. See the [E156 record](../audit/evidence/evidrilo-stored-content-transition-guard-2026-09-14.md).

E157 makes the Kotlin published-case reader reject an empty challenge-variant
array, aligning the mobile response boundary with the API content lifecycle.
Focused reader coverage passes `8/8`; the full Kotlin/JVM suite passes
`269/269`, JVM/Android compilation passes, and shared iOS targets compile.
Managed content publication, provider, device, staging, human, and submission
gates remain open. See the [E157 record](../audit/evidence/evidrilo-mobile-content-challenge-required-2026-09-14.md).

E158 adds `minItems: 1` to the versioned `case-summary.v1.json` variants array
and protects the invariant with the repository contract test. The focused
contract and full local verifier pass with Kotlin `269/269`, Node `77/77`, API
`146/146`, worker `5/5`, and deployment checks. Managed content publication,
provider, device, staging, human, and submission gates remain open. See the
[E158 record](../audit/evidence/evidrilo-case-schema-challenge-boundary-2026-09-14.md).

E159 aligns the remaining expressible published-case bounds by adding the
shared identifier pattern, non-empty title, and one-to-32 skill-tag rules to
the versioned schema. The focused contract and full local verifier pass with
Kotlin `269/269`, Node `77/77`, API `146/146`, worker `5/5`, and deployment
checks. Cross-field references remain runtime validation. Managed content
publication, provider, device, staging, human, and submission gates remain
open. See the [E159 record](../audit/evidence/evidrilo-case-schema-identifier-bounds-2026-09-14.md).

E160 corrects the content-authoring runbook's stale “optional challenge
variants” instruction. The documentation contract and full local verifier pass;
managed editorial publication, provider, device, staging, human, and submission
gates remain open. See the [E160 record](../audit/evidence/evidrilo-content-runbook-challenge-required-2026-09-14.md).

E161 aligns the versioned recommendation schema with the mobile/API
status-specific rules: exact calculation version, required nullable fields,
bounded identifiers/references, and conditional recommended/abstain shapes.
The focused contract and full local verifier pass with Kotlin `269/269`, Node
`78/78`, API `146/146`, worker `5/5`, and deployment checks. Provider, device,
staging, human, and submission gates remain open. See the [E161 record](../audit/evidence/evidrilo-recommendation-schema-alignment-2026-09-14.md).

E162 synchronizes the current Node verification snapshot after E161 added one
contract test. Historical counts remain provenance and no provider, device,
managed, staging, human, or submission gate is closed. See the [E162 record](../audit/evidence/evidrilo-node-verification-snapshot-2026-09-14.md).

E163 hardens local conclusion-session restoration by rejecting unknown enum
values, malformed escaped lists, and encoded values over 8 KiB before parsing.
The focused regression and final local verifier pass with Kotlin `270/270`,
Node `78/78`, API `146/146`, worker `5/5`, and deployment checks. External
provider, device, managed, staging, human, and submission gates remain open.
See the [E163 record](../audit/evidence/evidrilo-local-session-decoder-hardening-2026-09-14.md).

E164 hardens the cohort aggregate boundary by requiring an active organization
membership for every counted enrollment. Migration 028, its `28/28` contract
test, focused API readiness `4/4`, and fresh PostgreSQL/RLS smoke pass. The
tracker remains `78/92`; managed database, provider, device, staging, human,
publication, and submission gates remain open. See the [E164 record](../audit/evidence/evidrilo-cohort-active-membership-boundary-2026-09-14.md).

E165 hardens the cohort role boundary by requiring the counted organization
membership to have role `learner`. Non-learner enrollments therefore cannot
inflate learner totals or bypass aggregate suppression. Migration 029, the
focused contract, API readiness `4/4`, and fresh PostgreSQL/RLS smoke pass; the
tracker remains `78/92` and managed database, provider, device, staging,
human, publication, and submission gates remain open. See the [E165 record](../audit/evidence/evidrilo-cohort-learner-role-boundary-2026-09-14.md).

E166 closes the complementary membership role-change invariant. A grant or
role-change operation cannot demote the last active organization owner to a
non-owner role; the organization is locked before the owner count is checked.
The focused policy regression and full API suite pass `147/147`; the tracker
remains `78/92` and managed database, provider, device, staging, human,
publication, and submission gates remain open. See the [E166 record](../audit/evidence/evidrilo-last-owner-role-change-2026-09-14.md).

E167 hardens the billing webhook identity boundary. An all-zero `app_user_id`
is acknowledged as `ignored` before persistence, preventing an impossible
account from reaching the database store. The focused billing suite passes
`11/11` and the full API suite passes `148/148`; the tracker remains `78/92`
and provider, managed database, device, staging, human, publication, and
submission gates remain open. See the [E167 record](../audit/evidence/evidrilo-billing-empty-account-guard-2026-09-14.md).

E168 synchronizes active status headers after the audit found stale current
snapshot labels in the backend register, architecture notes, security boundary,
and all-area audit. The snapshot contract passes `23/23`; historical evidence,
the `78/92` tracker, and all external gates remain unchanged. See the [E168
record](../audit/evidence/evidrilo-current-status-snapshot-sync-2026-09-14.md).

E169 hardens the auth provider type boundary. Non-string confirmation metadata
cannot mark a local session as email-verified; focused auth coverage passes
`13/13`, and Kotlin/JVM `271/271` plus JVM/Android/shared-iOS compilation pass.
Provider, native runtime, managed, staging, human, publication, and submission
gates remain open. See the [E169 record](../audit/evidence/evidrilo-auth-provider-type-boundary-2026-09-14.md).

E170 adds explicit regression coverage for learner, teacher, and reviewer
attempts to assign membership roles. The existing policy denies those actors;
focused access-policy coverage passes `9/9` and the full API suite passes
`151/151` without changing authorization behavior. Provider, native runtime,
managed, staging, human, publication, and submission gates remain open. See
the [E170 record](../audit/evidence/evidrilo-membership-role-assignment-coverage-2026-09-14.md).

## G4 — RevenueCat completion

The premium unit is one `evidrilo_pro` entitlement with monthly and yearly
packages that unlock two distinct cases. Lifetime is intentionally excluded
from the approved commercial direction. The initial global reference anchors
are USD 1.00/month and USD 10.00/year; see the [monetization and pricing
note](business/monetization-and-pricing.md). Keep all SDK calls behind the
billing interface. The app-side allowlist rejects legacy lifetime identifiers;
the active offering has no lifetime package, and dashboard price migration plus
the transaction matrix remain G4 gates.

Close G4 only after an owner-authorized Test Store run records:

- offering available and unavailable states;
- purchase success and entitlement-based unlock;
- cancellation, failure, pending/unknown, and duplicate-tap behavior;
- restore and relaunch behavior;
- network/configuration failure behavior; and
- free-core fallback without a key or active entitlement.

Do not invent identifiers or describe sandbox results as production revenue.

E173 hardens asynchronous state boundaries in the optional sync and premium
paths. Stale network responses cannot overwrite cleared or newer queue state,
visible sync results are bound to account/consent generation, restore is
ordered before queue cleanup, and the complete approved monthly/yearly offer
catalog is retained. Focused coverage passes `18/18`; the full local boundary
passes Kotlin/JVM `306/306`, Node `87/87`, API `151/151`, worker `5/5`,
Android/shared-iOS compilation, and deployment checks. This changes no external
gate; the tracker remains `84/100`.

E174 closes repository-local fail-closed gaps in sync response validation, auth
refresh session cleanup, and direct billing presentation. Non-monotonic or
skipping cursors, unrelated push results, withdrawn email confirmation with a
stale session, and legacy lifetime purchase targets are rejected. Focused sync,
auth, and billing suites pass `18/18`, `14/14`, and `9/9`; the latest full local
boundary passes Kotlin/JVM `311/311`, Node `87/87`, API `151/151`, worker `5/5`,
Android/shared-iOS target compilation, deployment checks, and audio asset
checks. This changes no external gate; the tracker remains `84/100`.

E175 closes the remaining sync pull lower-bound gap: a returned change must have
`serverSequence > cursor` and `serverSequence <= nextCursor`, not merely be
monotonic within the response. The RED/GREEN regression and latest full local
boundary pass are recorded at sync `29/29`, Kotlin/JVM `312/312`, Node `87/87`,
API `151/151`, worker `5/5`, Android/shared-iOS compilation, deployment, and
audio checks. This changes no external gate; the tracker remains `84/100`.

E176 hardens request lifecycle and input boundaries. Mobile HTTP cancellation
now aborts platform work and ignores late callbacks; malformed JSON preserves
the stable API error envelope; duplicate sync command IDs are rejected before
storage; and cancelled recommendation/auth flows return to a retryable state.
The local boundary passes Kotlin/JVM `317/317`, Node `87/87`, API `153/153`,
worker `5/5`, with Android/shared-iOS compilation, deployment, and audio checks
passing. Runtime, provider, managed, staging, human, publication, and
submission gates remain open. See
[E176](../audit/evidence/evidrilo-request-lifecycle-and-input-boundaries-2026-09-14.md).

E177 hardens sync pull pagination: the client validates each response against
the requested `limit`, accepts valid smaller pages with `hasMore=true`, and
rejects oversized responses. The latest local boundary passes Kotlin/JVM
`319/319`, Node `87/87`, API `153/153`, worker `5/5`, Android/shared-iOS
compilation, deployment, and audio checks. External runtime, provider,
managed, staging, human, publication, and submission gates remain open. See
[E177](../audit/evidence/evidrilo-sync-page-size-boundary-2026-09-14.md).

E178 hardens API full-match validation and staging environment normalization.
Identifiers, digests, actions, reason codes, case-version IDs, and request IDs
reject trailing-newline boundary bypasses; conventional `Staging` is accepted
as canonical `staging`. API `158/158` passes. Managed staging, provider,
runtime, human, publication, and submission gates remain open. See
[E178](../audit/evidence/evidrilo-api-input-and-staging-boundary-2026-09-14.md).

E179 hardens consent-bound sync orchestration. A failed or invalidated pull
cannot start a push, account/consent changes cancel the active sync job, and
failed queue clears preserve truthful pending-count state. Kotlin/JVM
`321/321` passes. Provider, runtime, managed, staging, human, publication, and
submission gates remain open. See
[E179](../audit/evidence/evidrilo-sync-consent-cancellation-boundary-2026-09-14.md).

E180 prepares the repository-owned Android/iOS release boundary: optimized
Android release packaging and fail-closed signing, plus an iOS distribution
configuration template and unsigned CI host-build lane. Kotlin/JVM `324/324`,
Node `93/93`, API `161/161`, worker `5/5`, Android release packaging, shared
iOS target compilation, and release checks pass. Native runtime, archive,
signing, store, provider, managed, human, publication, and submission gates
remain open. See
[E180](../audit/evidence/evidrilo-mobile-release-candidate-preparation-2026-09-14.md).

E181 hardens the case-authoring transition boundary. Invalid schema, version,
and target-state values are rejected before storage; focused transition
coverage passes `2/2`. The full local verifier passes the current
Kotlin/Android build boundary, Node `93/93`, API `162/162`, worker `5/5`,
deployment, and asset checks. Runtime, provider, managed, staging, human,
publication, and submission gates remain open. See [E181](../audit/evidence/evidrilo-case-transition-contract-boundary-2026-09-15.md).

E183 hardens the shared Compose choice control with native selectable radio and
toggleable checkbox semantics, plus merged label/state information. Direct
contracts pass `25/25`; the full local verifier passes Node `95/95`, API
`163/163`, worker `5/5`, the current Kotlin/Android build boundary, Android
release packaging, deployment, and asset checks. Runtime, provider, managed,
staging, human, publication, and submission evidence remain open. See
[E183](../audit/evidence/evidrilo-choice-accessibility-semantics-2026-09-15.md).

E182 aligns the server sync-pull cursor boundary with the mobile parser. Cursors
outside `0..1_000_000_000_000_000` are rejected before storage; the focused
regression passes `1/1`. The full local verifier passes the current
Kotlin/Android build boundary, Node `93/93`, API `163/163`, worker `5/5`,
deployment, and asset checks. Runtime, provider, managed, staging, human,
publication, and submission gates remain open. See
[E182](../audit/evidence/evidrilo-sync-cursor-contract-boundary-2026-09-15.md).

E184 is the latest dated environment verification, not a product increment. A
checksum-verified Temurin JDK 21 restored the full Kotlin/Android release
verification; Node `95/95`, API `163/163`, worker `5/5`, audio, and deployment
checks also pass. The original and fresh temporary Android AVD probes still
stop before a usable framework/package service, so no current Android UI or G3
runtime evidence is claimed. See
[E184](../audit/evidence/evidrilo-android-runtime-probe-2026-09-15.md).

## G5 — Human validation

The current runbook is private and does not authorize contact. After the
owner authorizes it:

1. freeze the exact build, content, protocol, and fixture versions;
2. obtain two independent reviewer preflight passes;
3. run at least six consenting adult participant sessions;
4. store consent/contact records separately from anonymized observations; and
5. record confusion, trust, accessibility, burden, evidence anchoring, and
   evidence-change understanding without claiming population-level efficacy.

A failed fairness, privacy, or safety check stops the gate.

## G6–G8 — Release and submission

Before G7:

- the final build, repository, description, video, icon, and screenshot must
  describe the same revision;
- the public repository must contain source, assets, setup instructions, and a
  visible open-source license, while internal evidence remains excluded;
- the video must show the real app on the claimed target and stay within the
  official essential-footage limit;
- the icon and screenshot must meet the official dimensions;
- student eligibility, academic email, team/country rules, and guardian consent
  where applicable must be verified privately; and
- every spoken, written, or visual claim must map to an actual artifact or
  observation.

G7 is owner-controlled. Publication, repository visibility, external messages,
transactions, and Devpost submission require explicit owner authorization.
G8 freezes the submitted revision and its evidence; later changes need a new
record.

## Synchronized external gates — 13 September 2026

The following gates remain outside repository-only completion in this release:

- macOS/Xcode iOS host/runtime, native secure storage, and accessibility review;
- RevenueCat Test Store purchase/restore/revoke matrix, managed Paywall
  configuration, and real App Store/Google Play/Customer Center rendering;
- managed Supabase Auth/JWKS/RLS/migrations and authorized database replay;
- managed Supabase/Google/SMTP account configuration, real auth/recovery flows,
  and API claim/deletion verification;
- deployment HTTPS/proxy/secrets, backups, restore, alerts, rollback, and load;
- human validation, eligibility/guardian checks, public repository/store
  publication, and Devpost submission.

These gates must be copied with the same wording into the release and current
status records; none is implied by local source or synthetic tests.

## 21-day planning frame

This allocation is a schedule assumption, not a completion promise:

- **Days 1–15:** close G3, implement/verify G4, prepare the validation packet,
  run authorized human validation, and close reproducibility gaps.
- **Days 16–21:** fix evidenced defects, run regression checks, capture final
  assets, audit claims, verify public access, and preserve submission buffer.

If external access is late, mark the affected gate AT_RISK or NOT_READY. Never
replace it with documentation or invented evidence.

## Working sequence

1. Read the Source of Truth and select the first unmet goal.
2. Make one bounded change.
3. Add or update the narrowest relevant tests.
4. Run the smallest meaningful check available.
5. Record the actual result in the appropriate audit/runbook.
6. Update this roadmap only when the milestone state changes.

Do not reopen candidates, restore the 95-point target, add deferred platform
layers, publish, transact, invite, or submit without the corresponding decision
and authorization.

## Repository-owned completion program

The active implementation plan for completing all non-account-dependent work is
[`docs/superpowers/plans/2026-09-11-evidrilo-platform-completion.md`](superpowers/plans/2026-09-11-evidrilo-platform-completion.md),
with the repository-owned open-gate closure recorded in
[`docs/superpowers/plans/2026-09-12-evidrilo-open-gates-closure.md`](superpowers/plans/2026-09-12-evidrilo-open-gates-closure.md)
and its design contract in
[`docs/superpowers/specs/2026-09-11-evidrilo-platform-completion-design.md`](superpowers/specs/2026-09-11-evidrilo-platform-completion-design.md).
It covers navigation, history/settings/about surfaces, recovery,
accessibility semantics, billing failure handling, provider-neutral account and
sync contracts, API hardening, worker resilience, and reproducible
verification. It does not change the competition product boundary or claim
that accounts, cloud sync, managed infrastructure, store transactions, iOS
runtime, or human validation have happened.

The separate account implementation plan is
[`docs/superpowers/plans/2026-09-11-evidrilo-account-authentication.md`](superpowers/plans/2026-09-11-evidrilo-account-authentication.md).
It records the repository-owned credential, Google SSO, secure-session, and
account-lifecycle work and its external provider/runtime gates.

## Useful public documents

- [Development guide](development/README.md)
- [M0 product contract](product/m0-product-contract.md)
- [Platform decision](architecture/platform-decision.md)
- [RevenueCat boundary](architecture/revenuecat.md)
- [Testing and evidence](testing.md)
- [Release readiness](release.md)
