# Render + Supabase staging handoff

API staging status: `CONFIGURED FOR FREE / NOT DEPLOYED / RENDER ACCESS AND DATABASE MIGRATION REQUIRED`.
Full API-and-worker staging status: `NOT READY`.

The landing page has a separate Cloudflare Pages setup in
[`cloudflare-pages-landing.md`](cloudflare-pages-landing.md). The default API
Blueprint is [`render.yaml`](render.yaml). The background worker is in the
separate [`render-worker-paid.yaml`](render-worker-paid.yaml) Blueprint because
Render does not offer background workers on its Free compute plan. No provider
resources or secrets are created by these repository files.
See [`supabase-environments.md`](supabase-environments.md) for the current
Supabase snapshot and the environment-switch procedure.

## Chosen staging shape and limits

The current owner choice is Render Free with manual database migrations. The
default Blueprint therefore creates only the API as a Free web service in
Singapore. Auto-deploy and preview generation are disabled.

| Component | Plan | What it supports |
| --- | --- | --- |
| API | Render Free web service | Health and bounded API staging; 512 MB RAM, low CPU, sleeps after 15 minutes without inbound traffic, and may take about a minute to wake |
| Database/Auth | Separate Supabase Free project | Synthetic, disposable staging data only; Free projects can pause after 7 days of low activity and do not include managed database backups |
| Worker | Not included in the Free Blueprint | Account-auth deletion and asynchronous projections remain queued until the optional paid worker is enabled |
| Landing | Cloudflare Pages | Independent static landing deployment; does not depend on this API |

Render's Free plan is intended for previews and hobby use. Free web services
can restart and have no persistent filesystem. Supabase Free has usage limits
and no managed backup retention. Do not put real learner data in this staging
environment. Keep any test data disposable and export it before a project is
paused or reset if it must be retained.

The optional worker uses Render's paid `0.5c-512mb` compute plan. Current
published pricing lists this plan at about US$7/month while running; confirm
the current amount and available promo credit in the owner dashboard before
creating it. The worker is not included or authorized by the Free default. It
must not be synced until the owner elects to use paid compute.

## What the Free API Blueprint configures

- ASP.NET Core API in Singapore using `infra/docker/api.Dockerfile`.
- Render Free compute and `/health/ready` as the service health check.
- `main` as its source branch; automatic deployment and preview environments
  are off. An operator manually deploys an exact verified commit.
- CORS origin derived from the API's Render HTTPS URL.
- Supabase URL, publishable key, and database URL as protected `sync: false`
  inputs; no secret values are stored in the repository.
- No RevenueCat webhook configuration and no database migration job.

The Free API can support health checks and selected API/Auth smoke flows, but
it is not complete staging for features that require background processing.
Do not report account deletion as complete or test it as a successful user flow
until the worker is running. Do not use real accounts or data.

## Blocking security and release gates

The API and worker must use separate, non-owner PostgreSQL logins with reviewed
least-privilege grants. The migration/owner login must remain separate from
both runtime logins. The current repository does not yet provision and prove
those runtime grant sets, so neither Blueprint is ready to sync.

The current worktree also contains uncommitted application and migration
changes. Do not deploy that worktree or treat it as a release candidate. First
freeze an exact commit, run the repository's GitHub `Verify` workflow for that
commit, review the migration set, and use only that SHA for staging.

## Provider setup after the candidate is frozen

### 1. Render Free API

1. Sign in to the owner's Render account and create/select the Hobby workspace.
2. Connect `AndroLay/evidrilo` through Render's GitHub authorization flow.
3. Create a Blueprint using `infra/deployment/render.yaml` and select `main`.
4. Keep auto-deploy off. Review that the API plan is `free`; do not enable a
   paid service by syncing the worker Blueprint.
5. Do not sync until Supabase staging, runtime logins, migrations, and protected
   service inputs are ready.

### 2. Supabase staging project and manual migrations

1. Select the existing `Evidrilo Staging` project in Singapore and verify its
   project reference before making changes. Do not create a duplicate project.
   Use synthetic, disposable data and keep production credentials out of
   staging.
2. Configure `evidrilo://auth/callback` only if email or OAuth callbacks are
   enabled for the staging app.
3. Put only `SUPABASE_URL`, `SUPABASE_PUBLISHABLE_KEY`, and the API's
   least-privilege `DATABASE_URL` in the Render API service settings.
4. Use the Supabase session pooler for hosted Npgsql connections when direct
   connections are not IPv4-compatible. Require TLS and an explicit username.
5. Provision and review the separate API runtime role. Never use the Supabase
   project-owner login as the API runtime credential.
6. From a trusted operator machine, apply migrations for the exact candidate
   with `platform/database/migrations/apply-migrations.sh`. Never apply raw
   migration SQL by hand or point this command at production.
