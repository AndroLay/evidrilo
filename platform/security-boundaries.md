# Platform foundation security boundary

Latest repository increment: E191 / TARGET_SURFACES_AND_CASE_CATALOGUE_INTEGRATED / E190 / EVIDENCE_GRAPH_ANCHOR_CLOSURE_HARDENED / E189 / KOTLIN_MODULE_BOUNDARIES_AND_VERIFICATION_ALIGNED / E188 / RELEASE_VERSION_SOURCE_ALIGNED / E187 / REPOSITORY_ARCHITECTURE_MIGRATION_VERIFIED / E186 / BACKEND_ENGINE_SYNC_BOUNDARY_HARDENED / E185 / REVENUECAT_OFFERING_MIGRATION_OBSERVED / E183 / NATIVE_CHOICE_ACCESSIBILITY_SEMANTICS_HARDENED / E182 / SYNC_CURSOR_CONTRACT_BOUNDARY_ALIGNED / E181 / CASE_TRANSITION_CONTRACT_BOUNDARY_HARDENED / E180 / MOBILE_RELEASE_CANDIDATE_PREPARATION / E179 / SYNC_CONSENT_CANCELLATION_BOUNDARY_HARDENED / E178 / API_INPUT_AND_STAGING_BOUNDARY_HARDENED / E177 / SYNC_PULL_PAGE_SIZE_BOUNDARY_HARDENED / E176 / REQUEST_LIFECYCLE_AND_INPUT_BOUNDARIES_HARDENED / E175 / SYNC_PULL_CURSOR_LOWER_BOUND_GUARDED / E174 / FAIL_CLOSED_RESPONSE_REFRESH_BOUNDARIES / E173 / ASYNC_STATE_BOUNDARIES_HARDENED / E172 / LOCAL_SESSION_BOUNDARIES_HARDENED / E171 / OFFLINE_AUDIO_REPOSITORY_IMPLEMENTATION / E170 / MEMBERSHIP_ROLE_ASSIGNMENT_POLICY_COVERAGE / E169 / AUTH_PROVIDER_CONFIRMATION_TYPE_BOUNDED / E168 / CURRENT_STATUS_SNAPSHOTS_SYNCHRONIZED.

Status: E191 / TARGET_SURFACES_AND_CASE_CATALOGUE_INTEGRATED / E190 / EVIDENCE_GRAPH_ANCHOR_CLOSURE_HARDENED / E189 / KOTLIN_MODULE_BOUNDARIES_AND_VERIFICATION_ALIGNED / E188 / RELEASE_VERSION_SOURCE_ALIGNED / E187 / REPOSITORY_ARCHITECTURE_MIGRATION_VERIFIED / E186 / BACKEND_ENGINE_SYNC_BOUNDARY_HARDENED / E185 / REVENUECAT_OFFERING_MIGRATION_OBSERVED / E183 / NATIVE_CHOICE_ACCESSIBILITY_SEMANTICS_HARDENED / E182 / SYNC_CURSOR_CONTRACT_BOUNDARY_ALIGNED / E181 / CASE_TRANSITION_CONTRACT_BOUNDARY_HARDENED / E180 / MOBILE_RELEASE_CANDIDATE_PREPARATION / E179 / SYNC_CONSENT_CANCELLATION_BOUNDARY_HARDENED / E178 / API_INPUT_AND_STAGING_BOUNDARY_HARDENED / E177 / SYNC_PULL_PAGE_SIZE_BOUNDARY_HARDENED / E176 / REQUEST_LIFECYCLE_AND_INPUT_BOUNDARIES_HARDENED / E175 / SYNC_PULL_CURSOR_LOWER_BOUND_GUARDED / E174 / FAIL_CLOSED_RESPONSE_REFRESH_BOUNDARIES / E173 / ASYNC_STATE_BOUNDARIES_HARDENED / E172 / LOCAL_SESSION_BOUNDARIES_HARDENED / E171 / OFFLINE_AUDIO_REPOSITORY_IMPLEMENTATION / E170 / MEMBERSHIP_ROLE_ASSIGNMENT_POLICY_COVERAGE / E169 / AUTH_PROVIDER_CONFIRMATION_TYPE_BOUNDED / E168 / CURRENT_STATUS_SNAPSHOTS_SYNCHRONIZED / RUNTIME_GATES_OPEN / EXTERNAL_GATES_OPEN
The E191 verification passes the focused Kotlin/Android target tasks, full API
`187/187`, and the versioned contract suite. These checks cover source and
repository boundaries only; managed roles, secrets, deployment, and runtime
security remain owner-controlled gates.

