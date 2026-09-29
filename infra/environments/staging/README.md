# Staging environment boundary

The repository contains a Render Free API Blueprint and an optional paid
worker Blueprint under `infra/deployment/`. A separate `Evidrilo Staging`
Supabase project exists, and its Auth redirect allowlist includes
`evidrilo://auth/callback`. Local Android and iOS Debug configuration points to
that project. The database migration ledger is still unknown; no hosted
migrations, runtime database logins, or Render deployment are complete.

Staging is not ready for API use until the exact candidate is frozen and
verified, the migration set is reviewed and applied, and separate least-
privilege runtime logins are provisioned. The Free API alone does not process
queued account deletion or projection jobs. See
`infra/deployment/render-supabase-staging.md` and
`infra/deployment/supabase-environments.md` for the setup sequence and
environment handoff.
