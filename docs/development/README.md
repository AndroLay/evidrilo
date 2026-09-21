# Development Guide

This guide covers the reproducible repository workflow for Evidrilo. It is an
operational guide, not a claim that a device run, provider transaction, hosted
service, human study, publication, or competition submission has passed.

## Current product boundary

Evidrilo is a Kotlin Multiplatform and Compose Multiplatform mobile workspace
with a deterministic, case-bounded verification flow. The free path is local
first and does not require an account, network, API, or RevenueCat purchase.
The repository also contains an ASP.NET Core/PostgreSQL platform foundation;
local platform checks do not imply a managed deployment.

The current product contract is in
[`docs/product/m0-product-contract.md`](../product/m0-product-contract.md).
The current private control source is the
[Source of Truth](../../internal/research/next-gen/SOURCE_OF_TRUTH.md).

## Required tools

- JDK 21 and the included Gradle Wrapper;
- Android SDK for Android builds and runtime checks;
- macOS and Xcode for an iOS host run;
- .NET 10 SDK for the optional platform lane;
- Node.js for contract, asset, and package-boundary checks.

No global Gradle installation is required.

## First checkout

```bash
git clone https://github.com/AndroLay/evidrilo.git
cd evidrilo
cp local.properties.example local.properties
```

Set `sdk.dir` in `local.properties` to the Android SDK path. The file is
ignored and must never be committed.

Use the existing `main` checkout for owner-authorized work. Do not create,
remove, switch, publish, or push branches/worktrees without explicit owner
authorization. Before a commit, inspect the exact staged path list and never
stage ignored internal notes, credentials, caches, generated output, or
machine-specific files.

## Source-only workspace

Generated output and toolchains belong outside the repository. Keep these paths
out of a public checkout:

```text
.local/ .gradle-local/ .dotnet-local/ .gradle/
build/ **/build/ **/bin/ **/obj/ local.properties
```

Use the normal system SDKs or task-specific external cache locations. Do not
move or remove existing SDKs, caches, design sources, or private media as part
of a documentation or code change; those are owner actions.

## Verification workflow

Use the repository harness for the broad local boundary:

```bash
bash scripts/ci/verify-local.sh
```

Useful focused checks are:

```bash
./gradlew :composeApp:jvmTest :composeApp:compileKotlinJvm
./gradlew :androidApp:assembleDebug
dotnet test platform/api.Tests/Evidrilo.Api.Tests.csproj
dotnet test platform/worker.Tests/Evidrilo.Worker.Tests.csproj --configuration Release
node --test contracts/contracts.test.mjs
node --test platform/database/migrations/migrations.test.mjs
bash platform/database/integration/run-local-postgres-smoke.sh
bash platform/worker/integration/run-local-worker-smoke.sh
```

Run only the checks relevant to the changed boundary, then use the full
harness before a release decision. A passing local suite does not prove iOS
runtime, assistive-technology behavior, RevenueCat provider transactions,
managed services, or human comprehension.

## Mobile runtime

The bounded free flow is:

```text
Welcome → supplied case → evidence and relation → claim
→ scope and limitation → next action → verification
→ one revision → before/after → evidence-change challenge
→ changed-evidence feedback → local history
```

Android is the primary local runtime evidence path. A macOS/Xcode host run is
required before making an iOS runtime claim. Shared Kotlin compilation is not
an iOS runtime observation. Accessibility claims must identify the exact
device, route, assistive technology, and result observed.

## Platform lane

The optional local platform lane uses ASP.NET Core, PostgreSQL migrations/RLS,
and a worker. Its local integration command is:

```bash
bash platform/integration/run-local-api-worker-e2e.sh
```

The local database uses isolated development credentials only. Managed
Supabase, Render, production secrets, hosted TLS/proxy behavior, and external
deployment are separate owner-authorized gates. The free mobile route must
remain usable when the platform is unavailable.

## RevenueCat boundary

The intended product model is one `evidrilo_pro` entitlement with monthly and
yearly packages. The free core must remain available without RevenueCat. The
provider matrix must separately verify offering load, purchase success,
pending/cancel/failure handling, entitlement activation, restore, relaunch,
revocation, and offline fallback before those behaviors are claimed.

Use the
[RevenueCat test-store runbook](../operations/revenuecat-test-store-runbook.md)
for an owner-authorized provider run. Repository adapters and unit/integration
tests are not provider evidence.

## Public package boundary

Prepare a candidate in an empty directory outside the repository:

```bash
bash scripts/github/export-public-package.sh . /path/to/empty-candidate
bash scripts/verification/check-public-package.sh /path/to/empty-candidate
```

The exporter is allowlist-based and excludes internal research, private
submission notes, credentials, caches, generated output, and target-design
references. Verify the candidate tree before any owner-authorized publication.
The clean-clone check proves local reproducibility only; it does not prove
GitHub visibility or hosted CI.

## Documentation rules

- Use the Source of Truth for current scope and gate status.
- Use dated evidence records for historical provenance; do not copy their
  entire contents into active guides.
- Keep `internal/design/` as the sole current private visual target. It is not
  a public asset or a substitute for runtime visual review.
- Record provider, managed-service, human, and publication results only after
  the corresponding external action is actually performed.
- Update the concise active note and link to the dated evidence record instead
  of creating another status log.

## Related references

- [Testing strategy](../testing.md)
- [Release checklist](../release.md)
- [Backend execution guide](../operations/evidrilo-backend-execution.md)
- [Repository structure](../architecture/repository-structure.md)
- [Public package exporter](../../scripts/github/export-public-package.sh)
