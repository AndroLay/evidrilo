# Evidrilo Decision Log

This log records current architectural and delivery decisions. Dated evidence
records remain the authoritative detail for historical increments; decisions
below are kept append-only and must not be used to claim runtime or provider
verification that was not observed.

## D-077 — Add optional offline audio without changing the learning boundary

Decision: add an optional audio layer to Evidrilo with two separate channels:
reviewed bundled English narration for fixed product/case copy, and restrained
short sound effects for meaningful interactions. Dynamic text may use native
offline TTS only when an installed voice does not require a network connection.
Audio must never become a prerequisite for the free core, evaluator truth,
draft/history persistence, entitlement, sync, or analytics.

The implementation must use stable asset IDs, source-text checksums, explicit
license/provenance metadata, bounded package size, serialized playback, stale
callback cancellation, visible text fallback, and separate Android/iOS runtime
and human naturalness/accessibility evidence.

See the [offline audio design](superpowers/specs/2026-09-14-evidrilo-offline-audio-design.md)
and the [feature-gate map](superpowers/specs/2026-09-14-evidrilo-feature-gates-design.md).

## D-078 — Bind local restoration to supported workflow identities

Decision: local conclusion-session snapshots are untrusted input and must be
bound to the workflow case IDs before restoration. Main-case phases may restore
only the main case; evidence-change phases may restore only a main-case base
and the evidence-change case. Securely stored account identifiers also require
a bounded ASCII-safe identifier shape before they can reach billing, sync, or
request boundaries. Malformed records fail closed while safe legacy records
remain readable.

This is repository hardening only; provider-backed accounts, native
secure-storage runtime, managed infrastructure, and device runtime remain
external gates.

## D-079 — Guard asynchronous sync and billing state transitions

Decision: asynchronous sync responses must commit only when the stored queue
snapshot and request generation are still current. Account/session boundaries
route queue clearing through the coordinator, and visible sync results are
bound to the current account and consent generation. Billing state must retain
the complete approved monthly/yearly catalog when access and offers arrive in
separate callbacks. Lifetime remains fail-closed.

These local guards do not replace provider, runtime, managed, staging, human,
publication, or submission evidence.

## D-080 — Keep remote response and refresh boundaries fail-closed

Decision: a mobile sync response is accepted only when its cursor and server
sequences are monotonic and its push results exactly cover the requested
commands. Cursor regressions, duplicate/missing/unrelated result IDs, and
impossible server sequences retain the local queue. When auth refresh reports
that email confirmation is no longer present, the stale secure session is
cleared before returning the confirmation-required state. Direct billing
presentations also enforce the approved `monthly`/`yearly` product allowlist.

## D-081 — Require pull changes to advance the requested cursor

Decision: a sync pull response must contain only changes with
`serverSequence > cursor` and `serverSequence <= nextCursor`, in strictly
ascending order. A change equal to the requested cursor is a replay of already
observed state and must be rejected before it reaches the queue or local
projection.

## D-082 — Bound request cancellation and input validation at trust boundaries

Decision: every mobile HTTP callback bridge must be cancellation-aware and
ignore late completion after its coroutine is cancelled. JVM and Android
connections are interrupted/disconnected, and iOS tasks are cancelled. API
write endpoints must parse malformed JSON through a bounded strict reader so
the stable versioned error envelope is preserved. Sync command IDs must be
unique before client push construction and before server storage access.
Recommendation and auth redirect state must restore a retryable, non-busy state
when cancellation interrupts the operation.

This is a repository-local safety decision. It does not imply Android/iOS host
runtime, provider, managed, staging, human, publication, or submission
verification.

## D-083 — Validate the requested sync page size

Decision: the mobile sync pull parser must validate a response against the
validated `limit` sent by the caller. A response may contain at most that many
changes, and a response with `hasMore=true` must contain exactly that many.
The maximum page size is not a substitute for the requested page size.

This keeps pagination bounded and fail-closed while allowing callers to use a
smaller page for constrained devices or future incremental sync flows.

## D-084 — Use strict API matches and recognize staging explicitly

Decision: API identifiers, digests, actions, reason codes, case-version IDs,
and request IDs require a full input match using `\A…\z`; a trailing newline
must not be accepted by a helper-level validator. The platform environment
normalizer accepts the conventional ASP.NET Core `Staging` value and maps it to
canonical `staging`. Production-only configuration guards remain unchanged.

