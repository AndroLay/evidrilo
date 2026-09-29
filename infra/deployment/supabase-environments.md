# Supabase environment handoff

This document records the current Supabase staging setup and the steps for
carrying the same application configuration to another Supabase project. It
does not authorize production changes or replace the release gates in
[`render-supabase-staging.md`](render-supabase-staging.md).

## Current setup snapshot

Audit date: 2026-09-29.

| Environment | Project | Region | Current boundary |
| --- | --- | --- | --- |
| Staging | `Evidrilo Staging` (`cdgzbrrrvlrrgpzbgqog`) | `ap-southeast-1` | Synthetic data. Email sign-in and sign-up are enabled, email confirmation is required, Google and Apple sign-in are disabled, and manual identity linking is disabled. The `evidrilo://auth/callback` redirect is on the Auth allowlist. |
| Production | `Evidrilo` | `ap-south-1` | Not changed by this setup. Select it by project name in the Supabase dashboard and verify its project reference before any production action. |

The migration ledger could not be read through the Supabase Management API, so
the staging schema state is unknown. No database migration was applied. The
Render API and worker are not deployed, and no production setting was changed.

Local mobile configuration points to Staging:

- Android reads `supabaseUrl`, `supabasePublishableKey`,
  `supabaseAuthRedirectUrl`, `supabaseGoogleAuthEnabled`, and
  `supabaseAppleAuthEnabled` from the ignored root `local.properties` or Gradle
  properties.
- iOS Debug reads the equivalent `SUPABASE_*` values from the ignored
  `apps/ios/Configuration/Config.local.xcconfig`, included by
  `apps/ios/Configuration/Config.xcconfig`.
- The client configuration contains only the project URL and publishable key.
  Never put a Supabase secret key, service-role key, database password, or
  migration credential in a mobile configuration.

Android currently generates shared BuildConfig values from `local.properties`
for all Android build variants. Set the intended environment values before
each Android build; do not distribute a build that still points at Staging.
The iOS local override is scoped to Debug, leaving Release values unchanged.

## Configuration map

| Consumer | Settings | Where they belong |
| --- | --- | --- |
| Android and shared mobile code | `supabaseUrl`, `supabasePublishableKey`, `supabaseAuthRedirectUrl`, `supabaseGoogleAuthEnabled`, `supabaseAppleAuthEnabled` | Ignored `local.properties` for local builds; protected CI variables for automated builds. |
| iOS | `SUPABASE_URL`, `SUPABASE_PUBLISHABLE_KEY`, `SUPABASE_AUTH_REDIRECT_URL`, `SUPABASE_GOOGLE_AUTH_ENABLED`, `SUPABASE_APPLE_AUTH_ENABLED` | Local `Config.local.xcconfig`; environment-specific protected build settings for Release. |
| API | `ASPNETCORE_ENVIRONMENT`, `SUPABASE_URL`, `SUPABASE_PUBLISHABLE_KEY`, `DATABASE_URL` or `SUPABASE_DB_CONNECTION_STRING`, `CORS_ALLOWED_ORIGINS` | Render API protected settings. The API uses the publishable key for Auth/JWKS validation and a separate least-privilege PostgreSQL login. |
| Worker | `DOTNET_ENVIRONMENT`, `SUPABASE_AUTH_ADMIN_ENABLED`, `SUPABASE_URL`, `SUPABASE_SECRET_KEY`, `DATABASE_URL` or `SUPABASE_DB_CONNECTION_STRING` | Worker-only protected settings. The Supabase secret key is server-only and must not be given to the API or mobile app. |
| Migration operator | `EVIDRILO_MIGRATION_DATABASE_URL` | Owner-controlled operator environment only; never Render, GitHub Actions, or a checked-in `.env` file. |

The existing local API template is [`../../platform/api/.env.example`](../../platform/api/.env.example).
It is for local API development and must not be copied wholesale into Render.

## Move Staging configuration to another project

Use this sequence after the application and migration set are frozen:

1. Freeze the exact Git commit and confirm the repository's GitHub `Verify`
   workflow passes for that commit. Review all numbered migrations included
   in the candidate.
2. In Supabase, select the destination by project name and verify its reference
   and region. Obtain that project's own URL and publishable key. Never reuse
   Staging or Production database passwords, keys, or provider secrets across
   environments.
3. Set the mobile values for that environment:

   ```properties
   supabaseUrl=https://<project-ref>.supabase.co
   supabasePublishableKey=sb_publishable_<key-for-this-project>
   supabaseAuthRedirectUrl=evidrilo://auth/callback
   supabaseGoogleAuthEnabled=false
   supabaseAppleAuthEnabled=false
   ```

   The Google and Apple flags stay false until the matching provider
   credentials, redirect URIs, and device sign-in flows have been configured
   and verified for that environment.
4. Add `evidrilo://auth/callback` to that project's Supabase Auth redirect
   allowlist. Review email confirmation and recovery redirects, sender
   configuration, signup policy, and provider settings separately for each
   project. Keep manual identity linking disabled until its account-linking
   behavior has been explicitly reviewed.
5. Provision distinct database logins for the API and worker, plus a separate
   migration operator login. Review exact grants against the frozen migration
   set. Use the Supabase session pooler when the hosting network cannot reach
   the direct database endpoint; require TLS and an explicit username.
6. From a trusted operator machine, set
   `EVIDRILO_MIGRATION_DATABASE_URL` and run
   `platform/database/migrations/apply-migrations.sh` against the dedicated
   staging project. Record the ledger result and candidate SHA. Do not run the
   migration script from a dirty worktree or point it at Production.
7. Put only the API's URL, publishable key, API database URL, and explicit CORS
   origin into the protected Render API settings. If the paid worker is later
   authorized, configure its own database login and Supabase secret key in the
   worker settings. Keep auto-deploy off and deploy the same verified SHA.
8. Verify health endpoints, email sign-up and confirmation, password recovery,
   callback handling, API Auth/JWKS validation, and only the worker flows that
   are actually deployed. Use synthetic Staging accounts and data.

For Production, repeat these steps with the Production project and its own
credentials, database roles, sender/provider configuration, and release
approval. Never "promote" a Staging key or database URL into Production.
