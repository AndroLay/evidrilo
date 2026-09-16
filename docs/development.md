# Development Guide

## Status

Current authority: [Evidrilo Source of Truth](../research/next-gen/SOURCE_OF_TRUTH.md).

The repository contains a reusable Kotlin Multiplatform foundation and the M2
free-core Evidrilo loop, including the evidence-change challenge and local
comparison history. The Android base/persistence/challenge/history flow has
been observed on a local emulator, including a network-disabled
relaunch/clearability pass. The latest choice controls expose checked
semantics and remain usable at 130% text scale. The shared V8/V7 Compose UI
surfaces are implemented and the JVM desktop walkthrough covers Home, Guide,
Settings, History, About, practice, and Packs unavailable states. Current
Android UI runtime, iOS host runtime, and full assistive-technology/visual
accessibility checks still need verification.

The shared Home surface also includes the E146 recommendation boundary. It is
enabled only for a verified, live, consented session and launches only the
exact bundled `M0_T2:1` mapping; recommendation failure never disables local
practice. Native runtime, provider, managed API/database, and human validation
remain separate gates. E147 records the optional, non-grading AI scope; its
provider remains disabled pending external approval and configuration.

## Required tools

- Full JDK 21 installation (`java` and `javac`; a JRE alone is insufficient)
- Gradle Wrapper included in this repository
- Android SDK for Android builds
- macOS and Xcode for an iOS host run
- .NET 10 SDK for the optional platform lane

The project does not require a global Gradle installation.

## First checkout

```bash
git clone https://github.com/AndroLay/evidrilo.git
cd evidrilo
cp local.properties.example local.properties
```

Set `sdk.dir` in `local.properties` to the local Android SDK path. The file is
ignored and must never be committed.

## Git worktrees

Use a linked worktree for an isolated feature lane. The repository convention is
to keep linked worktrees under the ignored `.worktrees/` directory:

```bash
git worktree list
git worktree add .worktrees/ui-development -b ui/development main
cd .worktrees/ui-development
cp local.properties.example local.properties
```

Ignored files are not copied into a new worktree. If the UI lane needs private
design material, expose the approved primary-checkout `internal/design/` folder
through a local symlink or a local copy inside the worktree. Keep the worktree's
`internal/` directory ignored and never use `git add --force` for it. Configure
the external SDK and cache variables in the shell before building; do not put
machine paths or credentials in tracked files.

Inspect and remove a linked worktree only after its changes are integrated:

```bash
git worktree list
git worktree remove .worktrees/ui-development
git branch -d ui/development
```

The remove command affects only the linked checkout. It must not be used on the
primary checkout or on the external private design directory.

## Source-only workspace

The checkout should contain source and reproducible configuration, not machine
toolchains or dependency caches. Use the normal system Android SDK, JDK 21, and
.NET SDK when available. For a dedicated external cache root, configure it in
the shell that runs verification:

```bash
export EVIDRILO_GRADLE_USER_HOME="$HOME/.cache/evidrilo/gradle"
export EVIDRILO_NUGET_PACKAGES="$HOME/.cache/evidrilo/nuget"
export EVIDRILO_DOTNET_ROOT="/opt/dotnet"
export EVIDRILO_JAVA_HOME="/opt/jdks/jdk-21"
```

The values above are examples of external locations; replace them with the
actual system paths on the machine. `EVIDRILO_GRADLE_USER_HOME` takes
precedence over `GRADLE_USER_HOME`, and `EVIDRILO_NUGET_PACKAGES` takes
precedence over `NUGET_PACKAGES`. When neither is set, the scripts use an
external user cache under `EVIDRILO_CACHE_ROOT`, `XDG_CACHE_HOME`, or the
standard user cache directory.

`EVIDRILO_DOTNET_ROOT` is preferred for the API and worker runners. A system
`dotnet` installation is used next. Repository-local SDKs under `.dotnet-local`
or `.local/dotnet` are accepted only when
`EVIDRILO_ALLOW_REPOSITORY_TOOLCHAINS=1` is explicitly set. This prevents a
clean checkout from silently recreating a large repository-local toolchain.

On a source-only checkout, these paths should not contain toolchains or retained
generated output:

```text
.local/ .gradle-local/ .dotnet-local/ .gradle/
build/ **/build/ **/bin/ **/obj/
```

This working copy has completed the owner-approved migration after external
verification. The original toolchains and caches remain recoverable under the
machine's external Evidrilo cache locations, generated verification output is
archived separately, UI design sources are stored under ignored
`internal/design/`, and personal media remains outside the checkout. For an
older checkout, inspect the directories before any action:

```bash
du -sh .local .gradle-local .dotnet-local .gradle build \
  androidApp/build composeApp/build platform/api/bin platform/api/obj \
  platform/worker/bin platform/worker/obj design Gurwi video-notes 2>/dev/null
```

Generated output can be recreated after a successful verification with:

```bash
./gradlew clean
dotnet clean platform/api/Evidrilo.Api.csproj
dotnet clean platform/worker/Evidrilo.Worker.csproj
```

Moving or removing SDKs, caches, generated output, design sources, or private
media is an owner action. Keep a recoverable copy until the external build and
verification path has passed; the repository scripts do not perform that
cleanup automatically.

