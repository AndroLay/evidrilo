# Release Readiness

Current authority: [Evidrilo Source of Truth](../research/next-gen/SOURCE_OF_TRUTH.md).

This checklist is a gate, not a prediction of contest results. Every checked
item should have a reproducible command, a reviewer note, or a captured runtime
observation with its environment and date.

## Synchronized external gates — 13 September 2026

The following gates remain outside repository-only completion in this release:

- Android target runtime, assistive-technology review, and signed Play upload;
- macOS/Xcode iOS host/runtime, native secure storage, and accessibility review;
- RevenueCat Test Store purchase/restore/revoke matrix, managed Paywall
  configuration, and real App Store/Google Play/Customer Center rendering;
- managed Supabase Auth/JWKS/RLS/migrations and authorized database replay;
- managed Supabase/Google/SMTP account configuration, real auth/recovery
  flows, and API claim/deletion verification for the optional account lane;
- deployment HTTPS/proxy/secrets, backups, restore, alerts, rollback, and load;
- human validation, eligibility/guardian checks, public repository/store
  publication, and Devpost submission.

These gates must be copied with the same wording into the roadmap and current
status records; none is implied by local source or synthetic tests.

## Mobile release candidate preparation

The repository-owned release configuration is now explicit for both platforms:

- Android `release` enables R8 and resource shrinking, rejects a debug signing
  config, disables app backup and cleartext transport, and exposes
  `:androidApp:verifyReleaseSigning` as a fail-closed upload gate.
- iOS `Release` uses `Apple Distribution`, removes preview-only settings, and
  takes `TEAM_ID` from an ignored local xcconfig. The template is
  `iosApp/Configuration/Release.xcconfig.example`.
- The release configuration contract is checked by
  `bash scripts/check-mobile-release.sh .`.

An unsigned Android AAB can be built locally for packaging verification:

```bash
./gradlew --no-configuration-cache :androidApp:bundleRelease
```

Before Play upload, provide all four Android signing settings through the
ignored `local.properties` or `EVIDRILO_ANDROID_RELEASE_*` environment
variables, then run:

```bash
./gradlew :androidApp:verifyReleaseSigning :androidApp:bundleRelease
bash scripts/check-mobile-release.sh . --require-android-artifact
```

The Linux workspace has no Xcode toolchain. On macOS, use the checked-in
`Evidrilo` scheme with `Release.xcconfig`/`Local.xcconfig`, run the free flow
first, then archive and export with the owner's Apple distribution account.
The archive, signing, device runtime, StoreKit, and Play upload remain
external evidence gates.

## Release versioning and artifact handoff

Every Android/iOS release must use one release record and one human-readable
marketing version. The release is not ready for distribution until both
platform artifacts pass their platform-specific gates.

- Android `versionName` must match iOS `MARKETING_VERSION` (for example,
  `0.1.0`). Android `versionCode` and iOS `CURRENT_PROJECT_VERSION` are
  platform-specific positive build numbers and must increase for every new
  upload to that platform; never reuse a published build number.
- Android direct-download distribution requires a signed release APK. The
  signed AAB is the Play Console upload artifact; an unsigned local AAB is
  packaging evidence only and must not be handed to users.
- iOS distribution requires a signed archive/export produced with the owner's
  Apple distribution identity and provisioning configuration. An arbitrary
  `.ipa` copied to GitHub is not a universal iPhone installation method.
- Release artifacts, checksums, and release notes may be attached to a GitHub
  Release, but keystores, provisioning profiles, passwords, tokens, archives
  containing private symbols, and provider configuration remain outside the
  repository.

Before handoff, the owner must confirm the following sequence:

1. Set the same marketing version in Android `androidVersionName` and iOS
   `MARKETING_VERSION`; increment each platform's build number.
2. Build and verify Android signing, then produce both the signed APK for
   direct download and the signed AAB for Play upload.
3. On macOS, archive/export the iOS `Release` scheme, install the resulting
   build on the target test device, and verify the free flow before any premium
   or provider-dependent flow.
4. Run `bash scripts/check-mobile-release.sh .` and keep the Android/iOS
   artifact paths and checksums in private release evidence.
5. Publish only after the install, launch, version display, free-flow,
   accessibility, and signing checks pass on each claimed platform.

If any version, build number, signing, installation, or runtime check fails,
the release remains `NOT READY` and no download link should be announced.

## Product

- [x] The build demonstrates the bounded Evidrilo conclusion-chain flow. E106
      and E107 record Android hierarchy observations.
- [x] Free-core behavior works without an account, upload, or billing key. The
      local network-disabled pass completed the free flow.
