# Render + Supabase staging handoff

API staging status: `LIVE — hosted Staging on 5a40b748; final readiness 200`.
Full API-and-worker staging status: `READY WITH RISKS for Android judging / Test Store; iOS and Production release gates remain`.

Current snapshot: [acceptance status](../../docs/development/current-status.md).
Dated checks below are historical when superseded by the final hosted snapshot.
Local API/worker/PostgreSQL are stopped. API pooling is disabled to avoid retained
idle connections across independent stores; verified TLS and role grants remain.

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
both runtime logins. The candidate supplies the reviewed role plan and disposable grant checks in
`platform/database/roles/`. Hosted runtime credentials and grants have not yet
been provisioned; neither Blueprint is ready to sync.

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

### Latest live Staging and CI check (2026-10-01; E289)

- GitHub `main` is `aa3c6079133db9c9227d8c405aac148104d7be8f`. Its hosted
  [`Verify` workflow passed](https://github.com/AndroLay/evidrilo/actions/runs/36765705221).
  The separate
  [iOS Simulator workflow failed](https://github.com/AndroLay/evidrilo/actions/runs/36765705356)
  in `testGuestCanOpenManualProjectBasicsFromHome` with three UI assertions.
  Correction after inspecting the fetched tree: that SHA already includes
  migrations `053` and `054`; it does not include the latest owner-local UI
  and integration overlay.
- A read-only Supabase check confirmed `Evidrilo Staging`
  (`cdgzbrrrvlrrgpzbgqog`) is `ACTIVE_HEALTHY`; the public schema has no tables
  and the migration list is empty. No repository migration has been applied.
  The last observed Auth user count remains the 2026-09-30 value of zero.
- Render's connected workspace is reachable, but its service inventory is
  empty. No API or worker service or service-level environment configuration
  exists; workspace-level groups were not inspected. The connected Render
  tools do not expose Billing, so the promotional-credit balance and current
  worker price were not refreshed.
- RevenueCat `Evidrilo` has one Test Store app, active monthly/yearly products,
  the active `evidrilo_pro` entitlement, and one current offering with both
  packages attached. Test Store state reports USD 1.99/month and USD 19.99/year.
  No webhook is configured and no SDK purchase/restore was exercised.
- The previous Supabase-side Google client configuration and deep-link
  allowlist are recorded above. Google Cloud's callback/test-user list and an
  Android sign-in remain unverified; this session has no Google Cloud
  integration or `gcloud` CLI.
- A full `scripts/ci/verify-local.sh` run passed earlier on 2026-10-01:
  Kotlin/JVM and Android tests/build, 168 Node tests, 453 API tests, 30 worker
  tests, Docker Compose, architecture, audio assets, and deployment checks
  passed. A fresh rerun could not complete in this restricted Linux workspace:
  the default Gradle cache is read-only, and a new temporary cache exceeded its
  disk quota before the Kotlin build. The iOS simulator requires macOS/Xcode.
- The focused workflow checks passed (10 public-package and 16 mobile-platform
  tests); the deterministic 12-file Node check, exported public-package scan,
  deployment check, GitHub safety scan in a temporary staged public checkout,
  and `git diff --check` passed. A fresh full local rerun is limited by this
  workspace's read-only Gradle cache and temporary disk quota.
- A fail-fast API configuration check now rejects
  `LOCAL_DEVELOPER_ACCESS_ENABLED=true` in Staging or Production; the focused
  regression and full API suite pass.
- API and worker database logins still lack reviewed least-privilege grants.
  No migrations were applied and no Render service was created because the
  current owner-local migration set and runtime-role grants must be tied to a
  frozen candidate. The temporary `agentctl` Full lease is off. Production was
  not changed.

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

### Alignment candidate refresh (2026-10-01)

An isolated candidate is based on `aa3c6079133db9c9227d8c405aac148104d7be8f`.
The latest owner-local UI overlay was reconciled, preserving main's portable
Kotlin map sorting fix. Older statements above about untracked migrations or
local checks describe their dated observation; main now includes 053 and 054.
No push to main, Cloudflare Production publication, managed migration, runtime
credential change or Render service creation was performed in this refresh.

Fresh read-only inventory still shows healthy Supabase Staging with no public
tables/migration records and an empty Render service inventory. RevenueCat's
Test Store current offering contains `monthly` and `yearly` products. Its public
SDK key and Staging's enabled modern publishable key are confined to ignored
candidate build inputs. Google and Apple remain disabled until provider
callback acceptance. The Android manual Staging workflow requires complete
public environment inputs and an HTTPS API URL; those GitHub inputs and hosted
API URL remain to be provisioned. iOS simulator acceptance requires macOS CI.

The candidate role plan starts NOLOGIN, requires schema 055, and assigns API and
worker separate table/operation allowlists. Disposable tests prove exact
effective privileges, repeatable provisioning, server RLS reads/writes and
rejected cross-role operations. The runtime API/worker E2E gate must also pass
before an operator activates hosted logins. Keep the migration operator out of
both services. For Npgsql connection URLs use the named runtime username and TLS.

Luna 6 uses provider-default reasoning, as explicitly chosen by the owner.
The synthetic Evidrilo adapter probe was rejected with HTTP 429; do not report
AI delivery as accepted or publish Pro AI credits as a proven usable service.
The request/monthly spend limits remain USD 0.01/USD 2. Stage activation still
requires privacy consent, availability, verified account and credit acceptance.

### Hosted configuration acceptance refresh (2026-10-01)

Supabase Staging now has 55 checksum-matched repository migrations through
`055_runtime_account_lock` and 45 public tables. Separately approved API and
worker runtime logins are active with distinct table-operation grants. All 630
effective public-table permission checks match the manifest; both logins pass
a session-pooler TLS `verify-full` connection check, with no Auth table read or
public-schema CREATE privilege. SSL enforcement is enabled and confirmed.
A temporary local API process connected using the Staging API role and returned
HTTP 200 from `/health/live` and `/health/ready`; its readiness dependencies were
ready. This is cloud-database acceptance, not a hosted Render deployment.

Render service creation was refused with HTTP 402. The workspace dashboard
confirms no card on file and USD 50 promotional credit, valid until 2027-08-01.
The owner requested preserving this blocker and next steps. No API or paid
worker exists; payment setup must be completed before another creation attempt.

Google OAuth preflight returned `redirect_uri_mismatch`. The existing Google
Cloud Evidrilo Web client matches Staging's configured client and contains only
the Production callback. The Staging callback is being added while preserving
the Production URI; save/readback and authorized-account sign-in are still open.

The original AI key returned `insufficient_quota`. With the owner's dedicated
Evidrilo key, the real Luna 6 adapter now returns a valid synthetic structured
response with provider-default effort and measured cost USD 0.000048. This does
not prove mobile account/credit acceptance or hosted AI activation. Existing
request/monthly limits remain USD 0.01/USD 2; no credentials belong in Git.

Main `676b4c9` passes hosted Verify (run 36839418558). The prior main `ef5dbac`
passed the unsigned native iOS Release build and Simulator launch, but the guest
UI test failed on the static-text-only heading query. The exact heading query
now checks all accessibility element types and preserves the Project-name
assertion; native CI run 36839418513 is still running. Signed-device/App Store
and RevenueCat purchase/restore acceptance remain unproved.

### 2026-10-01: local Android account and AI acceptance

Google sign-in and session restoration were exercised in the native Android
emulator against Staging. Enabling the migrated custom access-token hook and
obtaining a fresh session resolved the API's verified-email rejection; credits,
consent, and entitlement endpoints returned 200. Local API general chat returned
200 with a live Luna 6 answer and the displayed balance changed from 20 to 19.
The client now sends bounded conversation history and optionally selected local
project notes. A synthetic project provider probe returned a valid reviewed-field
edit without inventing measurements (measured cost USD 0.000264). Changes require
client confirmation and a matching saved revision; existing owner projects were
not edited during verification. Chats remain local and account-scoped.

The API is running locally on port 15080, with the native emulator's reverse
mapping. Limits remain USD 0.01/request and USD 2/month. This is local acceptance,
not evidence of a hosted API, deployed worker, production readiness, or completed
RevenueCat purchases. Render billing remains blocked.

### 2026-10-01: RevenueCat Test Store purchase and credit acceptance

Native Android Debug used the official Test Store SDK and current offering.
Monthly checkout cancellation, failed purchase, retry, successful purchase,
restore, and relaunch were exercised. RevenueCat recorded initial purchase,
renewals, and expiration in sandbox. Annually also completed a Test Store
purchase; Pro remained active after relaunch/restore and Customer Center showed
the active subscription. No real store purchase or payment was attempted.

The local Staging API now has explicitly enabled server-to-RevenueCat sandbox
reconciliation. It uses provider-owned periods and the existing transactional
credit ledger, not a client entitlement flag. The first verified grant changed
the actual chat balance from 18 to 218; repeat refresh kept 218. Following the
separate Annually test purchase, the account has two period grants totalling 400,
plus its original 20-credit grant with 2 consumed. Restore did not mint another
grant. Monthly-anniversary fixtures cover annual accrual, renewal, month ends,
duplicate history, and expiry/revocation. Focused checks: 52 client tests and
44 API tests, all passing; Android Release rejects the Test Store key.

iOS local inputs are Debug-only, but native iOS transactions were not run here.
This does not prove a hosted webhook, Production billing, or store acceptance.
Render billing remains blocked; the API is still local on port 15080.

The final accumulated-credit client check found and fixed the former 220-credit
parser ceiling. 17 focused AiGateway checks pass, and 418 is now directly visible
in the Android chat header. Repeated restore still leaves exactly two subscription
grants totalling 400. Local snapshot reconciliation and webhook ingestion are
mutually exclusive; any later switch must reconcile timestamp precision before
importing events into this existing sandbox ledger.

## Hosted Staging setup — 1 October 2026

Owner approved Render deployment after adding billing. API service
`evidrilo-staging-api` uses Free Docker in Singapore at
`https://evidrilo-staging-api.onrender.com`; worker `evidrilo-staging-worker`
uses the previously approved `0.5c-512mb` plan. Both use manual deployment
from `main`, distinct runtime database roles, and verify-full pooler TLS.
Production remains unchanged. Secrets are entered through the project-scoped
operator broker and never committed.

Staging AI uses the owner-approved dedicated Experiential Luna 6 key with
provider-default effort, USD 0.01/request and USD 2/month limits. Billing is
Test Store only, with server-verified sandbox subscription reconciliation;
production webhook ingestion is not enabled alongside that mode.
The mobile Debug build points to hosted Staging. This is a judging/test build,
not evidence of real App Store/Play billing readiness. Final deployment uses `5a40b748`; readiness and signed-in account endpoints
returned 200, and Android general AI received a live reply through Render.
Verify CI passed; the latest native iOS CI result remains pending. See the
current acceptance snapshot for limits.