<!-- Historical status chain retained below for provenance.
Status: `E170 / MEMBERSHIP_ROLE_ASSIGNMENT_POLICY_COVERAGE / E169 / AUTH_PROVIDER_CONFIRMATION_TYPE_BOUNDED / E168 / CURRENT_STATUS_SNAPSHOTS_SYNCHRONIZED / E167 / BILLING_EMPTY_ACCOUNT_GUARDED / E166 / LAST_OWNER_ROLE_CHANGE_GUARDED / E165 / COHORT_LEARNER_ROLE_BOUNDARY_HARDENED / E164 / COHORT_MEMBERSHIP_BOUNDARY_HARDENED / E163 / LOCAL_SESSION_DECODER_HARDENED / E162 / NODE_VERIFICATION_SNAPSHOT_SYNCHRONIZED / E161 / RECOMMENDATION_SCHEMA_ALIGNMENT / E160 / CONTENT_RUNBOOK_CHALLENGE_REQUIRED / E159 / CASE_SCHEMA_IDENTIFIER_BOUNDS / E158 / CONTRACT_SCHEMA_ALIGNMENT / E157 / MOBILE_CONTENT_CHALLENGE_REQUIRED / E154 / AUTH_CALLBACK_INPUT_BOUNDED / E153 / CLIENT_BOUNDARIES_HARDENED / WEBHOOK_BODY_BOUNDED / AI_CANCELLATION_PROPAGATED / VERIFIED_SESSION_GATES_ALIGNED / E152 / ACCOUNT_DELETION_OWNER_GUARDED / E151 / REVENUECAT_PRODUCT_ALLOWLIST_GUARDED / E150 / RUNBOOK_STATUS_SYNCHRONIZED / E149 / CANONICAL_ENTITLEMENT_GUARDED / E148 / E147 / AI_SCOPE_DECISION_LOCKED / FOUNDATION_LOCAL_CHECKS_PASS / LOCAL_POSTGRES_RLS_PASS / LOCAL_WORKER_FENCING_PASS / CASE_LIFECYCLE_AUDIT_VERIFIED / CASE_LIFECYCLE_AUDIT_READ_VERIFIED / RECOMMENDATION_PROJECTION_VALIDATED / RECOMMENDATION_CANDIDATE_VALIDATED / SERVER_OWNED_WRITE_BOUNDARIES / CANONICAL_CONTENT_READER_VERIFIED / CLAIM_AUDIT_CURRENT / DATABASE_READINESS_LEDGER_GUARDED / BACKEND_REGISTER_SYNCHRONIZED / RUNBOOK_OBSERVATION_CLAIMS_GUARDED / REVENUECAT_UI_BOUNDARY_COMPILED / REVENUECAT_RUNBOOK_ALIGNED / PRODUCTION_CORS_EXPLICIT / GENERATED_OUTPUT_BOUNDARY_HARDENED / MIGRATION_RUNNER_CONCURRENCY_GUARDED / TRIGGER_FUNCTION_PRIVILEGES_HARDENED / BILLING_CALLBACK_IDENTITY_GUARDED / BILLING_CONFIG_STATUS_TRUTHFUL / BILLING_UNKNOWN_STATE_ALLOWLISTED / AUTH_REDIRECTS_REJECTED / ANDROID_NETWORK_PERMISSION_DECLARED / ACCOUNT_PROFILE_PROVISIONED / ACCOUNT_DELETION_ACCESS_GUARDED / ACCOUNT_DELETION_LEDGER_FALLBACK / EXTERNAL_GATES_OPEN`

