# Testing and Evidence

Current authority: [Evidrilo Source of Truth](../internal/research/next-gen/SOURCE_OF_TRUTH.md).

Testing is organized by what a check can actually prove. A green build is
valuable, but it is not evidence for claims outside its boundary.

## Local verification harness

Run the repository-owned baseline with:

```bash
bash scripts/ci/verify-local.sh
```

The harness uses a repository-local Gradle cache, runs the Kotlin/JVM/Android
and Node contract/migration checks, and runs the .NET API/worker checks when a
compatible .NET SDK is installed. Missing external toolchains are reported as
`UNAVAILABLE`; they are not converted into passing evidence. The harness does
not print configuration values or secrets.

E111 records the repository-owned completion verification on 11 September
2026: 137/137 JVM tests passed with zero failures or errors, plus JVM and
Android target compilation. That harness run passed 26 Node contract/migration
checks, API `90/90`, and worker `5/5` with the repository-ignored .NET 10 SDK
fallback. A separate shared-target compilation command also passed
Kotlin/Native `iosSimulatorArm64` and `iosArm64`; the Xcode host and iOS
runtime remain `NOT_RUN`.

The subsequent E112 UI increment passed 144/144 JVM tests with zero failures or
errors. The E113 account increment brings the full common suite to `174/174`
with zero failures/errors and passes the current JVM, Android debug,
`iosSimulatorArm64`, and
`iosArm64` compilation/assembly checks. It also records the desktop visual
  walkthrough of the V8/V7 shared Compose surfaces. The 15 September Android
  probe found ADB and the local AVD, but the emulator framework did not expose a
  usable `package` service and APK installation failed before app launch; the
  Android APK build is not runtime evidence. See the [E184 probe record](../audit/evidence/evidrilo-android-runtime-probe-2026-09-15.md).

The E114 whole-repository audit adds regression coverage for the confirmed
evaluator, persistence, toolchain, API, database, billing, sync, JSON-boundary,
and Supabase-claim defects. Its final repository run passed `175/175` common
JVM tests, API `103/103`, worker `5/5`, both Node contract/migration test files,
JVM/Android/shared-iOS compilation, and the local PostgreSQL/worker smoke
harnesses. These results remain local or synthetic evidence; provider, device,
and deployment gates below are unchanged.

E115 records the subsequent repository-owned closure pass. The current common
suite has 178 test cases; the active-case Home regression, public-package
checker, CI workflow syntax, and the Gradle harness's stale-cache protection
were verified locally. Fresh PostgreSQL and worker smoke outputs remain
passing. This does not replace Android UI, iOS host, accessibility, RevenueCat,
provider, human, publication, or submission evidence.

E116 records the RevenueCat catalog and adapter follow-up on 12 September 2026.
The authenticated dashboard inspection found the Evidrilo Test Store app,
`evidrilo_pro`, and the `default` offering with monthly, yearly, and lifetime
packages; it found no production store configuration, Paywall, or webhook. The
current app-side tests reject lifetime even if a provider returns it, and use
monthly/yearly reference prices. The Test Store transaction matrix and iOS
host/runtime remain unrun.

The current planning direction intentionally removes lifetime from the future
offer and uses monthly/yearly reference anchors of USD 1.00/month and USD
10.00/year. E116 remains historical dashboard evidence; catalog migration and
the monthly/yearly transaction matrix are still open.

The current mobile contract suite additionally covers consent-gated metadata
sync, queue/cursor recovery, exact response cursors, published content version
matching, analytics funnel/conversion/error taxonomy, analytics timestamps, and
safe HTTP token characters. These tests
prove local boundaries only, not managed API, device, or provider behavior.

The latest analytics-boundary verification records `221/221` JVM tests, `54/54`
Node boundary tests across 7 files, API `110/110`, worker `5/5`, shared iOS target
compilation, fresh PostgreSQL/worker smoke, deployment checks, Compose
configuration, and public-package export in [E119](../audit/evidence/evidrilo-analytics-funnel-closure-2026-09-13.md).

E120 adds the server-owned-write boundary: migration 022 removes client INSERT
paths for analytics and sync, cross-version analytics idempotency normalizes
nullable properties, and fresh PostgreSQL RLS smoke verifies the rejection.
The E120 verification record contains `221/221` JVM tests, `56/56` Node checks,
API `110/110`, worker `5/5`, shared iOS target compilation, deployment checks,
Compose configuration, and public-package export in
[E120](../audit/evidence/evidrilo-server-owned-write-boundary-2026-09-13.md).

E121 extends the same server-owned-write boundary to recommendation
interactions with migration 023. Fresh PostgreSQL RLS smoke rejects direct
client INSERT, and the current verification records `221/221` JVM tests,
`57/57` Node checks, API `110/110`, worker `5/5`, shared iOS target compilation,
and the worker smoke in
[E121](../audit/evidence/evidrilo-server-owned-recommendation-boundary-2026-09-13.md).

