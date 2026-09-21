# Repository Structure

Evidrilo is a Kotlin Multiplatform mobile application with a small
capability-organized ASP.NET Core platform. The repository keeps the bundled
learning loop local-first; online services are optional and must not become a
dependency for the free core.

## Source tree

```
text
evidrilo/
├── apps/
│   ├── android/              # Android application host
│   ├── ios/                  # iOS/Xcode host
│   └── mobile-shared/        # shared Compose UI and platform adapters
├── modules/
│   ├── core/                 # cross-cutting primitives
│   ├── domain/               # framework-independent models and rules
│   ├── application/          # workflows and orchestration
│   ├── data/                 # local persistence and repositories
│   ├── features/             # feature presentation contracts
│   └── design-system/        # shared visual system
├── contracts/               # versioned API schemas and fixtures
├── platform/
│   ├── api/                  # ASP.NET Core modular monolith
│   ├── worker/               # bounded background projection process
│   ├── database/             # PostgreSQL migrations and local harness
│   └── integration/          # local API/worker integration harness
├── infra/                    # local container configuration
├── scripts/                  # build, verification, release, and export tools
├── tests/                    # cross-boundary test assets
├── tooling/                  # repository tooling/configuration
├── docs/                     # product and engineering documentation
└── examples/                 # synthetic examples only
```

The tree is a map of existing ownership boundaries, not a target checklist.
Do not create placeholder modules or split projects merely to make the
directory structure look more layered.

## Runtime boundaries

```text
Learner
  │
  ▼
Compose Multiplatform app
  ├── Bundled case + deterministic evaluator
  │      └── Local draft, revision, evidence-change challenge, and history
  ├── BillingGateway
  │      └── RevenueCat adapter → active entitlement → premium cases
  └── Optional authenticated online capabilities
         └── ASP.NET Core API → PostgreSQL
                                └── bounded .NET worker
                                └── optional AI gateway → provider
```

The local evaluator does not call the API or an AI provider. The API and
database support optional authenticated capabilities such as account, sync,
published content, analytics, billing projection, and a server-owned AI credit
ledger. Those server features must preserve consent, authorization, and the
local-first free path. The AI gateway is optional, redacted, schema-checked,
and never authoritative for evaluator truth.

## Ownership by area

| Area | Responsibility |
| --- | --- |
| `apps/mobile-shared` | Compose screens, app composition, local workflow wiring, and host-facing adapters |
| `apps/android` | Android entry point, manifest, and Android-specific configuration |
| `apps/ios` | Thin iOS host that presents the shared app and owns iOS configuration |
| `modules/domain` | Deterministic case, draft, evaluation, and state-transition rules |
| `modules/application` | Account/session, consented analytics, sync, and recommendation orchestration |
| `modules/data` | Local persistence and repository abstractions |
| `modules/features` | Shared feature presentation models and contracts |
| `modules/design-system` | Reusable Compose components, tokens, and icons |
| `platform/api` | Authentication boundary, authorization, REST contracts, and server-owned operations |
| `platform/database` | Forward-only PostgreSQL migrations, RLS, and local database integration |
| `platform/worker` | Bounded, idempotent background projection/job processing |
| `contracts` | Language-neutral schemas and representative synthetic fixtures |

Compose screens and provider adapters remain in the shared app host when they
need platform-owned navigation, billing, or audio APIs. The API remains a
modular monolith until a concrete dependency or scaling need justifies a
verified split into separate .NET projects.

## Dependency direction

```text
Compose host ──> feature/application contracts ──> domain
      │                    │
      ├──> local data ─────┘
      ├──> BillingGateway ──> RevenueCat adapter
      └──> optional API client ──> versioned contracts

ASP.NET Core API ──> domain/application services ──> PostgreSQL
       │
       └──> worker contracts <── .NET worker
```

Keep the dependency direction explicit. Domain rules must not depend on
Compose, Android/iOS APIs, RevenueCat, HTTP, or database implementation
details. The local learning loop must remain complete if the optional API is
unavailable.

## Configuration and secrets

- Commit templates and empty examples only; keep local properties, signing
  material, provider credentials, and machine-specific Xcode settings out of
  source control.
- Client builds may contain only provider-approved public SDK keys. Never
  bundle service-role keys, secret API keys, database credentials, or webhook
  signing secrets.
- Keep sample data synthetic and avoid private learner drafts in fixtures,
  logs, screenshots, and public issue reports.

## Deliberately deferred

- Arbitrary document ingestion, OCR, and source-page/span indexing.
- AI as an evaluator or truth-grading service.
- Anonymous or client-controlled AI credit spending.
- Teacher dashboards before content governance, roles, and privacy review.
- Managed production hosting, realtime infrastructure, Redis, and
  microservices without separate approval and a measured need.
- Store-release automation; Next Gen does not require an app-store listing.

## References

- [Kotlin Multiplatform documentation](https://kotlinlang.org/docs/multiplatform.html)
- [Compose Multiplatform](https://www.jetbrains.com/compose-multiplatform/)
- [RevenueCat Kotlin Multiplatform installation](https://www.revenuecat.com/docs/getting-started/installation/kotlin-multiplatform)
- [AI assistance and credit contract](ai-assistance.md)
