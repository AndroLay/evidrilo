# Platform Decision — Evidrilo Kotlin Multiplatform

Latest increment: E186 / BACKEND_ENGINE_SYNC_BOUNDARY_HARDENED / E185 / REVENUECAT_OFFERING_MIGRATION_OBSERVED / E183 / NATIVE_CHOICE_ACCESSIBILITY_SEMANTICS_HARDENED / E182 / SYNC_CURSOR_CONTRACT_BOUNDARY_ALIGNED / E181 / CASE_TRANSITION_CONTRACT_BOUNDARY_HARDENED / E180 / MOBILE_RELEASE_CANDIDATE_PREPARATION / E179 / SYNC_CONSENT_CANCELLATION_BOUNDARY_HARDENED / E178 / API_INPUT_AND_STAGING_BOUNDARY_HARDENED / E177 / SYNC_PULL_PAGE_SIZE_BOUNDARY_HARDENED / E176 / REQUEST_LIFECYCLE_AND_INPUT_BOUNDARIES_HARDENED / E175 / SYNC_PULL_CURSOR_LOWER_BOUND_GUARDED / E174 / FAIL_CLOSED_RESPONSE_REFRESH_BOUNDARIES / E173 / ASYNC_STATE_BOUNDARIES_HARDENED / E172 / LOCAL_SESSION_BOUNDARIES_HARDENED / E171 / OFFLINE_AUDIO_REPOSITORY_IMPLEMENTATION / E170 / MEMBERSHIP_ROLE_ASSIGNMENT_POLICY_COVERAGE / E169 / AUTH_PROVIDER_CONFIRMATION_TYPE_BOUNDED / E168 / CURRENT_STATUS_SNAPSHOTS_SYNCHRONIZED.

Status: E186 / BACKEND_ENGINE_SYNC_BOUNDARY_HARDENED / E185 / REVENUECAT_OFFERING_MIGRATION_OBSERVED / E183 / NATIVE_CHOICE_ACCESSIBILITY_SEMANTICS_HARDENED / E182 / SYNC_CURSOR_CONTRACT_BOUNDARY_ALIGNED / E181 / CASE_TRANSITION_CONTRACT_BOUNDARY_HARDENED / E180 / MOBILE_RELEASE_CANDIDATE_PREPARATION / E179 / SYNC_CONSENT_CANCELLATION_BOUNDARY_HARDENED / E178 / API_INPUT_AND_STAGING_BOUNDARY_HARDENED / E177 / SYNC_PULL_PAGE_SIZE_BOUNDARY_HARDENED / E176 / REQUEST_LIFECYCLE_AND_INPUT_BOUNDARIES_HARDENED / E175 / SYNC_PULL_CURSOR_LOWER_BOUND_GUARDED / E174 / FAIL_CLOSED_RESPONSE_REFRESH_BOUNDARIES / E173 / ASYNC_STATE_BOUNDARIES_HARDENED / E172 / LOCAL_SESSION_BOUNDARIES_HARDENED / E171 / OFFLINE_AUDIO_REPOSITORY_IMPLEMENTATION / E170 / MEMBERSHIP_ROLE_ASSIGNMENT_POLICY_COVERAGE / E169 / AUTH_PROVIDER_CONFIRMATION_TYPE_BOUNDED / E168 / CURRENT_STATUS_SNAPSHOTS_SYNCHRONIZED / RUNTIME_GATES_OPEN / EXTERNAL_GATES_OPEN
<!-- Historical status chain retained below for provenance.
Status: `E170 / MEMBERSHIP_ROLE_ASSIGNMENT_POLICY_COVERAGE / E169 / AUTH_PROVIDER_CONFIRMATION_TYPE_BOUNDED / E168 / CURRENT_STATUS_SNAPSHOTS_SYNCHRONIZED / E167 / BILLING_EMPTY_ACCOUNT_GUARDED / E166 / LAST_OWNER_ROLE_CHANGE_GUARDED / E165 / COHORT_LEARNER_ROLE_BOUNDARY_HARDENED / E164 / COHORT_MEMBERSHIP_BOUNDARY_HARDENED / E163 / LOCAL_SESSION_DECODER_HARDENED / E162 / NODE_VERIFICATION_SNAPSHOT_SYNCHRONIZED / E161 / RECOMMENDATION_SCHEMA_ALIGNMENT / E160 / CONTENT_RUNBOOK_CHALLENGE_REQUIRED / E159 / CASE_SCHEMA_IDENTIFIER_BOUNDS / E158 / CONTRACT_SCHEMA_ALIGNMENT / E157 / MOBILE_CONTENT_CHALLENGE_REQUIRED / E154 / AUTH_CALLBACK_INPUT_BOUNDED / E153 / CLIENT_BOUNDARIES_HARDENED / WEBHOOK_BODY_BOUNDED / AI_CANCELLATION_PROPAGATED / VERIFIED_SESSION_GATES_ALIGNED / E152 / ACCOUNT_DELETION_OWNER_GUARDED / E151 / REVENUECAT_PRODUCT_ALLOWLIST_GUARDED / E150 / RUNBOOK_STATUS_SYNCHRONIZED / E149 / CANONICAL_ENTITLEMENT_GUARDED / E148 / E147 / AI_SCOPE_DECISION_LOCKED / E146 / CASE_LIFECYCLE_AUDIT_VERIFIED / E145 / REPOSITORY_OWNED_CLOSURE_VERIFIED / ACCOUNT_AUTH_REPO_IMPLEMENTED / MOBILE_METADATA_SYNC_IMPLEMENTED / CONSENTED_FUNNEL_ANALYTICS_IMPLEMENTED / SERVER_OWNED_WRITE_BOUNDARIES / CANONICAL_CONTENT_READER_VERIFIED / CLAIM_AUDIT_CURRENT / DATABASE_READINESS_LEDGER_GUARDED / BACKEND_REGISTER_SYNCHRONIZED / RUNBOOK_OBSERVATION_CLAIMS_GUARDED / REVENUECAT_UI_BOUNDARY_COMPILED / REVENUECAT_RUNBOOK_ALIGNED / PRODUCTION_CORS_EXPLICIT / GENERATED_OUTPUT_BOUNDARY_HARDENED / MIGRATION_RUNNER_CONCURRENCY_GUARDED / TRIGGER_FUNCTION_PRIVILEGES_HARDENED / BILLING_CALLBACK_IDENTITY_GUARDED / BILLING_CONFIG_STATUS_TRUTHFUL / BILLING_UNKNOWN_STATE_ALLOWLISTED / AUTH_REDIRECTS_REJECTED / ANDROID_NETWORK_PERMISSION_DECLARED / ACCOUNT_PROFILE_PROVISIONED / ACCOUNT_DELETION_ACCESS_GUARDED / ACCOUNT_DELETION_LEDGER_FALLBACK / M2_ANDROID_PARTIAL_RUNTIME / DEPLOYMENT_PREPARATION_PRESENT / IOS_RUNTIME_OPEN`

