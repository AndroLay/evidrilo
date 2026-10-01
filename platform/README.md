# Evidrilo Platform

Current observed runtime: [1 October Staging / judging acceptance](../docs/development/current-status.md).
Android uses hosted Render; Test Store is Debug-only. Production and native
iOS account/billing acceptance must not be inferred from Android evidence.

The platform is an optional online foundation for account-backed and
server-owned capabilities. The bundled learning workflow remains local-first
and must work without the API, database, or worker.

## Components

| Component | Purpose |
| --- | --- |
| `Evidrilo.Api` | ASP.NET Core/.NET 10 API with health, account, sync, progress, published case and project-template catalogs, student projects, authoring workflows, recommendation, AI-assistance, and billing boundaries |
| PostgreSQL | Versioned schema, migrations, row-level controls, and server-owned state |
| `Evidrilo.Worker` | Bounded asynchronous projection/job processing |
| `Evidrilo.sln` | Solution containing executable projects and tests |

The API is a capability-organized modular monolith. Keep related domain and
application logic together until a verified dependency boundary justifies a
separate project or service. The worker remains separate because it has a
distinct process and execution lifecycle.

## Data and trust boundaries

- The API derives identity from a verified authentication principal and
  applies authorization/resource-ownership checks server-side.
- The database uses forward-only migrations and account-scoped controls for
  protected data.
- Server writes are idempotent where retries are possible; event, case, and
  entitlement state is not inferred from client flags.
- The worker uses bounded, idempotent jobs and cannot make the mobile free
  workflow depend on background processing.
- AI assistance is optional and non-grading; provider output cannot replace
  deterministic evaluator results.
- D-126 and D-130 set server-owned AI grants at 20 credits once for a verified
  Free account and an additional 200 credits for each earned active
  `evidrilo_pro` month. Grants accumulate and do not expire after Pro ends.
  Forward migration 051 removes the old expiry behavior and supports
  multi-grant reservations; database/API integration acceptance remains
  environment-dependent. Failed assists release reservations; the client
  cannot grant or mutate credits.
- Missing configuration produces an explicit unavailable/degraded result,
  never a fabricated successful write or entitlement.

The mobile client connects to the platform through bounded readers for the
published M0 case, account progress, billing entitlements, and AI credit/assist
contracts. The API also exposes an optional account-owned academic project
workspace for manually entered student work: project CRUD, version history,
criterion/evidence links, and a structure-presence report. This report is
explicitly `not_assessed`; it does not score academic merit, evidence quality,
or real-world truth. Student project content remains private to its owner,
versioned on each successful save, exported with account data, and removed by
account deletion. The local evaluator for the bundled M0 case remains
authoritative and separate. The API also exposes account-scoped contextual AI conversation
session, turn, and clear routes. Session persistence is metadata-only; bounded
prior messages remain client-supplied and untrusted. Typed draft proposals are
returned as previews with `autoApplied: false`; the API does not update mobile
drafts. The mobile chat UI is not yet wired to these conversation routes.
These readers validate schema, ownership/session state, response
size, and transient failures before exposing data to Compose. Student project
routes use verified-account authorization, request fingerprints for
idempotent mutations, optimistic version checks, account-scoped PostgreSQL row
policies, and append-only revisions. They are an API foundation; the mobile
project workflow is not yet wired to these routes. Published case
content can update display text only when it matches the bundled evaluator's
stable case/version/fact contract. Local RevenueCat `CustomerInfo` remains the
immediate device access authority; the entitlement endpoint is reconciliation
and transparency, not a client-side grant mechanism.