## Verification commands

Run the common JVM tests and compiler check:

```bash
./gradlew :composeApp:jvmTest :composeApp:compileKotlinJvm
```

Build the Android debug artifact:

```bash
./gradlew :androidApp:assembleDebug
```

Run the optional ASP.NET Core platform checks:

```bash
dotnet test platform/api.Tests/Evidrilo.Api.Tests.csproj
dotnet build platform/api.Tests/Evidrilo.Api.Tests.csproj --configuration Release
node --test platform/contracts/contracts.test.mjs
node --test platform/database/migrations/migrations.test.mjs
bash platform/database/integration/run-local-postgres-smoke.sh
dotnet build platform/worker/Evidrilo.Worker.csproj --configuration Release
dotnet test platform/worker.Tests/Evidrilo.Worker.Tests.csproj --configuration Release
bash platform/worker/integration/run-local-worker-smoke.sh
```

The public workflow at [`../.github/workflows/verify.yml`](../.github/workflows/verify.yml)
keeps the deterministic mobile, contract, migration, API, and worker checks
repeatable on pushes and pull requests. Before creating a public repository
snapshot, check the exact candidate tree with:

```bash
bash scripts/check-public-package.sh <candidate-directory>
```

The package check rejects private working material and credential-shaped
values without printing matched contents. Hosted CI cannot substitute for
iOS/macOS runtime validation, provider transactions or login, human review,
publication, or submission evidence.

To prepare a public candidate from this mixed private workspace, use an empty
destination outside the repository:

```bash
bash scripts/export-public-package.sh . /path/to/empty-candidate
```

The exporter copies only the explicit public allowlist and runs the package and
deployment checks on the result. It never overwrites a non-empty destination.

After cloning the public repository, stage only the intended public files and
run `bash scripts/check-github-safety.sh .` before committing or pushing. The
check verifies the actual Git index and working tree, rejects untracked
non-ignored files and private/generated/credential-bearing paths, and enforces
the same public allowlist. It requires usable Git metadata and therefore is
intentionally unavailable in this mixed workspace.

The optional API and projection worker have a local container preparation:

```bash
bash scripts/check-deployment.sh .
docker compose -f deploy/docker-compose.local.yml config --quiet
docker compose -f deploy/docker-compose.local.yml up --build
curl --fail http://127.0.0.1:5080/health/live
```

The local Compose database uses trust authentication only inside its isolated
development network. Use a managed secret store, a protected PostgreSQL
connection, explicit origins/proxies, and the migration ledger before any
staging or production deployment. The full handoff and rollback boundaries are
in [`../deploy/README.md`](../deploy/README.md).

These checks use synthetic local authentication and do not contact Supabase.
The local PostgreSQL harness applies migrations
`001_platform_sync.sql` through `029_cohort_learner_role_boundary.sql` using
the checksum-guarded ledger runner at
`platform/database/migrations/apply-migrations.sh`, then verifies
RLS/lifecycle boundaries, including the server-derived email-verification
claim. On a managed Supabase project, enable the Custom Access Token Hook for
`public.custom_access_token_hook` after applying migration 020; the API's
verified-only routes depend on that claim because it is derived from
`auth.users.email_confirmed_at`, not from user-editable metadata. The worker
smoke harness then runs the separate Release worker against fresh PostgreSQL
and verifies expired-lease reclaim plus account/daily projection rebuild. The
sync API fails closed when no database connection is configured; applying the
migrations to a managed test database and running managed projection replay,
authoring, and worker-lease tests remain separate external gates. Raw SQL
migration replay must not bypass the ledger.
The free mobile flow does not depend on the API or .NET SDK.

Open `iosApp/iosApp.xcodeproj` in Xcode to run the iOS host. Linux can compile
Kotlin/Native targets in suitable environments, but compilation alone is not an
iOS simulator or device observation.

The Android smoke path used for M2 is:

```text
Welcome → supplied case → select facts and relation → write conclusion
→ select scope and limitations → choose next action → bounded feedback
→ revise once → before/after summary → evidence-change challenge
→ changed-evidence feedback → comparison summary/history
```

The free core is intentionally usable without a RevenueCat key or network.
The M2 Android host persists the active conclusion session and latest local
comparison locally; the challenge/comparison was restored after force-stop in
the local network-disabled pass. The iOS implementation uses the same shared
snapshot and history contracts but still needs a macOS/Xcode runtime
observation.

The current UI increment is tracked as E112. It preserves the evaluator,
persistence, account, and billing boundaries while adding the owner-approved
V8 Home/Guide/Settings direction and the V7 practice/history/packs/about
surfaces. The latest cross-target check reports 144/144 JVM tests and
successful JVM, Android debug, and shared iOS target compilation; `adb` is
unavailable for the corresponding Android UI runtime observation in this
environment.

The optional account increment is tracked as E113. It adds email/password,
password recovery, Google authorization-code PKCE, callback validation, secure
session refresh/sign-out, account-deletion preparation, and the account UI.
The free route remains independent of these services. Real provider setup and
runtime auth checks must be run only with owner-authorized accounts.