-->
Current authority: [Evidrilo Source of Truth](../../research/next-gen/SOURCE_OF_TRUTH.md),
[E118 all-area closure](../../audit/evidence/evidrilo-all-areas-closure-2026-09-13.md),
[E119 analytics boundary](../../audit/evidence/evidrilo-analytics-funnel-closure-2026-09-13.md),
and [E120 server-owned write boundary](../../audit/evidence/evidrilo-server-owned-write-boundary-2026-09-13.md),
[E121 recommendation write boundary](../../audit/evidence/evidrilo-server-owned-recommendation-boundary-2026-09-13.md).
The bounded AI scope decision is recorded in
[E147](../../audit/evidence/evidrilo-ai-bounded-assistance-2026-09-13.md).
The local recovery preparation is recorded in
[E148](../../audit/evidence/evidrilo-local-backup-restore-2026-09-13.md).
The canonical RevenueCat entitlement guard is recorded in
[E149](../../audit/evidence/evidrilo-billing-entitlement-allowlist-2026-09-13.md).
The operational runbook synchronization is recorded in
[E150](../../audit/evidence/evidrilo-runbook-status-synchronization-2026-09-13.md).
The server-side RevenueCat product allowlist is recorded in
[E151](../../audit/evidence/evidrilo-billing-product-allowlist-2026-09-14.md).
The account-deletion owner guard is recorded in
[E152](../../audit/evidence/evidrilo-account-deletion-owner-guard-2026-09-14.md).
The client and request-boundary hardening is recorded in
[E153](../../audit/evidence/evidrilo-client-boundary-hardening-2026-09-14.md).
The auth callback input boundary is recorded in
[E154](../../audit/evidence/evidrilo-auth-callback-boundary-2026-09-14.md).
The canonical content challenge boundary is recorded in
[E155](../../audit/evidence/evidrilo-content-challenge-required-2026-09-14.md).
The stored content transition guard is recorded in
[E156](../../audit/evidence/evidrilo-stored-content-transition-guard-2026-09-14.md).
The mobile content challenge boundary is recorded in
[E157](../../audit/evidence/evidrilo-mobile-content-challenge-required-2026-09-14.md).
The published-case schema alignment is recorded in
[E158](../../audit/evidence/evidrilo-case-schema-challenge-boundary-2026-09-14.md).
The published-case identifier-boundary alignment is recorded in
[E159](../../audit/evidence/evidrilo-case-schema-identifier-bounds-2026-09-14.md).
The content-authoring runbook correction is recorded in
[E160](../../audit/evidence/evidrilo-content-runbook-challenge-required-2026-09-14.md).
The recommendation schema alignment is recorded in
[E161](../../audit/evidence/evidrilo-recommendation-schema-alignment-2026-09-14.md).
The billing configuration truth correction is recorded in
[E122](../../audit/evidence/evidrilo-billing-config-readiness-2026-09-13.md).
The billing presentation allowlist correction is recorded in
[E123](../../audit/evidence/evidrilo-billing-offer-allowlist-2026-09-13.md).
The authenticated transport redirect correction is recorded in
[E124](../../audit/evidence/evidrilo-auth-redirect-safety-2026-09-13.md).
The account-deletion access correction is recorded in
[E126](../../audit/evidence/evidrilo-account-deletion-access-2026-09-13.md).
The legacy-account deletion-ledger fallback is recorded in
[E127](../../audit/evidence/evidrilo-account-deletion-ledger-fallback-2026-09-13.md).
New Auth account-profile provisioning is recorded in
[E128](../../audit/evidence/evidrilo-account-profile-provisioning-2026-09-13.md).
The billing callback identity guard is recorded in
[E129](../../audit/evidence/evidrilo-billing-callback-identity-2026-09-13.md).
The trigger function privilege hardening is recorded in
[E130](../../audit/evidence/evidrilo-trigger-function-privileges-2026-09-13.md).
The generated-output and migration-runner boundaries are recorded in
[E131](../../audit/evidence/evidrilo-generated-output-boundary-2026-09-13.md)
and [E132](../../audit/evidence/evidrilo-migration-runner-concurrency-2026-09-13.md).
Production CORS configuration hardening is recorded in
[E133](../../audit/evidence/evidrilo-production-cors-boundary-2026-09-13.md).
RevenueCat runbook catalog alignment is recorded in
[E134](../../audit/evidence/evidrilo-revenuecat-runbook-catalog-boundary-2026-09-13.md).
Migration-aware API database readiness is recorded in
[E135](../../audit/evidence/evidrilo-database-readiness-ledger-2026-09-13.md).
The provider-observation documentation boundary is recorded in
[E136](../../audit/evidence/evidrilo-runbook-observation-boundary-2026-09-13.md).
The active backend register synchronization is recorded in
[E137](../../audit/evidence/evidrilo-backend-register-synchronization-2026-09-13.md).
The managed RevenueCat UI boundary is recorded in
[E138](../../audit/evidence/evidrilo-revenuecat-managed-ui-2026-09-13.md).
The canonical published content reader boundary is recorded in
[E139](../../audit/evidence/evidrilo-canonical-content-reader-2026-09-13.md).
The provider-claim documentation boundary is recorded in
[E140](../../audit/evidence/evidrilo-revenuecat-documentation-claim-boundary-2026-09-13.md).
The case lifecycle audit boundary is recorded in
[E141](../../audit/evidence/evidrilo-case-lifecycle-audit-2026-09-13.md).
The bounded lifecycle audit read boundary is recorded in
[E142](../../audit/evidence/evidrilo-case-lifecycle-audit-read-2026-09-13.md).
The recommendation projection validation boundary is recorded in
[E143](../../audit/evidence/evidrilo-recommendation-projection-validation-2026-09-13.md).
The recommendation candidate validation boundary is recorded in
[E144](../../audit/evidence/evidrilo-recommendation-candidate-validation-2026-09-13.md).
The current status snapshot synchronization is recorded in
[E168](../../audit/evidence/evidrilo-current-status-snapshot-sync-2026-09-14.md).
The auth provider confirmation type boundary is recorded in
[E169](../../audit/evidence/evidrilo-auth-provider-type-boundary-2026-09-14.md).
The membership role-assignment policy coverage is recorded in
[E170](../../audit/evidence/evidrilo-membership-role-assignment-coverage-2026-09-14.md).

