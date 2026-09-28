# Evidrilo deployment preparation

Status: `PREPARED / NOT_DEPLOYED / OWNER_INFRASTRUCTURE_REQUIRED`

This directory contains a reproducible container boundary for the optional
ASP.NET Core API and projection worker. It is preparation only: no image has
been published, no managed database has been changed, and no production
secret is stored here.

## Local stack

The local Compose file starts four bounded services:

```text
postgres (healthcheck)
    |
    +--> migrations (checksum ledger, one shot)
              |
              +--> api (ASP.NET Core, port 5080)
              +--> worker (projection process)
```

Run it from the repository root:

```bash
docker compose -f infra/environments/local/docker-compose.yml up --build
curl --fail http://127.0.0.1:5080/health/live
```

Stop the stack when finished:

```bash
docker compose -f infra/environments/local/docker-compose.yml down
```

## Local backup/restore rehearsal

The repository also includes a disposable, local-only PostgreSQL dump/restore
smoke. It applies the current migration ledger, writes synthetic sentinel data,
creates a custom-format dump, restores it into a fresh database, and verifies
the sentinel plus the migration ledger:

```bash
EVIDRILO_RUN_DOCKER_SMOKE=1 \
  node platform/database/integration/backup-restore.test.mjs
```

The PostgreSQL image must already exist locally; set
`EVIDRILO_POSTGRES_IMAGE` to use another preloaded image. This rehearsal does
not prove managed backup retention, encryption, IAM, point-in-time recovery,
disaster recovery, staging, or production rollback. Those remain owner-run
gates in the supplied environment.

The local database intentionally uses PostgreSQL's trust mode inside the
isolated Compose network. It is suitable only for local development and must
not be reused for staging or production.

If host port 5080 is already occupied, choose another host port without
changing the API's internal port:

```bash
EVIDRILO_API_PORT=15080 docker compose -f infra/environments/local/docker-compose.yml up --build
curl --fail http://127.0.0.1:15080/health/live
```

When the Supabase project is already connected through `agentctl`, use the
repository bootstrap instead of copying credentials into a local `.env` file:

```bash
EVIDRILO_API_PORT=15080 \
  scripts/bootstrap/local-api-with-agentctl.sh
```

The bootstrap resolves only the selected Supabase project and its mobile-safe
client key in memory, passes them to Docker Compose for the current process,
and performs a readiness check. `agentctl` redacts `api_key` fields in its
human-readable JSON output, so the bootstrap reuses the same agentctl-managed
Secret Service credential for one official Management API request when that
redaction is encountered. It does not write credentials to the repository or
to a dotenv file. The local API port is bound to loopback only.

To build the Android debug client with the same project and local API URL:

```bash
EVIDRILO_API_PORT=15080 \
  scripts/bootstrap/build-android-local-with-agentctl.sh
```

Set `EVIDRILO_INSTALL_ANDROID=1` only when an authorized local emulator/device
is already running. These scripts are for local development; production and
staging still require the platform secret store and HTTPS configuration.

## Image boundaries

- `infra/docker/api.Dockerfile` builds only `platform/api` and runs the
  published API as the non-root image user.
- `infra/docker/worker.Dockerfile` builds only `platform/worker` and runs the
  published worker as the non-root image user.
- `.dockerignore` excludes local properties, credentials, private research,
  audit records, media, generated output, and machine caches from the build
  context.
- Both images use versioned .NET 10 base-image tags. A floating `latest` tag is
  rejected by the deployment checker.

## Staging and production handoff

The target operator must provide these values through the platform's secret
store or protected environment configuration:

| Component | Required configuration | Boundary |
| --- | --- | --- |
| API | `ASPNETCORE_ENVIRONMENT=Staging` or `Production` | startup rejects missing Supabase/database/HTTPS CORS-origin configuration |
| API | `SUPABASE_URL`, `SUPABASE_PUBLISHABLE_KEY` | HTTPS Supabase Auth/JWKS validation |
| API and worker | `DATABASE_URL` or `SUPABASE_DB_CONNECTION_STRING` | PostgreSQL URI (`postgresql://...`) or Npgsql `key=value;...` connection string with least-privilege credentials; staging/production require an explicit username and encrypted transport |
| API | `CORS_ALLOWED_ORIGINS` | required explicit origins; wildcard and missing values are rejected in production |
| API | `TRUSTED_PROXY_ADDRESSES` | explicit proxy IPs only when forwarded headers are required |
| API | `REVENUECAT_WEBHOOK_SECRET` or `REVENUECAT_WEBHOOK_AUTHORIZATION` | server-only billing delivery authentication; supply the full RevenueCat configuration together when billing is enabled |
| API | `REVENUECAT_ENTITLEMENT_ID` | expected premium entitlement, currently `evidrilo_pro`; partial configuration fails startup in staging/production |
| API | `REVENUECAT_MONTHLY_PRODUCT_ID`, `REVENUECAT_YEARLY_PRODUCT_ID` | configured RevenueCat product IDs mapped to the internal monthly/yearly aliases; never use `lifetime`; omit every RevenueCat setting only when billing is intentionally disabled |
| Worker | `WORKER_POLL_INTERVAL_SECONDS`, `WORKER_BATCH_SIZE` | bounded polling and batch size |
| Worker | `SUPABASE_AUTH_ADMIN_ENABLED=true`, `SUPABASE_URL`, `SUPABASE_SECRET_KEY` | worker-only Auth Admin deletion; HTTPS origin and secret key are required before the production worker starts |

Do not put those values in a Dockerfile, Compose file, image layer, source
fixture, log, screenshot, or public repository. The mobile publishable key is
configured separately through ignored local platform settings. Prefer the new
Supabase `sb_secret_...` key and send it only in `apikey`; the worker accepts
legacy `SUPABASE_SERVICE_ROLE_KEY` during migration, but that key is deprecated.
The Admin key must never be configured in the API or mobile app.

The API and worker normalize both PostgreSQL URI and Npgsql key/value formats.
For staging/production, a URI without `sslmode` defaults to `require`; an
explicit plaintext-capable mode (`disable`, `allow`, or `prefer`) is rejected.
Npgsql-format strings must specify `SSL Mode=Require`, `VerifyCA`, or
`VerifyFull`. Prefer `verify-full` with a trusted root certificate when the
database provider supplies one, so encryption also validates server identity.
Development keeps local PostgreSQL compatible with its Compose network.

## Safe rollout sequence

1. Build and scan the exact candidate with
   `bash scripts/verification/check-deployment.sh .`,
   `bash scripts/verification/check-public-package.sh <public-candidate>`, and (from a real
   Git checkout) `bash scripts/security/check-github-safety.sh .`.
2. Provision a disposable staging database and apply migrations only through
   `platform/database/migrations/apply-migrations.sh`; never replay raw SQL by
   hand.
3. Verify `/health/live` and `/health/ready`, request IDs, redacted errors,
   rate limits, webhook acknowledgement latency, and worker graceful shutdown.
4. Run the API and worker integration smoke checks against staging fixtures,
   then perform an authorized backup and restore rehearsal.
5. Deploy the API and worker independently with the same migration-compatible
   image revision. Keep the previous image available for rollback.
6. Configure the RevenueCat webhook only after the HTTPS endpoint is reachable
   and has a fast authenticated 2xx response.

## Rollback and operations

- Application rollback is an image roll-back only when the database schema is
  backward compatible with the previous application revision.
- For a forward-only migration, roll forward the application and keep the
  migration ledger as the authority; do not delete applied migration rows.
- Stop billing delivery before changing webhook authentication material, then
  replay only provider event IDs that were not acknowledged.
- Monitor API 5xx/429 rates, readiness degradation, webhook rejection and
  latency, database pool exhaustion, worker lease failures, retry exhaustion,
  dead-letter count, and projection lag. The worker log records only safe error
  codes and attempt counts, not account identifiers or provider response bodies.
- Before approving an account-deletion dead-letter replay, repair the provider
  secret/configuration, identify the row by opaque outbox ID, verify exactly one
  row was requeued, and confirm it reaches `completed`. Completed outbox rows
  scrub the account UUID; the minimal access tombstone and consent decision
  audit remain subject to a separately approved retention policy.
- A multi-instance deployment must replace the in-memory rate limiter with a
  distributed limiter before claiming horizontal-scale behavior.

The repository contains local tests for these boundaries, but managed
permissions, TLS/proxy behavior, real backups, alert delivery, capacity, and
crash recovery remain environment-specific verification gates.
