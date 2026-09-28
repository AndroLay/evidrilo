# Evidrilo worker

The worker is a separate .NET process boundary for projection and reconciliation
jobs. It does not run unbounded work in API request handlers. With a configured
database it claims account-scoped `analytics_projection` jobs using a bounded
lease, rebuilds account-level and daily `progress_*_projections` from
append-only events, and records success/failure with a maximum of ten attempts.
When explicitly enabled, it also processes durable Supabase Auth deletion jobs.
Without a database it remains a safe empty worker and never claims success.

## Account/Auth deletion outbox

Account deletion commits the platform tombstone, removes the account-owned
server data under the existing deletion rules, and enqueues Auth identity
deletion in the same transaction. The worker claims outbox jobs with
`FOR UPDATE SKIP LOCKED`, a 60-second lease and a fresh fencing token. Transient
provider failures retry at most eight times with backoff capped at 15 minutes;
terminal or exhausted jobs become `dead_letter`. A stale worker cannot complete
or fail a newer lease. Once provider deletion is confirmed, the outbox row drops
the account UUID; the separate durable tombstone remains so still-valid signed
access tokens continue to be rejected.

Local and test environments keep provider deletion disabled unless explicitly
enabled. No provider request is made by the PostgreSQL smoke tests. In staging
and production the worker fails startup unless the database, HTTPS
`SUPABASE_URL`, `SUPABASE_AUTH_ADMIN_ENABLED=true`, and a worker-only
`SUPABASE_SECRET_KEY` are configured. A legacy `SUPABASE_SERVICE_ROLE_KEY` is
accepted temporarily for migration compatibility; a new `sb_secret_...` key is
sent only in the `apikey` header. Do not expose either key to the API or mobile
app.

Inspect backlog without selecting account identifiers:

```sql
select status, attempts, last_error_code, count(*)
from public.account_auth_deletion_outbox
group by status, attempts, last_error_code
order by status, attempts;
```

For an approved operator replay after repairing provider configuration, update
only the selected `dead_letter` row by its opaque outbox UUID inside a transaction:

```sql
begin;
update public.account_auth_deletion_outbox
set status = 'queued', attempts = 0, available_at = now(),
    lease_token = null, leased_until = null, completed_at = null,
    last_error_code = null, updated_at = now()
where outbox_id = :'outbox_id'::uuid and status = 'dead_letter';
-- Commit only if exactly one row was updated; otherwise roll back.
commit;
```

The migration deliberately retains a minimal account tombstone and immutable
project-cloud-consent decision history. Their retention period is not invented
by application code and must be set by the privacy/legal owner before managed
release; a tombstone must not expire while an issuer-valid access token could
still be accepted.

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
its temporary container. This is local integration evidence. Managed Supabase grants,
pooling, deployment, recovery, and load behavior remain external gates.

The reproducible local API/worker container preparation is documented in
[`../../infra/README.md`](../../infra/README.md). It starts the worker only
after the checksum-guarded migration service completes. The Compose database
and trust authentication are local-only and must not be reused for a managed
deployment.

## Bounded performance contract

The API rejects raw request bodies above 4 MiB; the billing webhook applies a
128 KiB route limit; sync push/pull operations are capped at 100 commands or
changes per request; and the worker accepts a configured batch of 1–100 jobs.
Each claimed job has a 60-second lease and at most ten attempts, with retry
backoff capped at five minutes. API and billing rate limits are in-memory
single-instance defaults (60 and 120 requests per minute respectively); a
multi-instance deployment must replace them with a distributed limiter before
making a scale claim. These are explicit bounds, not a throughput benchmark.