This hardens repository boundaries without claiming that a managed staging
deployment or provider-backed staging configuration has been observed.

## D-085 — Cancel and serialize consent-bound sync work

Decision: sync orchestration must not push after its pull has been deferred or
invalidated, and the active sync job must be cancelled when account or sync
consent changes. Consent revocation reports the actual local clear result and
retains the reloaded pending count when clearing fails. Sync enablement has one
state-driven auto-sync owner so a consent change cannot launch duplicate work.

This protects the local-first and consent boundary. It does not claim that an
already transmitted provider/network request can be recalled, nor does it
replace runtime or managed-system verification.

## D-086 — Keep mobile release preparation optimized and signing-explicit

Decision: Android release candidates use R8/resource shrinking, explicit
version inputs, backup and cleartext safeguards, and a fail-closed local
signing task. A local unsigned AAB is valid only for packaging verification;
Play upload requires the owner's upload keystore. The iOS Release target uses
distribution signing with an owner-supplied `TEAM_ID` through ignored local
configuration. CI may compile the iOS Release host with signing disabled, but
that result is not an archive, device, or App Store release.

No provider key, signing file, receipt, or account credential belongs in the
repository. Native runtime, signing, store, accessibility, and owner approval
remain separate evidence gates.

## D-087 — Use native Compose semantics for conclusion choices

Decision: single-select conclusion, scope, limitation, and next-action choices
use `Modifier.selectable` with a radio role. Multi-select evidence facts use
`Modifier.toggleable` with a checkbox role. The choice card merges its label and
state for assistive technology while preserving the existing visual and
local-first behavior.

This closes a repository-level semantics defect without claiming TalkBack,
VoiceOver, or human accessibility validation. Device and assistive-technology
runtime gates remain separate.

## D-088 — Former subscription price anchor decision (superseded)

Historical decision: the initial Evidrilo subscription price anchors were
defined before the bounded AI extension. This decision is superseded by D-100;
the current price anchors are recorded only in D-100 and the active monetization
plan.

The `lifetime` package remains excluded. Store-localized prices, taxes, fees,
refunds, proceeds, and transaction outcomes remain external evidence gates.

The free core remains usable without purchase, and the app-side billing
allowlist continues to accept only `monthly` and `yearly`.

## D-089 — Separate local practice identity from server case identity

Decision: bundled cases keep a stable local identifier for offline drafts,
history, replay, and corruption recovery. Sync and analytics use an explicit
canonical server case-version identifier; an unmapped local case fails closed.
New sync commands must reference a published server case version. An existing
idempotent command replay is checked first so a previously accepted command
remains safely replayable even if the case is later retired. The API preflight
and database migration 030 enforce the same boundary.

This preserves offline independence while preventing local identifiers from
silently crossing the API/database foreign-key boundary.

## D-090 — Adopt the Evidence Graph full vision while preserving the current Verify Engine

Decision: Evidrilo's durable product model is the Evidence Graph connecting:

```text
Requirement
Evidence
Claim
Gap / Conflict
Action
Revision
Verification
```

The current deterministic `ConclusionCase` / evaluator / reducer system is the
first Verify Engine implementation and must be adapted rather than discarded.

The first bridge uses supplied/bounded content:

```text
Case → Workspace
Aim → Requirement
Observation → Evidence
Conclusion → Claim
Evaluator issue → Gap
Next action → Action
Evaluation → Verification
```

Broad arbitrary-document ingestion and provider-backed AI are not prerequisites
for this bridge.

## D-091 — Finish one clean repository baseline before resuming broad feature expansion

Decision: complete repository baseline finalization before new full-vision
breadth.

The baseline-finalization task includes:

- preserve current semantic dirty work;
- complete remaining cleanup;
- fix two-lane ownership;
- fix migration-caused/non-hermetic repository tests;
- synchronize current authority;
- resolve current public-export policy blocker;
- run the strongest practical verification;
- merge to clean `main`;
- record the exact baseline SHA.

After that SHA is recorded, broad structural migration is frozen unless a real
dependency, ownership, or runtime problem justifies reopening it.

## D-092 — Historical frontend/backend development lanes (superseded)

Decision: after baseline finalization, the active development model is:

```text
main
frontend
backend
```

Ownership:

```text
frontend
→ apps/**
→ modules/**

backend
→ platform/**
→ contracts/**
→ infra/**

main
→ integration/release/root governance
```

