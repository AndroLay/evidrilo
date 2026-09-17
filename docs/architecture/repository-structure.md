# Repository Structure — Evidrilo Mobile App

Latest increment: E186 / BACKEND_ENGINE_SYNC_BOUNDARY_HARDENED / E185 / REVENUECAT_OFFERING_MIGRATION_OBSERVED / E183 / NATIVE_CHOICE_ACCESSIBILITY_SEMANTICS_HARDENED / E182 / SYNC_CURSOR_CONTRACT_BOUNDARY_ALIGNED / E181 / CASE_TRANSITION_CONTRACT_BOUNDARY_HARDENED / E180 / MOBILE_RELEASE_CANDIDATE_PREPARATION / E179 / SYNC_CONSENT_CANCELLATION_BOUNDARY_HARDENED / E178 / API_INPUT_AND_STAGING_BOUNDARY_HARDENED / E177 / SYNC_PULL_PAGE_SIZE_BOUNDARY_HARDENED / E176 / REQUEST_LIFECYCLE_AND_INPUT_BOUNDARIES_HARDENED / E175 / SYNC_PULL_CURSOR_LOWER_BOUND_GUARDED / E174 / FAIL_CLOSED_RESPONSE_REFRESH_BOUNDARIES / E173 / ASYNC_STATE_BOUNDARIES_HARDENED / E172 / LOCAL_SESSION_BOUNDARIES_HARDENED / E171 / OFFLINE_AUDIO_REPOSITORY_IMPLEMENTATION / E170 / MEMBERSHIP_ROLE_ASSIGNMENT_POLICY_COVERAGE / E169 / AUTH_PROVIDER_CONFIRMATION_TYPE_BOUNDED / E168 / CURRENT_STATUS_SNAPSHOTS_SYNCHRONIZED.