-->
## Assets

- Supabase-issued identity tokens and account sessions;
- learner identity and future synced drafts/attempts;
- unpublished content and role assignments;
- billing/webhook data and future AI provider credentials;
- database backups, migration state, audit events, and operational logs.

## Actors and trust boundaries

| Actor | Boundary | Required control |
| --- | --- | --- |
| Anonymous mobile learner | KMP local app | Free evaluator remains local and complete |
| Authenticated mobile learner | HTTPS API | Supabase-issued token, server validation, ownership checks |
| API | Managed Auth/PostgreSQL | Environment configuration, least privilege, RLS in P2 |
| Worker | API/database | Narrow job credentials and idempotent projections in P3 |
| Teacher/reviewer | API/admin surface | Server role checks, aggregate-first views, audit events |
| AI provider | Gateway boundary | Redaction, schema validation, timeout, quota, no secrets |

## Foundation controls implemented

- Production options fail closed when required Supabase configuration is absent.
- The API never treats a decoded/unsigned token or client user ID as identity.
- Account identity comes only from the verified authentication principal.
- Public errors have bounded safe messages and request IDs; internal exceptions
  are not serialized.
- Raw API request bodies have a 1 MiB server limit before JSON materialization;
  individual routes apply narrower semantic limits after parsing.
- Liveness and readiness are separate; readiness exposes no URLs or keys.
- Database readiness additionally requires the migration ledger and the current
  migration version before the API reports its database as ready.
- Synthetic integration auth is available only inside the test project.
- Contract fixtures are checked for credential-shaped field names.
- Analytics accepts only typed, consented event properties and uses
  payload-bound client-event idempotency; progress is a rebuildable projection
  rather than client truth. Migration 022 removes client INSERT paths for
  analytics, sync, and recommendation interactions, making the validated API
  the mutation boundary; nullable analytics properties are normalized for
  older-client retries.
- Published case versions are immutable at the domain boundary; recommendation
  output can abstain and always carries a reason/evidence reference.
- Case lifecycle events are append-only, actor-bound, client-inaccessible, and
  retained through account deletion with actor anonymization.
- Lifecycle history reads are explicit API operations limited to active
  organization membership and author/reviewer/maintainer/owner roles; the
  response is closed and bounded to 128 events.
- AI input is opt-in and redacted, provider output is schema-checked, and
  timeout/quota/provider failures fall back without changing evaluator truth.
- Cohort access requires active organization scope and aggregate suppression;
  raw learner draft access is disabled by default.
- Account deletion requires a verified subject plus an explicit confirmation;
  platform-owned telemetry, entitlements, memberships, and sync rows are
  removed, while shared authored content is anonymized before managed Auth
  deletion remains an external operation.
- Configured database-backed API deployments check the server-owned deletion
  tombstone before protected route handlers and return `410 ACCOUNT_DELETED`;
  the completed deletion ledger covers legacy accounts without a profile row;
  only the explicit deletion route remains available for an idempotent retry.
- New Auth accounts provision an `account_profiles` row through migration 024's
  server-owned after-insert trigger with a fixed function search path and no
  public function privileges; the deletion ledger remains the fallback for
  legacy or incomplete rows.
- Asynchronous billing identity, refresh/offer, purchase, and restore results
  are accepted only when their request generation and initiating account ID
  still match the current session; session changes and premium close invalidate
  stale callbacks.
- Mobile account credentials are sent directly to the managed Auth provider;
  the Evidrilo API is never a password proxy. Only verified provider sessions
  reach secure persistence, with Android Keystore/iOS Keychain adapters and
  redacted diagnostics.