E122 fixes the RevenueCat configuration truth boundary: `BillingConfigured`
now requires webhook authentication and the entitlement identifier, so a
secret-only setup cannot appear ready. The focused regression is GREEN and the
full API suite passes `111/111`. See the
[E122](../audit/evidence/evidrilo-billing-config-readiness-2026-09-13.md).

E123 closes the unknown-state billing presentation escape: the fallback now
applies the approved monthly/yearly offer allowlist and cannot display or
select legacy `lifetime` while a transaction is unresolved. The focused
regression is RED before the change and GREEN after it. The current full local
verification passes `222/222` JVM, `57/57` Node, API `111/111`, worker `5/5`,
both shared iOS target compiles, fresh PostgreSQL/worker smoke, deployment
checks, and public export. See
[E123](../audit/evidence/evidrilo-billing-offer-allowlist-2026-09-13.md).

E124 closes automatic redirect handling in authenticated Android/JVM/iOS HTTP
transports. The redirect-policy regression is RED before the change and GREEN
after it; Android and JVM disable automatic redirects, while iOS rejects them
through its session delegate. The full local verification passes `223/223` JVM,
`57/57` Node, API `111/111`, worker `5/5`, both shared iOS target compiles,
fresh PostgreSQL/worker smoke, deployment checks, and public export. See
[E124](../audit/evidence/evidrilo-auth-redirect-safety-2026-09-13.md).

E125 closes a real Android host omission: the application manifest now declares
`android.permission.INTERNET`, and a focused mobile-platform check covers that
permission plus the Android/iOS `evidrilo://auth/callback` wiring. The focused
check is GREEN at `3/3`; the full local verification passes `223/223` JVM,
`60/60` Node across eight contract/boundary files, API `111/111`, worker `5/5`,
both shared iOS target compiles, fresh PostgreSQL/worker smoke, deployment
checks, public export, and a final `:androidApp:assembleDebug` artifact build.
See
[E125](../audit/evidence/evidrilo-android-network-permission-2026-09-13.md).

E126 closes the account-deletion access boundary. The focused regression is
RED before the middleware existed and GREEN at `5/5` after the configured host
wiring and server-owned tombstone guard were added. The current local boundary
passes `223/223` JVM, `60/60` Node, API `116/116`, worker `5/5`, shared iOS
target compilation, Android debug assembly, fresh PostgreSQL/worker smoke,
deployment checks, and public export. See
[E126](../audit/evidence/evidrilo-account-deletion-access-2026-09-13.md).

E127 closes the legacy-account variant of the deletion boundary. The status
contract regression is RED before the ledger fallback and GREEN at `9/9`; the
current local boundary passes `223/223` JVM, `60/60` Node, API `120/120`,
worker `5/5`, shared iOS target compilation, Android debug assembly, fresh
PostgreSQL/worker smoke with a missing-profile fixture, deployment checks, and
public export. See
[E127](../audit/evidence/evidrilo-account-deletion-ledger-fallback-2026-09-13.md).

E128 adds a RED/GREEN migration contract for new-account profile provisioning.
Migration 024's Auth after-insert trigger passes the fresh PostgreSQL smoke,
which emits `ACCOUNT_PROFILE_PROVISIONING_PASS` and retains the
`DELETION_WITHOUT_PROFILE_ASSERTION_PASS` legacy fixture. The current full
boundary passes `223/223` JVM, `61/61` Node, API `120/120`, worker `5/5`, both
shared iOS target compilations, Android debug assembly, deployment checks, and
public export. Managed Supabase trigger behavior remains unverified. See
[E128](../audit/evidence/evidrilo-account-profile-provisioning-2026-09-13.md).

E129 adds RED/GREEN coverage for a billing callback identity race. The
`BillingRequestGate` requires both a request generation and the initiating
account ID for identity, refresh/offer, purchase, and restore callbacks; close
and session changes invalidate stale work. The focused billing suite passes
`9/9`, and the fresh full local boundary passes `226/226` JVM, `61/61` Node,
API `120/120`, worker `5/5`, shared iOS target compilation, Android debug
assembly, deployment checks, and public export. Provider/device behavior
remains open. See
[E129](../audit/evidence/evidrilo-billing-callback-identity-2026-09-13.md).

E130 adds a RED/GREEN migration contract for trigger-only security-definer
privileges. Migration 025 revokes public execution from the sync and analytics
projection trigger functions; fresh PostgreSQL smoke passes
`TRIGGER_FUNCTION_PRIVILEGE_ASSERTION_PASS` and the existing migration,
checksum, RLS, lifecycle, deletion, and provisioning assertions. The current
Node boundary is `62/62`; managed role ACLs and deployment replay remain open.
See [E130](../audit/evidence/evidrilo-trigger-function-privileges-2026-09-13.md).