QA is enforced through tests, CI, and release gates rather than a permanent QA
worktree. Integration occurs through PR/CI/main rather than a permanent
integration worktree. Historical/legacy worktrees may be retained only for
preservation until safely reconciled.

**Current status:** superseded by the owner's later instruction to remove side
worktrees and use the existing `main` checkout. E204 observed one local
checkout on `main`; it did not verify remote branches. This entry remains as
decision history and is not an instruction to recreate the old lanes.

## D-093 — Use a DEMO-first managed deployment stack

Decision: the Shipaton managed DEMO target is:

```text
Render Free
→ ASP.NET Core API

Supabase Free
→ PostgreSQL
→ Auth

RevenueCat
→ mobile subscription/entitlement
→ webhook/projection

Netlify Free
→ optional landing/docs/legal
```

Google Cloud Run is not the current target because the required GCP billing
setup is unavailable. The ASP.NET backend must not be rewritten merely to fit
Netlify. Managed worker deployment is deferred until a real workload requires
it. Production remains future/not claimed.

## D-094 — Make Claim Boundary and traceability the signature UX

Decision: the core UX must expose why a result exists.

Canonical interaction surfaces include:

```text
Requirement Trace
Verify Claim / Claim Boundary
Evidence Lens
Verification Detail (with true Conflict Detail only for materially disagreeing evidence)
Evidence Delta / What Changed?
Why this action?
Contextual Paywall
```

Root navigation is:

```text
Home
Sources
Evidence
Action
Profile
```

Verify remains contextual. The old visual label `Claim Trace` is treated as
`Requirement Trace` when the screen describes requirement provenance rather
than evaluating a learner claim. Evidence coverage, evidence quality, source
state, and claim status must not be conflated.

## D-095 — Separate the locked Shipaton floor from podium-target differentiators

Decision: the locked floor is:

```text
bounded supplied case/workspace
→ Requirement projection
→ supplied evidence relationships
→ Gap / Action
→ learner Claim
→ deterministic verification / Claim Boundary
→ exactly one revision
→ before/after
→ evidence-change challenge
→ local history
→ RevenueCat monthly/yearly premium boundary
```

Podium-target differentiators are prioritized immediately after the floor is
stable:

```text
Evidence Lens
Requirement Trace
Verification Detail; true Conflict Detail is optional and must not be invented
What Changed / Evidence Delta
Why this action?
polished contextual paywall and state quality
```

Broad ingestion, OCR, arbitrary documents, multi-workspace depth,
collaboration, and unbounded AI are later scope and must not block the
competition proof. A bounded, optional AI assistant may be added only under
D-099, after the deterministic floor and RevenueCat boundary remain reliable.

## D-096 — Keep the backend as a capability-organized modular monolith

Decision: the current server shape remains the default:

```text
platform/api
platform/api.Tests
platform/worker
platform/worker.Tests
platform/database
platform/integration
```

Capability folders inside the API are acceptable. Do not require separate .NET
projects for Domain/Application/Infrastructure merely to match a textbook Clean
Architecture diagram. Extract a new project or service only when there is a
measured dependency, runtime, scaling, reliability, packaging, or ownership
reason. The worker remains a bounded separate process but is not required to be
managed-deployed for Shipaton.

## D-097 — Make action provenance and anchoring explicit

Decision: an action shown by the mobile Evidence Graph must identify whether it
was selected by the learner or suggested by a verification issue. When the
selected action depends on a supplied limitation, the projection must expose
the limitation anchor and mark the action stale when that limitation is not
selected in the active case. If no action is selected, the UI may show a
verification suggestion but must not present it as learner intent.

This keeps `Why this action?` explainable from bounded case data and prevents a
generic evaluator failure from being mistaken for an evidence conflict. The
focused projection tests do not replace native runtime or human validation.

## Resolved narration requirement — D-098

The former open choice about requiring reviewed narration for public export is
resolved by D-098 below. Narration remains optional; a valid, non-empty
effects-only catalog is sufficient. Asset integrity, provenance, license,
orphan-file, and package-size checks remain mandatory.

## D-098 — Keep narration optional for public export

Decision: public-package validation and export must not require recorded
narration. The existing fixed-copy text path remains visible, and platform
offline TTS remains a fallback only when an offline voice is available.
Existing interaction effects remain bundled under their current provenance
and license. Do not remove them or claim they are speech.