Status: E186 / BACKEND_ENGINE_SYNC_BOUNDARY_HARDENED / E185 / REVENUECAT_OFFERING_MIGRATION_OBSERVED / E183 / NATIVE_CHOICE_ACCESSIBILITY_SEMANTICS_HARDENED / E182 / SYNC_CURSOR_CONTRACT_BOUNDARY_ALIGNED / E181 / CASE_TRANSITION_CONTRACT_BOUNDARY_HARDENED / E180 / MOBILE_RELEASE_CANDIDATE_PREPARATION / E179 / SYNC_CONSENT_CANCELLATION_BOUNDARY_HARDENED / E178 / API_INPUT_AND_STAGING_BOUNDARY_HARDENED / E177 / SYNC_PULL_PAGE_SIZE_BOUNDARY_HARDENED / E176 / REQUEST_LIFECYCLE_AND_INPUT_BOUNDARIES_HARDENED / E175 / SYNC_PULL_CURSOR_LOWER_BOUND_GUARDED / E174 / FAIL_CLOSED_RESPONSE_REFRESH_BOUNDARIES / E173 / ASYNC_STATE_BOUNDARIES_HARDENED / E172 / LOCAL_SESSION_BOUNDARIES_HARDENED / E171 / OFFLINE_AUDIO_REPOSITORY_IMPLEMENTATION / E170 / MEMBERSHIP_ROLE_ASSIGNMENT_POLICY_COVERAGE / E169 / AUTH_PROVIDER_CONFIRMATION_TYPE_BOUNDED / E168 / CURRENT_STATUS_SNAPSHOTS_SYNCHRONIZED / RUNTIME_GATES_OPEN / EXTERNAL_GATES_OPEN
<!-- Historical status chain retained below for provenance.
Status: `E170 / MEMBERSHIP_ROLE_ASSIGNMENT_POLICY_COVERAGE / E169 / AUTH_PROVIDER_CONFIRMATION_TYPE_BOUNDED / E168 / CURRENT_STATUS_SNAPSHOTS_SYNCHRONIZED / E167 / BILLING_EMPTY_ACCOUNT_GUARDED / E166 / LAST_OWNER_ROLE_CHANGE_GUARDED / E165 / COHORT_LEARNER_ROLE_BOUNDARY_HARDENED / E164 / COHORT_MEMBERSHIP_BOUNDARY_HARDENED / E163 / LOCAL_SESSION_DECODER_HARDENED / E162 / NODE_VERIFICATION_SNAPSHOT_SYNCHRONIZED / E161 / RECOMMENDATION_SCHEMA_ALIGNMENT / E160 / CONTENT_RUNBOOK_CHALLENGE_REQUIRED / E159 / CASE_SCHEMA_IDENTIFIER_BOUNDS / E158 / CONTRACT_SCHEMA_ALIGNMENT / E157 / MOBILE_CONTENT_CHALLENGE_REQUIRED / E154 / AUTH_CALLBACK_INPUT_BOUNDED / E153 / CLIENT_BOUNDARIES_HARDENED / WEBHOOK_BODY_BOUNDED / AI_CANCELLATION_PROPAGATED / VERIFIED_SESSION_GATES_ALIGNED / E152 / ACCOUNT_DELETION_OWNER_GUARDED / E151 / REVENUECAT_PRODUCT_ALLOWLIST_GUARDED / E150 / RUNBOOK_STATUS_SYNCHRONIZED / E149 / CANONICAL_ENTITLEMENT_GUARDED / E148 / E147 / AI_SCOPE_DECISION_LOCKED / E146 / REPOSITORY_OWNED_CLOSURE_VERIFIED / CASE_LIFECYCLE_AUDIT_VERIFIED / CASE_LIFECYCLE_AUDIT_READ_VERIFIED / RECOMMENDATION_PROJECTION_VALIDATED / RECOMMENDATION_CANDIDATE_VALIDATED / CANONICAL_CONTENT_READER_VERIFIED / CLAIM_AUDIT_CURRENT / DATABASE_READINESS_LEDGER_GUARDED / BACKEND_REGISTER_SYNCHRONIZED / RUNBOOK_OBSERVATION_CLAIMS_GUARDED / REVENUECAT_UI_BOUNDARY_COMPILED / REVENUECAT_RUNBOOK_ALIGNED / PRODUCTION_CORS_EXPLICIT / GENERATED_OUTPUT_BOUNDARY_HARDENED / MIGRATION_RUNNER_CONCURRENCY_GUARDED / TRIGGER_FUNCTION_PRIVILEGES_HARDENED / BILLING_CALLBACK_IDENTITY_GUARDED / BILLING_CONFIG_STATUS_TRUTHFUL / BILLING_UNKNOWN_STATE_ALLOWLISTED / AUTH_REDIRECTS_REJECTED / ANDROID_NETWORK_PERMISSION_DECLARED / ACCOUNT_PROFILE_PROVISIONED / ACCOUNT_DELETION_ACCESS_GUARDED / ACCOUNT_DELETION_LEDGER_FALLBACK / M2_ANDROID_PARTIAL_RUNTIME / ACCOUNT_AUTH_REPO_IMPLEMENTED / MOBILE_METADATA_SYNC_IMPLEMENTED / CONSENTED_FUNNEL_ANALYTICS_IMPLEMENTED / SERVER_OWNED_WRITE_BOUNDARIES / PLATFORM_LOCAL_BOUNDARIES_PASS / DEPLOYMENT_PREPARATION_PRESENT / IOS_RUNTIME_OPEN`.