E131 adds RED/GREEN checks for generated-output publication boundaries: root
`.tmp` artifacts are excluded from Git staging, Docker contexts, public export,
and public-package checks. E132 adds RED/GREEN coverage for migration-runner
concurrency; ledger creation, checksum validation, and migration application
are serialized by one advisory transaction lock. The concurrent PostgreSQL
smoke and full `66/66` Node boundary pass. See
[E131](../audit/evidence/evidrilo-generated-output-boundary-2026-09-13.md) and
[E132](../audit/evidence/evidrilo-migration-runner-concurrency-2026-09-13.md).

E133 adds RED/GREEN coverage for production CORS configuration. A production
environment must provide explicit origins, while development/test keep the
localhost default; the focused configuration suite passes `10/10` and the
full API suite passes `121/121`. Staging proxy, TLS, and browser checks remain
open. See [E133](../audit/evidence/evidrilo-production-cors-boundary-2026-09-13.md).

E134 adds a contract for the RevenueCat Test Store runbook. It requires the
approved `evidrilo_pro` monthly/yearly-only catalog and rejects stale lifetime
offering/configuration instructions. The full public boundary is now `67/67`;
dashboard state and Test Store transactions remain open. See
[E134](../audit/evidence/evidrilo-revenuecat-runbook-catalog-boundary-2026-09-13.md).

E135 adds four readiness-state tests and verifies that `/health/ready` cannot
report a ready database before the current migration ledger entry
`025_trigger_function_privileges` is applied. The full API suite is now
`125/125`; the fresh PostgreSQL smoke and temporary Compose readiness/liveness
probe also pass. Managed replay and deployment checks remain open. See
[E135](../audit/evidence/evidrilo-database-readiness-ledger-2026-09-13.md).

E136 adds a documentation-boundary contract for provider runbooks. It rejects
language that overclaims RevenueCat dashboard configuration and requires the
runbook to identify dashboard state as an owner gate; the focused contract
passes `14/14`, and the full public boundary is now `68/68`. See
[E136](../audit/evidence/evidrilo-runbook-observation-boundary-2026-09-13.md).

E137 adds a contract for the active backend execution register. It requires the
current synchronization date and local API/worker/Node counts, preventing the
working register from silently reverting to the E110 snapshot. The focused
contract passes `15/15`, and the full public boundary is now `69/69`. See
[E137](../audit/evidence/evidrilo-backend-register-synchronization-2026-09-13.md).

E138 adds a platform-scoped RevenueCat UI dependency and adapters for managed
Paywall and Customer Center, with an availability policy and JVM fallback. The
new common tests bring the suite to `229/229`; the public boundary is `70/70`,
Android debug assembly passes, and both shared iOS targets compile. This is
compile/repository evidence only; provider configuration, transactions, and
real-device rendering remain open. See
[E138](../audit/evidence/evidrilo-revenuecat-managed-ui-2026-09-13.md).

E139 extends the published-case boundary with canonical objective, typed facts,
feedback rules, and non-empty challenge variants. The API and mobile readers
reject malformed or metadata-mismatched documents; the local PostgreSQL smoke
reads the canonical seed under RLS. The current common suite is `231/231`, the
public boundary is `71/71`, API tests are `130/130`, worker tests are `5/5`,
and shared iOS targets compile. Managed content, device, provider, and staging
evidence remain separate gates. See
[E139](../audit/evidence/evidrilo-canonical-content-reader-2026-09-13.md).

E140 removes an unobserved RevenueCat dashboard assertion from the architecture
note and adds a contract guard for future documentation changes. The direct
public contract suite passes `18/18`; the full public boundary is now `72/72`.
Provider dashboard state and transactions remain unobserved. See
[E140](../audit/evidence/evidrilo-revenuecat-documentation-claim-boundary-2026-09-13.md).

E141 adds migration `026_case_lifecycle_audit` and verifies that case creation
and each successful lifecycle transition produce one actor-bound append-only
event. Failed transitions do not produce events; audit update/delete attempts
are rejected; account deletion anonymizes the actor while retaining history.
The migration contract passes `27/27`, API tests pass `130/130`, and fresh
PostgreSQL smoke passes. The current full public boundary is `73/73`; managed
database replay and editorial operation remain open. See
[E141](../audit/evidence/evidrilo-case-lifecycle-audit-2026-09-13.md).

