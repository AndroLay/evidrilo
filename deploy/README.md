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
docker compose -f deploy/docker-compose.local.yml up --build
curl --fail http://127.0.0.1:5080/health/live
```

Stop the stack when finished:

```bash
docker compose -f deploy/docker-compose.local.yml down
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
EVIDRILO_API_PORT=15080 docker compose -f deploy/docker-compose.local.yml up --build
curl --fail http://127.0.0.1:15080/health/live
```

## Image boundaries

- `deploy/docker/api.Dockerfile` builds only `platform/api` and runs the
  published API as the non-root image user.
- `deploy/docker/worker.Dockerfile` builds only `platform/worker` and runs the
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
| API | `ASPNETCORE_ENVIRONMENT=Production` | startup rejects missing Supabase/database/CORS-origin configuration |
| API | `SUPABASE_URL`, `SUPABASE_PUBLISHABLE_KEY` | HTTPS Supabase Auth/JWKS validation |
| API and worker | `DATABASE_URL` or `SUPABASE_DB_CONNECTION_STRING` | managed PostgreSQL connection with least privilege |
| API | `CORS_ALLOWED_ORIGINS` | required explicit origins; wildcard and missing values are rejected in production |
| API | `TRUSTED_PROXY_ADDRESSES` | explicit proxy IPs only when forwarded headers are required |
| API | `REVENUECAT_WEBHOOK_SECRET` or `REVENUECAT_WEBHOOK_AUTHORIZATION` | server-only billing delivery authentication |
| API | `REVENUECAT_ENTITLEMENT_ID` | expected premium entitlement, currently `evidrilo_pro` |
| Worker | `WORKER_POLL_INTERVAL_SECONDS`, `WORKER_BATCH_SIZE` | bounded polling and batch size |

Do not put those values in a Dockerfile, Compose file, image layer, source
fixture, log, screenshot, or public repository. The mobile publishable key is
configured separately through ignored local platform settings.

## Safe rollout sequence

1. Build and scan the exact candidate with
   `bash scripts/check-deployment.sh .`,
   `bash scripts/check-public-package.sh <public-candidate>`, and (from a real
   Git checkout) `bash scripts/check-github-safety.sh .`.
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
  and projection lag.
- A multi-instance deployment must replace the in-memory rate limiter with a
  distributed limiter before claiming horizontal-scale behavior.

The repository contains local tests for these boundaries, but managed
permissions, TLS/proxy behavior, real backups, alert delivery, capacity, and
crash recovery remain environment-specific verification gates.