Strict validation still requires a non-empty valid asset catalog and retains
all schema, path, identifier, checksum, byte-length, format, license,
orphan-file, and size-budget checks. The explicit `--allow-empty` option remains
limited to local implementation/test contexts. Any future narration must be
reviewed for pronunciation, clarity, factual wording, redistribution rights,
and device playback before inclusion.

Rationale: narration is not required for the current Shipaton submission slice,
no reviewed production narration is bundled, and visible text plus optional
offline TTS already preserve access to fixed copy. Public export should not be
blocked on an optional asset.

## D-099 — Add bounded AI assistance with subscription credits

Decision: add an optional, server-mediated AI assistance extension without
changing the free local learning loop or the deterministic Verify Engine.

The first supported purposes are:

- explaining an already-produced feedback item;
- generating a bounded reflection question; and
- offering a meaning-preserving language alternative.

The assistant must not grade academic truth, invent evidence or sources, accept
requirements, choose entitlement state, or replace learner-authored reasoning.
The API keeps the existing `IAiProvider` abstraction. The first provider
adapter is planned as a server-side OpenAI integration with structured output;
provider configuration remains replaceable, while a second provider is not an
initial Shipaton fallback. The safe default remains `DisabledAiProvider`.

Credit policy:

- a verified free account receives 10 AI credits once after explicit consent;
- an active `evidrilo_pro` entitlement receives 100 credits per entitlement
  month;
- yearly subscriptions receive the same 100-credit monthly grant during each
  active annual month;
- unused credits do not roll over; lifetime packages and paid top-ups are not
  part of the initial plan;
- one accepted standard assist costs one credit;
- timeout, cancellation, provider failure, malformed output, policy rejection,
  and unavailable-provider results release the reservation and do not charge;
- the server ledger is authoritative, idempotent, account-isolated, and linked
  to verified RevenueCat entitlement periods; the mobile client cannot grant or
  mutate credits.

AI requires explicit context selection and opt-in. The gateway redacts and
minimizes input, avoids raw prompt/response logs, validates a bounded response
schema, applies rate/cost limits, and falls back to deterministic feedback when
the provider is unavailable. Provider data-use and retention terms require
owner review before activation.

This is an in-flow assistant, not a generic chat product. The server builds the
context from the active case, requirement, selected evidence, limitation,
deterministic rule, claim boundary, and current action/delta. The provider may
explain that context, ask one bounded reflection question, or suggest wording;
it may not introduce a new anchor or write claim status. The learner must make
the edit manually, after which the deterministic evaluator and Evidence Delta
remain the only sources of truth.

Rationale: ten one-time free credits allow a meaningful trial without creating
an anonymous abuse surface, while one hundred monthly credits make the premium
extension legible and predictable. The policy creates a measurable RevenueCat
value without paywalling evidence provenance or evaluator trust.

Consequences: a PostgreSQL credit ledger, grant reconciliation, consent UI,
provider matrix, privacy disclosure, and adversarial tests are required before
AI can be described as live. AI is optional for Next Gen and must be omitted
from submission claims if those gates are not verified.

## D-100 — Raise the monthly/yearly planning anchors for the bounded AI extension

Decision: the current Evidrilo pricing anchors are USD 1.99 per month for
`monthly` and USD 19.99 per year for `yearly`. The `lifetime` package remains
excluded. The increased anchors reflect the approved optional AI allowance and
the continuing value of reviewed premium evidence cases; they do not paywall
deterministic evaluator truth or the complete free learning loop.

The values are product-planning anchors only. The implementation must display
the localized price returned by the configured RevenueCat offering and must not
hardcode USD values into the paywall. Taxes, regional pricing, store fees,
refunds, net proceeds, introductory offers, and actual transaction outcomes
remain owner-authorized provider evidence. No RevenueCat catalog, product,
price, or offering is changed by this decision alone.

The premium value proposition must remain legible:

- the free core contains one complete evidence-to-claim workflow;
- `evidrilo_pro` adds two reviewed evidence cases;
- an active entitlement grants 100 AI credits per entitlement month, including
  monthly grants during an active yearly term;
- AI credits are optional assistance, not a paid truth score or a premium
  evaluator standard; and
- the paywall must explain the case and AI value without promising grades,
  scientific truth, or guaranteed outcomes.