E142 adds the versioned `/v1/authoring/cases/{caseVersionId}/audit` response.
The API verifies active organization membership and limits reads to
author/reviewer/maintainer/owner roles; responses retain deletion-anonymized
nullable fields and are bounded to 128 events. API tests pass `137/137`, the
current full public boundary passes `74/74`, and migration checks remain
`27/27`. Managed replay, admin UI, and editorial operation remain open. See
[E142](../audit/evidence/evidrilo-case-lifecycle-audit-read-2026-09-13.md).

E143 adds a regression for invalid recommendation projection counters. The
engine now abstains before candidate selection and evaluates the invariant with
overflow-safe arithmetic. The focused recommendation suite passes `12/12` and
the full API suite passes `138/138`; mobile recommendation integration remains
pending explicit design approval. See
[E143](../audit/evidence/evidrilo-recommendation-projection-validation-2026-09-13.md).

E144 adds a regression for a published recommendation candidate with a blank
skill identifier. The engine now ignores it before sorting or selecting. The
focused recommendation suite passes `13/13` and the full API suite passes
`139/139`; managed content and mobile integration remain pending. See
[E144](../audit/evidence/evidrilo-recommendation-candidate-validation-2026-09-13.md).

E145 verifies the complete local verifier plus the public-package, Git-safety,
and exporter fixture suites. The current run passes Gradle, Node `74/74`, API
`139/139`, worker `5/5`, deployment, and the exported candidate checks. The
actual workspace Git check remains blocked because its metadata is unusable;
remote visibility and push are not claimed. See
[E145](../audit/evidence/evidrilo-public-package-preflight-2026-09-13.md).

E146 adds the shared mobile recommendation boundary. The focused recommendation
suite passes `28/28`, the complete Kotlin/JVM suite passes `261/261` with zero
failures/errors, and JVM, Android, `iosSimulatorArm64`, and `iosArm64` targets
compile. Fresh PostgreSQL and worker smoke pass, while the full local verifier
passes Node `74/74`, API `139/139`, worker tests `5/5`, deployment, and public
export checks. A RED/GREEN regression confirms that each logical recommendation
interaction has its own idempotency UUID and retries reuse only that event's
UUID. These checks do not prove native runtime, accessibility, provider,
managed-deployment, or human behavior. See
[E146](../audit/evidence/evidrilo-recommendation-mobile-integration-2026-09-13.md).

E147 records the bounded AI decision. Focused AI API tests pass `39/39` and
the full API suite passes `139/139`, covering verified identity, explicit
opt-in fallback, request validation, input redaction, quota exhaustion,
provider-output validation, and timeout fallback. The provider is disabled by
default; no provider transaction or mobile runtime is implied. See
[E147](../audit/evidence/evidrilo-ai-bounded-assistance-2026-09-13.md).

E148 adds an opt-in integration test for the local PostgreSQL backup/restore
path. The focused test covers refusal of an unavailable image and, when Docker
is enabled, a real custom-format dump/restore into a fresh database with a
synthetic sentinel and migration-ledger assertion. This does not prove managed
backup or production recovery. See the
[E148](../audit/evidence/evidrilo-local-backup-restore-2026-09-13.md).

E149 adds a configuration regression proving that a noncanonical RevenueCat
entitlement cannot make the server billing boundary ready. The focused
configuration suite passes `11/11` and the full API suite passes `140/140`;
provider dashboard and transaction behavior remain external gates. See the
[E149](../audit/evidence/evidrilo-billing-entitlement-allowlist-2026-09-13.md).

E150 adds a contract regression for current operational snapshots. It catches
stale E140/E144 runbook/register status plus stale current-status, platform README,
root README, and all-area-audit wording and passes `22/22` after synchronization; the final
Node boundary passes `77/77`;
provider, runtime, managed, human, and
submission gates remain separate. See
[E150](../audit/evidence/evidrilo-runbook-status-synchronization-2026-09-13.md).

E151 adds a billing regression for the server product boundary. A signed active
RevenueCat event with canonical `evidrilo_pro` but `product_id=lifetime` is
ignored before the store is called; exact `monthly` and `yearly` events remain
eligible. Focused BillingTests pass `10/10` and the full API suite passes
`141/141`. Provider transaction and production behavior remain external. See
[E151](../audit/evidence/evidrilo-billing-product-allowlist-2026-09-14.md).

E152 adds the account-deletion owner regression. Migration
`027_account_deletion_owner_guard` locks each affected organization and rejects
deletion when the requested account is its sole active owner. The API maps the
database error to `409 OWNER_TRANSFER_REQUIRED`; the migration contract, API
suite, and fresh PostgreSQL smoke pass, including
`LAST_OWNER_DELETION_GUARD_PASS`. Managed database and Auth deletion remain
external. See [E152](../audit/evidence/evidrilo-account-deletion-owner-guard-2026-09-14.md).

