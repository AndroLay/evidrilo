# Evidrilo worker

The worker is a separate .NET process boundary for projection and reconciliation
jobs. It does not run unbounded work in API request handlers. With a configured
database it claims account-scoped `analytics_projection` jobs using a bounded
lease, rebuilds account-level and daily `progress_*_projections` from
append-only events, and records success/failure with a maximum of ten attempts.
Without a database it remains a safe empty worker and never claims success.

Before enabling it in staging, verify job idempotency, lease expiry and fencing,
retry limits, structured redacted logs, migration state, projection replay
parity with the API calculation, and graceful shutdown.

The local process boundary is covered by:

```bash
bash platform/worker/integration/run-local-worker-smoke.sh
```

The harness uses a fresh synthetic PostgreSQL 16 container, applies migrations
through the checksum ledger, seeds an expired projection lease, runs the
Release worker, asserts account/daily projection rebuilds, and removes only
its temporary container. E125 records the latest local verification, including
the non-root container image, migration-ordered Compose preparation, the typed
analytics funnel boundary, and the server-owned recommendation write boundary.
Managed Supabase grants,
pooling, deployment, recovery, and load behavior remain external gates.

The reproducible local API/worker container preparation is documented in
[`../../deploy/README.md`](../../deploy/README.md). It starts the worker only
after the checksum-guarded migration service completes. The Compose database
and trust authentication are local-only and must not be reused for a managed
deployment.

## Bounded performance contract

The API rejects raw request bodies above 1 MiB; the billing webhook applies a
128 KiB route limit; sync push/pull operations are capped at 100 commands or
changes per request; and the worker accepts a configured batch of 1–100 jobs.
Each claimed job has a 60-second lease and at most ten attempts, with retry
backoff capped at five minutes. API and billing rate limits are in-memory
single-instance defaults (60 and 120 requests per minute respectively); a
multi-instance deployment must replace them with a distributed limiter before
making a scale claim. These are explicit bounds, not a throughput benchmark.