- [x] Unsupported input produces an explicit abstention instead of a guess.
      Common adversarial/domain tests cover this boundary.
- [x] Initial draft history is immutable and only one revision is accepted.
      Common reducer tests and the Android base flow cover the boundary.
- [x] The evidence-change challenge starts with a fresh draft and visibly omits
      the removed observation.
- [x] One latest comparison-history entry is local-only and can be cleared.
      E106/E107 record history, relaunch, and clear observations.
- [x] Product copy does not claim scientific truth, academic grading, or proven
      learning outcomes.

## Engineering

- [ ] A clean clone can run the documented Gradle checks. The repository now
      provides `bash scripts/verify-local.sh`; the current workspace run uses
      a temporary JDK 21 and local Gradle cache, so clean-clone evidence remains
      open. Android builds additionally require a locally configured SDK via
      `local.properties` or `ANDROID_HOME`; the ignored file is never exported.
- [ ] The public GitHub workflow completes on the exact public checkout. The
      workflow and package-boundary checker are present, but hosted execution
      is not observed from this workspace.
- [x] The tracked-tree GitHub safety guard is present and tested. It checks
      actual Git metadata, the index, working-tree secret patterns, ignored
      local probes, and the explicit public allowlist before a public push.
      This workspace cannot run the pass because its `.git` directory is empty;
      the allowlisted exporter is the verified route here.
- [x] Common tests cover decision-table, adversarial, billing-state, sync,
      content, analytics, and premium-case fixtures. E120 reports `221/221`
      JVM tests with zero failures/errors, `56/56` Node boundary tests across 7
      files, API
      `110/110`, worker `5/5`, shared iOS target compilation, fresh
      PostgreSQL/worker smoke, and deployment/public-export checks.
- [x] The owner-approved V8/V7 shared Compose UI surfaces are implemented and
      the JVM desktop walkthrough observed the primary routes and premium
      unavailable state. Current Android UI runtime and iOS host/runtime remain
      separate open gates.
- [x] Android debug build is verified on the intended environment. The API 35
      `shipatonApi35` AVD installed and launched the APK.
- [x] Android release configuration and an optimized unsigned AAB are locally
      verified. R8, resource shrinking, release lint, manifest safety, and the
      no-debug-signing guard pass; signing is intentionally owner-local.
- [x] The owner-authorized RevenueCat Test Store catalog is recorded in
      [E116](../audit/evidence/evidrilo-revenuecat-dashboard-audit-2026-09-12.md).
      E116 historically observed `evidrilo_pro` and `monthly`, `yearly`, and
      `lifetime`; the app-side allowlist now rejects `lifetime`, while package
      removal/disablement in the dashboard remains an open G4 migration before
      transaction validation.
- [x] Repository-owned deployment preparation is guarded by
      `bash scripts/check-deployment.sh .`: non-root API/worker images,
      checksum-ledger Compose ordering, local Auth compatibility bootstrap,
      health checks, and an overridable host port were built and smoke-tested.
      This is local preparation only; production HTTPS, secrets, backups,
      alerts, rollback, and load evidence remain open.
- [ ] iOS host is run on macOS/Xcode; the current Linux checks validate the
      plist/config references and both shared Kotlin/Native targets, but
      compilation alone is not used as proof.
- [x] iOS Release project configuration is distribution-oriented and has a
      local `TEAM_ID`/xcconfig handoff; archive and export are not observed in
      this Linux workspace.
- [ ] No debug-only path is required for the demonstrated user flow.

## Billing

- [x] RevenueCat SDK initialization is isolated behind the billing boundary;
      the active UI consumes `BillingGateway` and does not call SDK types.
- [x] The adapters load/select all configured packages, map verified account
      UUIDs with RevenueCat customer identity, and reset identity on sign-out.
- [ ] Test Store purchase and entitlement behavior are verified on each target
      platform that claims support.
- [ ] Cancellation, failure, pending/unknown, restore, relaunch, and duplicate
      taps have explicit behavior in the local billing boundary; the Test
      Store matrix remains unrun on each claimed platform.
- [x] No production key, signing file, receipt, or customer data is committed
      in the current local source/configuration scan.

## Optional account lane

- [x] Email/password sign-in and account creation use the managed Auth provider
      directly; the Evidrilo API never receives a password.
- [x] Password-reset request, confirmation-required, rate-limit, offline,
      expiry, sign-out, and deletion-preparation states are explicit.
- [x] Google SSO uses the registered `evidrilo://auth/callback` redirect,
      authorization-code PKCE, single-use state, and secure session storage.