E153 adds client and request-boundary regressions. Mobile billing now requires
canonical `evidrilo_pro` and an approved active `monthly`/`yearly` product;
content, sync, and analytics reject unverified stored sessions; account
deletion preserves the owner-transfer action; chunked webhook bodies stop at
the 128 KiB route bound; and caller cancellation propagates through optional
AI. The current boundary passes Kotlin `267/267`, API `144/144`, Node `77/77`,
worker `5/5`, shared iOS target compilation, fresh PostgreSQL smoke, and fresh
worker smoke. See [E153](../audit/evidence/evidrilo-client-boundary-hardening-2026-09-14.md).

E154 adds a regression for oversized custom-scheme auth callbacks. URLs above
8 KiB are rejected before parsing or pending retention; the focused parser/bus
test, full local verifier, and shared iOS target compilation pass. The current
verifier records API `144/144`, Node `77/77`, and worker `5/5`. Native callback
runtime and provider-backed account verification remain external. See [E154](../audit/evidence/evidrilo-auth-callback-boundary-2026-09-14.md).

E155 adds a regression for an authoring document with no challenge variants.
The old validator fails the regression; the repaired validator requires one to
32 meaningful variants. Focused content/authoring coverage passes `22/22` and
the full API suite passes `145/145`. Managed content publication remains
external. See [E155](../audit/evidence/evidrilo-content-challenge-required-2026-09-14.md).

E156 adds the stored-content transition regression. Invalid or malformed stored
JSON is rejected before `approved` or `published`; focused content/authoring
coverage passes `23/23` and the full API suite passes `146/146`. Managed
database replay and publication remain external. See [E156](../audit/evidence/evidrilo-stored-content-transition-guard-2026-09-14.md).

E157 adds the mobile reader regression for an empty challenge-variant array.
Focused reader coverage passes `8/8`; the full Kotlin/JVM suite passes
`269/269`, JVM/Android compilation passes, and shared iOS targets compile.
Managed content publication and device/runtime acceptance remain external. See
[E157](../audit/evidence/evidrilo-mobile-content-challenge-required-2026-09-14.md).

E158 adds the schema lower bound `variants.minItems = 1`, and E159 aligns the
remaining expressible identifier, title, and skill-tag bounds with the API and
Kotlin readers. The focused contract and full local verifier pass with Kotlin
`269/269`, Node `77/77`, API `146/146`, worker `5/5`, and deployment checks.
Cross-field references remain runtime validation. See [E159](../audit/evidence/evidrilo-case-schema-identifier-bounds-2026-09-14.md).

E160 corrects the content-authoring runbook's stale “optional challenge
variants” instruction and protects the required one-to-32 invariant with the
documentation contract. The focused contract and full local verifier pass;
managed editorial publication remains external. See [E160](../audit/evidence/evidrilo-content-runbook-challenge-required-2026-09-14.md).

E161 aligns the recommendation schema with the mobile/API status-specific
response rules. The focused contract and full local verifier pass with Kotlin
`269/269`, Node `78/78`, API `146/146`, worker `5/5`, and deployment checks.
Provider and runtime evidence remain external. See [E161](../audit/evidence/evidrilo-recommendation-schema-alignment-2026-09-14.md).

E162 synchronizes the current Node verification snapshot after the E161
contract addition. The current Node boundary is `78/78`; historical counts
remain tied to their observed verification boundary. See [E162](../audit/evidence/evidrilo-node-verification-snapshot-2026-09-14.md).

E163 adds the local session-decoder regression. Unknown enum values, malformed
escaped lists, and encoded values over 8 KiB are rejected before restoration;
the focused test and final local verifier pass with Kotlin/JVM `270/270`, Node
`78/78`, API `146/146`, worker `5/5`, and deployment checks. See [E163](../audit/evidence/evidrilo-local-session-decoder-hardening-2026-09-14.md).

E164 adds a PostgreSQL regression for stale cohort enrollments. Aggregate
counts now require an active organization membership; migration contract
`28/28`, focused API readiness `4/4`, and the fresh PostgreSQL/RLS smoke pass.
See [E164](../audit/evidence/evidrilo-cohort-active-membership-boundary-2026-09-14.md).

E165 adds the companion role regression. Aggregate counts now require an active
organization membership whose role is exactly `learner`; migration 029, the
focused contract, API readiness `4/4`, and the fresh PostgreSQL/RLS smoke pass.
See [E165](../audit/evidence/evidrilo-cohort-learner-role-boundary-2026-09-14.md).

E166 adds the membership role-change regression. A grant/role-change operation
cannot demote the last active owner; focused policy coverage and the full API
suite pass `147/147`, with the organization lock preserving the concurrency
boundary. See [E166](../audit/evidence/evidrilo-last-owner-role-change-2026-09-14.md).