- Google mobile OAuth uses an authorization code, PKCE verifier/challenge,
  single-use state, and the fixed `evidrilo://auth/callback` redirect. The
  parser rejects wrong origins, duplicate parameters, malformed encoding,
  provider errors, and non-recovery access-token fragments.
- Access and refresh tokens are excluded from UI, analytics, exceptions, and
  logs. Refresh rotation replaces the secure record; invalid refresh or
  expired recovery sessions are cleared.
- Membership grant, role change, revoke, and leave operations are server-owned,
  audit-backed, serialized per organization, and protect the last active owner;
  invite delivery is not simulated without an approved provider.
- API mutations have a bounded fixed-window rate limit with a stable redacted
  `RATE_LIMITED` response; the billing webhook has a dedicated 120-per-minute
  per-source limit before signature processing; worker jobs have bounded attempts, idempotency, and
  attempt-based lease fencing so stale workers cannot mutate a newer claim.
- Forwarded headers are ignored by default. A deployment must provide an
  explicit comma-separated list of trusted proxy IP addresses before the API
  consumes `X-Forwarded-For` or `X-Forwarded-Proto`; arbitrary forwarded
  headers cannot change rate-limit identity or request scheme.
- API telemetry records only completion status and duration; account IDs, paths,
  tokens, request bodies, and provider payloads are not metric labels.
- Billing access is derived from a signed provider webhook and server-owned
  entitlement rows; a client premium flag is not authoritative. Provider event
  IDs are idempotent, payload reuse is rejected, and older events cannot roll
  back a newer entitlement projection. Active/granting events must carry the
  exact approved `monthly` or `yearly` product when a product is supplied;
  active grants also require the product field, and missing or unapproved
  products are ignored before persistence. Product-less expiration/revocation
  cleanup remains allowed.
- Billing readiness is reported as configured only when webhook authentication
  and the exact canonical `evidrilo_pro` entitlement are both present; a secret
  or unrelated entitlement cannot make the service appear ready. E149 records
  that configuration guard, E150 synchronizes the operational records, and
  E151 records the server product allowlist regression without changing any
  provider or production claim.
- Account deletion now locks each affected organization and refuses to remove
  a sole active owner until ownership is transferred. Migration 027 and the
  API `OWNER_TRANSFER_REQUIRED` mapping preserve the same last-owner invariant
  used by membership lifecycle operations; E152 records the local PostgreSQL
  verification.
- E153 adds defense in depth across the client and request boundaries: mobile
  entitlement access requires canonical `evidrilo_pro` plus an approved
  `monthly`/`yearly` product; content, sync, and analytics reject unverified
  stored sessions; account deletion preserves the owner-transfer action;
  chunked billing input is bounded before deserialization; and caller
  cancellation is not converted into an AI provider fallback. Local Kotlin
  `267/267`, API `144/144`, Node `77/77`, worker `5/5`, shared iOS compilation,
  PostgreSQL smoke, and worker smoke pass.

- E154 bounds custom-scheme auth callbacks to 8 KiB before query/fragment
  parsing and prevents oversized callbacks from entering the pending redirect
  bus. The focused parser/bus regression, full local verifier, and shared iOS
  target compilation pass; the current verifier records API `144/144`, Node
  `77/77`, and worker `5/5`. Native callback runtime and provider-backed
  account verification remain open.
- E155 requires one to 32 meaningful challenge variants in accepted authoring
  documents, preserving the evidence-change invariant before content can enter
  the publication workflow. Focused content/authoring coverage passes `22/22`
  and the full API suite passes `145/145`; managed content publication remains
  open. See the [E155 content boundary record](../audit/evidence/evidrilo-content-challenge-required-2026-09-14.md).
