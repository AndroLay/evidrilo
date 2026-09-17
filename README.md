# Evidrilo

Evidrilo is a mobile learning app for practising how to turn observations into
clear, measurable conclusions that respect the limits of the available
evidence.

A learner reads a case, selects relevant evidence, writes a conclusion,
receives explainable feedback, makes one revision, and then tests how the
conclusion changes when one observation is no longer available.

> Status: **E186 — Backend engine and sync boundary hardened**. RevenueCat price
> migration and transaction verification remain open. Repository
> implementation and local verification are available, but device runtime,
> provider, deployment, human review, store, and submission gates remain
> separate.

Current repository increment: E186 / BACKEND_ENGINE_SYNC_BOUNDARY_HARDENED / E185 / REVENUECAT_OFFERING_MIGRATION_OBSERVED / E183 / NATIVE_CHOICE_ACCESSIBILITY_SEMANTICS_HARDENED / E182 / SYNC_CURSOR_CONTRACT_BOUNDARY_ALIGNED / E181 / CASE_TRANSITION_CONTRACT_BOUNDARY_HARDENED / E180 / MOBILE_RELEASE_CANDIDATE_PREPARATION / E179 / SYNC_CONSENT_CANCELLATION_BOUNDARY_HARDENED / E178 / API_INPUT_AND_STAGING_BOUNDARY_HARDENED / E177 / SYNC_PULL_PAGE_SIZE_BOUNDARY_HARDENED.