The project-template catalog is a separate server-owned content boundary. It
defines five stable families—experimental laboratory, observational survey,
literature review, qualitative interview/field study, and design/engineering—
with versioned structure, method limitations, provenance, accessibility
expectations, and examples. Anonymous students can browse only current
published versions; authoring requires a verified account plus an active
organization author/reviewer/maintainer membership. Review approval records
reviewer-selected example IDs server-side, so authors cannot self-assert the
`reviewed` flag. Lifecycle and review decisions are append-only and actor
identities are anonymized on account deletion. The catalog is not a grade or
truth evaluator, and it is distinct from private student project documents.
No template content is seeded or marked published by this implementation.
The mobile client keeps family overviews bundled for offline use and performs
anonymous, read-only requests for published family counts, summaries, and
versioned details when the API is configured. A failed or unavailable request
does not block those overviews; this read path does not create or sync a student
project or persist a durable catalog cache.

Account export v1 remains a synchronous compatibility route capped at 128 KiB.
The verified-account v2 route queues one export per account and replays creation
by its idempotency key. Status and download access are account-scoped and marked
`no-store`; ready artifacts expire after 24 hours. The worker uses a fenced
lease and one global database lock, with a 64 MiB per-artifact cap and a 512
MiB aggregate ready-artifact cap. V2 adds Project AI consent, activity, and
conversation metadata but excludes prompts, responses, and provider request
payloads. Account deletion purges queued and retained export jobs.

## Local verification

### Local RevenueCat Test Store credit reconciliation

When a local Staging API has no public webhook URL, explicitly enable
`REVENUECAT_TEST_STORE_RECONCILIATION_ENABLED=true` and supply the public
`REVENUECAT_TEST_STORE_SDK_KEY` through its environment. Startup rejects this
mode outside Staging, without a database, or with a non-Test-Store key. It is
disabled by default; Production retains authenticated webhook delivery.
Do not combine this local sandbox ledger with webhook ingestion: startup rejects
both modes being enabled. Before switching an existing sandbox database to
webhooks, reconcile its historic grants because provider snapshots and webhook
period timestamps can have different precision. Production uses its separate
database and webhook source.

Verified-account credit and entitlement reads fetch the account's status directly
from RevenueCat over HTTPS. Only `test_store`, sandbox, `evidrilo_pro`, and the
approved monthly/yearly products qualify. Client success flags cannot grant
credits. Verified original purchases can be recovered when the local API was
offline. Existing ledger transactions add 200 credits for each earned monthly
anniversary, including annual subscriptions, and use unique grant keys to make
refresh/restore idempotent. Accelerated sandbox renewals retain the original
anniversary rather than adding 200 every five minutes. Expiry/revocation stops
new months while earned credits remain. Purchase accounting does not require
AI data consent; consent still gates AI spending and provider dispatch.

Grant reconciliation runs when the balance is read, including the automatic
refresh after purchase or restore. It catches up earned months without requiring
a timer inside the mobile app. This local mode does not establish hosted webhook,
worker, store-release, or Production acceptance.

From the repository root, run the canonical check:

```
bash
bash scripts/ci/verify-local.sh
```

For focused platform and disposable local-database checks, use:

```
bash
dotnet test platform/api.Tests/Evidrilo.Api.Tests.csproj
dotnet test platform/worker.Tests/Evidrilo.Worker.Tests.csproj --configuration Release
node --test contracts/contracts.test.mjs
node --test platform/database/migrations/migrations.test.mjs
bash platform/database/integration/run-local-postgres-smoke.sh
bash platform/worker/integration/run-local-worker-smoke.sh
bash platform/integration/run-local-api-worker-e2e.sh
```

The smoke scripts require the local .NET and Docker toolchains and use
disposable synthetic data. They do not connect to a managed production
database. See [platform testing](../docs/testing.md) for what each result
does and does not prove.

Run the API locally with development/degraded configuration:

```
bash
dotnet run --project platform/api/Evidrilo.Api.csproj --environment Development
```

The owner-local, Git-ignored `platform/api/appsettings.Development.json` pins
the Experiential Labs endpoint and `gpt-6-luna`, and points to an external
owner-only API-key file. Provider and Project AI activation remain false in
that file; enabling them also requires the configured database and the
separate approval gates described below.

Never put real URLs, publishable keys, tokens, passwords, service-role
credentials, webhook secrets, or production data in committed configuration,
fixtures, logs, screenshots, or documentation.

## Not implied by this code