E171 adds the optional offline-audio boundary without changing the KMP
platform decision: shared coordination and UI semantics remain common, while
native Android/iOS playback and offline-only TTS stay platform adapters.
Narration assets, device playback, and human audio review remain open.

E172 keeps local session restoration fail-closed inside the common storage and
security layers; it does not introduce a platform-specific dependency or alter
the shared/native split.

E173 keeps async sync-state protection in the shared layer: the coordinator
guards network commits with the stored snapshot/generation, while the app
binds visible results to account and consent state. Billing catalog retention
also remains common behavior; no platform-specific dependency is introduced.

E174 keeps the new fail-closed cursor, push-result, refresh-session, and billing
allowlist checks in the shared layer. They do not add a platform-specific
dependency or alter the Kotlin Multiplatform/native boundary; native runtime
and accessibility evidence remain separate.

E186 keeps offline practice identity separate from server identity in the
shared boundary. Local case IDs remain stable for drafts and history, while
sync and analytics use an explicit canonical case-version mapping. Invalid
cases and snapshots fail closed in common code, and the API/database published
case rule remains an optional remote boundary rather than a free-core
dependency.

## Decision

Build Evidrilo with **Kotlin Multiplatform and Compose
Multiplatform**, targeting Android and iOS from a shared `composeApp` module.
Keep `androidApp` and `iosApp` as separate platform entry points.

