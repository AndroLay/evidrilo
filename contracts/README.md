# Evidrilo platform contracts

This directory contains the first versioned HTTP response boundaries for the
optional Evidrilo platform lane. The files are public-source-safe: schemas and
fixtures use synthetic identities and contain no credentials, provider payloads,
participant data, or production URLs.

The contract tree is split by artifact responsibility:

- `schemas/` contains versioned JSON Schema documents;
- `fixtures/` contains synthetic, non-secret examples used by deterministic
  checks;
- `routes.v1.json` maps every registered API route to its response schema and
  backend endpoint test. The contract test compares it with the ASP.NET route
  registrations so a new handler cannot silently bypass the inventory;
- `openapi/` is reserved for a generated or reviewed HTTP description;
- `events/` is reserved for event envelopes once a public event contract is
  approved.

## Version 1 boundaries

- `GET /health/live` returns a dependency-free process liveness response.
- `GET /health/ready` reports only safe dependency statuses and distinguishes a
  configured/ready service from a degraded local service.
- `GET /v1/account/me` returns a verified account summary. It is not learning
  history and does not imply that cloud sync is enabled.
- `DELETE /v1/account/me` requires a verified subject and explicit confirmation;
  it returns a server-owned deletion preparation result and never claims that
  managed Auth deletion or session revocation has already happened.
- P2 sync contracts separate idempotent client commands, server cursors, and
  account-scoped change records. They carry a digest rather than raw draft
  text or credentials.
- P3 analytics events are consented, typed, and idempotent, covering practice
  funnel, completion, premium-action, client-error, and recommendation signals;
  progress summaries carry a calculation version and expose only the caller's
  projection; account and bounded daily progress reads share the same
  validated metrics. Analytics, sync, and recommendation writes pass through
  the server API; migrations 022–023 remove direct client INSERT paths.
- P4 authoring results and published case summaries identify immutable
  case/evaluator versions; authoring documents validate objective, typed
  observation/limitation facts, feedback-rule anchors, and challenge variants
  before storage. Published readers fail closed when stored content does not
  match the immutable metadata.
- P5 recommendation responses carry a reason and evidence references and may
  abstain; the versioned response schema distinguishes the required
  `recommended` and `abstain` shapes; interaction writes are consented and
  idempotent.
- P6 AI assist results expose typed success/fallback only; audit records never
  expose prompts or provider payloads.
- P7 cohort summaries are aggregate-only and expose suppression explicitly.
- P7 membership operations are server-owned, role-scoped, auditable, and
  protect the last active owner; invitation delivery is not a local contract.
- HTTP failures use `{ schema, version, code, message, requestId }`.

All response schemas are closed at the root with `additionalProperties: false`.
Credential-shaped fields and raw provider claims are intentionally excluded.
The mobile free core does not call these endpoints and remains usable offline.

AI provider configuration, invitation delivery, managed Auth lifecycle, and
billing webhook delivery still require external authorization and verification
evidence. They must not be added by extending an account summary or sync
response.

Run the dependency-free contract checks from the repository root:

```bash
node --test contracts/contracts.test.mjs
```