The current all-area increment also adds optional, consent-gated cloud progress
metadata sync, a published-case reader, consented mobile completion/revision
events, onboarding/support/sync states, and operated content, sync, support,
and recovery runbooks. Sync intentionally carries IDs, revision metadata, and
snapshot digests only; learner draft text remains on the device.

The current whole-repository audit is tracked as E114. It reproduced and fixed
confirmed correctness issues in evaluator token matching, Android draft
persistence, Java toolchain selection, API rate-limit metadata/order, review
decision privacy, billing and sync timestamps, missing enum fields, and the
Supabase email-verification claim boundary. It also removed an unnecessary
projection sort and byte-boxing allocation. The latest verification is
recorded in [the E114 audit record](../audit/evidence/evidrilo-whole-repository-audit-2026-09-11.md).

E115 records the repository-owned closure after that audit: the active-case
Home regression, public-package boundary checker, read-only CI workflow,
stale-Gradle-cache protection, and fresh local verification are recorded in
[the E115 closure record](../audit/evidence/evidrilo-open-gates-closure-2026-09-12.md).

E116 records the RevenueCat follow-up: the owner-authorized Test Store catalog
is configured with the `evidrilo_pro` entitlement and monthly/yearly/lifetime
packages, the KMP adapters synchronize verified account UUIDs with RevenueCat
customer identity, and the API accepts the official signed webhook shape with
idempotent projection. The current app-side allowlist and fixtures accept only
monthly/yearly; the dashboard lifetime package still requires owner migration.
Test Store transactions, production store connectors, webhook delivery, and
iOS host runtime remain external gates.

The current planning direction excludes lifetime and targets only monthly and
yearly subscriptions, anchored at USD 1.00/month and USD 10.00/year. This is a
pricing hypothesis recorded in [the monetization note](business/monetization-and-pricing.md);
the app-side implementation is verified, while catalog migration and
transaction validation remain pending.

E117 records the repository-owned finishing pass. It adds non-root API/worker
Dockerfiles,
checksum-ledger Compose ordering, deployment secret/health checks, expanded
repository-boundary tests, and disclosure state semantics for accessibility.
The submission workspace also contains an evidence-bound English claim matrix
and an executable icon/screenshot/video validator. These additions prepare
the remaining gates but do not create runtime, provider, human, or publication
evidence. The final harness passed `186/186` JVM tests, `52/52` Node checks,
API `108/108`, worker `5/5`, Android debug assembly, and both shared iOS
targets. Docker builds, Compose smoke, and the public candidate export also
passed; readiness was intentionally degraded without managed Supabase.

E118 records the all-area closure on 13 September 2026. The current post-change
verification passes `221/221` JVM tests, JVM/Android/shared-iOS target
compilation, `54/54` Node boundary tests across 7 files, API `110/110`, worker `5/5`,
fresh PostgreSQL/worker smoke, deployment checks, Compose configuration, and
allowlisted public export. It also hardens sync cursor/version integrity,
timestamp validation, and HTTP token material. Device, provider, managed
deployment, human, publication, and submission evidence remains open; see the
[E118 record](../audit/evidence/evidrilo-all-areas-closure-2026-09-13.md).

E119 adds the versioned analytics funnel boundary: practice start, paywall
view, monthly/yearly premium actions, and bounded client-error codes. The app
wires these events only when account and analytics consent are present; renewal
and refund remain webhook-owned billing signals. Migration 021 extends the
append-only event constraint without changing progress projection semantics.
The fresh focused/full verification passes `221/221` JVM, `54/54` Node,
`110/110` API, `5/5` worker, shared iOS target compilation, and PostgreSQL
migration/RLS smoke.

E120 closes the direct client-write bypass. Migration 022 removes client INSERT
policies for analytics and sync, the fresh RLS smoke asserts rejection, and the
analytics store normalizes nullable properties so retries from older app
versions remain idempotent. Verification passes `221/221` JVM, `56/56` Node,
API `110/110`, worker `5/5`, shared iOS target compilation, and PostgreSQL/RLS
smoke through migration 022.

E121 removes the remaining direct recommendation-event INSERT path through
migration 023. The fresh RLS smoke asserts rejection, and verification passes
`221/221` JVM, `57/57` Node, API `110/110`, worker `5/5`, shared iOS target
compilation, and PostgreSQL/RLS smoke through migration 023.

E122 fixes the RevenueCat configuration summary so it is marked configured only
when webhook authentication and the entitlement identifier are both present.
The focused regression is GREEN and the full API suite passes `111/111`; the
provider, device, and deployment gates remain external.

E123 applies the monthly/yearly offer allowlist to the unknown transaction
fallback, so unresolved provider state cannot display or select legacy
`lifetime`. The focused regression is RED before the change and GREEN after it;
the current local verification passes `222/222` JVM, `57/57` Node, API
`111/111`, worker `5/5`, both shared iOS target compiles, and fresh
PostgreSQL/worker smoke.

E124 closes automatic redirect handling in authenticated Android/JVM/iOS HTTP
transports. The redirect-policy regression is RED before the change and GREEN
after it; `223/223` JVM tests, Android compilation, both shared iOS target
compiles, the full contract/API/worker harness, fresh PostgreSQL/worker smoke,
and the available dependency checks pass. Device, managed TLS/proxy, and
provider behavior remain external.

