# Render + Supabase staging handoff

API staging status: `BLUEPRINT AND LOCAL GATES VERIFIED / NOT DEPLOYED / CANDIDATE AND DATABASE ROLE GATES REMAIN`.
Full API-and-worker staging status: `NOT READY — no Render services, frozen candidate, runtime database roles, or applied migrations`.

The landing page has a separate Cloudflare Pages setup in
[`cloudflare-pages-landing.md`](cloudflare-pages-landing.md). The default API
Blueprint is [`render.yaml`](render.yaml). The background worker is in the
separate [`render-worker-paid.yaml`](render-worker-paid.yaml) Blueprint because
Render does not offer background workers on its Free compute plan. No provider
resources or secrets are created by these repository files.
See [`supabase-environments.md`](supabase-environments.md) for the current
Supabase snapshot and the environment-switch procedure.

## Chosen staging shape and limits

The approved staging shape is a Free API plus one paid `0.5c-512mb` background
worker in Singapore, with manual database migrations. The worker uses the
owner's Render promotional credit. Auto-deploy and preview generation are
disabled. Neither service has been created yet.

| Component | Plan | What it supports |
| --- | --- | --- |
| API | Render Free web service | Health and bounded API staging; 512 MB RAM, low CPU, sleeps after 15 minutes without inbound traffic, and may take about a minute to wake |
| Database/Auth | Separate Supabase Free project | Synthetic, disposable staging data only; Free projects can pause after 7 days of low activity and do not include managed database backups |
| Worker | Paid `0.5c-512mb` | Approved for staging; processes account-auth deletion and asynchronous projections. It is not yet created. |
| Landing | Cloudflare Pages | Independent static landing deployment; does not depend on this API |

Render's Free plan is intended for previews and hobby use. Free web services
can restart and have no persistent filesystem. Supabase Free has usage limits
and no managed backup retention. Do not put real learner data in this staging
environment. Keep any test data disposable and export it before a project is
paused or reset if it must be retained.

The worker uses Render's paid `0.5c-512mb` plan. The owner approved using the
available US$50 promotional credit for it. The credit is applied to invoices,
so it is not a hard spend cap; confirm the current balance and rate before
activation. Keep the worker off until its database role and the frozen
migration set are ready.

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

### 3. Paid worker (approved, not yet created)

After the migration set and worker database role are ready, create a separate
Blueprint from `infra/deployment/render-worker-paid.yaml`. Confirm its current
price and available credit in Render Billing first. The worker must use its
own reviewed least-privilege database login and the Supabase `sb_secret_...`
key. Put those values only in the worker's protected Render settings. Never
configure the Supabase secret key on the API or mobile app. Deploy the worker
from the same migration-compatible commit as the API.

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
6. Do not accept worker-dependent account deletion or projection flows until
   the approved paid worker is deployed from the same SHA and passes its smoke
   checks.
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

### Latest live preflight before candidate freeze (2026-10-01)

- A read-only Supabase Staging check confirmed `Evidrilo Staging`
  (`cdgzbrrrvlrrgpzbgqog`) remains `ACTIVE_HEALTHY`. The public schema has zero
  tables and no `public.evidrilo_schema_migrations` ledger, so no repository
  migrations have been applied. The last observed Auth user count remains the
  2026-09-30 value of zero.
- The Render service inventory is empty. No API or worker service, deployment,
  or Render-side secret configuration exists.
- Local `main` and `origin/main` still point to
  `85582b8a920106870ef10d7a59669f5f8b9a5238`, while extensive application and
  migration changes remain uncommitted, including `053` and `054`. There is no
  frozen candidate commit.
- On 2026-10-01, `gh auth status` and a read-only GitHub API check succeeded
  for `AndroLay` with the `repo` and `workflow` scopes. The current worktree
  still has no frozen candidate SHA, so no candidate has been pushed and no
  GitHub `Verify` result exists for these local changes.
- GitHub's latest checks for the existing `main` SHA
  `85582b8a920106870ef10d7a59669f5f8b9a5238` are not green: contract/migration,
  public-package boundary, mobile compile/JVM, and iOS simulator checks failed;
  ASP.NET/worker checks passed. The CI logs show the Android job calls a Gradle
  task that does not exist and the public-package job lacks `rg`, which the
  repository checks require. The local candidate now uses the supported
  Android source-set tasks and installs `ripgrep` in the affected jobs. These
  fixes have not yet run on GitHub.
- A full `scripts/ci/verify-local.sh` run passed earlier on 2026-10-01:
  Kotlin/JVM and Android tests/build, 168 Node tests, 453 API tests, 30 worker
  tests, Docker Compose, architecture, audio assets, and deployment checks
  passed. A fresh rerun could not complete in this restricted Linux workspace:
  the default Gradle cache is read-only, and a new temporary cache exceeded its
  disk quota before the Kotlin build. The iOS simulator requires macOS/Xcode.