- E156 revalidates stored authoring JSON under the transition row lock before
  `approved` or `published`, so legacy or mutated rows cannot bypass the
  canonical content validator. Focused content/authoring coverage passes
  `23/23` and the full API suite passes `146/146`. Managed database replay and
  publication remain open. See the [E156 content transition record](../audit/evidence/evidrilo-stored-content-transition-guard-2026-09-14.md).
- E157 makes the mobile published-case reader reject an empty challenge-variant
  array. Focused reader coverage passes `8/8`; Kotlin/JVM coverage passes
  `269/269`, JVM/Android compilation passes, and shared iOS targets compile.
  Managed content publication, provider, device, staging, human, and
  submission gates remain open. See the [E157 content reader record](../audit/evidence/evidrilo-mobile-content-challenge-required-2026-09-14.md).
- E158 aligns the versioned published-case schema with the API and Kotlin
  content boundaries by requiring at least one challenge variant. The focused
  contract and full local verifier pass; managed content publication remains
  open. See the [E158 schema boundary record](../audit/evidence/evidrilo-case-schema-challenge-boundary-2026-09-14.md).
- E159 aligns identifier, title, and skill-tag bounds in the versioned
  published-case schema with the API and Kotlin readers. Cross-field references
  remain runtime validation. See the [E159 schema identifier-boundary record](../audit/evidence/evidrilo-case-schema-identifier-bounds-2026-09-14.md).
- E160 corrects the operated content-authoring runbook so it requires one to
  32 meaningful challenge variants; managed editorial publication remains open.
  See the [E160 content-runbook record](../audit/evidence/evidrilo-content-runbook-challenge-required-2026-09-14.md).
- E161 aligns the recommendation schema with the mobile/API status-specific
  response boundary. Provider and runtime evidence remain open. See the [E161
  recommendation-schema record](../audit/evidence/evidrilo-recommendation-schema-alignment-2026-09-14.md).
- E163 hardens local conclusion-session restoration by rejecting unknown enum
  values, malformed escaped lists, and encoded values over 8 KiB before
  parsing. The focused regression and final local verifier pass with
  Kotlin/JVM `270/270`; external provider, device, managed, staging, human,
  and submission gates remain open. See the [E163 local-session decoder
  record](../audit/evidence/evidrilo-local-session-decoder-hardening-2026-09-14.md).
- E164 hardens cohort aggregates so inactive organization memberships cannot
  remain represented through stale enrollment rows. Migration 028, the `28/28`
  migration contract, focused API readiness `4/4`, and fresh PostgreSQL/RLS
  smoke pass. Managed database, provider, device, staging, human, publication,
  and submission gates remain open. See the [E164 cohort boundary record](../audit/evidence/evidrilo-cohort-active-membership-boundary-2026-09-14.md).
- E165 hardens the same server-owned aggregate so only active memberships with
  role `learner` contribute to learner totals. Migration 029, focused contract,
  API readiness `4/4`, and fresh PostgreSQL/RLS smoke pass. Managed database,
  provider, device, staging, human, publication, and submission gates remain
  open. See the [E165 cohort role-boundary record](../audit/evidence/evidrilo-cohort-learner-role-boundary-2026-09-14.md).
- E166 closes a membership role-change invariant: a grant cannot demote the
  last active organization owner to a non-owner role. The organization row is
  locked before the owner count is checked; focused policy coverage and the
  full API suite pass `147/147`. Managed database, provider, device, staging,
  human, publication, and submission gates remain open. See the [E166
  last-owner role-change record](../audit/evidence/evidrilo-last-owner-role-change-2026-09-14.md).
- E167 rejects the all-zero `app_user_id` before a signed RevenueCat event
  reaches the entitlement store. The focused billing suite passes `11/11` and
  the full API suite passes `148/148`; provider, managed database, device,
  staging, human, publication, and submission gates remain open. See the [E167
  billing identity record](../audit/evidence/evidrilo-billing-empty-account-guard-2026-09-14.md).