- [x] Android/iOS/desktop configuration fails closed when values are empty or
      secret-shaped; no client credential is tracked here.
- [ ] Disposable-account verification of email confirmation, Google callback,
      reset-link recovery, refresh rotation, revoke/sign-out, and API ownership
      remains open until owner-authorized provider and runtime access exists.
- [ ] Android Keystore, iOS Keychain, and iOS host runtime observations remain
      open.

## Quality and accessibility

- [ ] Text scaling, semantic labels, focus order, contrast, and tap targets are
      reviewed.
- [ ] Offline free-core behavior and billing failure recovery are checked.
- [ ] Screenshots and icon assets come from the same build being demonstrated.
- [ ] Third-party media and music have documented permission or are not used.

## Submission and claims

- [ ] The demo video is within the applicable time limit and shows the real app.
- [ ] The repository is public, open source, contains the required source/assets/
      instructions, and has a visible open-source license when submitted.
- [ ] The submission materials are in English or include an English translation.
- [ ] The icon is 1024×1024 and the screenshot is 1179×2556 without a device
      frame, both from the demonstrated build.
- [ ] Original-work, third-party-license, trademark, and media checks pass.
- [ ] Student eligibility, academic email, and guardian consent where required
      are confirmed privately by the submitter.
- [ ] Every claim in the description and video can be traced to evidence.
- [ ] Store, account, publication, and external communication actions are
      explicitly authorized by the owner.

## Repository-owned completion tracking

The current implementation plans are
[`docs/superpowers/plans/2026-09-11-evidrilo-platform-completion.md`](superpowers/plans/2026-09-11-evidrilo-platform-completion.md)
and
[`docs/superpowers/plans/2026-09-12-evidrilo-open-gates-closure.md`](superpowers/plans/2026-09-12-evidrilo-open-gates-closure.md).
It is an engineering completion plan, not a substitute for iOS,
RevenueCat, managed Supabase, deployment, human-validation, or submission
evidence.

E117 records the repository-owned finishing pass. It adds non-root deployment
images and local Compose ordering, repository-boundary checks and public-package
export, submission asset validation, and accessibility disclosure semantics.
The full local harness passed `186/186` JVM tests, `52/52` Node checks, API
`108/108`, worker `5/5`, Android debug assembly, and both shared iOS targets.
The local Compose smoke and Docker builds passed; readiness was intentionally
degraded without managed Supabase. A public candidate export also passed its
boundary checks and built JVM plus Android with an explicit SDK path. None of
this is production deployment, store, human, or publication evidence.

E118 records the all-area repository closure. It adds consent-gated metadata
sync, exact cursor/version checks, mobile analytics/support/onboarding states,
and content/sync/support/recovery runbooks. The app-side premium allowlist now
rejects lifetime and accepts monthly/yearly only; the dashboard package
migration and transaction matrix remain open.

E119 records the analytics funnel closure: typed practice-start, paywall-view,
monthly/yearly premium-action, and bounded client-error events are wired to the
shared app and persisted through migration 021. Renewal, refund, and reversal
remain webhook-owned signals. Fresh verification passes `221/221` JVM,
`54/54` Node, API `110/110`, worker `5/5`, shared iOS target compilation, and
PostgreSQL/RLS smoke; provider, device, managed deployment, human, and
publication gates remain open.

E120 records the server-owned-write closure: migration 022 removes client
INSERT paths for analytics and sync, the database smoke verifies rejection, and
nullable analytics properties are normalized for idempotent retries across app
versions. Fresh verification passes `221/221` JVM, `56/56` Node, API `110/110`,
worker `5/5`, shared iOS target compilation, and PostgreSQL/RLS smoke through
migration 022. Provider, device, managed deployment, human, and publication
gates remain open.

E121 records the recommendation-write closure: migration 023 removes the
remaining client INSERT policy, and fresh PostgreSQL RLS smoke verifies that
recommendation interactions use the validated API boundary. Verification passes
`221/221` JVM, `57/57` Node, API `110/110`, worker `5/5`, shared iOS target
compilation, and PostgreSQL/RLS smoke through migration 023. Provider, device,
managed deployment, human, and publication gates remain open.

E122 fixes the billing configuration truth boundary: `BillingConfigured` now
requires webhook authentication and the configured entitlement identifier. The
full API suite passes `111/111`; provider, device, managed deployment, human,
and publication gates remain open.