-->
Current authority: [Evidrilo Source of Truth](../../internal/research/next-gen/SOURCE_OF_TRUTH.md),
[E107 local verification](../../audit/evidence/m2-accessibility-offline-relaunch-local-verification.md),
and [E108 PostgreSQL integration](../../audit/evidence/m3-backend-postgresql-local-integration.md),
[E109 migration ledger verification](../../audit/evidence/migration-ledger-local-verification.md),
and [E110 worker PostgreSQL integration](../../audit/evidence/worker-postgresql-local-integration.md),
and [E113 account authentication](../../audit/evidence/evidrilo-account-authentication.md),
and [E114 whole-repository audit](../../audit/evidence/evidrilo-whole-repository-audit-2026-09-11.md),
and [E117 repository-owned finishing](../../audit/evidence/evidrilo-repository-owned-finishing-2026-09-12.md),
and [E118 all-area closure](../../audit/evidence/evidrilo-all-areas-closure-2026-09-13.md).
The bounded AI scope decision is recorded in
[E147](../../audit/evidence/evidrilo-ai-bounded-assistance-2026-09-13.md).
The local backup/restore preparation is recorded in
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
The latest analytics boundary is recorded in
[E119](../../audit/evidence/evidrilo-analytics-funnel-closure-2026-09-13.md).
The latest server-owned write boundary is recorded in
[E120](../../audit/evidence/evidrilo-server-owned-write-boundary-2026-09-13.md).
The latest recommendation write boundary is recorded in
[E121](../../audit/evidence/evidrilo-server-owned-recommendation-boundary-2026-09-13.md).
The latest billing configuration boundary is recorded in
[E122](../../audit/evidence/evidrilo-billing-config-readiness-2026-09-13.md).
The latest billing presentation boundary is recorded in
[E123](../../audit/evidence/evidrilo-billing-offer-allowlist-2026-09-13.md).
The latest authenticated transport boundary is recorded in
[E124](../../audit/evidence/evidrilo-auth-redirect-safety-2026-09-13.md).
The latest account lifecycle boundary is recorded in
[E126](../../audit/evidence/evidrilo-account-deletion-access-2026-09-13.md).
The deletion-ledger fallback is recorded in
[E127](../../audit/evidence/evidrilo-account-deletion-ledger-fallback-2026-09-13.md).
New Auth account-profile provisioning is recorded in
[E128](../../audit/evidence/evidrilo-account-profile-provisioning-2026-09-13.md).
The billing callback identity guard is recorded in
[E129](../../audit/evidence/evidrilo-billing-callback-identity-2026-09-13.md).
The trigger function privilege hardening is recorded in
[E130](../../audit/evidence/evidrilo-trigger-function-privileges-2026-09-13.md).
The generated-output boundary and migration-runner concurrency hardening are
recorded in [E131](../../audit/evidence/evidrilo-generated-output-boundary-2026-09-13.md)
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
The lifecycle audit read boundary is recorded in
[E142](../../audit/evidence/evidrilo-case-lifecycle-audit-read-2026-09-13.md).
The latest recommendation projection validation is recorded in
[E143](../../audit/evidence/evidrilo-recommendation-projection-validation-2026-09-13.md).
The latest recommendation candidate validation is recorded in
[E144](../../audit/evidence/evidrilo-recommendation-candidate-validation-2026-09-13.md).
The current status snapshot synchronization is recorded in
[E168](../../audit/evidence/evidrilo-current-status-snapshot-sync-2026-09-14.md).
The auth provider confirmation type boundary is recorded in
[E169](../../audit/evidence/evidrilo-auth-provider-type-boundary-2026-09-14.md).
The membership role-assignment policy coverage is recorded in
[E170](../../audit/evidence/evidrilo-membership-role-assignment-coverage-2026-09-14.md).

E171 adds the audio catalog, manifest, common coordinator, platform adapters,
and bounded interaction assets under the existing shared/platform split.
Reviewed narration, device playback, and human audio-quality evidence remain
open; audio is not part of evaluator state or synchronization contracts.

E172 adds fail-closed restoration validation within the existing storage and
security boundaries: supported phase/case combinations are enforced and local
secure-session account identifiers are restricted to a bounded safe form.

E173 adds the shared sync request gate and queue commit guard under the existing
common boundary. Account restoration and consent transitions route queue
clearing through the coordinator, while premium offer catalog retention stays
in the common billing reducer. No learner-authored draft text is added to sync
or billing payloads.

E174 extends the same common boundary with strict sync cursor/sequence and
push-result validation, stale-session cleanup after refresh, and defense in
depth for the monthly/yearly billing allowlist. These guards remain repository
local and do not claim provider, native runtime, managed, or human evidence.

E175 extends the sync boundary with a strict lower bound: a pull change must be
newer than the requested cursor as well as ordered and bounded by `nextCursor`.
This prevents a replayed boundary change from entering queue or projection
processing; it remains repository-local evidence.

E186 keeps the local and server case identities explicit. Offline drafts,
history, and replay retain stable local IDs; sync and analytics use the mapped
canonical case-version ID. The API and migration 030 reject new writes for
unpublished versions, while existing idempotent replays remain readable. This
boundary is covered by pure Kotlin, API, and local PostgreSQL tests.

Evidrilo is the approved product name. The current source includes the verified
pure Kotlin conclusion domain, the shared M2 free-core UI, one evidence-change
variant, and one latest local comparison-history entry. The challenge has
automated coverage and partial Android runtime evidence; iOS host/runtime and
full accessibility sign-off remain open for the new path.
The Android host label is Evidrilo; the committed icon artwork is present, but
the final provenance-verified icon package and public submission assets are
not ready. The package namespace `dev.nextgen.mobile` is retained
temporarily until a deliberate rename milestone. Android semantics/text-scale
and offline/relaunch observations are local partial evidence; the fresh local
PostgreSQL/RLS harness is recorded by E108, the migration ledger by E109, and
the worker process boundary by E110; iOS host and full accessibility sign-off
remain open. The optional sync/analytics/content-reader boundaries are now
implemented behind explicit account, consent, HTTPS, and version checks; their
managed and runtime observations remain separate gates.

