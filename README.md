# Evidrilo

Evidrilo is a local-first evidence workspace that helps learners turn bounded
observations into clear, appropriately scoped conclusions.

~~~text
Requirement → Evidence → Claim → Boundary → Action → Revision → Verification
~~~

The product evaluates the relationship between a learner's input and the facts
provided by a case. It is not a scientific-truth grader, plagiarism checker,
academic marking system, safety advisor, or general-purpose AI answer generator.

[![Kotlin](https://img.shields.io/badge/Kotlin-2.3.20-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Compose Multiplatform](https://img.shields.io/badge/Compose%20Multiplatform-1.11.1-4285F4?logo=jetpackcompose&logoColor=white)](https://www.jetbrains.com/compose-multiplatform/)
[![Android](https://img.shields.io/badge/Android-API%2035-3DDC84?logo=android&logoColor=white)](https://developer.android.com/)
[![iOS](https://img.shields.io/badge/iOS-SwiftUI-000000?logo=apple&logoColor=white)](https://developer.apple.com/xcode/swiftui/)
[![ASP.NET Core](https://img.shields.io/badge/ASP.NET%20Core-.NET%2010-512BD4?logo=dotnet&logoColor=white)](https://dotnet.microsoft.com/apps/aspnet)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-336791?logo=postgresql&logoColor=white)](https://www.postgresql.org/)
[![RevenueCat](https://img.shields.io/badge/RevenueCat-KMP%203.7.0-00AEEF)](https://www.revenuecat.com/)
[![License](https://img.shields.io/badge/License-MIT-2f855a)](./LICENSE)

## At a glance

| Area | Decision |
| --- | --- |
| Product | Evidence-linked conclusion practice with transparent feedback |
| Free core | Bundled case, local persistence, one revision, and evidence-change challenge |
| Mobile | Kotlin Multiplatform and Compose Multiplatform for Android and iOS |
| Platform | ASP.NET Core API, PostgreSQL, migrations, and a bounded worker lane |
| Monetization | RevenueCat evidrilo_pro entitlement with monthly/yearly products |
| Connectivity | Offline-first free practice; connected content and sync are optional |
| Repository license | MIT; see the single root [LICENSE](LICENSE) file |

Current structural baseline: `13af4ec`.

## Current status

The repository contains the current applied product direction:

- target home, sources, workspace, evidence, claim trace, verification,
  action-plan, profile, history, and evidence-change surfaces;
- a deterministic conclusion engine with anchored feedback and explicit
  CANNOT_ASSESS behavior;
- local session persistence, one-revision history, and an evidence-change
  challenge;
- ASP.NET Core API, PostgreSQL migrations, published-case contracts,
  authoring boundaries, evidence graph, sync, analytics, billing projection,
  and worker boundaries;
- RevenueCat monthly/yearly access, restore, retry, pending, cancellation, and
  fail-closed states.

The bundled M0_T2 case remains the safe offline default. The published-case API
is a prepared content boundary and is not yet claimed as the default mobile
runtime source.

The following still require owner-side or device-side evidence: Android/iOS
runtime review, TalkBack/VoiceOver review, RevenueCat Test Store transactions,
managed deployment, human validation, store release, and final Shipaton assets.

## Core experience

~~~mermaid
flowchart LR
    A[Open case] --> B[Read requirement and facts]
    B --> C[Map evidence]
    C --> D[Write claim and scope]
    D --> E[State limits and next action]
    E --> F{Deterministic evaluation}
    F -->|Anchors are sufficient| G[Prioritized feedback]
    F -->|Missing or ambiguous anchor| H[CANNOT_ASSESS]
    G --> I[One guided revision]
    H --> I
    I --> J[Compare before and after]
    J --> K[Evidence-change challenge]
    K --> L[Local history and next practice]
~~~

Every feedback item should make three things visible:

1. what the learner wrote;
2. which fact or rule supports the feedback; and
3. the smallest useful next change.

The evaluator abstains when those relationships cannot be established from the
active case. It never invents a positive result because a response looks
plausible.

## Architecture

Evidrilo uses a local-first, ports-and-adapters architecture. Shared Kotlin
code owns the case model, evaluator, reducers, session state, persistence
contracts, and most of the UI. Android and iOS hosts provide platform
capabilities. The optional platform lane owns authenticated content, consented
metadata, billing projections, analytics, and editorial workflows.

~~~mermaid
flowchart TB
    Learner((Learner)) --> Host[Android or iOS host]

    subgraph Mobile[Kotlin Multiplatform client]
        UI[Compose UI]
        App[Application coordination]
        Domain[Case model and deterministic evaluator]
        Local[(Local session and history)]
        Ports[HTTP, auth, audio, secure storage, billing ports]
        UI --> App --> Domain
        App --> Local
        App --> Ports
    end

    Host --> UI
    Ports -. optional .-> API[ASP.NET Core API]
    API --> DB[(PostgreSQL)]
    Worker[.NET worker] --> DB
    Billing[RevenueCat] -. CustomerInfo and signed webhooks .-> Ports
    Billing -. signed event .-> API
~~~

The trust direction is fixed:

~~~text
Case facts and versioned rules → deterministic evaluator → learner-visible feedback
~~~

Network services, RevenueCat, and future AI assistance may enrich the product,
but they must not silently replace that chain.

### Runtime modes

| Mode | Behavior |
| --- | --- |
| Offline free core | Bundled content, local drafts, local history, and deterministic evaluation. No account or network is required. |
| Connected content | Published immutable case after schema, identifier, hash, and evaluator-version checks. Invalid content falls back locally. |
| Premium | Active evidrilo_pro entitlement from RevenueCat. Only monthly and yearly products are accepted. |
| Sync | Explicit consent and verified account required. The competition scope syncs bounded progress metadata, not learner-authored draft text. |
| AI assistance | Optional future capability; non-grading, opt-in, redacted, bounded, and unable to determine truth or premium access. |

### RevenueCat boundary

RevenueCat is an adapter, not part of the evaluator. The client uses active
CustomerInfo entitlement state; the server accepts only verified, ordered, and
idempotent webhook projections. Purchase, restore, pending, cancellation,
revocation, expiry, unknown-product, and provider-outage states keep the free
core available and fail closed for premium access.

See the [RevenueCat architecture](docs/architecture/revenuecat.md) and the
[RevenueCat operations documentation](docs/operations/README.md).

## Tech stack

| Layer | Technology | Responsibility |
| --- | --- | --- |
| Shared mobile | Kotlin Multiplatform 2.3.20 | Domain, evaluator, state, persistence contracts, networking boundary, and shared UI |
| UI | Compose Multiplatform 1.11.1 | Android/iOS presentation and JVM walkthrough |
| Hosts | Android SDK/API 35 and SwiftUI/Xcode | Native lifecycle, secure storage, audio, HTTP, and packaging |
| Billing | RevenueCat KMP 3.7.0 | Offerings, purchase, restore, entitlement, Paywall, Customer Center, and webhook boundary |
| API | ASP.NET Core/.NET 10 | Versioned REST boundary, auth validation, policy, rate limits, and health checks |
| Data | PostgreSQL 16 and Npgsql 10 | Content, accounts, sync, billing projections, analytics, and audit data |
| Worker | .NET 10 hosted worker | Lease-bound projections, retries, and bounded rebuilds |
| Local operations | Docker Compose and migration ledger | Reproducible API/worker/PostgreSQL development stack |

## Repository map

| Path | Responsibility |
| --- | --- |
| `modules/domain` | Framework-neutral case model, evaluator, reducers, and feedback |
| modules/application | Account/session, consent, sync, analytics, and recommendation orchestration |
| modules/data | Persistence contracts, codecs, recovery, and storage adapters |
| modules/design-system | Shared visual tokens, components, icons, fonts, and reviewed assets |
| modules/features | Feature presentation contracts and user-facing state |
| apps/mobile-shared | Compose screens, coordinator, content/audio/billing adapters, and shared tests |
| apps/android | Android host and platform configuration |
| apps/ios | Xcode host, SwiftUI entry point, and iOS configuration |
| contracts | Versioned JSON schemas, route manifests, and fixtures |
| platform | ASP.NET Core API, worker, database integration, and tests |
| infra | Local Compose, Dockerfiles, and deployment handoff boundaries |
| scripts | Verification, safety, packaging, asset, and architecture checks |
| docs | Public product, architecture, development, testing, release, and operations documentation |
| internal | Ignored/private research, design, audit, and agent context; never part of the public package |

## Contracts and safety rules

These rules are product invariants:

- A session evaluates exactly one identified CaseVersion.
- Published case versions are immutable; content changes create a new version,
  hash, and compatibility boundary.
- Determinate feedback must reference facts or rules in the active case.
- Unknown, stale, contradictory, or ambiguous anchors produce rejection or
  CANNOT_ASSESS, never an invented answer.
- The same case version and input produce the same evaluator result; AI is not
  on the truth decision path.
- The competition flow records one initial response and one guided revision.
- Caller-supplied account IDs never establish identity; the API uses verified
  provider claims.
- Sync requires consent, bounded commands, an idempotency key, and a cursor.
- Learner-authored drafts remain local within the current competition boundary.
- Passwords, access tokens, provider payloads, private research, reviewer data,
  secrets, and generated output do not belong in the public repository.

Detailed records live in [docs/decisions.md](docs/decisions.md), the
[product contract](docs/product/m0-product-contract.md), and the
[architecture documentation](docs/architecture/).

## Public repository boundary

The public package contains reproducible source, contracts, synthetic fixtures,
tests, scripts, and public documentation. It excludes private research,
participant/reviewer data, design working files, provider credentials, database
secrets, signing material, generated output, caches, and private media.

Before preparing a public package, run:

~~~bash
bash scripts/github/export-public-package.sh . /path/to/empty-candidate
bash scripts/verification/check-public-package.sh /path/to/empty-candidate
bash scripts/security/check-github-safety.sh .
~~~

The exporter is stricter than a normal source checkout. A clean local workspace
is not evidence that a public package, provider transaction, or production
deployment has been verified.

## Quick start

### Requirements

- JDK 21 with java and javac;
- Android SDK for Android builds;
- .NET 10 SDK for API and worker work;
- Docker Compose for the local platform stack;
- macOS and Xcode for the iOS host.

The Gradle Wrapper is included; a global Gradle installation is not required.

### Mobile and shared tests

~~~bash
git clone https://github.com/AndroLay/evidrilo.git
cd evidrilo
cp local.properties.example local.properties
# Set sdk.dir in local.properties to the Android SDK location.

./gradlew :composeApp:jvmTest :composeApp:compileKotlinJvm
./gradlew :androidApp:assembleDebug
~~~

### API, worker, and contracts

~~~bash
dotnet test platform/api.Tests/Evidrilo.Api.Tests.csproj
dotnet test platform/worker.Tests/Evidrilo.Worker.Tests.csproj --configuration Release
node --test contracts/contracts.test.mjs
node --test platform/database/migrations/migrations.test.mjs
~~~

### Local platform stack

~~~bash
bash scripts/verification/check-deployment.sh .
docker compose -f infra/environments/local/docker-compose.yml up --build -d
curl --fail http://127.0.0.1:5080/health/live
docker compose -f infra/environments/local/docker-compose.yml down
~~~

The local PostgreSQL trust mode is isolated to development and must not be used
for staging or production. For source-only checkouts, keep SDKs, caches, NuGet
packages, generated output, and private media outside the repository. See the
[development guide](docs/development/README.md) for machine-local overrides.

For iOS, open apps/ios/iosApp.xcodeproj in Xcode on macOS. Linux compilation
does not prove that the iOS host launches or renders on a simulator/device.

## Verification

Use the smallest relevant check during development, then run the complete local
verification script for a release candidate:

~~~bash
bash scripts/ci/verify-local.sh
~~~

| Evidence | Proves | Does not prove |
| --- | --- | --- |
| commonTest and JVM tests | Deterministic domain, reducer, persistence, and billing-state behavior for executed tests | Android/iOS rendering or device behavior |
| Android build | Configured Android source compiles and packages | Visual quality, accessibility, signing, or store upload |
| .NET and database tests | API, worker, migration, and integration contracts for executed environments | Managed provider or production traffic |
| Architecture, safety, and package checks | Public boundary, dependency rules, and forbidden artifact protection | Human review or final publication |
| Android emulator/iOS simulator and accessibility review | Runtime interaction on tested targets | Untested devices and future provider configurations |
| RevenueCat Test Store and managed staging | Offering, purchase, restore, revoke, auth, and deployment behavior in those environments | Long-term revenue, retention, or scale |
| Human validation | Whether intended learners understand the flow and feedback | A guarantee of learning outcomes or competition results |

The repository uses evidence language deliberately: **implemented** means code and
focused checks exist; **verified** means the named command or environment was
actually run; **open** means an external gate remains.

## Documentation

- [Product contract](docs/product/m0-product-contract.md)
- [Architecture decision](docs/architecture/platform-decision.md)
- [Repository structure](docs/architecture/repository-structure.md)
- [RevenueCat architecture](docs/architecture/revenuecat.md)
- [Development guide](docs/development/README.md)
- [Testing guide](docs/testing.md)
- [Release guide](docs/release.md)
- [Roadmap](docs/roadmap.md)
- [Decisions](docs/decisions.md)
- [API documentation](docs/api/README.md)
- [Third-party notices](THIRD_PARTY_NOTICES.md)

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md). Changes should preserve the free core,
privacy boundaries, deterministic evaluator, fail-closed entitlement behavior,
backward-compatible migrations, and tests appropriate to the changed boundary.

## License

Evidrilo source is released under the [MIT License](LICENSE).

Third-party notices are recorded separately in
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) and the linked
[docs/licenses/](docs/licenses/) files. Those notices do not replace or add a
second root project license.
