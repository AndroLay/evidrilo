# Deployment boundary

Local Docker preparation remains separate from provider deployment. The
landing target is Cloudflare Pages; its Git integration settings are in
[`cloudflare-pages-landing.md`](cloudflare-pages-landing.md). The staging API
uses the Render Free web-service Blueprint and Supabase; see [`render.yaml`](render.yaml)
and [`render-supabase-staging.md`](render-supabase-staging.md).
For the Supabase environment mapping and the later Staging-to-Production
handoff, see [`supabase-environments.md`](supabase-environments.md).

Cloudflare Pages uses Git integration for the landing page. The Render API
Blueprint uses manual deploys and does not run migrations. Render Free does not
provide background-worker services; the optional worker Blueprint in
[`render-worker-paid.yaml`](render-worker-paid.yaml) requires paid compute and
must be enabled separately. Without that worker, queued account deletion and
projection jobs are not complete. Do not deploy until the exact candidate has
passed GitHub Verify, its migrations are applied, and separate least-privilege
runtime database logins are provisioned. Provider credentials belong only in
provider secret settings.

The API's built-in fixed-window rate limits are process-local. Keep the API at
one instance until a shared trusted-edge or distributed limiter is configured;
the database-backed AI credit and provider-spend ceilings do not bound general
API traffic across instances.
