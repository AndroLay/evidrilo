# Supabase environment handoff

This document records the current Supabase staging setup and the steps for
carrying the same application configuration to another Supabase project. It
does not authorize production changes or replace the release gates in
[`render-supabase-staging.md`](render-supabase-staging.md).

## Current setup snapshot

Audit date: 2026-10-01.

| Environment | Project | Region | Current boundary |
| --- | --- | --- | --- |
| Staging | `Evidrilo Staging` (`cdgzbrrrvlrrgpzbgqog`) | `ap-southeast-1` | Synthetic data. Email sign-in and sign-up are enabled, email confirmation is required, Google is configured, Apple stays disabled under D-132, and manual identity linking is disabled. The `evidrilo://auth/callback` redirect is on the Auth allowlist. |
| Production | `Evidrilo` | `ap-south-1` | Not changed by this setup. Select it by project name in the Supabase dashboard and verify its project reference before any production action. |

The 2026-10-01 read-only Staging preflight confirmed the project is still
healthy, with zero public tables and no
`public.evidrilo_schema_migrations` ledger. The last observed Auth user count
was zero on 2026-09-30. No repository migration has been applied. The Google
client pair is configured in Supabase and the app deep-link redirect is
allowlisted, but the Google Cloud callback/test-user settings and an Android
sign-in have not been verified. The Render service inventory is empty, and no
production setting was changed. See the [Render/Supabase staging handoff](render-supabase-staging.md)
for the current activation gates.

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

   These sample values keep provider sign-in disabled in a new environment.
   For the existing Staging project, Google is configured in Supabase, but
   leave the mobile flag disabled until its Google Cloud callback, test-user
   allowlist, and Android sign-in are verified. Keep Apple disabled under D-132
   until its credentials, redirect URIs, and device sign-in flow are verified.
4. Add `evidrilo://auth/callback` and
   `evidrilo://auth/callback?state=*` to that project's Supabase Auth redirect
   allowlist. The second entry preserves the app's per-attempt correlation
   value when Supabase returns the PKCE code; keep the callback path restricted.
   Review email confirmation and recovery redirects, sender
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

### Verified-email access token hook

After applying the migrations that define `public.custom_access_token_hook`,
enable the Supabase Auth custom access token hook with URI
`pg-functions://postgres/public/custom_access_token_hook`. Confirm the Auth admin
role has its migration-defined execute grant. The API requires the server-derived
root `email_verified` claim; Google identity metadata alone is insufficient.
Existing sessions must obtain a fresh token after activation. Verify authenticated
credits, consent, and entitlement endpoints return 200 with that fresh token.
This hook is enabled in Staging; activation there does not configure Production.

For Production, repeat these steps with the Production project and its own
credentials, database roles, sender/provider configuration, and release
approval. Never "promote" a Staging key or database URL into Production.