E123 closes the unknown-state billing presentation escape. The fallback now
filters offers through the approved monthly/yearly allowlist and keeps the
unresolved state non-purchasable. Local verification passes `222/222` JVM,
`57/57` Node, API `111/111`, worker `5/5`, shared iOS target compilation, and
fresh PostgreSQL/worker smoke; provider, device, managed deployment, human,
and publication gates remain open.

E124 closes automatic redirect handling in authenticated mobile transports.
Android/JVM reject `HttpURLConnection` redirects and iOS rejects redirects in
its task delegate. Verification passes `223/223` JVM, `57/57` Node, API
`111/111`, worker `5/5`, shared iOS target compilation, and fresh
PostgreSQL/worker smoke; device, managed TLS/proxy, provider, human, and
publication gates remain open.

E125 closes the missing Android `INTERNET` manifest permission and adds a
focused check for the Android/iOS `evidrilo://auth/callback` host wiring. The
focused check passes `3/3`; the full local harness passes `223/223` JVM,
`60/60` Node, API `111/111`, worker `5/5`, shared iOS target compilation, and
fresh PostgreSQL/worker smoke; `:androidApp:assembleDebug` also passes.
Device, provider, managed deployment, human, and publication gates remain open.

E126 closes a real account-lifecycle security gap: configured API deployments
now reject protected-route use by a verified session after server-owned account
deletion with `410 ACCOUNT_DELETED`, while the deletion request remains
idempotently retryable. The focused lifecycle suite passes `5/5`; the current
local boundary passes `223/223` JVM, `60/60` Node, API `116/116`, worker `5/5`,
shared iOS target compilation, Android debug assembly, fresh PostgreSQL/worker
smoke, deployment checks, and public export. Managed provider, deployment,
device, human, and publication gates remain open.

E127 also covers legacy accounts without an `account_profiles` row: a completed
server-owned deletion ledger entry now blocks API access. The focused lifecycle
suite passes `9/9`; the current local boundary passes `223/223` JVM, `60/60`
Node, API `120/120`, worker `5/5`, shared iOS target compilation, Android debug
assembly, fresh PostgreSQL/worker smoke with the missing-profile fixture,
deployment checks, and public export. Managed provider, deployment, device,
human, and publication gates remain open.

E128 adds migration 024 so every new Auth account provisions an
`account_profiles` row through an idempotent server-owned trigger. The local
database smoke passes provisioning, checksum replay, RLS, and the legacy
missing-profile fallback; the current boundary passes `223/223` JVM, `61/61`
Node, API `120/120`, worker `5/5`, shared iOS target compilation, Android debug
assembly, deployment checks, and public export. Managed Supabase trigger
behavior, provider, deployment, device, human, and publication gates remain
open.

E129 binds asynchronous RevenueCat identity, refresh/offer, purchase, and
restore callbacks to the initiating request generation and account ID. The
focused billing suite passes `9/9`; the current local boundary passes
`226/226` JVM, `61/61` Node, API `120/120`, worker `5/5`, shared iOS target
compilation, Android debug assembly, deployment checks, and public export.
Provider transactions, native account switching, deployment, device, human,
and publication gates remain open.

E130 adds migration 025 to revoke public execution from the sync and analytics
projection `SECURITY DEFINER` trigger functions. The fresh PostgreSQL smoke
passes the trigger privilege assertion and checksum replay through 025. The
managed database replay, role ACL, backup/restore, deployment, device, human,
and publication gates remain open.

E131 closes the generated-output publication boundary for root `.tmp` compiler
artifacts. E132 closes the migration-runner concurrency race by keeping ledger
creation, checksum validation, and migration application under one advisory
transaction lock. Local regression evidence passes; managed replay, backup,
restore, deployment, device, human, and publication gates remain open.

E133 makes production CORS fail closed when no explicit origin list is
provided. The API configuration and full API suite pass; actual staging/production
origins, proxy, TLS, deployment, device, human, and publication gates remain
open.

E134 aligns the RevenueCat Test Store runbook with the approved monthly/yearly-
only catalog and adds a pre-matrix catalog guard. The dashboard, transaction,
deployment, device, human, and publication gates remain open.

E135 makes API readiness migration-aware: a database is ready only when the
current migration ledger entry `025_trigger_function_privileges` is visible.
API, PostgreSQL, and temporary Compose health checks pass. Managed deployment,
backup/restore, device, human, and publication gates remain open.

E136 corrects the RevenueCat runbook so its approved catalog is not presented
as an observed dashboard state. The focused contract passes `14/14`; dashboard,
transaction, deployment, device, human, and publication gates remain open.

E137 synchronizes the active backend execution register with the current local
verification boundary. Its focused contract passes `15/15`; managed database,
provider, runtime, deployment, device, human, and publication gates remain
open.