- After the CI fixes, the focused workflow checks passed (10 public-package and
  16 mobile-platform tests); the deterministic 12-file Node check, exported
  public-package scan, deployment check, GitHub safety scan in a temporary
  staged public checkout, and `git diff --check` passed. This still is not a hosted GitHub
  `Verify` result for a frozen candidate.
- A fail-fast API configuration check now rejects
  `LOCAL_DEVELOPER_ACCESS_ENABLED=true` in Staging or Production; the focused
  regression and full API suite pass.
- API and worker database logins still lack reviewed least-privilege grants.
  No migrations were applied and no Render service was created because the
  migration set and runtime-role grants must be tied to the frozen candidate.
  The temporary `agentctl` Full lease is off. Production was not changed.

The initial credentialless snapshot in this document predates the current
Supabase connection. As of 2026-09-29, the dedicated Staging project exists and
its `evidrilo://auth/callback` redirect allowlist was updated and verified.
Ignored local Android and iOS Debug settings now point to that project's URL
and publishable key; no secret key was placed in the mobile configuration.
The database migration ledger could not be read in that earlier session, and
no migration was applied then. Production remained unchanged.
An earlier read-only agentctl check on 2026-09-30 reported provider
connectivity. A later check on the same date found agentctl in `Partial` mode
with no configured credentials for GitHub, Supabase, Render, RevenueCat, or
Google, and no API reachability for Supabase, Render, or RevenueCat. Treat the
earlier check as a record of that point in the session; it is superseded by the
current live check below. At that earlier point, no provider changes could be
made and the migration ledger was unknown.
The Cloudflare landing deployment record is historical; current public
reachability and a Git-triggered redeploy were not verified from this
environment.

As of 2026-09-30, local `main` matched `origin/main` and both Render Blueprint
files were tracked. This does not prove that a live Render workspace,
repository connection, or API service exists; none was verified. The worktree
has active uncommitted application and migration work. Migrations `053` and
`054` are untracked, and migrations `051` through `054` have not been frozen
into a release candidate. The `Verify` workflow exists,
but no run for a frozen candidate SHA has been confirmed. Staging email
delivery and the mobile sign-up/recovery journeys have not been exercised.
These are pending activation gates, not completed setup evidence.
On 2026-09-30, the owner-provided Google OAuth client pair was applied to the
Staging Supabase Auth provider and Google sign-in was enabled. A redacted
read-back confirmed that the configured Client ID matches the private
credential file and that `evidrilo://auth/callback` remains on the Supabase
redirect allowlist. The Google Cloud client's Authorized redirect URIs and
test-user list were not independently verified, and no end-to-end mobile
sign-in was run. Keep OAuth acceptance pending until the Supabase callback is
confirmed in Google Cloud and a Debug sign-in succeeds with an authorized test
user.

### Latest live Staging check (2026-09-30)

- The owner-provided Supabase and Render API credentials are available to
  `agentctl` through the local OS keyring. The temporary `agentctl` access
  lease is turned off after each operation.
- The dedicated Supabase Staging project is `ACTIVE_HEALTHY` in
  `ap-southeast-1`. It has zero Auth users and zero public tables. The
  `public.evidrilo_schema_migrations` ledger does not exist, and no repository
  migrations have been applied. A new Staging-only database-owner password is
  stored in the local OS keyring; its TLS session-pooler connection was
  verified. It is not present in Render, GitHub, or the repository.
- The Render owner workspace is reachable, but its service list is empty. No
  API or worker service, Git repository connection, or deployment exists yet.
- Local `main` and `origin/main` are both at
  `85582b8a920106870ef10d7a59669f5f8b9a5238`. GitHub `Verify` failed for this
  SHA in contract/migration checks, public-package boundary, and mobile
  compile/JVM checks; the ASP.NET/worker job passed.
- The worktree contains extensive uncommitted application changes and
  untracked migrations `053` and `054`. Migration `054` was amended locally to
  install its account-deletion cleanup trigger, but this change is not frozen
  or verified. API/worker least-privilege database grants also remain
  unproven.

Do not apply migrations or create Render services until the application and
migration changes are frozen on a commit whose `Verify` workflow passes, and
separate API and worker database roles have reviewed grants. Production remains
untouched.

## Official references

- [Render Free services and limitations](https://render.com/docs/free)
- [Render background workers](https://render.com/docs/background-workers)
- [Render compute plans and pricing](https://render.com/docs/compute-plans)
- [Render Blueprints](https://render.com/docs/blueprint-spec)
- [Supabase project pausing](https://supabase.com/docs/guides/platform/free-project-pausing)
- [Supabase backups](https://supabase.com/docs/guides/platform/backups)
- [Supabase database connections](https://supabase.com/docs/guides/database/connecting-to-postgres)