The approved product name is **Evidrilo**. The current technical namespace is
still `dev.nextgen.mobile` and remains temporary until a deliberate package
rename milestone; it must not be treated as evidence that the Evidrilo product
loop is implemented.

The mobile account boundary is provider-neutral and fail-closed. The
repository now contains an optional Supabase Auth adapter for email/password,
password recovery, Google OAuth with PKCE, refresh, local sign-out, and
account-deletion preparation. It may return a verified session, offline,
invalid-credentials, expired, or not-configured status. The local session
controller still checks the verified flag before writing secure storage; a
caller-supplied account id cannot establish identity. Empty configuration
continues to expose the not-configured state rather than a fake sign-in flow.

The optional sync boundary is implemented and redacted by construction: it carries a
bounded cursor, a consent decision, a bounded idempotency key, command ids,
case/revision metadata, and a snapshot digest. Learner-authored conclusion
text, access tokens, and raw provider claims are not part of the mobile sync
model. The mobile client adds consent-gated queue/cursor/retry handling and
rejects mismatched responses. Network transport, Supabase credentials, and
the current sync contract intentionally keeps learner-authored draft text on
the device. An encrypted cross-device draft merge is not a current product
requirement and may only be opened as a new decision after validation
demonstrates a need.

## Why this is the current best fit

- It honors the team's Kotlin preference.
- It allows one implementation of the scenario engine, feedback rules, and
  most of the UI.
- It gives Android and iOS a consistent demo experience.
- RevenueCat integration can remain behind a small application billing
  boundary; see [`revenuecat.md`](revenuecat.md).
- It avoids introducing a separate JavaScript or Dart stack for a small MVP.

## Trade-offs accepted

- iOS still requires an Xcode host project; the initial thin host is now
  committed under `iosApp/`.
- Full iOS build and runtime validation require macOS/Xcode or a macOS CI
  runner.
- Compose Multiplatform and the RevenueCat KMP SDK must be kept on compatible
  versions.
- Platform-specific store configuration remains necessary even when purchase
  logic is shared.

## Alternatives rejected for now

### Native Kotlin Android only

Simpler for the first device, but it would not satisfy the explicit Android and
iOS target requested for this project.

### KMP shared logic with SwiftUI on iOS

Technically viable and potentially more native on iOS, but it requires a
second UI implementation and does not match the goal of keeping the first
product mostly Kotlin.

### Flutter or React Native

Both can support the product, but they do not align with the selected Kotlin
direction and would add a different primary language/runtime.

## Validation state entering M2

The shared domain gate is complete and the M2 shared Compose flow includes the
base conclusion loop, evidence-change challenge, and local comparison history.
A local Android emulator has reached the base feedback, revision, summary,
challenge feedback, comparison, and clearable history states. A
network-disabled pass restored challenge/comparison after relaunch. The latest
choice controls expose checked semantics and remain in the scrollable
hierarchy at 130% text scale. Both Kotlin/Native iOS target compilation tasks
pass on Linux.
The following evidence is still required before claiming a working
cross-platform product:

- iOS runtime launch through the committed Xcode host.
- iOS shared framework must be consumed by the committed Xcode host on macOS/CI.
- The complete shared Compose flow renders on both targets.
- The evidence-change challenge/history path renders on both targets.
- The billing boundary can initialize with platform-specific Test Store
  configuration without making the free core unavailable.
- The common domain gate is verified by the M1 test suite.

If the iOS build path fails because of environment access, the shared Kotlin
architecture remains valid, but the iOS validation must move to a macOS/CI
environment rather than being claimed locally.