A repository implementation or local integration test does not establish a
managed deployment, hosted authentication configuration, provider delivery,
email/SMS delivery, production backup, alerting, rollback, or load readiness.
The server-only OpenAI-compatible Responses adapter is implemented behind
`AI_PROVIDER_ENABLED` and `AI_PROVIDER_ACTIVATION_APPROVED`; both default to
false. Activation also requires a database, a server-side key, an explicitly
configured model and reviewed rates, plus per-request and monthly spend limits.
`AI_PROVIDER_BASE_URL` accepts only the OpenAI and Experiential Labs HTTPS v1
endpoints. A key may be loaded from `AI_PROVIDER_API_KEY_FILE`; on Unix the file
must be owner-readable only (for example, mode `0600`). The existing
`OPENAI_API_KEY` setting remains supported for direct-provider deployments.
The selected Staging model is `gpt-6-luna` at Experiential Labs, using provider
reasoning defaults (D-140). Leave `AI_REASONING_EFFORT` unset: the live model
metadata currently marks reasoning controls unsupported. The optional setting
is validated and serialized only when explicitly configured for a supported
route. The Staging example caps output at 1,024 tokens, provider time at 60 seconds,
request spend at USD 0.01 and shared monthly spend at USD 2. Ordinary mobile
account calls retain their short timeout; calls waiting for AI allow 75 seconds.
The outer gateway and provider-spend lease respect the configured provider time.

An early 1 October probe received HTTP 429 with the exhausted owner key.
A dedicated key later enabled live Android general chat through Render Staging.
The hosted candidate uses 2,048 output tokens, the existing request/monthly
spend limits, verified sign-in, consent and server credit accounting. Default
activation flags in source remain off. Device acceptance for every project
operation and native iOS remains separate from this general chat observation.

Project scaffold, stage-assist, and General chat adapters share that stateless Responses
transport, token accounting, and provider-spend ledger. They remain off unless
`PROJECT_AI_PROVIDER_ENABLED`, `PROJECT_AI_PROVIDER_ACTIVATION_APPROVED`, and
`PROJECT_AI_PRIVACY_APPROVED` are all explicitly true in addition to the shared
provider configuration. Each request still requires the account's current
Project AI consent. General chat also requires the separate
`PROJECT_AI_GENERAL_CHAT_POLICY_APPROVED` setting, which defaults to false.
Every General chat request must also include
`X-Evidrilo-General-Chat-Consent: general-chat.v1`; the API checks this before
credit reservation or provider dispatch. Its route sends a bounded redacted
message, optional recent conversation (eight turns, 16,000 characters), and
an explicitly selected local project snapshot. The API stores no transcript
and writes metadata-only activity. The mobile client stores account-scoped
session history locally. Sending requires verified sign-in, credits, and current
AI consent; consent is requested once rather than through a per-message checkbox.
Project replies may propose changes to known editable fields. The client previews
these changes and requires confirmation before saving an assistance-marked revision;
stale revisions, dirty editor state, and unknown fields are rejected. The snapshot
is untrusted input, not proof of server ownership, and this route does not modify
server project records. General chat
remains unavailable until its separate cost, limit, and retention policy is
approved. These settings do not prove that
provider terms, retention controls, privacy review, or hosted configuration
have been approved.

Opted-in requests are rehydrated from the published case and accepted output
must cite server-approved evidence or limitation anchors before it reaches the
client. Anchor validation establishes ID membership, not that generated prose
is semantically entailed by the cited material. The API does not independently
re-run the on-device KMP evaluator. The adapter sets Responses `store:false`,
but that does not guarantee zero retention at the gateway or upstream provider.
Review the Experiential organization capture setting and provider route before
using real student content. The owner-approved Staging runtime has live general-chat evidence. Production
privacy/retention and operational review remain separate; keep other environments
disabled until their gates are explicitly approved. See the [AI assistance contract](../docs/architecture/ai-assistance.md).

Deployment preparation and ownership boundaries are described in
[infra/README.md](../infra/README.md).
