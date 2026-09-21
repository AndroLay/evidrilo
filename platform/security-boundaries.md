# Platform Security Boundaries

This document describes code-level security contracts in the optional
Evidrilo platform. It is not a production security certification. Repository
tests and local PostgreSQL checks do not prove managed provider settings,
deployment controls, or runtime behavior in a hosted environment.

## Protected data

- Account identity, authentication sessions, and future sync metadata.
- Learner-authored drafts and attempt history.
- Unpublished case content, review decisions, and organization roles.
- RevenueCat webhook events and server-owned entitlement projections.
- Operational logs, migration state, backups, and any future AI requests.

Collect and retain only what the active feature needs. Learner drafts,
reviewer correspondence, participant identity, credentials, and production
provider payloads must not appear in public fixtures, logs, screenshots, or
issue reports.

## Actors and trust boundaries

| Actor | Boundary | Required control |
| --- | --- | --- |
| Anonymous learner | Local mobile app | Complete free workflow; no server identity implied |
| Authenticated learner | HTTPS API | Verified provider token, account-scoped authorization, resource ownership |
| API | Auth provider and database | Validated configuration, least privilege, server-side policy, row-level controls |
| Worker | Job/database boundary | Narrow credentials, bounded retries, idempotency, lease fencing |
| Author/reviewer/teacher | Organization-scoped API | Active membership, assigned role, audit trail, aggregate-first access |
| AI provider | Optional gateway | Explicit opt-in, redaction, output validation, timeout and fallback |

## Identity and sessions

- Authorization uses a verified authentication principal; client-supplied
  account IDs and decoded unsigned tokens do not establish identity.
- The mobile app persists only provider-verified sessions in platform secure
  storage. Tokens are excluded from UI, analytics, exceptions, and logs.
- OAuth uses authorization code with PKCE, single-use state, and the fixed
  `evidrilo://auth/callback` redirect. Callback parsing rejects wrong origins, malformed or
  duplicate parameters, and provider errors.
- Session refresh, sign-out, account switch, and recovery invalidate stale
  asynchronous work before it can restore access.
- Missing provider configuration produces a clear unavailable/not-configured
  result rather than a fake authenticated state.

## API and database

- API request bodies have a bounded size; individual operations apply
  narrower semantic validation.
- Errors use bounded public messages and request identifiers; internal
  exceptions, tokens, and raw payloads are not serialized.
- Liveness and readiness are separate. Readiness does not disclose URLs or
  secrets, and database readiness requires the expected migration state.
- Mutations validate authentication, organization membership, role, consent,
  ownership, idempotency, and resource state at the server boundary.
- Account-scoped rows use database controls in addition to API authorization.
  Local PostgreSQL tests are not proof of managed grants or hosted policy
  configuration.
- Sensitive lifecycle events are append-only and actor-bound; account
  deletion follows explicit confirmation and preserves only deletion-safe
  audit records.
- Membership operations protect the last active organization owner.
- Forwarded headers are ignored unless trusted proxy addresses are explicitly
  configured.
- Rate limits and worker retries are bounded; stale worker leases cannot
  overwrite a newer claim.

## Content, analytics, and AI

- Published case versions are immutable and schema-validated. Draft content
  is not exposed as published content.
- Analytics accepts typed, consented events; learner-authored draft text is
  not an analytics payload.
- Cohort views are organization-scoped and aggregate-first; suppress small
  groups and do not grant general access to learner drafts.
- Recommendations may abstain and expose their reason and evidence
  reference.
- AI assistance is optional and non-grading. Inputs are explicitly selected
  and redacted; output is schema-checked; timeout, cost, quota, and provider
  failures fall back without changing evaluator truth.
- AI credit state is server-owned and account-scoped. The ledger must make
  grants idempotent, reserve before a provider call, consume only an accepted
  schema-valid response, and release on timeout, cancellation, provider
  failure, malformed output, policy rejection, or unavailable configuration.
  The client cannot grant, transfer, or edit credits; raw prompts and raw
  responses are not ordinary audit-log data.

## Billing

- RevenueCat webhook events require provider authentication and stable event
  identity; duplicate and stale events cannot roll entitlement state backward.
- Server access is derived from the canonical
  `evidrilo_pro` entitlement and approved monthly/yearly products, not a client
  flag.
- Unknown or unapproved active products fail closed. Product-less expiry or
  revocation cleanup may remove old access but cannot grant new access.
- `evidrilo_pro` grants 100 AI credits per active entitlement month; a verified
  free account receives a one-time 10-credit grant after consent. Yearly
  entitlements receive monthly grants while active, with no rollover. Webhook
  replay, restore, and concurrent requests must not duplicate a grant or spend
  one credit twice.
- Billing failure must not disable the local free workflow.

## Configuration and operations

- Commit examples only. Keep API secrets, database credentials, signing
  material, webhook secrets, and provider service-role keys outside the
  repository.
- Client applications may receive only provider-approved public SDK keys.
- Do not enable a managed endpoint, provider, email delivery, AI provider, or
  production data path without a separately approved configuration and
  environment-specific test.
- Backups, retention, restore, monitoring, incident response, and rollback
  require named owners and a verified target environment before production
  use.

## Verification boundary

Unit tests and disposable local PostgreSQL harnesses can establish only the
code and local scenarios they exercise. External verification remains
separate for managed authentication/JWKS/RLS, RevenueCat delivery and
transactions, OAuth/email delivery, mobile secure storage, hosted AI,
deployment secrets, backups, alerts, rollback, and human research.

Never send requests to production as part of the local test suite.