E125 closes the missing Android host network permission. The focused
mobile-platform check is RED before the manifest change and GREEN at `3/3`
after it; the full local harness now passes `223/223` JVM, `60/60` Node, API
`111/111`, worker `5/5`, both shared iOS target compiles, fresh
PostgreSQL/worker smoke, deployment checks, and public export. A final
`:androidApp:assembleDebug` also passes. Device, provider, and managed
deployment behavior remain external.

E126 closes the account-deletion access gap. A configured database-backed API
now checks the server-owned deletion tombstone before protected handlers and
returns `410 ACCOUNT_DELETED`; the deletion route remains available for an
idempotent retry. The focused lifecycle suite passes `5/5`; the full local
boundary passes `223/223` JVM, `60/60` Node, API `116/116`, worker `5/5`, both
shared iOS target compiles, Android debug assembly, fresh PostgreSQL/worker
smoke, deployment checks, and public export. Managed provider and deployed
runtime behavior remain external.

E127 closes the legacy-account variant of that gap. The API store now treats a
completed deletion ledger row as deleted even when no `account_profiles` row
exists, while retaining the profile tombstone check. The focused lifecycle
suite passes `9/9`; the current local boundary passes `223/223` JVM, `60/60`
Node, API `120/120`, worker `5/5`, both shared iOS target compiles, Android
debug assembly, fresh PostgreSQL/worker smoke with the missing-profile
fixture, deployment checks, and public export. Managed provider and deployed
runtime behavior remain external.

E128 adds migration 024 to provision `account_profiles` from new `auth.users`
rows through an idempotent server-owned trigger with a fixed function search
path. E127's deletion-ledger fallback remains for legacy or incomplete rows.
The migration contract and fresh PostgreSQL smoke pass, including
`ACCOUNT_PROFILE_PROVISIONING_PASS` and the missing-profile deletion fixture;
the current local boundary passes `223/223` JVM, `61/61` Node, API `120/120`,
worker `5/5`, both shared iOS target compiles, Android debug assembly,
deployment checks, and public export. Managed Supabase trigger behavior and
deployed runtime behavior remain external.

E129 binds asynchronous RevenueCat identity, refresh/offer, purchase, and
restore callbacks to the request generation and account ID that initiated
them. The focused billing suite passes `9/9`; the fresh local boundary passes
`226/226` JVM, `61/61` Node, API `120/120`, worker `5/5`, both shared iOS
target compiles, Android debug assembly, deployment checks, and public export.
Provider transactions and native account-switch runtime remain external.

E130 adds migration 025 to revoke public execution from the sync and analytics
projection `SECURITY DEFINER` trigger functions. Fresh PostgreSQL smoke passes
the privilege assertion and continues to pass migration replay, RLS,
lifecycle, deletion, and account-profile provisioning checks. Managed role ACLs
and deployment replay remain external.

E131 excludes root `.tmp` compiler output from Git staging, Docker contexts,
public export, and public-package checks. E132 makes migration-ledger creation,
checksum validation, and per-version application share one advisory transaction
lock; concurrent replay, idempotency, checksum, and PostgreSQL smoke checks
pass. Managed replay and deployment remain external.

E133 requires an explicit CORS origin list for production configuration while
retaining the localhost default only for development and test. The focused
configuration suite passes `10/10` and the full API suite passes `121/121`;
staging origin, proxy, TLS, and browser behavior remain external.

E134 aligns the RevenueCat Test Store runbook with the approved monthly/yearly-
only catalog, adds a catalog inspection before transactions, and rejects stale
lifetime configuration text through the contract suite. The actual dashboard
state and Test Store matrix remain external.

E135 makes API readiness check the migration ledger and require
`025_trigger_function_privileges` before returning a ready database state. The
readiness regression, full API suite (`125/125`), Release build, PostgreSQL
smoke, and temporary Compose health probe pass; managed deployment remains
external.

E136 removes wording from the RevenueCat runbook that could be mistaken for
dashboard observation. The approved catalog is now clearly labeled as intended
configuration, while dashboard state and transactions remain owner evidence;
the focused contract passes `14/14`.

E137 synchronizes the active backend execution register with the current local
boundary and protects its date and result counts with a contract. The focused
contract passes `15/15`; managed database, provider, runtime, and deployment
evidence remains external.

E138 adds the platform-scoped RevenueCat managed Paywall and Customer Center
adapters through `purchases-kmp-ui`, with a key/catalog/platform guard and JVM
fallback. The common suite passes `229/229`, the public boundary `70/70`, and
Android/shared-iOS compilation passes; dashboard, transaction, and device UI
evidence remain external.

E139 carries the canonical objective, typed observations/limitations, feedback
rules, and non-empty challenge variants through the published API and mobile
reader. Local PostgreSQL seed/read and endpoint serialization are covered; the
current suite passes `231/231` JVM, `71/71` public-boundary checks, API
`130/130`, and worker `5/5`, with shared iOS targets compiling. Managed content
operation and runtime/provider evidence remain external.

E140 removes an unobserved RevenueCat dashboard assertion from the architecture
note and protects that claim boundary with a contract test. The direct public
contract suite passes `18/18`; the full public boundary is now `72/72`.
Provider state remains an owner-observed gate.