E138 adds platform-scoped RevenueCat managed Paywall and Customer Center
adapters with a key/catalog/platform guard and JVM fallback. The local boundary
passes `229/229` JVM, `70/70` Node, API `125/125`, and worker `5/5`, while
provider configuration, transactions, and real-device UI rendering remain
open.

E139 extends the published case contract and readers with canonical objective,
observations, limitations, feedback rules, and non-empty challenge variants.
The latest local boundary passes `231/231` JVM, `71/71` Node, API `130/130`,
and worker `5/5`; the local PostgreSQL content seed/read and shared iOS target
compilations pass. Managed content, device, provider, staging, and release
evidence remain open.

E140 corrects an architecture-note claim that was phrased as observed RevenueCat
dashboard state and adds a regression guard. The direct contract suite passes
`18/18`; the full public boundary is now `72/72`. Provider configuration,
transactions, managed content, device, staging, and release evidence remain
open.

E141 adds the case lifecycle audit boundary. Migration `026_case_lifecycle_audit`
records actor-bound append-only creation and successful transitions, rejects
audit mutation/deletion, and anonymizes deleted actors while retaining history.
Migration checks pass `27/27`, API `130/130` passes, and fresh PostgreSQL smoke
passes; managed replay, admin UI, editorial operation, device, staging, and
release evidence remain open.

E142 adds the bounded, role-scoped lifecycle-audit read endpoint with a closed
versioned response and deletion-safe nullable fields. API `137/137`, Node
`74/74`, and migration `27/27` checks pass; managed database replay, admin UI,
editorial operation, device, staging, and release evidence remain open.

E143 makes recommendation projection selection fail closed for invalid counter
shapes and uses overflow-safe arithmetic. The focused recommendation suite
passes `12/12` and the full API suite passes `138/138`; mobile recommendation
integration remains pending explicit design approval. Managed projection,
device, staging, and release evidence remain open.

E144 requires a non-empty skill identifier on every selected recommendation
candidate so recommendation explanations cannot be empty. The focused suite
passes `13/13` and the full API suite passes `139/139`; managed content,
mobile, device, staging, and release evidence remain open. See the [E144
record](../audit/evidence/evidrilo-recommendation-candidate-validation-2026-09-13.md).

E145 verifies the complete local verifier and the allowlisted exporter. The
current run passes Gradle, Node `74/74`, API `139/139`, worker `5/5`,
deployment, and the exported candidate checks. The workspace Git checker
remains blocked by unusable Git metadata, so index cleanliness, remote
visibility, push, and submission are still unverified. See
the [E145 record](../audit/evidence/evidrilo-public-package-preflight-2026-09-13.md).

E146 closes the repository-owned recommendation integration boundary. The
consented verified-session path parses a closed response, maps only the exact
bundled `M0_T2:1` case, preserves the free local fallback, and uses separate
idempotency UUIDs for `shown`, `accepted`, and `dismissed` events. Focused
recommendation tests pass `28/28`, the full Kotlin/JVM suite passes `261/261`,
and the available shared targets compile. Native runtime/accessibility,
RevenueCat transactions, managed deployment/content, human validation, and
submission remain open. See the
[E146 record](../audit/evidence/evidrilo-recommendation-mobile-integration-2026-09-13.md).

E147 locks AI to optional, non-grading assistance only: deterministic feedback
explanation, one evidence-scope/limitation reflection question, or a
meaning-preserving language alternative. Focused AI API tests pass `39/39` and
the full API suite passes `139/139`; the default provider remains disabled.
Provider approval/configuration, legal/privacy, cost, mobile runtime, managed
deployment, and human validation remain release gates. See the
[E147 record](../audit/evidence/evidrilo-ai-bounded-assistance-2026-09-13.md).

E148 verifies the local recovery preparation with a disposable PostgreSQL
container and a custom-format dump/restore into a fresh database. It is useful
for repository regression only; managed backup retention, IAM, point-in-time
recovery, staging, disaster recovery, and production rollback still require an
authorized environment. See the
[E148 record](../audit/evidence/evidrilo-local-backup-restore-2026-09-13.md).

E149 hardens the billing readiness predicate to the canonical `evidrilo_pro`
entitlement. Misconfigured or unrelated entitlement IDs fail closed; focused
configuration tests pass `11/11` and the full API suite passes `140/140`. This
does not replace RevenueCat dashboard,
Test Store, or production verification. See the
[E149 record](../audit/evidence/evidrilo-billing-entitlement-allowlist-2026-09-13.md).

