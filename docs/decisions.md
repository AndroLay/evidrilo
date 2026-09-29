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

Task-specific audio and feature-gate design notes are local-only historical
artifacts under the ignored `docs/superpowers/` directory. The settled public
policy is recorded here; do not treat an old design note as current scope.

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

Historical policy record: D-126 supersedes this decision's initial grant
amounts, D-127 supersedes its fixed per-operation pricing, and D-130 replaces
the no-rollover rule. Use those later decisions for current credit policy.

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

- a verified Free account receives the one-time grant defined by D-126 after
  explicit consent;
- an active `evidrilo_pro` entitlement receives the monthly grant defined by
  D-126, including yearly subscriptions;
- unused credits did not roll over under the policy recorded at that time;
  D-130 later supersedes this rule. Lifetime packages and paid top-ups are not
  part of the initial plan;
- successful AI requests are priced from verified provider-token usage under
  D-127; there is no fixed per-answer or per-operation charge;
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

Rationale: a finite Free grant allows a meaningful trial without creating an
anonymous abuse surface, while a recurring Pro grant makes the optional
extension legible and predictable. Current grant amounts are defined by D-126
and usage pricing by D-127. The policy creates measurable RevenueCat value
without paywalling evidence provenance or evaluator trust.

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
- an active entitlement receives the monthly AI grant defined by D-126,
  including monthly grants during an active yearly term;
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

## D-103 — Use RevenueCat Test Store for the Next Gen submission

Decision: the current Evidrilo submission targets the Shipaton Next Gen Award.
For this category, a RevenueCat Test Store/sandbox purchase is sufficient to
demonstrate the required monetization flow. A real App Store/Google Play
transaction, production payment, production revenue, store listing, or paid
developer account is not required for Next Gen.

There is no published automatic or numeric score deduction for using the Test
Store. The Next Gen rules publish qualitative criteria rather than numeric
weights; the relevant question is whether RevenueCat is used thoughtfully in a
working flow. The risk is a weak or simulated implementation, not the sandbox
environment itself.

The build must still integrate the official RevenueCat SDK and demonstrate an
actual entitlement-backed purchase path. The intended evidence sequence is:

```text
RevenueCat offering
  → monthly/yearly Test Store purchase
  → CustomerInfo with evidrilo_pro
  → premium evidence case and subscription AI-credit state unlock
```