E167 adds the billing identity regression. A signed RevenueCat event with an
all-zero `app_user_id` is acknowledged as `ignored` before persistence. The
focused billing suite passes `11/11` and the full API suite passes `148/148`;
provider delivery and managed transaction evidence remain open. See
[E167](../audit/evidence/evidrilo-billing-empty-account-guard-2026-09-14.md).

E169 adds the auth provider type regression. Boolean, numeric, object, and
array confirmation metadata cannot produce a verified local session; focused
auth coverage passes `13/13`, and Kotlin/JVM passes `271/271` with JVM,
Android, and shared-iOS compilation. Provider-backed and native runtime
evidence remain open. See
[E169](../audit/evidence/evidrilo-auth-provider-type-boundary-2026-09-14.md).

E170 adds explicit membership role-assignment policy coverage. Learner,
teacher, and reviewer attempts remain denied; focused access-policy coverage
passes `9/9` and the full API suite passes `151/151`. See the [E170
membership role-assignment record](../audit/evidence/evidrilo-membership-role-assignment-coverage-2026-09-14.md).

E171 adds the optional offline-audio boundary. Focused audio coverage passes
`28/28`, five bounded interaction effects pass manifest and budget checks, and
the full local boundary passes Kotlin/JVM `299/299`, Node `87/87`, API
`151/151`, and worker `5/5`. Reviewed narration, device playback,
accessibility-service, and human audio-quality evidence remain open. See
[E171](../audit/evidence/evidrilo-offline-audio-implementation-2026-09-14.md).

E172 adds fail-closed local restoration checks. Session phases are bound to
supported main/challenge case IDs, and secure-session account identifiers are
bounded before billing, sync, or request use. Focused coverage passes `5/5`
for each boundary; the full local boundary passes Kotlin/JVM `301/301`, Node
`87/87`, API `151/151`, worker `5/5`, Android compilation, shared iOS target
compilation, and deployment checks. Provider, native runtime, managed,
staging, human, publication, and submission gates remain open. See
[E172](../audit/evidence/evidrilo-local-session-boundary-2026-09-14.md).

E173 adds RED/GREEN coverage for stale sync responses and asynchronous billing
catalog updates. Focused sync, request-gate, and premium-state coverage passes
`18/18`; the full local boundary passes Kotlin/JVM `306/306`, Node `87/87`,
API `151/151`, worker `5/5`, Android compilation, shared iOS target
compilation, deployment checks, and audio asset checks. These results do not
prove native runtime, provider, managed, staging, or human gates. See
[E173](../audit/evidence/evidrilo-async-state-boundaries-2026-09-14.md).

E175 adds a RED/GREEN regression for the sync pull lower bound. A returned
change must be strictly newer than the requested cursor and no greater than
`nextCursor`; the focused sync suite passes `29/29`. The latest full local
boundary passes Kotlin/JVM `312/312`, Node `87/87`, API `151/151`, worker `5/5`,
Android/shared iOS target compilation, deployment checks, and audio asset
checks. These results do not prove native runtime, provider, managed, staging,
or human gates. See
[E175](../audit/evidence/evidrilo-sync-cursor-lower-bound-2026-09-14.md).

E176 adds regression coverage for cancellation-safe mobile HTTP, stable
malformed-JSON API errors, duplicate sync command IDs, and retryable
recommendation cancellation. The current local boundary passes Kotlin/JVM
`317/317`, Node `87/87`, API `153/153`, worker `5/5`, Android/shared-iOS target
compilation, deployment, and audio checks. These results do not prove native
runtime, provider, managed, staging, or human gates. See [E176](../audit/evidence/evidrilo-request-lifecycle-and-input-boundaries-2026-09-14.md).

E177 adds sync pagination regressions for a valid smaller page with
`hasMore=true` and an oversized response. The parser now validates the caller's
requested limit. Kotlin/JVM `319/319`, Node `87/87`, API `153/153`, worker
`5/5`, Android/shared-iOS target compilation, deployment, and audio checks pass;
runtime, provider, managed, staging, and human gates remain separate. See
[E177](../audit/evidence/evidrilo-sync-page-size-boundary-2026-09-14.md).

E178 adds regressions for strict API full-match input validation and the
conventional `Staging` environment. API `158/158` passes; runtime, provider,
managed staging, and human gates remain separate. See
[E178](../audit/evidence/evidrilo-api-input-and-staging-boundary-2026-09-14.md).

E179 adds sync regressions for invalidated-pull push suppression and truthful
consent-clear state. Kotlin/JVM `321/321` passes; runtime, provider, managed,
staging, and human gates remain separate. See
[E179](../audit/evidence/evidrilo-sync-consent-cancellation-boundary-2026-09-14.md).

