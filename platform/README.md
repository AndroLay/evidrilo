# Evidrilo Platform

The platform is an optional online foundation for account-backed and
server-owned capabilities. The bundled learning workflow remains local-first
and must work without the API, database, or worker.

## Components

| Component | Purpose |
| --- | --- |
| `Evidrilo.Api` | ASP.NET Core/.NET 10 API with health, authenticated account, sync, progress, case/content, recommendation, AI-assistance, and billing boundaries |
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
- The planned AI credit ledger is server-owned: 10 one-time credits for a
  verified free account and 100 credits per active `evidrilo_pro` entitlement
  month. Reservations are released on failed assists, and the client cannot
  grant or mutate credits.
- Missing configuration produces an explicit unavailable/degraded result,
  never a fabricated successful write or entitlement.

## Local verification

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

Never put real URLs, publishable keys, tokens, passwords, service-role
credentials, webhook secrets, or production data in committed configuration,
fixtures, logs, screenshots, or documentation.

## Not implied by this code

A repository implementation or local integration test does not establish a
managed deployment, hosted authentication configuration, provider delivery,
email/SMS delivery, production backup, alerting, rollback, or load readiness.
The current AI gateway remains provider-disabled with a provisional in-memory
quota; it does not establish a credit ledger, live provider, privacy approval,
or AI runtime evidence.
Those require a separately approved environment and evidence.

Deployment preparation and ownership boundaries are described in
[infra/README.md](../infra/README.md).
