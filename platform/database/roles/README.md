# Dedicated runtime database roles

Apply `provision-runtime-roles.sql` only as the migration operator, after the
frozen candidate migrations through 055. It creates NOLOGIN API and worker roles;
no password or runtime activation occurs. Both use NOBYPASSRLS and no inherited
roles. The existing Supabase operator remains separate and must never be installed
on Render. Runtime passwords/LOGIN are configured through the operator's secure
credential store only after role acceptance; never commit them or pass them in
command arguments.

The explicit table/operation allowlist follows the candidate API/worker stores.
Server-only permissive RLS policies target these exact roles, because the API
validates authenticated account/organization scopes and the worker processes
cross-account export/projection jobs. These roles are trusted server identities,
not learner roles: they must not be granted to anon, authenticated, authenticator,
or other callers. Existing client policies and revocations remain intact. No
blanket table grant, role membership, superuser, CREATE DATABASE, CREATE ROLE,
replication, BYPASSRLS or table ownership is given. New tables require a new review.

Before activation prove: API cannot access auth.users or worker_jobs; worker cannot
mutate entitlements, credits or student project content; neither can alter tables
or the migration ledger; allowed API and worker flows still succeed. The script
revokes PUBLIC's CREATE privilege on public schema. Re-running reconciles only
these roles' grants/policies, with no data deletion. Password rotation and role
retirement are operator tasks, separate from forward-only schema migrations.

`platform/database/integration/run-local-postgres-smoke.sh` applies this plan twice
and runs `runtime-role-smoke.sql`. The latter verifies the complete effective
public-table allowlist, role attributes, denied Auth access, denied DDL, API
consent writes and worker lease writes. `platform/integration/run-local-api-worker-e2e.sh`
uses distinct API/worker logins in a loopback-only disposable container; the owner
connection is retained only for synthetic fixture setup/assertions. LOGIN in
that harness is never an instruction to enable hosted credentials without review.