7. Record the migration ledger result and the API image SHA in the private
   release record.

Keep the migration/owner credential outside Render and GitHub Actions. Do not
put credentials in a checked-in `.env` file, image, log, or screenshot.

### 3. Optional paid worker

Only after the owner chooses paid compute, create a separate Blueprint from
`infra/deployment/render-worker-paid.yaml`. Confirm its current price and the
available credit in Render Billing first. The worker must use its own reviewed
least-privilege database login and the Supabase `sb_secret_...` key. Put those
values only in the worker's protected Render settings. Never configure the
Supabase secret key on the API or mobile app. Deploy the worker from the same
migration-compatible commit as the API.

The worker processes account-auth deletion and progress projections. Verify
that it processes only synthetic staging jobs, records safe error codes, and
shuts down cleanly before considering those flows available.

### 4. RevenueCat and mobile client

RevenueCat remains separate from API health. Use the Test Store for a staging
purchase path and label all evidence as sandbox. Do not configure a webhook
until a Test Store project has the `evidrilo_pro` entitlement and exactly one
monthly and one yearly product with confirmed product IDs.

The mobile app receives only its platform public SDK key and the confirmed
product IDs through protected/local build configuration. Never put a RevenueCat
secret or server key in the app. If server-side entitlement projection is
enabled, add the complete webhook authentication value, entitlement ID, and
monthly/yearly IDs together to the API's protected settings, then configure
`/v1/billing/webhook` after HTTPS health is confirmed. Test offering load,
monthly/yearly Test Store purchase, entitlement activation, restore,
cancellation/failure, and relaunch. Do not claim production payment evidence.

AI provider variables remain disabled. Provider terms, privacy/retention
approval, product consent, cost limits, and live runtime gates are not complete.

## Controlled staging acceptance

1. Freeze an exact full commit SHA on `main` and confirm GitHub `Verify` passes
   for that SHA.
2. Review the exact migration ledger and provision the API runtime role; if the
   worker is enabled, also provision its separate runtime role.
3. Apply migrations manually to the dedicated Supabase staging project.
4. Manually sync/deploy the API with protected secrets and auto-deploy off.
5. Confirm `/health/live` and `/health/ready`, request IDs, redacted errors,
   CORS, and TLS behavior using synthetic data.
6. Do not accept worker-dependent account deletion or projection flows unless
   the paid worker is deployed from the same SHA and passes its smoke checks.
7. Exercise the RevenueCat Test Store only if its separate project/products and
   protected client configuration are ready.

Render Free web services can roll back only to recent previous deploys; schema
migrations are forward-only. Roll back application images only when the
already-applied schema is compatible. Render rollback is not a Supabase
backup/restore. Supabase Free does not provide managed backups, so this staging
must contain no data that cannot be discarded or manually exported.

## Observability and present status

Use Render service logs and health checks for the limited API smoke. No alert
destination, managed backup/restore rehearsal, or production monitoring provider
is configured. Those remain explicit operational gates before any production
use.

The initial credentialless snapshot in this document predates the current
Supabase connection. As of 2026-09-29, the dedicated Staging project exists and
its `evidrilo://auth/callback` redirect allowlist was updated and verified.
Ignored local Android and iOS Debug settings now point to that project's URL
and publishable key; no secret key was placed in the mobile configuration.
The database migration ledger remains unknown because its read-only Management
API query failed, and no migration was applied. Production remains unchanged.
The temporary agentctl access lease is Off. Render is not deployed. The
Cloudflare landing deployment record is historical; current public reachability
and a Git-triggered redeploy were not verified from this environment.

The Render Blueprints and current application/migration edits are still in the
local worktree. `infra/deployment/render.yaml` is untracked and is not available
to a Git-connected Render Blueprint on `main` yet. The `Verify` workflow exists,
but no run for a frozen candidate SHA has been confirmed. A live Render
workspace, repository connection, or API service was not verified. Migrations
`051` and `052` are not part of a frozen candidate. Staging email delivery and
the mobile sign-up/recovery journeys have not been exercised. These are pending
activation gates, not completed setup evidence.

## Official references

- [Render Free services and limitations](https://render.com/docs/free)
- [Render background workers](https://render.com/docs/background-workers)
- [Render compute plans and pricing](https://render.com/docs/compute-plans)
- [Render Blueprints](https://render.com/docs/blueprint-spec)
- [Supabase project pausing](https://supabase.com/docs/guides/platform/free-project-pausing)
- [Supabase backups](https://supabase.com/docs/guides/platform/backups)
- [Supabase database connections](https://supabase.com/docs/guides/database/connecting-to-postgres)