E141 adds the actor-bound append-only case lifecycle audit migration. The
authoring API supplies a transaction-local reason, successful creation and
state transitions produce one event, failed transitions produce none, and
account deletion anonymizes actors without deleting history. Migration checks
pass `27/27`, API tests pass `130/130`, and fresh PostgreSQL smoke passes; the
current full public boundary is `73/73`.

E142 adds the versioned lifecycle-audit read endpoint. It enforces active
organization membership and author/reviewer/maintainer/owner roles, returns a
closed response with explicit nullable deletion-safe fields, and caps the
response at 128 events. API `137/137`, Node `74/74`, and migration `27/27`
checks pass; managed replay, admin UI, and editorial operation remain open.

E143 makes the recommendation engine abstain for invalid projection counter
shapes and evaluates the count invariant with overflow-safe arithmetic. The
focused recommendation suite passes `12/12` and the full API suite passes
`138/138`; mobile recommendation integration remains pending explicit design
approval. See the [E143 record](../audit/evidence/evidrilo-recommendation-projection-validation-2026-09-13.md).

E144 requires a non-empty skill identifier on every selected recommendation
candidate so the result remains explainable. The focused recommendation suite
passes `13/13` and the full API suite passes `139/139`; managed content and
mobile integration remain external or pending. See the [E144 record](../audit/evidence/evidrilo-recommendation-candidate-validation-2026-09-13.md).

E145 verifies the complete local verifier and the allowlisted exporter. The
current run passes Gradle, Node `74/74`, API `139/139`, worker `5/5`,
deployment, and the exported candidate checks, while the workspace Git checker
remains blocked by unusable Git metadata. This does not prove a clean index,
remote visibility, push, or submission. See the
[E145 record](../audit/evidence/evidrilo-public-package-preflight-2026-09-13.md).

E146 integrates the mobile recommendation parser, gateway, exact case registry,
safe Home card, lifecycle controller, and typed analytics. Focused
recommendation coverage passes `28/28`, the full Kotlin/JVM suite passes
`261/261`, and JVM/Android/shared-iOS target compilation passes. Final review
also fixes the server idempotency collision by assigning separate UUIDs to
`shown`, `accepted`, and `dismissed`, reusing each only across retries of that
same event. See the
[E146 record](../audit/evidence/evidrilo-recommendation-mobile-integration-2026-09-13.md).

E147 locks AI to optional, non-grading assistance for deterministic feedback
explanation, one evidence-scope/limitation reflection question, or a
meaning-preserving language alternative. The provider-neutral API boundary
keeps verified identity, explicit opt-in, redaction, bounds, quota, timeout,
typed fallback, and metadata-only audit; focused AI API tests pass `39/39` and
the full API suite passes `139/139`. The provider remains disabled pending
external approval and configuration. See the
[E147 record](../audit/evidence/evidrilo-ai-bounded-assistance-2026-09-13.md).

E148 adds a local-only PostgreSQL backup/restore rehearsal. It applies the
current migration ledger, dumps synthetic sentinel data in custom format,
restores into a fresh database, and verifies the restored sentinel and ledger.
The opt-in Docker test passes; managed retention, IAM, point-in-time recovery,
staging, and production rollback remain external gates. See the
[E148 record](../audit/evidence/evidrilo-local-backup-restore-2026-09-13.md).

E149 fixes the server billing configuration boundary: the webhook can become
usable only with webhook authentication and the exact `evidrilo_pro` entitlement.
A focused configuration regression passes `11/11` and the full API suite passes
`140/140`; a provider or production configuration result is not implied. See the
[E149 record](../audit/evidence/evidrilo-billing-entitlement-allowlist-2026-09-13.md).

E150 synchronizes the RevenueCat Test Store runbook, backend execution register,
current status page, platform README, root README, and all-area audit with the current
E149/Node `77/77`/API `140/140` boundary. The focused contract passes `22/22`;
this is documentation evidence only and does not imply
provider, runtime, managed, human, or production verification. See the
[E150 record](../audit/evidence/evidrilo-runbook-status-synchronization-2026-09-13.md).

E151 closes the server-side RevenueCat product allowlist gap. Active grants now
require exact `monthly` or `yearly` products, and a signed canonical-entitlement
`lifetime` event is ignored before persistence. Focused BillingTests pass
`10/10`; the full API suite passes `141/141`. Provider, runtime, managed,
human, and production verification remain open. See the
[E151 record](../audit/evidence/evidrilo-billing-product-allowlist-2026-09-14.md).

E152 protects the last active organization owner during account deletion.
Migration `027_account_deletion_owner_guard` locks affected organizations and
maps the fail-closed database boundary to `409 OWNER_TRANSFER_REQUIRED` in the
API. Fresh PostgreSQL smoke observes `LAST_OWNER_DELETION_GUARD_PASS` and the
existing deletion assertions; provider, managed, deployment, and human gates
remain open. See the [E152 record](../audit/evidence/evidrilo-account-deletion-owner-guard-2026-09-14.md).