The retained `askready`, `feedback`, `model`, and `practice` packages are legacy
foundation material. They are not part of the active Evidrilo user flow and
must not be shown as submission evidence. Cleanup or namespace renaming is a
separate deliberate milestone.

## Goals

- Share Kotlin domain logic and Compose UI between Android and iOS.
- Keep the first product small enough to finish and demonstrate reliably.
- Keep RevenueCat behind a narrow billing boundary.
- Make the public repository understandable and runnable from a clean clone.
- Keep the submission lane local-first while building the optional platform lane
  behind explicit milestones and independent verification.

## Current public repository tree

```text
evidrilo/
├── README.md
├── CONTRIBUTING.md
├── LICENSE
├── .gitignore
├── gradlew
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
│
├── gradle/
│   ├── libs.versions.toml
│   └── wrapper/
│       ├── gradle-wrapper.jar
│       └── gradle-wrapper.properties
│
├── apps/
│   ├── mobile-shared/
│   │   ├── build.gradle.kts
│   │   └── src/
│   │       ├── commonMain/
│   │       │   └── kotlin/dev/nextgen/mobile/
│   │       │       ├── App.kt
│   │       │       ├── EvidriloApp.kt
│   │       │       ├── EvidriloPresentation.kt
│   │       │       ├── storage/
│   │       │       │   ├── ConclusionSessionStore.kt
│   │       │       │   ├── ConclusionSessionStoreFactory.kt
│   │       │       │   ├── ConclusionHistoryStore.kt
│   │       │       │   └── ConclusionHistoryStoreFactory.kt
│   │       │       ├── billing/
│   │       │       │   ├── BillingModels.kt
│   │       │       │   └── PlatformBillingGateway.kt
│   │       │       └── domain/
│   │       │           ├── askready/
│   │       │           ├── feedback/
│   │       │           ├── model/
│   │       │           └── practice/
│   │       │
│   │       ├── commonTest/
│   │       │   └── kotlin/dev/nextgen/mobile/
│   │       │       ├── EvidriloPresentationTest.kt
│   │       │       └── storage/ConclusionSessionCodecTest.kt
│   │       │
│   │       ├── androidMain/
│   │       │   └── kotlin/dev/nextgen/mobile/
│   │       │       └── storage/ConclusionSessionStore.android.kt
│   │       │
│   │       ├── iosMain/
│   │       │   └── kotlin/dev/nextgen/mobile/
│   │       │       └── storage/ConclusionSessionStore.ios.kt
│   │       │
│   │       └── jvmMain/       # no-op store for JVM/Desktop checks
│   │
│   └── android/
│       ├── build.gradle.kts
│       └── src/main/
│
├── apps/ios/                # committed Xcode host; runtime sign-off remains open
│
├── contracts/               # versioned public REST/JSON contracts and fixtures
│
├── platform/                # optional post-gate platform lane
│   ├── api/                 # ASP.NET Core API
│   ├── database/            # migrations, local PostgreSQL/RLS harness
│   ├── worker/              # bounded .NET worker process boundary
│   └── api.Tests/           # API/unit/integration tests
│
├── infra/                   # repository-owned local container preparation
│   ├── docker/              # API and worker Dockerfiles
│   ├── environments/local/  # local Compose only
│   └── README.md            # handoff and non-production boundary
│
├── docs/
│   ├── README.md
│   ├── decisions.md
│   ├── development.md
│   ├── release.md
│   ├── testing.md
│   ├── architecture/
│   │   ├── platform-decision.md
│   │   ├── repository-structure.md
│   │   └── revenuecat.md
│   └── product/
│       └── m0-product-contract.md
│
└── local.properties.example
```

## Responsibility of each area

### `apps/mobile-shared`

The shared Kotlin Multiplatform module. It owns the shared Compose UI, domain
logic, scenario data, local session snapshot contract, and the RevenueCat KMP
adapter. The M2 free core is local-first and does not initialize billing.

### `apps/android`

The Android application entry point, Android manifest, Android-specific
configuration, and Android RevenueCat public API key configuration.

### `apps/ios`

The thin Xcode host application and iOS-specific configuration. It presents the
shared `App()` composable through `MainViewController()` and invokes the
Gradle framework embed task. The shared module compiles for
`iosSimulatorArm64` and `iosArm64`, but macOS/Xcode runtime evidence is still
required before claiming iOS launch. It must not contain duplicated scenario
logic or business rules.