E150 synchronizes the operational RevenueCat, backend, current-status,
platform README, root README, and all-area audit records and guards them with a `22/22`
repository contract; the final Node boundary passes `77/77`. It does not change the open
provider, device, managed deployment, human, or publication gates. See the
[E150 record](../audit/evidence/evidrilo-runbook-status-synchronization-2026-09-13.md).

E151 closes a server-side billing allowlist gap: active webhook grants now
require exact `monthly` or `yearly` products, and a signed `lifetime` event is
ignored before persistence. Focused BillingTests pass `10/10` and the full API
suite passes `141/141`; this remains local repository evidence and does not
close dashboard, Test Store, device, or production gates. See the
[E151 record](../audit/evidence/evidrilo-billing-product-allowlist-2026-09-14.md).

E152 closes the local account-deletion owner invariant. Migration
`027_account_deletion_owner_guard` rejects deletion of a sole active
organization owner until ownership is transferred, and the API exposes
`409 OWNER_TRANSFER_REQUIRED`. Fresh PostgreSQL smoke and the existing deletion
assertions pass; managed database, Auth, deployment, runtime, human, and
submission gates remain open. See the [E152 record](../audit/evidence/evidrilo-account-deletion-owner-guard-2026-09-14.md).

E153 hardens the local client/request boundary. Canonical product-aware billing,
verified-session gates for content/sync/analytics, actionable owner-transfer
copy, bounded chunked webhook input, and caller-cancellation propagation are
now covered. The current local boundary passes Kotlin `267/267`, API `144/144`,
Node `77/77`, worker `5/5`, shared iOS target compilation, PostgreSQL smoke,
and worker smoke. Provider, native runtime, managed, staging, production,
human, and submission gates remain open. See the [E153 record](../audit/evidence/evidrilo-client-boundary-hardening-2026-09-14.md).

E154 adds an 8 KiB bound for custom-scheme auth callbacks before parsing or
pending retention. The full local verifier and shared iOS target compilation
pass, with API `144/144`, Node `77/77`, and worker `5/5`; native callback
runtime and provider-backed account verification remain open. See the [E154 record](../audit/evidence/evidrilo-auth-callback-boundary-2026-09-14.md).

E155 requires one to 32 meaningful challenge variants in every accepted
authoring document. The focused content/authoring coverage passes `22/22` and
the full API suite passes `145/145`; managed content publication and all
runtime, provider, staging, human, and submission gates remain open. See the
[E155 record](../audit/evidence/evidrilo-content-challenge-required-2026-09-14.md).

E156 revalidates stored authoring JSON under the transition row lock before
`approved` or `published`. Focused content/authoring coverage passes `23/23`
and the full API suite passes `146/146`; managed database replay, content
publication, runtime, provider, staging, human, and submission gates remain
open. See the [E156 record](../audit/evidence/evidrilo-stored-content-transition-guard-2026-09-14.md).

E157 makes the Kotlin published-case reader reject an empty challenge-variant
array. Focused reader coverage passes `8/8`; the full Kotlin/JVM suite passes
`269/269`, JVM/Android compilation passes, and shared iOS targets compile.
Managed content publication, provider, device, staging, human, and submission
gates remain open. See the [E157 record](../audit/evidence/evidrilo-mobile-content-challenge-required-2026-09-14.md).

E158 adds `minItems: 1` to the published-case schema, and E159 aligns its
identifier, title, and skill-tag bounds with the API and Kotlin readers. The
focused contract and full local verifier pass with Kotlin `269/269`, Node
`77/77`, API `146/146`, worker `5/5`, and deployment checks. Cross-field
references remain runtime validation. See the [E159 record](../audit/evidence/evidrilo-case-schema-identifier-bounds-2026-09-14.md).

E160 corrects the content-authoring runbook's stale “optional challenge
variants” instruction and protects the required one-to-32 invariant with the
documentation contract. The focused contract and full local verifier pass;
managed editorial publication remains external. See the [E160 record](../audit/evidence/evidrilo-content-runbook-challenge-required-2026-09-14.md).

E161 aligns the recommendation schema with the mobile/API status-specific
response rules. The focused contract and full local verifier pass with Kotlin
`269/269`, Node `78/78`, API `146/146`, worker `5/5`, and deployment checks.
Provider and runtime evidence remain external. See the [E161 record](../audit/evidence/evidrilo-recommendation-schema-alignment-2026-09-14.md).

E162 synchronizes the current Node verification snapshot after the E161
contract addition. This documentation increment does not close any external
release gate. See the [E162 record](../audit/evidence/evidrilo-node-verification-snapshot-2026-09-14.md).