E153 hardens client/request boundaries: mobile billing requires canonical
`evidrilo_pro` plus an approved `monthly`/`yearly` product, content/sync/
analytics reject unverified stored sessions, account deletion keeps the
owner-transfer action visible, chunked webhook input is bounded before parsing,
and caller cancellation is propagated through optional AI. The current local
boundary passes Kotlin `267/267`, API `144/144`, Node `77/77`, worker `5/5`,
shared iOS target compilation, and fresh PostgreSQL/worker smoke. External
provider, runtime, managed, staging, production, human, and submission gates
remain open. See [E153](../audit/evidence/evidrilo-client-boundary-hardening-2026-09-14.md).

E154 bounds custom-scheme auth callbacks to 8 KiB before parsing or pending
retention. The callback parser/bus regression, full local verifier, and shared
iOS target compilation pass; the current verifier records API `144/144`, Node
`77/77`, and worker `5/5`. Native callback runtime and provider-backed account
verification remain open. See [E154](../audit/evidence/evidrilo-auth-callback-boundary-2026-09-14.md).

E155 requires one to 32 meaningful challenge variants in every accepted
authoring document. Focused content/authoring coverage passes `22/22` and the
full API suite passes `145/145`; managed content publication, provider,
runtime, staging, human, and submission gates remain open. See [E155](../audit/evidence/evidrilo-content-challenge-required-2026-09-14.md).

E156 revalidates stored authoring JSON under the transition row lock before
`approved` or `published`. Focused content/authoring coverage passes `23/23`
and the full API suite passes `146/146`; managed database replay, content
publication, provider, runtime, staging, human, and submission gates remain
open. See [E156](../audit/evidence/evidrilo-stored-content-transition-guard-2026-09-14.md).

E157 makes the Kotlin published-case reader reject an empty challenge-variant
array. Focused reader coverage passes `8/8`; the full Kotlin/JVM suite passes
`269/269`, JVM/Android compilation passes, and shared iOS targets compile.
Managed content publication, provider, device, staging, human, and submission
gates remain open. See [E157](../audit/evidence/evidrilo-mobile-content-challenge-required-2026-09-14.md).

E158 adds `minItems: 1` to the published-case schema, and E159 aligns its
identifier, title, and skill-tag bounds with the API and Kotlin readers. The
focused contract and full local verifier pass with Kotlin `269/269`, Node
`77/77`, API `146/146`, worker `5/5`, and deployment checks. Cross-field
references remain runtime validation. See [E159](../audit/evidence/evidrilo-case-schema-identifier-bounds-2026-09-14.md).

E160 corrects the content-authoring runbook's stale “optional challenge
variants” instruction and protects the required one-to-32 invariant with the
documentation contract. The focused contract and full local verifier pass;
managed editorial publication remains external. See [E160](../audit/evidence/evidrilo-content-runbook-challenge-required-2026-09-14.md).

E161 aligns the recommendation schema with the mobile/API status-specific
response rules. The focused contract and full local verifier pass with Kotlin
`269/269`, Node `78/78`, API `146/146`, worker `5/5`, and deployment checks.
Provider and runtime evidence remain external. See [E161](../audit/evidence/evidrilo-recommendation-schema-alignment-2026-09-14.md).

E162 synchronizes the current Node verification snapshot after the E161
contract addition; historical counts remain provenance. See [E162](../audit/evidence/evidrilo-node-verification-snapshot-2026-09-14.md).

E163 hardens local conclusion-session restoration by rejecting unknown enum
values, malformed escaped lists, and encoded values over 8 KiB before parsing.
The final local verifier passes Kotlin/JVM `271/271`, Node `78/78`, API
`146/146`, worker `5/5`, and deployment checks. External provider, device,
managed, staging, human, and submission gates remain open. See [E163](../audit/evidence/evidrilo-local-session-decoder-hardening-2026-09-14.md).

E164 hardens the cohort aggregate boundary so stale enrollments are excluded
when organization membership is inactive. Migration 028, the `28/28` migration
contract, focused API readiness `4/4`, and fresh PostgreSQL/RLS smoke pass. The
tracker remains `78/92`; managed database, provider, device, staging, human,
publication, and submission gates remain open. See [E164](../audit/evidence/evidrilo-cohort-active-membership-boundary-2026-09-14.md).

E165 hardens the cohort role boundary so only active `learner` memberships are
counted. Migration 029, its focused contract, API readiness `4/4`, and fresh
PostgreSQL/RLS smoke pass; the tracker remains `78/92` and managed database,
provider, device, staging, human, publication, and submission gates remain open.
See [E165](../audit/evidence/evidrilo-cohort-learner-role-boundary-2026-09-14.md).

E166 hardens membership role changes so a grant cannot demote the last active
organization owner. The store locks the organization before counting owners;
the focused policy regression and full API suite pass `147/147`. See [E166](../audit/evidence/evidrilo-last-owner-role-change-2026-09-14.md).

E167 hardens the billing webhook identity boundary. The RevenueCat parser now
ignores an all-zero `app_user_id` before the store is called; the focused
billing suite passes `11/11` and the full API suite passes `148/148`. See
[E167](../audit/evidence/evidrilo-billing-empty-account-guard-2026-09-14.md).