### `domain`

Pure Kotlin models and deterministic behavior. This layer should not depend on
Compose, Android APIs, iOS APIs, RevenueCat, or network services.

### `data`

Local persistence and repositories. The M2 session store persists only the
bundled-case draft snapshot, while the separate history store retains one
latest base-versus-challenge comparison; a remote backend is deliberately out
of scope for this free-core lane.

### `billing`

The only application-facing boundary for monetization. Screens depend on
`BillingGateway`, while RevenueCat-specific calls remain inside the adapter.

### `account`

The optional provider-neutral account boundary. It owns email/password,
password recovery, Google authorization-code PKCE, callback parsing, secure
session lifecycle, and account presentation. Supabase REST, browser launch,
HTTP, Keystore, and Keychain details stay behind platform actuals; the free
evaluator never depends on this package.

### `commonTest`

Tests for scenario selection, feedback rules, premium access decisions, and
progress behavior that should be identical on both platforms.

### `platform`

The optional post-gate platform lane. `api` owns server-side identity,
authorization, health, and optional sync through ASP.NET Core/Npgsql.
`contracts` contains public synthetic JSON contracts, `database` contains
forward-only PostgreSQL/RLS migrations, and `worker` is the separate bounded
projection/job process boundary. Platform code must never make the local free
evaluator require an account, network, or backend.

## Dependency direction

```text
ui ────────────────┐
data ──────────────┼──> domain
billing adapter ───┘

ui ───> billing interface
RevenueCat adapter ───> RevenueCat KMP SDK
apps/android / apps/ios ───> apps/mobile-shared
```

The `core` directory must remain small. It is not a general-purpose dumping
ground; new code belongs in `domain`, `data`, `billing`, or `ui` unless its
cross-cutting role is clear.

## Configuration and secrets

- Commit only example configuration.
- Ignore `local.properties`, local signing files, and machine-specific Xcode
  configuration.
- Keep `local.properties.example` and an iOS configuration example in the
  repository.
- Never commit credentials, private tokens, signing certificates, or personal
  user data.
- RevenueCat public SDK keys are platform-specific and must be documented in
  the setup guide; production keys and store credentials must not be confused
  with private server secrets.

## Ideal-structure audit

| Concern | Assessment | Decision |
|---|---|---|
| Android and iOS support | Required by the chosen direction | Use KMP with separate `apps/android` and `apps/ios` entry points |
| Shared UI | Appropriate for a small demo and consistent experience | Use Compose Multiplatform in `commonMain` |
| Shared business logic | Essential for consistent scoring and feedback | Keep it in pure Kotlin `domain` |
| RevenueCat | Required by Shipaton and available for KMP | Isolate it in `billing`; validate the current SDK version during scaffold |
| Clean checkout | Necessary for a public repository | Include Gradle Wrapper, README, public docs, license, and sample configuration |
| Testing | Core logic must behave identically on both platforms | Start with `commonTest`; add platform tests only where needed |
| Product scope | Submission lane remains local-first while platform lane is isolated | Keep platform calls optional and gate each new backend capability |
| CI | Useful, but not needed before the first local scaffold | Add Android and macOS/iOS workflows after the first clean build |
| Final app name | Evidrilo approved | Keep temporary package names until the rename milestone |

## What is intentionally deferred

- Managed production account configuration and cloud database activation.
- Live projection/job execution and deployment wiring.
- AI-generated feedback as a required dependency.
- Voice recording and speech-to-text.
- Social sharing and community features.
- Desktop or web targets.
- Kotlin Multiplatform-specific abstractions beyond what the first flow needs.
- App Store and Google Play release automation.

## Foundation definition

The first scaffold is considered structurally sufficient when it can provide:

1. One shared Compose screen that opens on Android and iOS.
2. One scenario model and one deterministic feedback flow.
3. A shared `BillingGateway` with a RevenueCat implementation boundary.
4. A common test for the scenario/feedback logic.
5. README instructions that distinguish Android local validation from the
   macOS/Xcode requirement for full iOS validation.

## References

- [JetBrains: New KMP default project structure](https://blog.jetbrains.com/kotlin/2026/05/new-kmp-default-structure/)
- [RevenueCat: Kotlin Multiplatform installation](https://www.revenuecat.com/docs/getting-started/installation/kotlin-multiplatform)
- [RevenueCat: SDK configuration and Test Store](https://www.revenuecat.com/docs/getting-started/configuring-sdk)