- E168 synchronizes the active security/status headers with the current
  evidence authority. The snapshot contract passes `23/23`; no security
  boundary, tracker count, or external gate is changed. See the [E168 snapshot
  synchronization record](../audit/evidence/evidrilo-current-status-snapshot-sync-2026-09-14.md).
- E169 hardens the auth provider type boundary. Non-string confirmation
  metadata cannot mark a local session as email-verified; focused auth coverage
  passes `13/13` and Kotlin/JVM `271/271` with cross-target compilation. Live
  provider, native secure-storage, managed, staging, human, publication, and
  submission evidence remain open. See the [E169 auth provider type-boundary
  record](../audit/evidence/evidrilo-auth-provider-type-boundary-2026-09-14.md).
- E170 adds explicit policy regressions for learner, teacher, and reviewer role
  assignment attempts. The existing authorization policy denies those actors;
  focused access-policy coverage passes `9/9` and the full API suite passes
  `151/151` without changing the policy. Live provider, native secure-storage,
  managed, staging, human, publication, and submission evidence remain open.
  See the [E170 membership role-assignment record](../audit/evidence/evidrilo-membership-role-assignment-coverage-2026-09-14.md).

- E171 keeps audio outside evaluator truth, drafts, history, entitlements, sync,
  and analytics. Audio assets are manifest-validated and the platform adapters
  reject network-dependent TTS; device interruption, accessibility-service,
  and human pronunciation review remain open.

- E172 binds local session restoration to supported workflow identities. A
  decoded phase must use the expected main/challenge case IDs, and secure local
  session records reject unsafe account identifiers before downstream use.

- E173 binds asynchronous sync results to the queue snapshot, generation,
  account, and consent that initiated them. Clear, account-switch, and consent
  boundaries invalidate older work; the premium reducer retains only the
  approved monthly/yearly catalog. Runtime/provider/managed verification is
  still open.

- E174 makes remote response handling fail closed: pull cursors and server
  sequences must be monotonic and non-skipping, push results must exactly cover
  the submitted commands, refresh must clear a stale session when confirmation
  is withdrawn, and direct billing presentations must reject lifetime. These
  are repository guards; provider, native runtime, managed, staging, and human
  verification remain open.

- E186 separates local practice identity from canonical server case-version
  identity. Unknown mappings fail closed; new sync commands require a published
  case version; existing idempotent replays are checked before current case
  publication state; and migration 030 enforces the same rule for direct
  database writes. This is local engine/API/PostgreSQL evidence only.

## External gates not run

These are intentionally not claimed by local tests:

- real Supabase issuer/JWKS validation;
- managed Supabase RLS grants, Auth integration, and hosted migration behavior;
  local PostgreSQL migration/RLS behavior is covered by E108's fresh-container
  harness;
- managed/live analytics projection rebuilds, account-claim propagation/RLS,
  case authoring mutations, account deletion function execution, membership
  lifecycle, and worker lease execution against an authorized database;
  E110 covers the separate worker claim/rebuild process boundary only against
  synthetic local PostgreSQL;
- configured AI provider behavior, legal/privacy approval, and cost controls;
- teacher dashboard SQL authorization and suppression tests;
- RevenueCat webhook secret rotation, signature delivery, entitlement restore,
  revoke, and duplicate-event behavior in a managed test project;
- email verification, password reset, refresh rotation, and revoke-all-session
  behavior with a disposable external account;
- Google Cloud OAuth client/provider setup, consent-screen behavior, callback
  delivery on Android/iOS, and recovery-link delivery with an approved SMTP
  provider;
- Android Keystore/iOS Keychain runtime observation;
- deployment secret storage, HTTPS, backups, restore, alerts, and rollback;
- human review, participant data, and production load behavior.

No production target is scanned or contacted by the foundation test suite.

The billing boundary also applies its approved monthly/yearly product
allowlist to unresolved provider presentation states. This prevents a legacy
package from becoming visible while preserving the fail-closed, non-purchasable
unknown state; provider and production verification remain external.