RevenueCat acceptance remains provider-gated: offering load, monthly/yearly
selection, purchase, entitlement activation, restore, pending/cancel/failure,
relaunch, and supported revocation/expiry must be observed before the new
anchors or the AI allowance are described as live. The pricing decision changes
the plan; it does not create transaction evidence.

## D-101 — Lock the current submission boundary and defer full online semantics

Decision: the Next Gen candidate remains local-first and bounded. The complete
free workflow is authoritative on-device, while the repository API remains an
optional platform foundation and must not be described as a complete online
workspace.

For the current submission:

- the two premium cases remain bundled in the mobile application and are not
  served through the API case catalogue;
- RevenueCat `CustomerInfo` is the client access authority for the bundled
  premium cases, while the API does not claim server-side premium enforcement;
- sync remains consented metadata and snapshot-digest transport, not full draft
  or workspace restoration;
- client analytics remain telemetry and cannot be used as teacher grades or
  authoritative learning outcomes;
- organization roles remain the product access model; infrastructure operators
  are not presented as `SystemAdmin` or `SuperAdmin` application actors;
- `/health/ready` remains a local diagnostic contract for the current scope;
  hosted readiness semantics belong to a future managed release boundary;
- server-owned account export is available only to a verified account and
  explicitly marks local drafts as not on the server; managed retention and
  Supabase Auth identity deletion remain separate provider/owner operations.

Live AI is a gated premium extension, not a submission dependency. It may appear
in the candidate only if the D-099 credit ledger, grounded context, provider,
privacy, runtime, and provider-evidence gates all pass. Otherwise the safe
provider-disabled state remains in the build and no live-AI or live-credit claim
appears in the video, Devpost copy, or public README.

This decision removes scope ambiguity without discarding the online platform
roadmap. Remote premium content, full workspace sync, server-owned assessment,
teacher workflows, managed readiness, and live AI remain post-submission or
separately gated capabilities.

## D-102 — Lock first launch, optional account, Google auth, and notification behavior

Decision: Evidrilo always starts with a short, dismissible guide and then opens
the complete local workflow. Account access and notifications are additive; they
must never become a prerequisite for the free offline experience.

The frozen behavior is:

- First launch shows a concise guide explaining the evidence-to-claim workflow,
  local storage, the free case, and the next action. `Try the free case` and
  `Skip introduction` both enter the same local Home flow. Completion is stored
  locally and the guide can be replayed from Settings.
- The learner can use the bundled case offline immediately after the guide. No
  login, network, API, AI provider, billing key, or notification permission is
  required to start, continue, revise, or review local history.
- After the learner reaches a meaningful value point (for example, opening the
  first feedback result or completing the first comparison), Evidrilo may show
  one dismissible account-benefit prompt. It must not appear as a launch wall or
  interrupt an active draft. The prompt may describe only verified benefits:
  account recovery, entitlement identity, consented progress metadata sync, and
  account-bound AI eligibility when live AI is actually enabled. It must not
  promise cloud restoration of learner-authored drafts while sync remains
  metadata-only.
- Email sign-up, email sign-in, password recovery, and Google sign-in use the
  existing provider-neutral account boundary. Google uses the configured
  Supabase OAuth authorization-code flow with PKCE, state/callback validation,
  secure session storage, verified provider identity, and a safe return to the
  local workflow when the provider is unavailable or cancelled.
- Notifications are local, optional reminders in the current product scope.
  They do not require an account, network, or remote push service. The initial
  categories are `Continue an unfinished case` and learner-enabled `Review a
  completed case`; both categories are off by default and promotional
  notifications are not enabled.
- Notification permission is requested only after an explicit learner action or
  from Settings, never on the first launch before value is shown. Settings must
  provide a master enable/disable control, category controls, scheduled-reminder
  cancellation, a learner-selected daily/weekly cadence and local time,
  next-schedule visibility, permission-denied guidance, and an OS-settings
  recovery path.
  Turning notifications off cancels future Evidrilo schedules and does not
  delete local learning data.
- Remote push, device-token storage, and server campaigns are deferred until a
  managed online notification boundary, consent, retention, and operational
  ownership exist.

This is a product contract, not proof that provider configuration, Google
redirects, OS notification permissions, or runtime behavior have been verified.
The implementation may fix bugs and add tests within this contract; changing
the launch order, account requirement, notification model, or claimed account
benefits requires an explicit new decision.