E117 records the final repository-owned verification pass. The complete
harness passed `186/186` JVM tests, `52/52` Node contract/repository-boundary
checks, API `108/108`, worker `5/5`, Android debug assembly, and shared
`iosSimulatorArm64`/`iosArm64` compilation. Deployment checks, Compose config,
Docker API/worker builds, and the public-package export checks also passed. A
temporary local Compose smoke applied the auth compatibility shim and
checksum-guarded migrations through 020, started API and worker on an
overridden host port, and returned a successful `/health/live`; `/health/ready`
was intentionally degraded because managed Supabase configuration was absent.
The public candidate also passed JVM and Android compilation when supplied an
explicit Android SDK path. These checks do not prove a clean hosted clone,
device/iOS host runtime, RevenueCat transaction, managed deployment, human
validation, or publication.

## Test levels

### Common domain tests

Pure Kotlin tests cover conclusion-chain models, evaluator decision tables,
priority, anchors, abstention, adversarial fixtures, evidence-change variants,
active-case literal derivation, revision immutability, exactly-one-revision
behavior, reset, local history, and premium access decisions.

Run:

```bash
./gradlew :composeApp:jvmTest
```

### JVM compilation

The JVM compilation check catches shared-source and dependency errors without
claiming Android or iOS runtime behavior.

```bash
./gradlew :composeApp:compileKotlinJvm
```

### Compose UI increment (E112)

The shared UI implementation covers the V8 Home/Guide/Settings direction and
the V7 practice/history/packs/about surfaces. The desktop smoke walkthrough
observed Home, Guide with disclosure expansion, Evidence practice, Settings,
About, History, and the honest premium-unavailable state. It also checked
Home → practice, draft-preserving Back, and premium exit/navigation behavior.
The current Android UI runtime, iOS host/runtime, full assistive-technology
review, measured contrast, and device text-scale review remain open.

### Android verification

The debug build verifies that the Android application and shared module can be
assembled in the configured environment.

```bash
./gradlew :androidApp:assembleDebug
```

An emulator or device run is a separate observation and must be recorded as
such.

M2 Android observation: the debug APK was installed on the local `shipatonApi35`
emulator and the Evidrilo flow reached the bounded `Pass` feedback state,
entered `Revise once`, accepted an edited claim, and displayed `Before feedback`
and `After one revision` in the summary. An incomplete submission also showed
a visible text validation notice. This is Android evidence only; it does not
prove iOS, billing, accessibility, reviewer agreement, or participant value.
The same Android observation also verified that a selected draft survives a
force-stop/relaunch and that `Reset this practice` clears the stored session
before a later relaunch. The iOS store implementation is compile-checked but
has not received a host runtime observation.

The evidence-change challenge and local-history behavior have automated
coverage. The latest Android debug artifact was observed on the local
`shipatonApi35` AVD: the variant notice rendered without `OBS-COLD-01`, all
four changed-evidence checks passed, comparison rendered, force-stop/relaunch
restored the latest local entry, and
the explicit clear action removed it. A local network-disabled pass also
completed the challenge, restored challenge/comparison after force-stop and
relaunch, and cleared the history entry. The latest APK exposes checked
choice semantics and retained the scrollable hierarchy at 130% text scale.
This is Android-only partial accessibility evidence; TalkBack/service
traversal, visual contrast, switch access, the final-artifact offline rerun,
and the iOS host path remain open.

### iOS verification

An authorized macOS/Xcode environment must run the committed host on a
simulator or device. Kotlin/Native compilation on Linux does not prove host
launch, layout, accessibility, or purchase behavior.

Current status: host runtime `NOT_RUN`. The Linux-side
`compileKotlinIosSimulatorArm64` and `compileKotlinIosArm64` tasks pass, but
those results do not prove Xcode host launch, layout, accessibility, or device
behavior.

### Billing verification

RevenueCat Test Store checks are separate from domain tests. They must verify:

- free core remains usable without a billing key;
- offering loading and unavailable-offering states;
- purchase success, cancellation, failure, pending, and unknown states;
- entitlement-based unlock rather than a UI-only boolean;
- restore, relaunch, duplicate taps, and loss of network;
- premium content remains unavailable until entitlement is confirmed.

Sandbox results must be labeled as sandbox results and must not be presented as
production revenue.

## Feedback evaluator test contract

Every fixture should specify input, expected status, priority, anchor IDs, and
reason independently of the implementation. The suite must include:

- complete supported conclusions;
- incomplete and partially filled input;
- invalid or external fact IDs;
- missing comparator evidence;
- limitation and causal overclaims;
- safe paraphrase and unsafe negation;
- unsupported numbers or external causes;
- conflicting feedback priorities;
- preserved initial draft and rejected second revision.

## Platform foundation tests