E163 hardens local conclusion-session restoration. Unknown enum values,
malformed escaped lists, and encoded values over 8 KiB fail closed before
parsing. The final local verifier passes Kotlin/JVM `270/270`, Node `78/78`,
API `146/146`, worker `5/5`, and deployment checks; runtime, provider,
managed, human, and submission gates remain external. See the [E163 record](../audit/evidence/evidrilo-local-session-decoder-hardening-2026-09-14.md).

E164 hardens the cohort aggregate boundary by requiring active organization
membership for every counted enrollment. Migration 028, migration contract
`28/28`, focused API readiness `4/4`, and fresh PostgreSQL/RLS smoke pass. This
does not close managed database, runtime, provider, staging, human, or
submission gates. See the [E164 record](../audit/evidence/evidrilo-cohort-active-membership-boundary-2026-09-14.md).

E165 hardens the aggregate role boundary by requiring the active organization
membership to have role `learner`. Migration 029, its focused contract, API
readiness `4/4`, and fresh PostgreSQL/RLS smoke pass. This does not close
managed database, runtime, provider, staging, human, or submission gates. See
the [E165 record](../audit/evidence/evidrilo-cohort-learner-role-boundary-2026-09-14.md).

E166 closes the last-owner role-change boundary. The organization is locked
before a grant can demote an active owner, and the full API suite passes
`147/147`. This does not close managed database, runtime, provider, staging,
human, or submission gates. See the [E166 record](../audit/evidence/evidrilo-last-owner-role-change-2026-09-14.md).

E167 closes the empty-account billing identity boundary. Signed events carrying
the all-zero `app_user_id` are ignored before persistence; the focused billing
suite passes `11/11` and the full API suite passes `148/148`. This does not
close managed database, runtime, provider, staging, human, or submission gates.
See the [E167 record](../audit/evidence/evidrilo-billing-empty-account-guard-2026-09-14.md).

E169 hardens the auth provider confirmation boundary, and E170 preserves
explicit membership role-assignment policy coverage. The focused auth and
membership checks pass `13/13` and `9/9`; provider, native runtime, managed,
staging, human, publication, and submission gates remain open. See the
[E169 record](../audit/evidence/evidrilo-auth-provider-type-boundary-2026-09-14.md)
and [E170 record](../audit/evidence/evidrilo-membership-role-assignment-coverage-2026-09-14.md).

E171 records the repository-owned optional offline-audio implementation. The
focused audio boundary passes `28/28`, while reviewed narration, device
playback, accessibility-service, and human audio-quality evidence remain open.
See the [E171 record](../audit/evidence/evidrilo-offline-audio-implementation-2026-09-14.md).

E172 binds local conclusion restoration to supported case IDs and rejects
unsafe secure-session account identifiers before downstream use. Focused
coverage passes `5/5` for each boundary; the full local Kotlin/JVM boundary is
`301/301` with Android and shared iOS target compilation passing. Provider,
native runtime, managed, staging, human, publication, and submission gates
remain open. See the [E172 record](../audit/evidence/evidrilo-local-session-boundary-2026-09-14.md).

E173 hardens the async sync and billing state boundaries. Queue writes require
the request's unchanged snapshot/generation, stale visible results are
ignored after account or consent changes, account restoration is ordered before
queue cleanup, and the approved monthly/yearly catalog is retained. Focused
coverage passes `18/18`; the full local Kotlin/JVM boundary is `306/306` with
Node `87/87`, API `151/151`, worker `5/5`, Android/shared-iOS compilation, and
deployment checks passing. Runtime, provider, managed, staging, human,
publication, and submission gates remain open. See the [E173 record](../audit/evidence/evidrilo-async-state-boundaries-2026-09-14.md).

E174 adds fail-closed checks at three repository boundaries. Sync rejects
non-monotonic or skipping cursors and unrelated push results; refresh clears a
stale secure session when email confirmation is withdrawn; and direct billing
presentations cannot target lifetime. Focused sync, auth, and billing suites
pass `18/18`, `14/14`, and `9/9`; the latest full local boundary is `311/311`
JVM tests with Node `87/87`, API `151/151`, worker `5/5`, Android/shared-iOS
target compilation, deployment checks, and audio asset checks passing. Runtime,
provider, managed, staging, human, publication, and submission gates remain
open. See the [E174 record](../audit/evidence/evidrilo-fail-closed-response-and-refresh-boundaries-2026-09-14.md).