The video and submission copy must label this as sandbox/Test Store evidence
and must not describe it as real revenue. If the project later targets a
non-Next-Gen category or a store release, that is a separate release gate with
its own store/provider requirements. This decision is based on the [official
Next Gen rules](https://revenuecat-shipaton-2026.devpost.com/rules), the [Next
Gen page](https://www.shipaton.com/next-gen), and the [RevenueCat manager's
Test Store clarification](https://revenuecat-shipaton-2026.devpost.com/forum_topics/44695-next-gen-eligibility-is-a-test-store-only-purchase-sufficient).

## D-104 — Make active students the primary Evidrilo audience

Decision: Evidrilo's primary product user is an active student working
independently on an evidence-heavy academic task. The first persona reads a
supplied bounded case, traces requirements to evidence, writes a claim in their
own words, reviews fact-anchored feedback, revises, and responds to an evidence
change. The Shipaton Next Gen eligibility context includes students aged 13 or
older with qualifying academic status, but the product must not claim validated
coverage for every student age, institution, or academic domain.

The synthetic tablet-dissolution case remains the first proof vehicle and does
not make the product STEM-only. Teacher, mentor, reviewer, administrator, and
support roles are secondary or future actors. Internal domain references may
continue to use `learner` as the role name for the student; public product and
submission copy should say **student** when describing the target audience.

Execution consequence: product narratives, onboarding copy, submission copy,
acceptance criteria, and future validation protocols must make the student
problem and student workflow explicit. Do not build or claim teacher dashboards,
professional workflows, broad subject coverage, or learning efficacy as part of
the current audience without a separate decision and evidence.

## D-105 — Use branded loading only at bootstrap and network boundaries

Decision: Evidrilo uses one restrained, logo-led loading surface in two bounded
contexts:

- On app launch, the screen covers the shell only while local state and optional
  session restoration resolve. It exits into the guide or Home even when the
  account provider is offline or unavailable; no artificial minimum duration is
  added.
- A provider or network-only destination may use the same full-screen loading
  surface while its real request is in flight. It must provide a safe Back action
  when the destination was opened from an existing page and must explain that
  local work remains safe.

Home, Sources, Evidence, Workspace, History, and other offline-capable surfaces
must not wait for network. They use bundled/local state and an explicit
unavailable or degraded message when an optional service cannot be reached.
Account, AI, sync, and similar actions should prefer an inline busy state when
preserving the current form or context is more useful than replacing the page.

The loading surface exposes a semantic loading label and truthful context. It
does not show fake progress, claim a successful provider operation, or create an
indefinite dependency on an external service. Runtime/provider evidence remains
separate from this UI contract.

## D-106 — Use an Evidence-Grounded AI Loop for project assistance

Decision: Evidrilo's AI is an optional, contextual assistant inside the active
student workspace. It must follow the Evidence-Grounded AI Loop rather than
becoming a generic chat product:

```text
workspace/case
  → requirement + Evidence Lens + selected evidence/limitations
  → claim + scope + limitation + next action
  → deterministic feedback/gap/action/delta
  → context preview + explicit consent + visible credit state
  → server-rebuilt case context
  → bounded conversation for this case/revision
  → grounded explanation/question/draft proposal
  → learner reviews, edits, applies, or dismisses
  → existing draft reducer only after explicit apply
  → deterministic re-check
  → Evidence Delta / History
```

The D-106 case-AI context identity is the published case version,
the current draft/revision fingerprint, and the deterministic evaluation
fingerprint. This tuple is the synchronization boundary for case AI, separate
from D-119's project identity. Every conversation turn must rebuild and
validate the tuple server-side. A changed case, draft, selected evidence,
limitation, or evaluation invalidates the old context.

The assistant may explain deterministic feedback, explain a requirement-to-
evidence relationship, ask a grounded reflection question, explain a gap or
Evidence Delta, and propose meaning-preserving wording for learner-authored
claim text, scope, limitation, or next-action notes. A proposal must contain
before/after values and approved anchor IDs. The learner must explicitly review
and apply it through the existing draft reducer; deterministic verification then
runs again and remains the only source of truth.

AI must not add evidence, invent sources, change requirements, alter evaluator
status, mark actions complete, change subscriptions or credits, or edit
repository source code. The provider output must be typed and grounded. Missing
or stale context, unknown anchors, fabricated facts, unsupported operations,
malformed output, provider failure, or policy failure must return a safe refusal
or unavailable state and leave the free local workflow intact. The system must
not promise that a language model can never make a mistake; it must ensure that
an ungrounded answer cannot become authoritative Evidrilo state.

Conversation is short and contextual (target maximum five turns per active
context), session-local by default, clearable by the learner, and not evidence
or canonical History. There is no global chat destination, unlimited chat,
automatic draft mutation, provider-generated evidence, or AI-generated truth
score. The current v1 assist envelope remains unchanged until a future,
contract-tested version adds conversation and proposal fields. The provider
remains disabled until privacy, cost, provider, and runtime gates are complete.

## D-107 — Use Duolingo and Brilliant as interaction references, not asset sources

Decision: Evidrilo's UI and motion direction may learn from two reference
products while remaining an original Evidrilo experience:

- **Duolingo** is the reference for approachable interaction energy, immediate
  feedback, tactile primary actions, progress moments, onboarding rhythm, and
  restrained celebration motion.
- **Brilliant** is the reference for structured student learning, step-by-step
  explanations, interactive checkpoints, clear result feedback, and a calm
  relationship between content and progress. Its guided learning paths and
  practice checkpoints are interaction references, not product claims about
  Evidrilo.

The current private target in
`/home/andro/dev/projects/Devpost/RevenueCat Shipaton 2026/internal/design/`
remains the only visual authority for Evidrilo's brand, composition, colors,
navigation, and page hierarchy. Duolingo and Brilliant influence interaction
quality only; they do not replace the target PNGs or the Evidrilo visual
contract.

The implementation boundary is:

```text
Evidrilo target design
  + Duolingo interaction and motion principles
  + Brilliant learning clarity and feedback principles
  -> original Kotlin/Compose UI and motion
```

Evidrilo must not copy or redistribute Duolingo or Brilliant logos, characters,
illustrations, screenshots, proprietary UI artwork, internal animations, app
assets, or CDN assets. Any third-party asset that is actually shipped must have
an explicit compatible license, provenance record, checksum, and redistribution
approval. Reference screenshots or downloaded brand materials, if retained for
private study, stay outside the application and public export.

Motion must remain purposeful and accessible: press feedback, selection,
progress, success, loading, and route transitions may animate; evaluator truth,
evidence support, and claim status must never be communicated by motion or
color alone. Reduced-motion behavior, 48 dp touch targets, long text, offline
states, and native platform behavior remain mandatory.

Initial application mapping:

- Home and onboarding combine Brilliant's clarity with Duolingo-like progress
  feedback.
- Sources and Evidence prioritize Brilliant-style information hierarchy and
  interactive checkpoints.
- Verify Claim, Claim Boundary, Action, and What Changed use brief feedback
  motion while keeping deterministic semantics authoritative.
- Profile, Account, Settings, and Premium use the same friendly interaction
  language without introducing gamified pressure or a login wall.

This decision authorizes visual and motion refinement only. It does not
authorize arbitrary ingestion, generic chat, invented evidence, mascot-based
branding, or a new navigation architecture.

## D-108 — Adopt a student academic learning-and-project workspace as the long-term direction

Decision: beyond the current Shipaton slice, Evidrilo's strategic destination is
a student-first academic learning and project workspace. It should connect
short, structured learning with applying that learning to a student-owned
academic task or research project. The distinctive product promise is to make
the path from task/question and method, through sources or data, to claims,
criteria, feedback, and revision inspectable and explainable.

This is a product direction, not a claim that the current app already supports
courses, student-authored research projects, general-purpose research methods,
open content packs, or a universal evaluator. The current Shipaton release
remains the bounded M0 case and deterministic workflow recorded by D-095 and
the [M0 contract](product/m0-product-contract.md). D-108 does not authorize
feature expansion before submission, change the current UI target, or alter the
approved RevenueCat products/prices and AI-credit policy in D-099/D-100.

The post-submission order is evidence-gated:

1. Discover one repeated, real academic task with an explicitly selected
   student segment; obtain permission and consent before recruiting people or
   handling real assignments/data.
2. Build one manually entered, end-to-end academic project workflow around the
   selected task and method. A hypothesis is optional and method-dependent, not
   a universal required field.
3. Validate each formative evaluator against versioned, reviewed examples and
   relevant domain reviewers. Separate draft completeness, evidence support,
   rubric evidence, and real-world truth; explain anchors and abstain when the
   inputs exceed the tested contract. Do not issue automatic grades or
   institutional decisions. After a method-specific evaluator and explicit
   thresholds pass validation, Evidrilo may summarize its visible criterion
   states with a formative outcome such as `CRITERIA_MET`, `NEEDS_REVISION`,
   `INCOMPLETE`, `NEEDS_REVIEW`, or `CANNOT_ASSESS`. This reports only whether
   the tested Evidrilo criteria were met; it is not a course grade, official
   assignment pass, institutional decision, opaque numeric score, or claim of
   real-world truth. The outcome must expose its rubric/evaluator version,
   criteria, evidence anchors, reasons, and unresolved conditions, and must
   abstain from an overall pass when its criteria or evidence fall outside the
   validated contract.
4. Only after those contracts are trustworthy, develop reviewed content
   authoring and small learning pathways that prepare students to complete the
   applied project. Any portable/open content format and content license need
   their own explicit decision.
5. Consider optional class/teacher sharing, account/cloud continuity, AI
   assistance, and wider domains only when validated student needs, privacy,
   governance, and operating costs justify them. Student work remains private
   by default.

AI remains a grounded, optional assistant and never becomes the evidence
authority, evaluator, source of facts, or autonomous author. No claim of
learning efficacy, research quality, broad domain coverage, or unique market
position is permitted without evidence. Products including Gurwi, Duolingo,
Brilliant, Coursera, and research-workspace/evaluator tools are references or
adjacent products—not templates to copy and not proof that Evidrilo's proposed
integration is differentiated.

The recommended learner journey in the [post-Shipaton roadmap](roadmap.md)
is a UX hypothesis to validate, not a separately settled screen contract or
authorization to expand the current release.

Execution consequence: current work continues under the Next Gen delivery plan.
After the competition, use the strategic path in [the roadmap](roadmap.md).
The detailed private research note is kept under `internal/research/next-gen/`;
it is not part of the public repository. Product-market fit, the first
task/method, evaluator acceptance thresholds, licensing, and feature timing
remain validation questions, not additional owner approvals needed to accept
this direction.

Reference material: [Duolingo design resources](https://design.duolingo.com/resources),
[Duolingo imagery guidance](https://design.duolingo.com/identity/imagery), and
[Brilliant product features](https://brilliant.org/help/features/).

## D-109 — Pull forward a local-only student project API foundation

Decision: under the owner's explicit 24 September 2026 instruction to finish
locally buildable platform-backend work before deployment/staging, authorize
one backend-only foundation for a manually entered, student-owned academic
project. This is a deliberate sequencing exception to D-108's post-competition
product validation order; it does not change the current Shipaton/M0 mobile
scope or claim that the larger student workflow has been validated.

The bounded foundation may create, list, read, save, version, export, and delete
account-owned project records; retain append-only revisions; link entered
criteria with entered sources/data/observations; and return a structural
presence report. The optional hypothesis is student-supplied and method text
is not interpreted. The report must remain `not_assessed` and must never claim
academic merit, evidence quality, real-world truth, a grade, or a pass/fail
result. Project records require verified account identity, per-account
authorization, idempotent mutations, optimistic concurrency, bounded inputs,
and deletion/export coverage.

This authorization covers local code and synthetic local verification only.
It does not authorize connecting the mobile UI, handling real student work,
recruiting or contacting reviewers, activating live AI/RevenueCat providers,
changing credentials, or deploying/activating staging. The M0 evaluator stays
the sole truth authority; AI and RevenueCat provider activation and managed
operations retain their separate gates. Human/domain validation and consent
are required before the project structure report can evolve into a
method-specific formative evaluator or before real student data is collected.

## D-110 — Link Google to the active account without switching identities

Decision: a signed-in student may explicitly add Google as another sign-in
identity for the currently active Evidrilo account. The signed-out
`Continue with Google` action remains sign-in/sign-up; it is not relabelled as
account linking. Linking uses Supabase's authenticated identity-link flow and
PKCE, then verifies that the returned session has the same account ID and that
Google appears in that account's provider identities before persisting it.

The flow must never silently merge accounts or replace the active account. If
the Google identity is already associated with an account, stop with a generic
conflict message and do not disclose the other account. Cancellation, provider
failure, or an unverifiable callback preserves the existing Evidrilo session.
The account screen exposes linked/unlinked status and a separate explicit
`Link Google to this account` action. Unlinking is not included in this
decision.

Supabase manual identity linking must be enabled for the project. A disabled
setting is reported without discarding the current session. This decision
authorizes the local implementation and synthetic tests only; it does not
authorize changing Supabase configuration or prove a live provider/runtime
link.

## D-111 — Frame Evidrilo around its student academic-work target

Decision: top-level product and onboarding copy must identify Evidrilo's target
as a student-first academic learning and project workspace for assignments and
research. The current bounded M0 experience is introduced as a guided example
that demonstrates evidence-grounded reasoning, not as the product boundary.

Copy must remain honest about implementation: the current mobile build does not
yet provide student-authored project workspaces, broad research-method support,
or course/class workflows. This supersedes D-102's exact onboarding wording and
labels such as `one bounded case at a time` and `Try the free case`; it does not
change the M0 case contract, current free/premium policy, offline entry, skip
behavior, or the rule that no account is required to begin.

## D-112 — Set five selectable academic project-template catalog families

Decision: the long-term student-facing project-template catalog has five
initial top-level families:

1. Experimental and laboratory work.
2. Observational and survey studies.
3. Literature reviews.
4. Qualitative interviews and field studies.
5. Design and engineering projects.

Students will be able to choose the family/template that fits their assignment
or project. Starting from an assignment or question remains a general entry
path, not a sixth method category. These families organize project starters;
they are not a course catalog, five ready-to-use templates, or five validated
evaluators. The catalog must not merge materially different methods or make a
hypothesis mandatory across all projects.

Each concrete template must have a versioned scope, intended output, input
requirements, method-specific limitations, source/data provenance rules,
accessibility expectations, and reviewed examples. A category is available to
students only when its actual template is ready and reviewed; do not expose
empty selectable placeholders or silently classify an ambiguous assignment.
Evaluator feedback remains criterion-based and formative, with anchors and an
abstention path; no general academic pass grade is authorized.

D-112 adopts the catalog taxonomy and eventual student choice only. It does not
authorize implementing all five templates now or change the current M0 release
scope. Follow D-108's post-release sequence: validate a recurring student task,
select the first method from evidence, validate its evaluator with relevant
reviewers and students, then expand one method at a time. D-109's local backend
foundation remains storage/report infrastructure, not a mobile catalog or
academic evaluator.

## D-113 — Start the five-family project-template catalog work now

Decision: begin local product development of the student project-template
catalog now, using the five families already selected in D-112. The catalog is
an active product workstream rather than work deferred until after the current
competition slice. This advances timing only; it does not claim that the mobile
catalog, any concrete template, or any method-specific evaluator is already
implemented.

Start with the shared catalog/template contract and a coherent browse, inspect,
and choose path. Develop concrete templates incrementally. A family may be
shown as an actionable choice only when it has a scoped, versioned template
with clear inputs, intended outputs, method-specific limits, provenance rules,
accessibility expectations, and reviewed examples. Do not show empty choices,
silently classify an ambiguous assignment, or imply every family is ready at
once. The first method-specific template remains selected by recurring-task
evidence and appropriate student/domain review; this decision does not authorize
participant contact or use of real student data.

Keep catalog work separate from the evidenced M0/Next Gen submission slice
unless that slice is explicitly changed and re-verified. Work may proceed
locally with synthetic or manually entered examples. Evaluators remain
criterion-based, formative, anchored, and able to abstain; no universal grade,
pass score, or automated academic decision is authorized. D-113 supersedes
D-112 only on when catalog development may start; D-112's five-family taxonomy
and quality gates remain in force.

## D-114 — Set Free and Pro student-project limits

Decision: the student-project product uses one concurrent active-project limit
for each access tier:

- Free may keep up to **5 active projects**.
- An active `evidrilo_pro` entitlement may keep up to **50 active projects**;
  monthly and yearly packages receive the same limit.

The cap counts student-owned projects, whether started from a blank project or
from a reviewed catalog template. Browsing a catalog or previewing a template
does not consume a slot. Completed or archived projects remain accessible and
do not consume an active slot; reactivating one requires an available slot.
Project quantity does not change the evaluator, feedback standard, or access to
the complete free reasoning loop.

If Pro access expires while more than five projects are active, preserve every
project. The student may continue to open, edit, export, or delete existing
work, but cannot create or reactivate projects until the active count is within
the Free limit. Do not delete, hide, or make academic work read-only as a
subscription-expiry penalty.

These limits are product policy, not implementation evidence. Until the mobile
student-project workflow is connected to account-backed storage, the limit is
device-local and must not be described as an account-wide quota, cloud backup,
or cross-device sync. Account-wide enforcement requires verified entitlement
state and atomic per-account quota enforcement on the server, with concurrency
and downgrade-preservation tests. D-109's local API foundation alone does not
satisfy that boundary.

At adoption, D-114 did not change the then-current AI grant or provider gate.
D-126 later set the current grant targets, and D-127 set token-based pricing;
neither project limits nor AI availability may be presented as live until
their respective client/provider behavior is implemented and verified.

## D-115 — Define catalog-guided AI project creation and in-project assistance

**Timing update (D-116, 27 September 2026):** D-116 supersedes the future-only
timing below. Catalog-guided project AI is an active local pre-deadline
development workstream; local implementation may begin now. D-115's product
behavior and safety boundaries remain in force, and the flow must not be
described as working until D-116's acceptance gates pass.

Decision: the five-family catalog is the structured starting point for
student-owned projects, and optional AI assistance is intended to participate
inside that project workflow—not as a separate, context-free chatbot. Once a
versioned catalog template is reviewed and selectable, a student may ask AI to
create an editable project draft from the chosen template and the student's
own assignment/question, goals, and constraints. AI may scaffold the project
record and propose initial values for fields the template supports. The saved
project remains student-owned; it is created only after the student reviews
and confirms the draft. The saved project uses one D-114 project slot.

The student confirms the catalog family/template. AI may explain alternatives
or recommend a fit, but must not silently classify an ambiguous assignment or
claim that an unreviewed/unavailable template is supported. Within the chosen
project, AI may offer contextual help at meaningful stages—framing the task,
clarifying fields, planning next steps, organizing student-provided sources or
data, explaining a bounded evaluator finding, and proposing revisions. Every
suggestion remains review/edit/apply/dismiss. The project workflow must also
support manual creation and progress without AI, and keep the locally supported
work usable offline.

This permission covers project scaffolding and bounded in-flow assistance,
not a finished research proposal, paper, analysis, findings, citations, or
references. AI must not fabricate or silently add sources, observations, data,
quotes, results, or method claims; the student must supply/verify them.
Deterministic, method-validated evaluator rules remain separate and
authoritative; AI cannot set grades, `CRITERIA_MET`, evidence support, or
truth-status. Ambiguity, missing provenance, unsupported methods, and provider
failure must produce a clarification, abstention, or unavailable state—not
invented certainty.

D-115 does not claim that project generation or project-context mobile AI
already exists, and it does not widen the currently verified M0/Next Gen
capability. D-106 remains the active bounded AI contract for the current case
workflow. D-116 supersedes D-115's future-only timing and permits local
implementation now; a versioned project-AI contract, reviewed selectable
templates, explicit data-use consent, privacy/retention controls, provider and
cost limits, transparent credit treatment, account/quota authorization,
adversarial tests, accessibility, and appropriate student/domain review remain
required before the end-to-end flow can be described as working. Offline project
work must remain usable when AI is unavailable. D-116 does not change D-108's
evidence, privacy, evaluator, or staging gates.

## D-116 — Start catalog-guided AI project development before the Next Gen deadline

Decision: D-115 is no longer deferred solely to a later product phase. Begin
local implementation now as an active pre-deadline workstream, targeting the
official RevenueCat Shipaton 2026 submission deadline of **30 September 2026,
11:45pm PDT** (official rules checked 27 September 2026). D-116 changes the
timing of D-115, not its student-control, provenance, evaluator, or privacy
boundaries.

The minimum intended vertical slice is one versioned, genuinely reviewed and
selectable catalog template; a student chooses and confirms it and supplies
their own assignment/question and constraints; AI returns a typed, editable
project scaffold; the student reviews and confirms before it is saved; and
contextual AI help is available from within that project for a bounded next
step or revision. This is project work, not a generic detached chat. The
student may also create and continue the project manually, including offline
where supported.

The slice is acceptable to describe as working only after the actual
provider-backed flow is observed on the candidate app/API, not when only a fake
provider or mock response passes tests. Required evidence includes: a reviewed
selectable template; validated request/output contracts limited to its fields;
explicit context/data-use consent; visible credit/cost treatment; student
review/edit/confirm and later apply/dismiss behavior; saved project identity
and D-114 quota enforcement; bounded provider failure/offline recovery; and
Android end-to-end tests showing the saved project can be reopened and AI help
remains contextual. No AI output becomes evidence or evaluator truth.

The project must not be represented as complete or shown as live in submission
materials while any required gate is open. In particular, the catalog currently
has no selectable template and the AI provider remains disabled; privacy/cost
approval, provider authorization, human/domain review, and quota integration
are dependencies, not items to bypass with synthetic success. If these gates
cannot be closed before freeze, record the work as incomplete and keep it out
of live-capability claims. This workstream does not authorize spending,
provider/dashboard changes, deployment, publication, participant contact, or a
submission; those remain separately owner-controlled.

D-115 remains the source for allowed AI behavior and restrictions: no finished
academic deliverable, invented/verified sources or data, automatic project
mutation, grade, or AI evaluator. D-126 governs grant targets and D-127 governs
token-based pricing; there is no fixed project-scaffold price. D-114 still
governs project slots. The local-first M0 evaluator and manual workflow remain
available independently of AI.

For clarity, D-116 supersedes D-115 only where D-115 said its gates were
required before implementation could begin. Local implementation may start
now; those same gates remain mandatory before describing the end-to-end AI
project flow as working, including in a submission video or project narrative.

## D-117 — Adopt Project Workspace Final Spec v1 and start Rilis 1 locally

Decision: adopt **Evidrilo Project Workspace — Spesifikasi Produk Final v1.0**
(27 September 2026) as the current detailed product contract for the project
workspace. It supersedes the earlier workspace concept where details differ.
This is a product and implementation decision, not evidence that its screens,
formats, quota, evaluator, provider, or cloud flows are already implemented.

The first project pilot is a directed literature synthesis for Indonesian
undergraduate students. That is an initial product hypothesis, not validated
market demand. Rilis 1 supports one local, manual project journey from an
assignment brief and research question through student-entered sources,
extraction/evidence matrix, synthesis, student-authored claim and limitations,
structure-only checks, revision, and export. It is not a systematic review or
meta-analysis; source discovery, web/database search, automatic DOI
verification, general academic grading, and universal truth evaluation are
not part of this release.

A project may start blank and locally without an account or reviewed template.
Catalog guidance is optional; only a genuinely published and reviewed
versioned template may be selected as a method template. The M0 tablet case
remains a separate learning case and must never be prefilled as the student's
research project. No source, data, result, finding, or claim is fabricated to
make a new project look complete.

Clarifications to D-114: the 5 Free / 50 Pro limit applies per installation in
Rilis 1. Pro's higher cap is available only when a real RevenueCat
`evidrilo_pro` entitlement is verified; unknown/unavailable entitlement uses
the Free cap. Active projects consume slots; completed, archived, and trashed
projects do not. Trash retention is 30 days. Downgrade preserves every
project and its ability to open, edit, export, or delete; it blocks only new
projects or reactivation that would exceed the Free active cap. This does not
promise account-wide quota or cross-device continuity; those belong to Rilis 2.

Clarifications to D-099/D-115/D-116 recorded the then-current fixed project-AI
prices and grants; D-126 later superseded those grants and D-127 superseded the
fixed prices. The AI provider remains server-mediated and disabled by default;
owner approval of provider terms, retention, privacy, consent, and cost plus
provider-backed verification are still required before activation or a
live-capability claim. Project AI does not replace the manual/offline project
workflow or deterministic, bounded checks.

Release order is Rilis 1 local manual workflow and portable outputs; Rilis 1.1
provider-gated AI; Rilis 2 opt-in cloud continuity with atomic account-wide
quota, revision/conflict handling, and deletion propagation. Other four
method families become selectable only after method-specific review. The
decision does not authorize provider activation/spend, participant contact,
deployment, publishing, or submission.

## D-118 — Make Get Started optional and require sign-in for Free functionality

Status: D-120 later supersedes the blanket sign-in requirement for local
Project Workspace use and anonymous read-only catalog browsing. D-118 remains
in force for account-bound learning and other account-bound capabilities.

D-121 later clarifies entry timing: Home is the first-launch destination and
Get Started is an explicitly opened, optional, replayable guide rather than a
mandatory first screen. Successful authentication restores the requested
destination and does not force the full-screen tour; any guide offer must remain
non-blocking. This clarification changes presentation timing, not D-118's
account-bound M0 learning gate or the tour's synthetic/no-project boundary.

Decision: the product offers a short, optional guided Get Started tour, while
all actual Free product functionality requires an authenticated account. This
supersedes D-102's account-optional access rule and D-117's permission to enter
and use the real Free project workspace without an account. It changes product
policy; it does not claim that the current app implementation already enforces
the new gate.

Implementation note (E230, 27 September 2026): the shared mobile source now
enforces the signed-out route/action boundary and provides the isolated
three-state Get Started tour, with local Kotlin/JVM and Android package
verification recorded in the private evidence index. Provider sign-in, device
accessibility/runtime, and iOS runtime remain unverified; see the current
Source of Truth rather than treating this adoption note as runtime proof.

The tour targets completion in about three minutes and uses a clearly isolated
synthetic demo. It guides the student through choosing a reviewed catalog
template when one is available (otherwise the screen must say that the example
is a demo, not a reviewed selectable template), confirming a brief/question,
inspecting and selecting a source, linking a finding to its evidence/source,
trying one short evidence-change case, and reviewing a bounded output showing
the source-to-evidence-to-finding-to-claim relationship, limitations, and next
action. The tour does not create or prefill a real student project, assign an
academic grade, or claim that a conclusion is universally true.

The target pacing is: welcome (15 seconds); choose a reviewed template or
clearly labeled demo (20); confirm the brief/question (20); inspect/select a
source (30); link a finding (30); answer one short case question (25); inspect
the bounded output (30); choose the next step (10). The three-minute target is
not a countdown. Use a sequential spotlight with one focus and one small action
per step, a step indicator, Back/Next/Skip controls, and accessible focus and
spoken announcements. Preloaded synthetic data avoids lengthy typing. Keep the
demo's case response and project-shaped sample separate from real student data.

The entry and authentication behavior is:

- A first-time student may start the demo, choose `Continue with Google` (or
  another supported sign-in method), or skip Get Started. The demo can be
  previewed while signed out; creating, editing, saving, or using actual Free
  product functionality is gated on successful sign-in. Skipping the tour does
  not bypass authentication.
- After successful Google sign-in/sign-up, offer the optional tour to students
  who have not completed it, including returning account holders. They may
  skip and enter the authenticated Free experience immediately; completing the
  tour is never an access prerequisite. If they explicitly skipped earlier in
  the same onboarding attempt, do not immediately prompt them again; the tour
  remains available from Help/Profile and may be offered on a later sign-in.
- Keep `not started`, `skipped`, and `completed` as distinct tour states. A
  completed tour is not automatically replayed. A skipped tour remains
  replayable from Help/Profile and may be offered again on a later successful
  sign-in while incomplete.
- When signed in, Get Started can always be skipped. When signed out, skipping
  proceeds to sign-in before the real Free experience. The pre-auth demo stays
  separate from the student's project data.
- Sign-in does not imply cloud backup, project sync, or upload. Preserve the
  separately approved local-storage, consent, and data-transfer boundaries.

The account gate is not a purchase gate: signed-in students can use Free
features without buying Pro. Tour completion and Free access must not be
represented as implementation evidence until their respective acceptance
checks pass.

## D-119 — Make project AI available at supported stages with explicit project binding

Decision: project AI is a contextual aid reachable from every supported stage
of a student project, from brief and method planning through source/data work,
analysis, findings, claims, limitations, revision, and output preparation.
Each stage exposes only operations defined for the selected method/template;
an unsupported operation must explain the boundary and preserve the manual
workflow. This broadens the intended project-AI surface, not the evaluator's
authority or the set of methods that have actually been reviewed.

For clarity, D-119 supersedes only D-106's prohibition on a global AI
destination and D-115's “not a separate, context-free chatbot” boundary to
permit an explicitly selected **General chat** mode. General chat is isolated
from every project and remains subject to its own approved cost, limit, and
retention policy. D-106 continues to govern case AI; project AI remains bound
to one selected project and stage. No mode inherits another mode's context.

Every project-bound request must identify exactly one project and the current
stage. Opening AI from a project carries that project as a visible default and
allows the student to change it before sending. Opening AI outside a project
requires an explicit choice between one project and **General chat**. A request
may include only the project fields/items the student selects after preview;
the app and server must never infer consent to send other projects or the whole
workspace. Server authorization must confirm ownership, consent, allowed
purpose, and the current project revision.

**General chat** is a separate, unlinked mode. It carries no project ID, does
not read project data, and cannot write to a project. Moving from a general
answer to project work requires a new project-bound request with its own
project/context selection and review. The mode must remain visibly labeled
`General chat` so its content is not mistaken for project-grounded advice.

All generated project content is a proposal. Before sending, show the selected
project (or General chat), stage/purpose, selected context, provider/data-use
notice, consent, and cost. Before applying a response, validate its schema,
allowed fields, referenced IDs, provenance, policy, and base revision; these
checks establish format and linkage, not academic truth. The student must
inspect the proposal against the source material and may edit, apply selected
parts, or dismiss it. Apply is an explicit reducer action that creates a
revision and AI provenance; run the supported deterministic/structural checks
again afterward. AI must not grade, assert source truth, invent or verify
sources/data/results, or set evaluator states.

AI activity history must identify project-bound sessions with the project,
stage, operation, time, and outcome (including whether a proposal was applied
and the resulting revision when applicable). General-chat entries must be
clearly shown as unlinked and carry no project association. This is a
metadata-only history by default: raw prompts/responses are not retained just
to populate history. Project deletion removes its linked history metadata;
account deletion removes the account's AI metadata. Cross-device history
sync and transcript retention require separate privacy/retention decisions
and are not implied by this decision. AI history is not project evidence or a
project revision log.

D-119 preserves D-106's existing case-AI contract and D-115's project-AI safety
limits, with only the narrow General-chat exception stated above. Its original
fixed project-AI pricing was superseded by D-127's token-based policy; the
provider remains disabled. It does not authorize provider activation, spend,
cloud sync, or live-capability claims. General chat uses D-127's shared account
balance, while request limits and retention beyond metadata remain separate
gates. This decision records target behavior; all-stage mobile
integration, project selection/history UI, new versioned API contracts, privacy
review, method review, credit acceptance, and device evidence remain open.

## D-120 — Keep Project Workspace local-first and available without an account

Decision (owner confirmed 28 September 2026): students may browse the published
project-template catalog and create, open, edit, save, reopen, archive, import,
export, and manage projects stored locally without signing in. This is the
active Rilis 1 project workflow from Final Spec v1; it does not broaden the
separate M0 learning flow or claim human-validated usefulness.

The anonymous catalog is read-only. Template-specific project creation remains
limited to compatible, actually published and reviewed templates; a student
may always start a blank/manual project. Existing local-project safeguards,
on-device persistence, archive validation, revision history, and export/import
behavior continue to apply. The current quota is five active local projects on
the Free path and up to fifty when an active `evidrilo_pro` entitlement is
confirmed for a signed-in account. If the entitlement cannot be verified, use
the Free limit without deleting, hiding, or rewriting existing local work.

Sign-in remains required for account-bound M0 learning activity, project AI,
AI-credit access/settlement, RevenueCat identity/entitlement, account profile,
and any account-backed service. Project AI additionally requires its separate
explicit, revocable consent and remains unavailable while its provider is
disabled. Cloud sync or transfer requires its own consent and is not enabled by
signing in. Signing in must not silently upload, claim ownership of, merge, or
move projects that were created locally; local project data remains local
unless a separately approved transfer flow is implemented and accepted.

D-120 supersedes only D-118's blanket gate for the local Project Workspace and
anonymous read-only catalog surfaces. It reaffirms D-117's local-first project
storage and leaves D-118's account-bound learning gate, D-106/D-115 AI safety
boundaries, and separate sync consent intact. This is a product decision, not
proof of implementation: source/tests must establish local route and operation
access, AI/account isolation, accurate copy, and graceful account/entitlement
fallback; Android/iOS device behavior remains a separate acceptance gate.

## D-121 — Adopt Home-first, local-first UX with contextual access

Decision (owner confirmed 28 September 2026): adopt Pattern B from the 42
synthetic multi-persona tabletop replays as the implementation target for the
whole-app entry, account, Settings, support, history, and premium flows. The
replays compared account-first, Home/local-first with contextual access, and
unbounded access. Pattern B is the selected product direction; the tabletop
exercise is design analysis, not user research or proof of usability.

The target starts at Home. A student can begin or resume local project work
without first completing Get Started or signing in; the guide remains optional
and replayable. Practice/case learning stays a visibly separate path with its
own account gate. Profile is an account hub: when signed out, explain the
Profile-specific sign-in need, preserve the requested destination, and keep
local projects and Support reachable. Successful authentication returns to the
requested destination and may offer an unfinished guide non-blockingly.

Settings is a public shell for device-local preferences, privacy/data
explanations, reminders/audio, Help, Support, and About. Gate only the
individual action that actually needs an account, verified identity, provider,
or consent, such as account export/deletion, account linking, RevenueCat
identity/restore, AI, and cloud operations. Sign-in never silently uploads or
merges local projects. Keep case-learning History distinct from local project
revision history. Make Support reachable from Settings, Profile, and account
gates; the selected contact is `andrlay30@gmail.com`, with a visible copy
fallback if `mailto:` cannot open.

Show a readable Free/Pro comparison before a purchase choice. Use current store
offerings for price, trial eligibility, and renewal terms; use verified
RevenueCat entitlement for Pro access. Do not present a policy quota as an
available entitlement unless the enforcing path is verified. Purchase cancel,
renewal cancel, restore, pending, expiry, and revoke are distinct states.
Expiry may remove only Pro benefits; it must preserve local projects and Free
work. AI consent, analytics consent, cloud consent, authentication, and billing
remain separate controls and follow their existing decisions.

D-121 clarifies the access presentation around D-120; it does not supersede
D-120's local-first permissions, D-118's account-bound M0 learning gate,
D-115/D-119's AI boundaries, or separate cloud-consent requirements. It
selects a target flow only. It does not implement the public Settings route,
activate the Support email, create a Free/Pro comparison UI, establish provider
behavior, or constitute device/user acceptance. Source observation, tests,
accessibility review, and runtime acceptance remain required before any of
those are called implemented.

## D-122 — Remove attachments from the current project revision only

Decision (owner confirmed 28 September 2026): removing a file from a local
project detaches it from the current project revision; it does not erase the
file from earlier saved revisions. The editor must ask for confirmation before
removal. Canceling the confirmation leaves project metadata and file storage
unchanged.

Removal creates a new project checkpoint without the attachment and preserves
the attachment bytes while any retained revision snapshot still references
them. Prepare the existing per-attachment deletion fence before metadata
changes; if checkpoint persistence fails, cancel the fence or recover against
the saved current and historical references, leaving the current attachment
intact. Physical cleanup is allowed only after no retained current or historical
project state references the file; use the existing recovery mechanism.

D-122 governs the local project attachment action only. It does not authorize
deleting project history, changing archive compatibility, cloud transfer, or
claiming device-level cleanup acceptance. Platform runtime and accessibility
verification remain separate gates.

## D-123 — Simplify first-use and project-workspace interactions

Decision (owner confirmed 28 September 2026): adopt the concise, consequence-
visible UX target in the [product workflows](product/workflows.md#d-123-interaction-principles). Keep D-120's local-first project access and D-121's
Home-first, contextual account gates.

Student-facing screens should state one purpose, use short supporting copy, and
show one primary action. Preserve clear privacy, consent, cost, and data-transfer
choices at the point of use, with optional detail progressively disclosed.

When an account-gated action is selected, open the main sign-in screen directly,
remember the requested destination, and return there after successful sign-in.
Show Google as the primary provider only when configured; offer email only when
configured. An unavailable provider gets a short recovery message and a path
back to local work. Provider diagnostics belong in logs/support rather than
long user-facing explanations, and an unavailable provider must not appear as an
active button.

Get Started remains optional, skippable, and replayable, with a roughly three-
minute content target that is not yet usability-validated. Use a visual
progress bar, a descriptive current-section title, and accessible progress
semantics instead of visible “Step N of M” copy. The guide begins nearly blank
and uses the student's own assignment/topic or permits continuing without one.
It teaches a field-neutral reasoning path without passing fabricated sources or
findings off as student material. It does not create project/history data unless
the student explicitly starts a project.

Project stages use a visible structural progress bar and remain freely
navigable; the separate full review is available before confirmation/export.
Selections show their immediate effect and next useful action. A recorded
evidence relationship documents a student choice; it is not an Evidrilo
judgment that a claim is true. Use plain language instead of exposing internal
record IDs or unexplained terms such as “link a finding.”

The student catalog must make a reviewed, published template useful: inspect
its method, expected output, and sections, then create a local project scaffold
from its exact immutable version. Templates do not fill in research evidence or
conclusions. While none is selectable, keep blank-project creation primary and
explain the empty catalog briefly. Case practice remains separate from student
projects; case facts never become project sources automatically.

Remove visible narration **Listen** controls from current product surfaces.
This does not remove operating-system screen-reader support or redefine
separate selection feedback or device-local sound preferences. This narrows
D-121's Settings-shell mention of audio controls only for current narration
Listen controls. It does not revoke D-098's optional audio capability for a
separately accepted future use.
D-119 continues to govern the persistent, project-bound AI proposal
experience; D-123 does not activate its provider or claim that the target is
implemented.

D-123 records a product and UX target, not implementation or human usability
evidence. The current project pilot remains manual directed literature
synthesis; the field-neutral guide must not imply that every research method,
template family, or evaluator is ready. Device, accessibility, provider, and
human acceptance remain open.

## D-125 — Temporarily enable local guest access across the mobile workflow

Owner-directed 29 September 2026: temporarily hide and disable mobile sign-in
while the account flow is unclear. Students may use locally stored projects,
the read-only catalog, the bundled case workflow, and local history without an
account. Home presents one primary project action: create when there is no
active project, otherwise continue the most recently updated active project;
new-project and view-all actions are secondary links.

Account operations, cloud sync, analytics transmission, server AI/project AI,
RevenueCat Pro access, and other account-bound services remain unavailable in
this guest-only client mode. The mobile client does not restore account
sessions, handle auth callbacks, or initialize RevenueCat while this switch is
on. This decision does not grant anonymous access to protected APIs, change
server authorization, consume or delete stored credentials, or upload local
project content. Public read-only catalog requests may still use the network.
Local project limits continue to follow D-124; without a verified Pro
entitlement, the Free limit applies.

This is a temporary client access and presentation switch, not a replacement
for the account product. Restore sign-in only after an explicit owner decision
and a clear account UX review. It does not claim that all account/provider or
device acceptance gates are complete.

## D-124 — Keep the research core Free and make Pro optional depth and capacity

Decision (owner-directed 29 September 2026): preserve an actually useful Free
research workflow and use Pro for additional capacity and optional depth. This
adds a catalog-access policy to the existing Free/Pro model; it does not change
the approved prices, RevenueCat entitlement, project limits, AI allowances, or
current implementation status.

All five catalog-family guides, read-only browsing, and previews of exact
published template versions remain Free. For any family that has a reviewed
and published baseline template, keep at least one baseline template in that
family startable on Free. A family without reviewed/published content remains
guide-only and must not be presented as a ready-to-use template. Publication
and methodological review are separate from subscription entitlement.

Pro may later include optional specialized or advanced templates, but no whole
method family, core project stage, evidence/provenance capability, or basic
method guidance may be Pro-exclusive. Any paid template must still be reviewed,
published, accurately scoped, and previewable before purchase. Pro adds optional
depth; it must not sell a stronger truth judgment, better evidence, or a more
credible academic result.

Keep blank/manual project creation, the core project workflow, essential report
export, and complete project-data portability available on Free. Existing
projects remain openable, editable, exportable, and deletable after Pro expiry;
the existing D-114 rule may block only new or reactivated active projects above
the Free limit. Cloud sync or backup is not part of the current Pro promise; it
may be considered only after the separate R2 consent, privacy, retention,
security, quota, cost, operations, and acceptance gates pass.

Project limits remain five active projects per Free installation and 50 per
active Pro installation. D-126 later set the AI grant targets at 20 one-time
Free credits and 200 credits per active Pro entitlement month; D-127 later
replaced fixed Project AI prices with token-based pricing shared by every AI
mode. These later decisions do not make AI available: it remains bounded by a
verified account, consent, the server ledger, and provider/cost gates. General
chat has no separate allowance. Do not introduce paid top-ups, one-time credit
packs, lifetime plans, institutional sales, or advertising in v1.

D-124 supplements D-099, D-100, D-114, D-117, D-120, and D-123. It is a product
policy target, not evidence that reviewed templates, Pro template entitlements,
RevenueCat transactions, account-wide quotas, cloud sync, or device acceptance
are implemented.

## D-126 — Increase the Free and Pro AI credit allowances

Owner-directed target, 29 September 2026: raise the optional AI credit allowance
to 20 credits once for a verified Free account after explicit consent, and 200
credits per active `evidrilo_pro` entitlement month. Monthly and yearly
subscriptions receive the same monthly grant while active; a yearly plan does
not receive the full annual amount up front. Under the policy recorded on this
date, unused credits did not roll over; D-130 later supersedes that rule.

D-126 changed the grant quantities, not supported AI purposes, account
verification, consent, RevenueCat entitlement checks, or the server-owned
ledger. At the time this decision was recorded, the existing fixed operation
prices remained in force; D-127 below supersedes those historical prices with
token-based settlement. General-chat allowance was unresolved at D-126; D-127
later placed chat on the same usage-based account balance. D-126 did not
activate a provider, authorize spend, or make AI available to guest users.

The decision-time observation found that migration `031_ai_credit_ledger`
limited each grant below the updated target and that forward migration 050 was needed to
raise the bound. The pre-consent label was also stale at that observation; the
current UI must request consent without promising a tier-specific grant before
the account's entitlement is known. Fresh/upgraded database acceptance and
the independent provider/privacy gates remain separate from the 20/200 product
target; it is not evidence of live credit availability.

D-130 later supersedes D-126's no-rollover rule. Read D-126 as the historical
record of the grant amounts; current carry-forward behavior follows D-130.

D-126 supersedes the Free/Pro grant amounts recorded in D-099, D-100, D-114,
D-115, D-117, and D-124. D-127 supersedes the fixed operation-pricing rules
recorded in earlier entries. Older decisions preserve their product context
and point to the current grant/pricing rules; current guidance follows D-126
grant amounts, D-127 pricing, and D-130 carry-forward behavior.

## D-127 — Charge AI credits from token usage

Owner-directed policy, 29 September 2026: use the verified provider-reported
token usage for every successful AI request, including case assistance,
conversation/chat, and project previews. These requests draw from the same
account AI credit balance; chat does not have a separate allowance.

For the selected GPT-6 Luna standard-context rates, price uncached input at
$0.10, cached input at $0.01, cache-write input at $0.125, and output at $0.50
per one million tokens. Reasoning tokens are a subset of output tokens and must
not be charged twice. The server holds the maximum estimated credit cost before
provider dispatch, then settles validated usage after an accepted response.
One credit represents $0.001 of provider cost, rounded up per successful
request. The maximum charge for one request is 200 credits; provider monthly
spend limits remain a separate server-side control.

Failures, malformed or missing usage, rejected output, cancellation, timeout,
and requests that become stale before a valid preview release the user's
reservation. A valid project preview is charged when generated, whether the
student later applies or dismisses it. There are no separate fixed credit
prices per operation.

This policy supplements D-126's 20 one-time Free and 200-per-active-Pro-month
grants and supersedes its earlier fixed operation-cost rule. Model rates must
remain owner-configured and match the pinned model and applicable pricing tier.
The provider remains disabled unless its separate privacy, retention, cost,
consent, and runtime gates are approved. This decision does not authorize
provider activation, spend, deployment, or a live-AI claim.

## D-128 — Add Apple sign-in without creating duplicate Evidrilo accounts

Current Apple availability is governed by D-132. This entry remains a future
product target and does not authorize Apple provider setup or enablement now.

Owner-directed product target, 29 September 2026: offer **Continue with Apple**
alongside **Continue with Google** when each provider is configured. Both
providers must resolve to one stable internal Evidrilo account when the student
explicitly links them from an authenticated account. This extends D-102's
provider-neutral authentication boundary and D-110's explicit Google-linking
rule. D-129 authorizes optional sign-in while local guest access remains active.

The internal Evidrilo account ID is canonical. A provider identity is keyed by
its issuer/provider and stable subject identifier (`sub`), not by email. Email
is verified contact/profile data and may change or be hidden; it is not a safe
cross-provider identity key. During Apple authorization, the student may choose
to share a verified email or use a private relay address. The app must accept
either choice and must not add a separate personal-email prompt just to use
Apple sign-in. The current Supabase browser OAuth flow requests email but does
not return Apple's full name; name collection is a separate optional profile
flow. See Apple's
[authentication](https://developer.apple.com/documentation/signinwithapple/authenticating-users-with-sign-in-with-apple)
and [private relay](https://developer.apple.com/documentation/signinwithapple/communicating-using-the-private-email-relay-service)
guidance and Google's [OpenID Connect identifier guidance](https://developers.google.com/identity/openid-connect/reference).

Account linking is an explicit action available from the active authenticated
account. Show that account's verified email when available, explain Apple's
private relay option, authorize Apple, verify its identity, and confirm the
result resolves to the same internal account ID before accepting the new
session. Linking must never switch the active session or merge project,
history, billing, or entitlement records.

Supabase Auth automatically links OAuth identities that return the same
verified email to one Supabase user. The mobile client cannot disable this
managed-auth behavior. It does not merge separate Evidrilo records in app code,
but it means same-email sign-in can attach an identity without the explicit
link confirmation. For a different email, including an Apple private relay,
the student must sign in to the account they intend to keep and link Apple from
that account. A provider identity already linked elsewhere produces a generic
conflict without disclosing that account. Cancellation, provider error,
unverified callback, and conflict preserve the current session and existing
data.

The client now contains Apple OAuth, provider-identity verification, explicit
linking, same-account-ID enforcement, and backward-compatible secure session
fields. Provider dashboard setup, manual-link enablement, same-email managed
Auth behavior, and Android/iOS runtime acceptance remain separate gates.

## D-129 — Restore optional account sign-in while local guest access remains active

Owner-directed 29 September 2026: allow email, configured Google, and
configured Apple sign-in, session restore, auth callbacks, and explicit
provider linking alongside the local guest workflow. This supersedes only
D-125's mobile sign-in/session/callback pause. It does not retire guest mode.

Keep server AI and credit use, Pro/RevenueCat, cloud sync, analytics
transmission, recommendations, account-backed platform projections,
notification-preference backup, account export, and account deletion paused
while `TEMPORARY_GUEST_MODE_ENABLED` is true. Do not associate or upload local
project data when a student signs in. API authorization remains unchanged.
Expose each OAuth button only when the build enables that provider and the
Supabase client is configured; Apple remains disabled by default until the
Apple/Supabase project setup is complete. The mobile client never contains an
Apple signing secret.

This decision authorizes the local code change only. It does not claim that
Apple credentials, Supabase manual linking, provider callbacks, or device
runtime have been configured or verified.

Apple activation is currently deferred by D-132; the provider and app flag stay
disabled until the owner explicitly revisits that decision.

## D-130 — Accumulate Pro AI credits without resetting the balance

Owner-directed policy, 29 September 2026: after explicit AI consent, a verified
Free account receives 20 credits once. Each earned active `evidrilo_pro`
entitlement month adds 200 credits to the existing balance. If all 20 Free
credits remain unspent, the first Pro grant brings the balance to 220; if some
were used, the grant adds 200 to what remains. Each later active month adds
another 200 instead of resetting the balance.

Monthly and yearly subscriptions use the same monthly grant cadence. Yearly
plans do not receive 2,400 credits up front. Each monthly grant is keyed to its
verified entitlement-period start so webhook retries, balance reads, and
restores cannot grant the same period twice. Unused earned credits do not
expire, roll back, or disappear when Pro ends; ending Pro only stops future
monthly grants. Free's one-time grant, token-based usage pricing, and the
200-credit maximum charge per request remain as defined by D-126 and D-127.

D-130 supersedes D-126's no-rollover rule. It does not enable the AI provider,
authorize provider spend, deployment, or a live-AI claim.

## D-131 — Keep local Free access and enable account-bound Pro independently

Owner-directed 29 September 2026: keep the Free project and bundled-case
workflow usable locally without sign-in, while enabling the Evidrilo Pro
presentation and RevenueCat purchase/restore path independently from
the temporary guest-mode flag. Pro requires a signed-in Evidrilo account and a
confirmed active Evidrilo Pro entitlement from the configured RevenueCat
provider. A missing provider configuration, network error, unknown transaction,
or unconfirmed entitlement remains locked and uses the Free project limit; the
client must never synthesize or cache a local Pro grant.

This changes only the mobile RevenueCat Pro gate and account identity needed
for that provider. It does not enable server AI/credit use, cloud sync,
analytics transmission, account-backed projections, or upload local project
data. Local projects remain on-device. API authorization is unchanged. Pro
configuration and Test Store/runtime evidence remain separate acceptance gates.

D-131 supersedes only D-129's statement that Pro/RevenueCat must remain paused
while local guest access is active. All other D-129 pauses remain in effect.

## D-132 — Defer Apple sign-in and keep Apple provider configuration empty

Owner-directed decision, 30 September 2026: defer **Continue with Apple**.
There is no Apple Developer setup available for this project, and Apple login
is not needed for the current app or API-platform work.

- Keep the Apple provider disabled in Supabase Auth and keep
  `SUPABASE_APPLE_AUTH_ENABLED` / `supabaseAppleAuthEnabled` false in app builds.
- Leave Apple-specific client IDs, signing keys, and generated provider secrets
  unset. Empty Apple provider configuration is intentional; do not add
  placeholder credentials.
- The empty Apple configuration is not a prerequisite for the app API,
  Supabase project operation, email sign-in, or Google sign-in when Google is
  separately configured. Do not claim Apple sign-in is available.
- Revisit only after the owner explicitly chooses Apple sign-in and Apple
  Developer setup is available. Complete provider configuration and Android/iOS
  runtime verification before enabling the provider.

D-132 defers D-128's Apple sign-in product target and clarifies D-129's
configured-provider gate. If the app later targets App Store distribution while
using Google for its primary account login, review Apple's current
[Guideline 4.8](https://developer.apple.com/app-store/review/guidelines/) and
applicability before release.