The optional ASP.NET Core foundation has a separate boundary suite:

```bash
dotnet test platform/api.Tests/Evidrilo.Api.Tests.csproj
dotnet build platform/api.Tests/Evidrilo.Api.Tests.csproj --configuration Release
node --test contracts/contracts.test.mjs
node --test platform/database/migrations/migrations.test.mjs
bash platform/database/integration/run-local-postgres-smoke.sh
dotnet build platform/worker/Evidrilo.Worker.csproj --configuration Release
dotnet test platform/worker.Tests/Evidrilo.Worker.Tests.csproj --configuration Release
bash platform/worker/integration/run-local-worker-smoke.sh
```

It verifies configuration fail-closed behavior, request IDs, redacted error
contracts, liveness/readiness separation, synthetic authentication, verified
account identity, sync/analytics/progress/case/recommendation/authoring
boundaries, account and bounded daily progress reads, deterministic projections,
AI redaction/fallback/audit behavior,
membership lifecycle and account-deletion boundaries, ordered billing event
handling, role policy, worker retry policy, and public contract closure. The
local PostgreSQL harness additionally applies all migrations to a fresh
database and checks RLS/lifecycle behavior. The worker harness then runs the
separate Release worker against fresh PostgreSQL and checks expired-lease claim,
account/daily projection rebuild, completion, and cleanup. These checks do not
prove real Supabase JWKS/RLS grants, managed worker grants/pooling, provider
behavior, mobile secure-storage runtime behavior, deployment, or backup
restore.

The 11 September 2026 local rerun passed both integration harnesses: the
PostgreSQL migration/RLS harness emitted
`EVIDRILO_POSTGRES_INTEGRATION_PASS`, and the worker harness emitted
`EVIDRILO_WORKER_INTEGRATION_PASS`. Both used temporary local containers and
synthetic identities only.

The optional mobile account boundary has JVM coverage for secure-session state,
verified-account rejection, redacted diagnostics, and presentation states. The
Android Keystore and iOS Keychain adapters are compile-checked; native secure
storage runtime and host UI remain `NOT_RUN` until device/simulator evidence
exists. E113 adds focused coverage for email/password outcomes, confirmation
and reset non-enumeration, refresh rotation, malformed provider responses,
Google PKCE/state mismatch, recovery fragments, logout failure, deletion
confirmation, form validation, and secret-shaped configuration rejection.

The Google path is deliberately authorization-code based: the app opens the
system browser, validates the registered callback and single-use state, then
exchanges the code with Supabase using the verifier. Non-recovery access-token
fragments are rejected. A real Google account, verification/reset email, and
provider callback on Android/iOS are external checks, not local test claims.

## Accessibility checks

The product must communicate status through text and semantic labels, not color
alone. Review text scaling, screen-reader labels, focus order, contrast, tap
targets, error recovery, and offline behavior on both target platforms where
the environment permits.

E183 hardens the shared Compose choice control at the repository boundary:
single-select choices use native selectable radio semantics and multi-select
evidence facts use native toggleable checkbox semantics, with merged label/state
information. The direct contract suite passes `25/25`; the full local verifier
passes Node `95/95`, API `163/163`, worker `5/5`, the current Kotlin/Android
build boundary, Android release packaging, deployment, and asset checks. This
does not prove TalkBack, VoiceOver, device runtime, or human accessibility
validation.

E184 is the latest dated environment verification, not a product increment. A
checksum-verified Temurin JDK 21 restored the full Kotlin/Android release
verification; Node `95/95`, API `163/163`, worker `5/5`, audio, and deployment
checks also pass. Android AVD probes still stop before a usable
framework/package service, so current Android UI and G3 runtime evidence remain
unrun. See the [E184 probe record](../audit/evidence/evidrilo-android-runtime-probe-2026-09-15.md).

E186 records the backend-first verification boundary. The focused engine/sync
checks pass `8/8`; the full Kotlin/JVM suite passes `332/332`; the API suite
passes `172/172`; Node contract, migration, safety, deployment, public-boundary,
export, and asset checks pass `96/96` when executed in the host environment;
worker tests pass `5/5`; and fresh PostgreSQL/RLS plus worker-process smoke pass
through migration 030. The first sandbox Node attempt failed only because its
nested process-spawning checks were denied by the sandbox; the same checks
passed in the authorized host execution. No runtime, provider, managed,
human, or submission evidence is inferred from these results. See the
[E186 evidence record](../audit/evidence/evidrilo-backend-engine-sync-boundary-2026-09-16.md).

## Evidence language

Use precise labels such as `PASS`, `FAIL`, `NOT_RUN`, `UNKNOWN`, and
`CANNOT_ASSESS`. Never convert an unrun platform, participant, reviewer, or
billing check into a positive claim.