E175 adds a fail-closed lower bound to sync pull validation. Each returned
change must be newer than the requested cursor and no greater than `nextCursor`;
the focused regression passes `29/29`, and the latest full local boundary passes
Kotlin/JVM `312/312`, Node `87/87`, API `151/151`, worker `5/5`,
Android/shared-iOS target compilation, deployment, and audio checks. This is
repository evidence only; runtime, provider, managed, staging, human,
publication, and submission gates remain open. See the [E175 record](../audit/evidence/evidrilo-sync-cursor-lower-bound-2026-09-14.md).

E176 adds repository-local hardening for request cancellation, malformed JSON,
duplicate sync command IDs, and cancellation recovery in recommendation/auth
state. Kotlin/JVM `317/317`, Node `87/87`, API `153/153`, worker `5/5`,
Android/shared-iOS compilation, deployment, and audio checks pass. This does
not close native runtime, provider, managed, staging, human, publication, or
submission gates. See the [E176 record](../audit/evidence/evidrilo-request-lifecycle-and-input-boundaries-2026-09-14.md).

E177 adds a fail-closed sync pagination guard. Pull responses must honor the
requested page size, including when `hasMore=true`. The latest local boundary
passes Kotlin/JVM `319/319`, Node `87/87`, API `153/153`, worker `5/5`,
Android/shared-iOS compilation, deployment, and audio checks. Native runtime,
provider, managed, staging, human, publication, and submission gates remain
open. See the [E177 record](../audit/evidence/evidrilo-sync-page-size-boundary-2026-09-14.md).

E178 hardens API full-match validation and staging environment normalization.
Identifiers, digests, actions, reason codes, case-version IDs, and request IDs
reject trailing-newline boundary bypasses; conventional `Staging` is accepted
as canonical `staging`. API `158/158` passes. Native runtime, provider,
managed staging, human, publication, and submission gates remain open. See the
[E178 record](../audit/evidence/evidrilo-api-input-and-staging-boundary-2026-09-14.md).

E179 hardens consent-bound sync orchestration. Failed or invalidated pulls do
not start a push, active sync jobs are cancelled on account/consent changes,
enablement has one state-driven launch owner, and failed queue clears preserve
truthful pending-count state. Kotlin/JVM `321/321` passes; runtime, provider,
managed, staging, human, publication, and submission gates remain open. See the
[E179 record](../audit/evidence/evidrilo-sync-consent-cancellation-boundary-2026-09-14.md).

E181 hardens the case-authoring transition boundary. Invalid schema, version,
and target-state values are rejected before storage; focused transition
coverage passes `2/2`. The full local verifier passes the current
Kotlin/Android build boundary, Node `93/93`, API `162/162`, worker `5/5`,
deployment, and asset checks. This does not change any device, provider,
managed, human, publication, or submission gate. See [E181 record](../audit/evidence/evidrilo-case-transition-contract-boundary-2026-09-15.md).

E183 hardens the shared Compose choice control. Radio-style choices use native
selectable semantics, multi-select evidence facts use native toggleable
semantics, and the card merges label/state information. Direct contracts pass
`25/25`; the full local verifier passes Node `95/95`, API `163/163`, worker
`5/5`, the current Kotlin/Android build boundary, Android release packaging,
deployment, and asset checks. This does not prove device runtime, TalkBack,
VoiceOver, provider, managed, human, publication, or submission evidence. See
[E183](../audit/evidence/evidrilo-choice-accessibility-semantics-2026-09-15.md).

E182 aligns the server sync-pull cursor boundary with the mobile parser. Cursors
outside `0..1_000_000_000_000_000` are rejected before storage; the focused
regression passes `1/1`. The full local verifier passes the current
Kotlin/Android build boundary, Node `93/93`, API `163/163`, worker `5/5`,
deployment, and asset checks. Runtime, provider, managed, staging, human,
publication, and submission gates remain open. See
[E182 record](../audit/evidence/evidrilo-sync-cursor-contract-boundary-2026-09-15.md).

E180 prepares the Android/iOS release boundary. Android's optimized release
bundle, fail-closed signing task, manifest defaults, and version inputs pass
local checks; iOS has a distribution-oriented Release template and an unsigned
CI host-build lane. Kotlin/JVM `324/324`, Node `93/93`, API `161/161`, worker
`5/5`, Android release packaging, shared iOS target compilation, and release
checks pass. Device runtime, archive/export, signing, store, provider, managed,
accessibility, human, publication, and submission gates remain open. See
[E180 record](../audit/evidence/evidrilo-mobile-release-candidate-preparation-2026-09-14.md).