E169 hardens the auth provider boundary so non-string confirmation metadata
cannot mark a local session as email-verified. Focused auth coverage passes
`13/13`, and Kotlin/JVM passes `271/271` with JVM, Android, and shared-iOS
compilation. Provider, native runtime, managed, staging, human, publication,
and submission gates remain open. See
[E169](../audit/evidence/evidrilo-auth-provider-type-boundary-2026-09-14.md).

E170 adds explicit regression coverage for learner, teacher, and reviewer
membership role-assignment attempts. The existing authorization policy denies
those actors; focused access-policy coverage passes `9/9` and the full API
suite passes `151/151` without changing authorization behavior. Provider,
native runtime, managed, staging, human, publication, and submission gates
remain open. See
[E170](../audit/evidence/evidrilo-membership-role-assignment-coverage-2026-09-14.md).

E171 records the repository-owned offline-audio implementation: focused audio
coverage passes `28/28`, five bounded interaction effects pass manifest and
budget checks, and the full local boundary passes Kotlin/JVM `299/299`, Node
`87/87`, API `151/151`, and worker `5/5`. Reviewed narration, device playback,
accessibility-service, and human audio-quality evidence remain open. See
[E171](../audit/evidence/evidrilo-offline-audio-implementation-2026-09-14.md).

E172 hardens local restoration boundaries. Session phases are bound to the
supported main/challenge case IDs, and secure-session records reject unsafe
account identifiers before billing, sync, or request use. Focused coverage
passes `5/5` for each boundary; the full local boundary passes Kotlin/JVM
`301/301`, Node `87/87`, API `151/151`, worker `5/5`, Android compilation,
shared iOS target compilation, and deployment checks. Provider, native runtime,
managed, staging, human, publication, and submission gates remain open. See
[E172](../audit/evidence/evidrilo-local-session-boundary-2026-09-14.md).

E173 hardens asynchronous sync and billing state boundaries. Network results
must still match the local queue snapshot and request generation before they
commit; visible sync results are tied to account/consent state; restoration
waits before clearing queues; and the full approved monthly/yearly catalog is
preserved. Focused coverage passes `18/18`; the full local boundary passes
Kotlin/JVM `306/306`, Node `87/87`, API `151/151`, worker `5/5`, Android and
shared iOS compilation, and deployment checks. External runtime, provider,
managed, human, publication, and submission gates remain open. See
[E173](../audit/evidence/evidrilo-async-state-boundaries-2026-09-14.md).

E174 hardens response and refresh boundaries with fail-closed validation. Sync
rejects non-monotonic or skipping cursors and unrelated push results; auth
refresh clears a stale secure session when confirmation is withdrawn; and
direct billing presentations enforce monthly/yearly only. Focused sync, auth,
and billing suites pass `18/18`, `14/14`, and `9/9`; the latest full local
boundary passes Kotlin/JVM `311/311`, Node `87/87`, API `151/151`, worker `5/5`,
Android/shared-iOS target compilation, deployment checks, and audio asset
checks. External runtime, provider, managed, staging, human, publication, and
submission gates remain open. See [E174](../audit/evidence/evidrilo-fail-closed-response-and-refresh-boundaries-2026-09-14.md).

E175 closes the remaining pull-response lower-bound gap: returned changes must
have `serverSequence > cursor` and `serverSequence <= nextCursor`. The focused
sync regression passes `29/29`; the latest full local boundary passes
Kotlin/JVM `312/312`, Node `87/87`, API `151/151`, worker `5/5`,
Android/shared-iOS target compilation, deployment, and audio checks. External
runtime, provider, managed, staging, human, publication, and submission gates
remain open. See [E175](../audit/evidence/evidrilo-sync-cursor-lower-bound-2026-09-14.md).

E176 hardens cancellation and malformed-input boundaries across the mobile
transport, API write endpoints, sync validation, recommendation state, and
account redirect lifecycle. The current local boundary passes Kotlin/JVM
`317/317`, Node `87/87`, API `153/153`, worker `5/5`, Android/shared-iOS target
compilation, deployment, and audio checks. Runtime, provider, managed, staging,
human, publication, and submission gates remain open. See [E176](../audit/evidence/evidrilo-request-lifecycle-and-input-boundaries-2026-09-14.md).

E177 hardens sync pull pagination by passing the validated requested `limit`
into response validation. A smaller `hasMore=true` page is accepted, while an
oversized response is rejected. Kotlin/JVM `319/319`, Node `87/87`, API
`153/153`, worker `5/5`, Android/shared-iOS target compilation, deployment,
and audio checks pass. Runtime, provider, managed, staging, human,
publication, and submission gates remain open. See [E177](../audit/evidence/evidrilo-sync-page-size-boundary-2026-09-14.md).

E178 hardens API full-match validation and staging environment normalization.
Identifiers, digests, actions, reason codes, case-version IDs, and request IDs
reject trailing-newline boundary bypasses; conventional `Staging` is accepted
as canonical `staging`. API `158/158` passes. Runtime, provider, managed
staging, human, publication, and submission gates remain open. See [E178](../audit/evidence/evidrilo-api-input-and-staging-boundary-2026-09-14.md).