[![Kotlin](https://img.shields.io/badge/Kotlin-2.3.20-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Compose Multiplatform](https://img.shields.io/badge/Compose%20Multiplatform-1.11.1-4285F4?logo=jetpackcompose&logoColor=white)](https://www.jetbrains.com/compose-multiplatform/)
[![Android](https://img.shields.io/badge/Android-API%2035-3DDC84?logo=android&logoColor=white)](https://developer.android.com/)
[![SwiftUI](https://img.shields.io/badge/iOS-SwiftUI-000000?logo=apple&logoColor=white)](https://developer.apple.com/xcode/swiftui/)
[![ASP.NET Core](https://img.shields.io/badge/ASP.NET%20Core-.NET%2010-512BD4?logo=dotnet&logoColor=white)](https://dotnet.microsoft.com/apps/aspnet)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-336791?logo=postgresql&logoColor=white)](https://www.postgresql.org/)
[![RevenueCat](https://img.shields.io/badge/RevenueCat-KMP%203.7.0-00AEEF)](https://www.revenuecat.com/)
[![Docker Compose](https://img.shields.io/badge/Docker%20Compose-local%20stack-2496ED?logo=docker&logoColor=white)](https://docs.docker.com/compose/)
[![License](https://img.shields.io/badge/License-MIT-2f855a)](./LICENSE)

## Product flow

```mermaid
flowchart LR
    A[Open app] --> B[Read bounded case]
    B --> C[Connect observations]
    C --> D[Write conclusion]
    D --> E[Receive evidence-anchored feedback]
    E --> F[Revise once]
    F --> G[Evidence-change challenge]
    G --> H[Compare before and after]
    H --> I[Review local history]
```

Evidrilo is intentionally bounded. The evaluator only assesses the
relationship between the goal, observations, limitations, and conclusion in
the supplied case. It is not a scientific-truth grader, academic marking
system, citation manager, safety advisor, or automatic answer generator.

## Product scope

| Area | Main features | Dependency |
| --- | --- | --- |
| Free learning | One synthetic case, fact/relation selection, conclusion, scope, limitation, next action, deterministic feedback, one revision, before/after comparison, evidence-change challenge, history, and reset/replay | Local only; no login, API, network, database, AI, or billing |
| Offline audio | Lightweight optional interaction sounds, offline TTS through platform adapters, accessible controls, lifecycle handling, and Android audio focus | Device voice/audio capability; never affects evaluation or persistence |
| Accounts | Email/password, recovery, session refresh, sign-out, account deletion, and Google OAuth authorization-code PKCE | Auth provider and device runtime still require verification |
| Sync | Progress metadata, cursor, retry, consent, idempotency, and conflict-safe boundaries | Verified login, API, and PostgreSQL; learner drafts remain local |
| Premium | Two additional cases through the `evidrilo_pro` entitlement, `monthly` and `yearly` packages, Paywall, and Customer Center | RevenueCat; lifetime is rejected fail-closed |
| Platform | Content reader, authoring/review/publish, analytics, progress, recommendation, AI safety boundary, cohort/membership, audit trail, and worker projections | Optional API and managed infrastructure |

The free core remains usable when login, API, RevenueCat, database, audio, or
network is unavailable. Premium access is never opened by a local flag; it is
derived from a valid entitlement.

## Tech stack

| Layer | Technology | Role |
| --- | --- | --- |
| Shared mobile | Kotlin Multiplatform **2.3.20** | Domain model, evaluator, state, persistence contract, network boundary, and most UI |
| UI | Compose Multiplatform **1.11.1** | Shared UI surface for Android, iOS, and the JVM walkthrough |
| Android | Android SDK, Kotlin Android, Android Gradle Plugin **8.13.2**, R8/resource shrinking | Android host, secure/platform adapters, and release packaging |
| iOS | Thin SwiftUI/Xcode host plus shared Kotlin/Native framework | iOS entry point and native platform integration |
| Billing | RevenueCat KMP **3.7.0** | Offerings, purchase, restore, entitlement, Paywall, Customer Center, and webhook boundary |
| API | C# with ASP.NET Core **.NET 10** | Versioned REST API, JWT/JWKS validation, authorization, rate limiting, error model, and health checks |
| Database | PostgreSQL **16** locally; Supabase managed PostgreSQL as the target | Migrations, RLS, sync feed, account/progress, content, billing projection, and audit data |
| Data access | Npgsql **10.0.0** | PostgreSQL access from the API and worker |
| Worker | .NET 10 hosted background worker | Projection rebuilds, lease fencing, retries, and bounded batch processing |
| Local operations | Docker, Docker Compose, and a versioned migration ledger | Reproducible API/worker/PostgreSQL development stack |
| Verification | Gradle tests, .NET tests, Node.js built-in test runner, and PostgreSQL smoke tests | Unit, contract, migration, integration, boundary, and packaging checks |

## Architecture

```mermaid
flowchart TD
    subgraph Mobile[Mobile client]
        UI[Compose Multiplatform UI]
        Core[Local-first core<br/>case, evaluator, history, audio]
        Adapters[Platform adapters<br/>secure storage, auth, HTTP, billing]
        UI --> Core
        UI --> Adapters
        Adapters --> Core
    end

    subgraph Optional[Optional cloud path]
        Auth[Supabase Auth<br/>email/password + Google PKCE]
        API[ASP.NET Core API<br/>versioned REST + policy boundaries]
        DB[(Supabase PostgreSQL)]
        Worker[.NET projection worker]
        Auth --> API
        API --> DB
        Worker --> DB
    end

    Billing[RevenueCat<br/>monthly/yearly] -->|entitlement in app| Adapters
    Billing -->|signed webhook| API
    Adapters -->|verified session + consented metadata| API
```

### Boundary principles

- The free learning loop is local-first and has no mandatory cloud dependency.
- Passwords are managed by the Auth provider; the Evidrilo API never receives
  or stores passwords.
- Sync carries only identifiers, revision metadata, cursors, and snapshot
  digests. Learner-authored conclusion drafts remain on the device for the
  competition scope.
- `evidrilo_pro` is the canonical entitlement. Only `monthly` and `yearly` are
  valid; `lifetime`, unknown products, and unverified states are rejected
  fail-closed.
- AI, if activated later, must remain optional, opt-in, non-grading, redacted,
  quota/timeout bounded, and unable to determine evaluator truth or premium
  access.
- The API and worker follow the database managed by the same migration ledger;
  every rollout and rollback must remain schema-compatible.

## Repository map

| Path | Responsibility |
| --- | --- |
| `apps/mobile-shared/src/commonMain` | Shared domain, evaluator, reducer, persistence contract, feature state, and Compose UI |
| `apps/mobile-shared/src/commonTest` | Cross-platform deterministic tests and regression boundaries |
| `apps/android` | Android host, manifest, secure storage, audio, HTTP, and release configuration |
| `iosApp` | Xcode host, Info.plist, iOS configuration, and SwiftUI entry point |
| `platform/api` | ASP.NET Core modular monolith API and storage adapters |
| `contracts` | Versioned JSON contracts, schemas, and fixtures |
| `platform/database` | PostgreSQL/Supabase migrations, ledger, RLS, and integration smoke tests |
| `platform/worker` | Projection worker plus lease/retry handling |
| `deploy` | Dockerfiles and local Compose; not a production deployment |
| `scripts` | Verification, release, public-package, asset, safety, and deployment checks |
| `docs` | Product, architecture, development, testing, release, business, and decision documents |

## Quick start

### Requirements

- JDK 21 with both `java` and `javac`.
- Android SDK for Android builds.
- .NET 10 SDK for the API/worker lane.
- Docker Compose for the optional local platform stack.
- macOS and Xcode for running the iOS host.

The Gradle Wrapper is included; a global Gradle installation is not required.

### Source-only workspace

The repository is intentionally source-first. Android SDKs, JDKs, .NET SDKs,
Gradle state, NuGet packages, generated output, and private raw media should
live outside the public source package. UI design working files may be kept in
the ignored `internal/design/` workspace folder. The scripts use external
locations by default and accept these optional machine-local overrides:

```bash
export EVIDRILO_GRADLE_USER_HOME="$HOME/.cache/evidrilo/gradle"
export EVIDRILO_NUGET_PACKAGES="$HOME/.cache/evidrilo/nuget"
export EVIDRILO_DOTNET_ROOT="/opt/dotnet"
```

Set `EVIDRILO_JAVA_HOME` for a JDK 21 installation when the system `java` and
`javac` are not already JDK 21. Set `sdk.dir` in the ignored
`local.properties` file for the Android SDK. Existing repository-local
toolchains are compatibility fallbacks only; use
`EVIDRILO_ALLOW_REPOSITORY_TOOLCHAINS=1` explicitly while migrating an older
checkout.

The verification scripts do not create repository-local caches by default. This
working copy has completed the external-path migration: toolchains, caches,
generated output, and private raw media are preserved outside the checkout; UI
design sources are kept under ignored `internal/design/`. On an older checkout,
inspect those directories and move them only
after the external-path verification succeeds. See the
[development guide](docs/development.md#source-only-workspace) for the safe
sequence.

```bash
git clone https://github.com/AndroLay/evidrilo.git
cd evidrilo
cp local.properties.example local.properties
# Set sdk.dir in local.properties to the Android SDK location.

./gradlew :composeApp:jvmTest :composeApp:compileKotlinJvm
./gradlew :androidApp:assembleDebug
```

For the broader repository verification pass:

```bash
bash scripts/verify-local.sh
```

The script runs the available Kotlin, Android, contract, migration, repository
boundary, deployment, API, worker, and asset lanes. A lane requiring a device,
macOS/Xcode, a provider account, or a managed service is not considered passed
merely because compilation succeeds.

### Run the API and database lanes

```bash
dotnet test platform/api.Tests/Evidrilo.Api.Tests.csproj
dotnet test platform/worker.Tests/Evidrilo.Worker.Tests.csproj --configuration Release
node --test contracts/contracts.test.mjs
node --test platform/database/migrations/migrations.test.mjs
```

Start the local API, worker, migration, and PostgreSQL stack:

```bash
bash scripts/check-deployment.sh .
docker compose -f deploy/docker-compose.local.yml up --build -d
curl --fail http://127.0.0.1:5080/health/live
docker compose -f deploy/docker-compose.local.yml down
```

This Compose stack is development-only. Its PostgreSQL trust mode is limited to
the isolated local network and must not be used for staging or production.

### Run iOS

Open `iosApp/iosApp.xcodeproj` in Xcode on macOS. Kotlin/Native compilation on
Linux does not prove that the iOS host launches, renders, or runs on a
simulator/device.

## Feature development

Use this sequence to keep shared code and boundaries consistent:

1. Define behavior in the shared domain and add a regression test in
   `commonTest`.
2. Add Compose state/reducer/UI coverage for happy, loading, error, empty,
   offline, cancellation, and retry states.
3. For OS capabilities, define an interface in the common source set and keep
   implementations small in `androidMain`/`iosMain`; do not bring native SDKs
   into the evaluator domain.
4. For API changes, update the contract/schema first, then migration, storage,
   authorization, endpoint, and integration tests.
5. For RevenueCat changes, preserve the monthly/yearly allowlist and cover
   active, pending, cancelled, failed, restored, revoked, unknown, and
   no-network states.
6. Run focused tests after a change and `scripts/verify-local.sh` at the end of
   a batch. Document decisions and evidence only from results that were
   actually observed.

Primary working documents:

- [Development guide](docs/development.md) — local toolchain and workflow.
- [Testing guide](docs/testing.md) — verification scope and limitations.
- [Architecture decision](docs/architecture/platform-decision.md) — why KMP,
  Compose, the API, and adapters were selected.
- [Repository structure](docs/architecture/repository-structure.md) —
  dependency direction and ownership.
- [RevenueCat boundary](docs/architecture/revenuecat.md) — entitlement and
  failure behavior.
- [Product contract](docs/product/m0-product-contract.md) — free-core rules.
- [Monetization note](docs/business/monetization-and-pricing.md) — reference
  pricing and package decisions.

## Deployment target

Deployment has not been performed. The prepared target for staging and
production is:

| Component | Recommended target | Notes |
| --- | --- | --- |
| API | Render Web Service from `deploy/docker/api.Dockerfile` | HTTPS, `/health/live`, environment secrets, and explicit CORS |
| Worker | Render Background Worker from `deploy/docker/worker.Dockerfile` | No public traffic; runs projection jobs |
| Database + Auth | Separate Supabase managed projects for staging and production | PostgreSQL, Auth/JWKS, RLS, migration ledger, and backup/recovery plan |
| Billing callback | RevenueCat webhook to the HTTPS API | Secret/auth header remains in the secret store; server entitlement stays fail-closed |
| Local development | Docker Compose | Not a production security profile |

Render is the initial deployment recommendation because the existing API and
worker Dockerfiles map directly to web-service and background-worker services.
Supabase provides managed PostgreSQL/Auth so the team does not need to operate
the database on a VPS at the start. A VPS remains possible, but adds ownership
of TLS, firewalling, patching, backups, monitoring, recovery, and scaling.

Provider references: [Render Web Services](https://render.com/docs/web-services),
[Render Background Workers](https://render.com/docs/background-workers), and
[Supabase database connections](https://supabase.com/docs/guides/database/connecting-to-postgres).
Deployment, staging, managed backup/restore, and load-test status must not be
called verified until they are run in the owner's environment.

## Configuration and security

- `local.properties` stores Android machine configuration and is Git-ignored.
- Local iOS configuration uses ignored `.xcconfig` files; `*.example` files are
  templates only.
- The API starts from [`platform/api/.env.example`](platform/api/.env.example).
- The API and worker receive configuration through environment variables or a
  secret manager, never through Dockerfiles, source code, image layers, logs,
  fixtures, or the README.
- A RevenueCat public SDK key may be application configuration, but webhook
  secrets, database credentials, signing keys, access tokens, service-role keys,
  and provider payloads remain server-side and must not enter the repository.
- Before creating a public package, run:

```bash
bash scripts/export-public-package.sh . /path/to/empty-candidate
bash scripts/check-public-package.sh /path/to/empty-candidate
```

In a checkout with valid Git metadata, also run
`bash scripts/check-github-safety.sh .` before committing or pushing. The
preparation workspace must not be treated as evidence that a push or public
publication has occurred.

## Status and evidence boundary

The latest recorded repository snapshot is E186:

- Implementation tracker: **85/100 (85%)**. This measures task coverage, not
  release readiness.
- Kotlin/JVM: **332/332** tests; Node contracts/migrations: **96/96**.
- ASP.NET Core API: **172/172** tests; worker: **5/5**.
- Android release packaging and shared iOS target compilation are available.

The latest local snapshot records Kotlin/JVM 332/332, Node 96/96, API 172/172, and worker 5/5; these are repository evidence only. E186 hardens engine validation, local snapshot preflight, canonical case identity, published-only sync admission, and the database boundary trigger. E185 remains the owner-authorized RevenueCat observation; its approved price migration and transaction matrix remain open. E183 hardens native radio and checkbox semantics; E182 aligns the server sync cursor bound; E181 rejects invalid case-transition contracts; E169 hardens the auth provider boundary; E170 adds explicit regression coverage.

E184 is the latest dated environment verification, not a product increment: a
checksum-verified Temurin JDK 21 restored the Kotlin/Android release check, but
Android emulator probes still stop before a usable framework/package service.
Current Android UI/runtime and G3 evidence therefore remain open; see the
[E184 probe record](audit/evidence/evidrilo-android-runtime-probe-2026-09-15.md).

The main open gates are:

- Real Android/iOS runtime and TalkBack/VoiceOver accessibility review.
- RevenueCat Test Store price migration, transaction matrix, and real entitlement
  behavior.
- Managed Supabase, Google SSO, email delivery, staging, production API,
  managed backup/restore, monitoring, and load testing.
- Reviewer/participant validation, final assets, public repository, store
  release, and submission.

This README intentionally does not duplicate the E-series chronology. Detailed
evidence, decisions, and task status are maintained in the [local Source of Truth](internal/research/next-gen/SOURCE_OF_TRUTH.md),
[evidence index](audit/evidence-index.md), [roadmap](docs/roadmap.md), and
[decisions](docs/decisions.md). A local build or test proves only the boundary
that was run; it does not prove device runtime, provider transactions, managed
deployment, human review, or publication.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md). Changes must preserve the free core,
privacy boundaries, fail-closed entitlement, backward-compatible migrations,
and tests appropriate to the changed boundary.

## License

Evidrilo is released under the [MIT License](LICENSE).