E179 hardens consent-bound sync orchestration. Failed or invalidated pulls do
not start a push, active sync jobs are cancelled on account/consent changes,
enablement has one state-driven launch owner, and failed queue clears preserve
truthful pending-count state. Kotlin/JVM `321/321` passes; runtime, provider,
managed, staging, human, publication, and submission gates remain open. See
[E179](../audit/evidence/evidrilo-sync-consent-cancellation-boundary-2026-09-14.md).

E181 hardens the case-authoring transition boundary. The endpoint now rejects
unknown schema, version, and target-state values before storage; focused
transition coverage passes `2/2`. The full local verifier passes the current
Kotlin/Android build boundary, Node `93/93`, API `162/162`, worker `5/5`,
deployment, and asset checks. This is repository-local evidence only; runtime,
provider, managed, staging, human, publication, and submission gates remain
open. See
[E181](../audit/evidence/evidrilo-case-transition-contract-boundary-2026-09-15.md).

E183 hardens the shared Compose choice control. Radio-style choices use native
selectable semantics, multi-select evidence facts use native toggleable
semantics, and the card merges label/state information. Direct contracts pass
`25/25`; the full local verifier passes Node `95/95`, API `163/163`, worker
`5/5`, the current Kotlin/Android build boundary, Android release packaging,
deployment, and asset checks. This is repository/build evidence only; runtime,
provider, managed, staging, human, publication, and submission gates remain
open. See [E183](../audit/evidence/evidrilo-choice-accessibility-semantics-2026-09-15.md).

E182 aligns the server sync-pull cursor boundary with the mobile parser. Cursors
outside `0..1_000_000_000_000_000` are rejected before storage; the focused
regression passes `1/1`. The full local verifier passes the current
Kotlin/Android build boundary, Node `93/93`, API `163/163`, worker `5/5`,
deployment, and asset checks. Runtime, provider, managed, staging, human,
publication, and submission gates remain open. See
[E182](../audit/evidence/evidrilo-sync-cursor-contract-boundary-2026-09-15.md).

E180 prepares the mobile release candidate. Android's optimized release bundle,
fail-closed signing task, manifest security defaults, and explicit version
inputs pass local checks; iOS has a distribution-oriented Release template and
an unsigned CI host-build lane. Kotlin/JVM `324/324`, Node `93/93`, API
`161/161`, worker `5/5`, Android release packaging, shared iOS target
compilation, and release checks pass. Device runtime, archive/export, signing,
store, provider, managed, accessibility, human, publication, and submission
gates remain open. See [E180](../audit/evidence/evidrilo-mobile-release-candidate-preparation-2026-09-14.md).

## Local configuration

Keep the following local:

- `local.properties`
- signing files and certificates
- private or production billing configuration
- Android Gradle properties `revenuecatAndroidApiKey`,
  `revenuecatEntitlementId`, and `revenuecatProductIds`
- Android release settings `androidVersionCode`, `androidVersionName`,
  `androidReleaseKeystore`, `androidReleaseStorePassword`,
  `androidReleaseKeyAlias`, and `androidReleaseKeyPassword` in ignored local
  properties, or the corresponding `EVIDRILO_ANDROID_*` environment values
- Android Gradle properties `supabaseUrl`, `supabasePublishableKey`,
  `supabaseAuthRedirectUrl`, and `evidriloApiBaseUrl`
- iOS `REVENUECAT_PUBLIC_SDK_KEY`, `REVENUECAT_ENTITLEMENT_ID`, and
  `REVENUECAT_PRODUCT_IDS` in a local/ignored xcconfig
- iOS `SUPABASE_URL`, `SUPABASE_PUBLISHABLE_KEY`,
  `SUPABASE_AUTH_REDIRECT_URL`, and `EVIDRILO_API_BASE_URL` in a local/ignored
  xcconfig. The supported redirect is `evidrilo://auth/callback`.
- API `TRUSTED_PROXY_ADDRESSES` only when deployment terminates TLS through
  explicitly named proxy IPs; forwarded headers are ignored by default
- machine-specific Xcode settings
- iOS release signing and version overrides from the ignored
  `iosApp/Configuration/Release.xcconfig` or `Local.xcconfig`, based on
  `Release.xcconfig.example`

The configuration files in this repository contain only examples and empty
defaults. RevenueCat public Test Store values may be supplied through local
configuration when the billing milestone is approved; production credentials
and secret API keys must never be placed in source control. Product and
entitlement identifiers are also owner-created configuration, not hard-coded
dashboard evidence.

For the optional account lane, create/configure the Supabase project outside
the repository, enable email/password and Google provider settings, register
`evidrilo://auth/callback` in the Auth redirect allow-list, configure an
approved SMTP sender for verification/reset mail, and use a Google Web OAuth
client as required by the provider setup. The mobile build receives only the
Supabase project URL and publishable key; service-role/secret keys never belong
in a client build. No provider values are present in this checkout.

## Change workflow

1. Read the relevant product and architecture documents.
2. Make the smallest change that satisfies the current milestone.
3. Add or update deterministic tests with the change.
4. Run the narrowest relevant checks, then the repository checks above.
5. Update the decision log only when a durable project decision changes.
6. Keep public documentation in English and free of internal evidence paths.

Do not use `git add .` in a workspace that contains internal material. Stage the
public allowlist explicitly.
