# Evidrilo Roadmap

This roadmap describes product direction and sequencing. It is the
repository-owned preparation boundary, not a live
progress ledger, delivery promise, or proof that a listed capability is already
available. Current implementation status belongs in the project's private
evidence records; technical procedures live in the linked engineering docs.

## Product outcome

Evidrilo's long-term direction is a student-first academic learning and project
workspace: students learn a method, apply it to an academic task or research
project, and inspect how criteria, sources/data, claims, feedback, and revisions
relate. Its central value is helping students see what their evidence supports,
what remains uncertain or missing, and what they can improve next.

This is the strategic destination, not a description of capabilities already
available. The current verified Shipaton proof remains one fictional
tablet-dissolution case and its bounded deterministic feedback loop. That
submission evidence is not proof of the wider student-authored workspace or a
universal academic evaluator. Under D-117, local implementation of Rilis 1 is
now an active workstream; claim only project functions that pass their own
acceptance tests and runtime checks.

## Competition-sized product slice

The first complete slice should show one coherent student journey:

1. Read the task, supplied observations, and stated limitations.
2. Trace the requirement to relevant evidence.
3. Write a learner-owned claim, scope, limitation, and next action.
4. Review transparent, fact-anchored checks, including abstention when evidence
   is insufficient.
5. Make one revision and compare it with the original.
6. Reconsider the claim after a controlled evidence change and inspect local
   history.

The M0 learning loop remains purchase-free and account-gated. Separately, the
Rilis 1 Project Workspace, manual local project creation, and read-only catalog
browsing are available without an account. Account-bound AI, learning activity,
and connected services keep their own sign-in/consent requirements. Signing in
alone does not promise cloud backup or project sync. RevenueCat may unlock
additional project capacity or other approved premium value; it must not sell
truth, safer feedback, or the core verification rules. See [D-118](decisions.md#d-118-make-get-started-optional-and-require-sign-in-for-free-functionality)
and [D-120](decisions.md#d-120-keep-project-workspace-local-first-and-available-without-an-account).

First launch offers an optional, approximately three-minute Get Started tour
and supported sign-in choices. The tour uses an isolated synthetic demo, can
always be skipped, and can be replayed from Help/Profile. After successful
Google sign-in/sign-up, offer the tour to returning students who have not
completed it; skipping opens the authenticated Free experience without delay.
For a signed-in student, skipping opens the available Free experience without
delay. A signed-out student returns to Home and local projects; account-bound
M0 learning and connected features still require sign-in.
Purchase remains optional for the learning core and local project work.
The tour does not fabricate a reviewed template when none is available. Email
and Google sign-in use the provider-neutral PKCE account boundary. Sign-in does
not itself enable cloud sync or transfer project drafts. A restrained
Evidrilo loading screen may cover the shell only while local/session bootstrap
resolves; provider or network-only pages may use the same screen with safe Back.
Local reminders are opt-in, off by default, configurable by category and
cadence/time, and disableable from Settings; remote push is deferred. See
[D-118](decisions.md#d-118-make-get-started-optional-and-require-sign-in-for-free-functionality).

The approved interaction references are Duolingo and Brilliant. Duolingo is
used only as a reference for approachable motion, tactile actions, progress
feedback, and brief celebration states. Brilliant is used only as a reference
for clear student learning steps, explanations, checkpoints, and result
feedback. The private `internal/design/` target remains the sole visual
authority for Evidrilo's brand, composition, colors, navigation, and page
hierarchy. The app must use original Kotlin/Compose implementations and must
not copy or ship third-party logos, characters, screenshots, illustrations,
proprietary animations, or undocumented downloaded assets.

For the current Next Gen candidate, the two premium cases remain bundled in
the mobile app and are gated by RevenueCat `CustomerInfo`. When a verified
account is available, the mobile client may fetch the published M0 case and
adapt only its display text after strict case/version/fact checks; the bundled
evaluator remains authoritative and is the offline fallback. The API, sync,
analytics, and AI boundaries follow D-101; this connection does not imply
full cloud draft restoration or a live AI provider.

## Active catalog workstream (D-113)

Catalog development is active as a local product workstream, using the five
families adopted in D-112. The backend foundation now provides the versioned
catalog contract, published-only browse/detail APIs, and a separately
authorized author/reviewer/publisher lifecycle. Review-selected example IDs
are server-owned; client-authored `reviewed` flags cannot satisfy publication.
The current mobile flow includes offline family overviews, published template
browse/detail, a My Projects list, and local project-draft storage and editing.
Under D-120, Home, anonymous read-only catalog browsing, and manual/local
project work do not require sign-in; the separate M0 learning workflow remains
account-gated. Template-backed creation requires an actually published and
reviewed server detail, while a blank/manual project remains available when
the catalog is empty. Sign-in does not enable cloud sync or upload, and project
draft content is not academically evaluated by this path.

The five intended families are experimental/laboratory, observational/survey,
literature review, qualitative interview/field study, and design/engineering.
Do not expose empty placeholders as usable choices. At the E225 API-linked
Android smoke observation, the local API was reachable and returned no seeded
or published template content: all five family counts were zero. No newer API
read is claimed by this roadmap. Students can
browse family guidance and the My Projects empty state, but cannot start a
template-backed project yet. A concrete template must pass scope, input/output,
method-limit, provenance, accessibility, and reviewed-example gates. Select the
first method from evidence about a recurring student task, with appropriate
human/domain review; no participant contact or real student data is authorized
by this roadmap.

D-124 sets the access policy for catalog growth: all family guides and
published-template previews remain Free, and each family keeps at least one
reviewed/published baseline template startable on Free when available. Pro may
later add reviewed specialist templates as optional depth; it cannot lock a
whole method family, core project work, essential export, or data portability.
This policy does not imply that any reviewed template or subscription gate is
currently available.

This workstream does not silently expand the current M0/Next Gen submission
claims. Keep the local-first core, student ownership, explicit template choice,
and formative, bounded evaluation. Do not create universal grades, mandatory
hypotheses, or automatic classification of ambiguous assignments.

## Active Project Workspace Rilis 1 (D-117)

The adopted Final Spec v1 starts with one directed-literature-synthesis pilot
for Indonesian undergraduate students. This is an initial product hypothesis,
not human-validated demand. Rilis 1 is project-first and local-first: students
may create a blank/manual project without signing in or selecting a catalog
template, then enter their own brief, question, sources, extraction
notes, synthesis, claim, and limitations. The project workflow is separate
from the M0 tablet learning case; no sample case evidence is inserted into
student work. Project AI and account-bound services still require sign-in;
cloud transfer requires separate consent and is not implied by signing in.

The intended R1 path is brief → question/aim → manually entered sources →
extraction/evidence matrix → student-written synthesis → bounded
claim/limitations → structure-only check → revision → export. Checks report
presence and traceability only unless a method-specific rule has been reviewed;
they do not grade academic quality or decide universal truth. R1 does not
include systematic-review/meta-analysis claims, automated source discovery,
database search, or DOI verification.

The D-114 Free/Pro caps are 5/50 active projects per installation for R1; only
a verified RevenueCat `evidrilo_pro` entitlement can grant the Pro cap. Archive,
completion, and Trash do not consume active slots; downgrade preserves and
keeps existing work usable. Account-wide atomic quotas are Rilis 2. R1's
portable outputs are PDF/DOCX/Markdown, CSV for tabular data, and validated
`.evproj` backup/restore; unsupported or unsafe formats must remain explicitly
unavailable.

Rilis 1.1 is the optional server-side AI extension, not a dependency of local
work: a scaffold costs 3 credits and a standard assist costs 1 only when
applied. The provider remains disabled until owner privacy/retention/cost
approval and provider-backed acceptance pass. Rilis 2 is opt-in cloud
continuity and requires account-wide atomic quota, conflict/revision recovery,
and deletion propagation. These target decisions do not assert that any of
those features already pass implementation gates.

## Staged product path beyond the catalog foundation

The remaining direction is gated and does not authorize participant
recruitment, provider activation, or deployment. D-113 starts local catalog
development now. D-116 advances D-115's catalog-guided AI project creation and
in-project assistance into an active local development workstream targeting
the official deadline of **30 September 2026, 11:45pm PDT** ([official rules](https://revenuecat-shipaton-2026.devpost.com/rules), checked 27 September
2026). This authorizes local implementation work, not provider spending or
activation, deployment, or a claim that the feature already works. Include the
flow in the candidate only if its acceptance evidence is complete before
freeze.

1. **Discover one real student task.** Select a student segment and observe a
   recurring academic assignment/project workflow with appropriate permission,
   consent, privacy, and any required ethics review. Do not treat competition
   eligibility as market validation.
2. **Build one applied project vertical slice.** Support one selected academic
   method from brief and criteria through question, source/data plan, evidence,
   bounded analysis/claim, formative feedback, revision, and a traceable export.
   Start with explicit/manual inputs. A hypothesis is optional and depends on
   the method; it is not a universal research field.
   A single reviewed worked example or concise in-context explanation may
   support this slice when validation shows it is needed; this is not a course
   library or authoring platform.
3. **Validate the evaluator before generalizing it.** Use versioned examples,
   domain reviewers, adversarial cases, measured false-positive/false-confidence
   rates, and explicit abstention rules. Keep formative feedback separate from
   grades, institutional decisions, and real-world truth.
4. **Build governed authoring and a small learning pathway.** Only after the
   project and evaluator contracts are understood, create reviewed content and
   short lessons/checkpoints that help students apply a skill in the project.
   This stage scales beyond the limited, reviewed learning aid that may be
   included in the first vertical slice.
   The target progression is pathway → course → unit → lesson/activity →
   practice/review/assessment → applied project. Assessments are formative by
   default; formal exams or grades, if ever needed, require
   educator-owned criteria and must not become automatic institutional
   decisions by Evidrilo. Project feedback stays criterion-by-criterion and
   anchored to the relevant work. After a method-specific evaluator and its
   thresholds have been validated against reviewed examples and domain review,
   Evidrilo may summarize the tested criteria with an explicit formative
   outcome such as `CRITERIA_MET`, `NEEDS_REVISION`, `INCOMPLETE`,
   `NEEDS_REVIEW`, or `CANNOT_ASSESS`. The outcome must show its rubric/version,
   criteria states, evidence anchors, reasons, and unresolved conditions; it
   must not imply a course grade, institutional pass, research quality, or
   real-world truth. If criteria are ambiguous, evidence is insufficient, or
   the case is outside the validated contract, the evaluator abstains rather
   than issuing an overall pass. Do not collapse distinct dimensions into an
   opaque numeric score or advertise a project “pass grade.” Completion counts
   (for example, criteria linked out of the total) describe progress only, not
   quality or academic merit. A lesson checkpoint may say “complete” or “try
   again” for that bounded activity; this remains separate from the formative
   evaluation of a student-owned project. Any grade or formal academic decision
   remains with the educator or institution.
   Define content licenses and any portable pack format separately; MIT code
   does not make bundled content open-licensed.
5. **Active pre-deadline AI project workstream (D-115/D-116).** Implement one
   end-to-end flow: the student chooses one reviewed, selectable catalog
   template and supplies task context; an authorized real provider returns a
   typed, editable project scaffold; the student reviews and confirms before
   save; and contextual AI help is available from the saved project. Acceptance
   requires the versioned project contract, explicit consent, visible
   credit/cost treatment, D-114 quota enforcement, manual/offline fallback,
   provider failure recovery, and Android end-to-end evidence. AI cannot invent
   sources/data, write a finished academic deliverable, or determine evaluator
   truth. At the time of this update no catalog template is selectable and the
   provider is disabled. Local implementation is active; if template review,
   provider/privacy authorization, quota integration, or runtime proof is still
   open at freeze, mark the feature incomplete and do not claim or depict it as
   live. D-116 does not authorize provider activation, spending, deployment, or
   publication.
6. **Add optional class and platform capabilities when validated.** Consider
   student-controlled sharing, teacher views, sync, and analytics only when a
   demonstrated need, privacy/governance model, and operational cost justify
   them. Drafts remain private by default.
7. **Expand one method/domain at a time.** Each new area needs its own content
   contract, evaluator version, reviewed fixtures, provenance, accessibility,
   and human validation. Never imply universal support from the M0 case.

### Recommended student experience (post-Shipaton UX hypothesis)

The journey should be **project-first, guided, and flexible**, not a course
catalog followed by a blank workspace or a locked linear wizard. One project is
the student's through-line; the interface surfaces one useful next action and
reveals its evidence detail progressively.

1. **Start or resume.** Returning students see `Continue project` first. A new
   student can start from an assignment/question or try one bounded example;
   learning paths remain an optional route, not a prerequisite.
2. **Frame the task.** Capture the task, intended outcome, criteria, and key
   constraints with a small editable task map. Keep the full rubric available;
   ask the student to confirm its interpretation instead of silently rewriting
   it.
3. **Choose a next step.** Recommend one concrete action from the project's
   actual state. Show the overall path and dependencies, but let students skip
   known material and resume work already completed elsewhere.
4. **Learn in context.** Offer a short explanation, worked example, hint, or
   interactive checkpoint when useful. Let the student defer it, then return
   to the same project step to apply the idea.
5. **Build the work.** Plan the method, gather sources/data, and draft as the
   selected task requires. Capture provenance as work is added; make suggested
   links visible and student-confirmed. A hypothesis is optional and
   method-dependent.
6. **Review one priority.** At meaningful checkpoints, show the most useful
   supported, missing, or over-scoped relationship first, with an exact
   criterion/evidence anchor and an expandable trace. The evaluator stays
   formative, method-bounded, and able to abstain.
7. **Revise by choice.** The student accepts, edits, or rejects feedback and
   makes the change. Preserve earlier work and explain how the revision affects
   evidence, claims, limits, and the next action.
8. **See progress and continue.** Summarize what changed, what remains
   uncertain, and one next step. Support pause/resume and a traceable export;
   never label the work universally true, graded, or institutionally approved.

An optional AI assistant belongs inside these project steps, including initial
project scaffolding from a selected reviewed catalog template. It is not a
separate blank chat: its suggestions use the current project/template context,
are shown for student review, and never replace the deterministic evaluator.
The exact UI and stage-by-stage affordances remain a design hypothesis. D-116
authorizes local implementation before the deadline; neither D-115 nor D-116
proves that the flow is implemented or currently works.

This sequence is a design hypothesis, not a settled screen map or implemented
feature claim. Validate whether students can start from both an assignment and
a guided example, recognize their next action, apply an optional lesson to the
same project, explain an evaluator finding from its anchors, and understand
that completeness, evidence support, and real-world truth are different.
Set quantitative acceptance thresholds before any participant study. Preserve
student consent, privacy, accessibility, and owner authorization requirements.

### Five-family selectable project-template catalog (D-112, timing advanced by D-113)

The adopted long-term catalog is organized into five student-selectable
families: experimental and laboratory work; observational and survey studies;
literature reviews; qualitative interviews and field studies; and design and
engineering projects. Students may choose the family that fits their task;
starting from an assignment/question remains a general entry path, not another
method category. Catalog development is active now under D-113, but this is not
a claim that five complete templates or evaluators are already available. Only
a concrete, versioned, reviewed template may appear as an actionable choice.
Choose the first method-specific template after validating a recurring student
task, then expand incrementally. Do not silently classify ambiguous
assignments, merge different methods, or make hypotheses universal.

The exact first segment, assignment, method, evaluator thresholds, content
license, and pack format are open validation outcomes. The intended direction
is settled by D-108; these details should be resolved by evidence at the stage
where they matter, not by adding speculative features now.

## Supporting platform and product workstreams

The following contracts preserve useful technical boundaries. They are not a
priority order and must be pulled only when a product-path gate or verified
operational need requires them.

### Preserve the local learning loop

- Keep task, evidence, claim boundary, action, revision, and comparison state
  connected to one active case identity.
- Preserve drafts and history locally, support reset/recovery, and make offline
  behavior explicit.
- Keep deterministic evaluation explainable and test abstention, ambiguity,
  removed evidence, and unfair input conditions.
- Validate accessibility and user comprehension before broadening the feature
  set.

### Optional account and sync

- Add accounts only when cross-device continuity is a demonstrated need.
- Make sync opt-in and consented; send the minimum data necessary.
- Define conflict resolution, export, deletion, retention, and recovery before
  enabling real learner data.
- Preserve the complete local free workflow when authentication or the API is
  unavailable.

The current platform sync is intentionally metadata-only: it records bounded
commands and snapshot digests rather than learner-authored drafts. The mobile
client now also consumes authenticated progress and entitlement projections for
account transparency, but these projections never override local RevenueCat
access or local drafts. It must not be marketed as full cloud workspace
restoration until server-owned workspace, draft, evidence, feedback, revision,
and conflict-resolution APIs exist.

### Local student-project API foundation (D-109)

The owner-authorized bounded backend-only foundation has been implemented and
locally verified in E219/E220: verified-account project CRUD, student-entered
task/question/method/hypothesis/criteria/evidence/claim fields, criterion-to-
evidence links, append-only revisions, idempotency and optimistic concurrency,
account export/deletion, and a structural-presence report. The report is
`not_assessed`; it does not score academic merit, evidence quality, truth, or
course criteria. The API is not connected to the mobile project UI and does
not replace metadata-only M0 sync or the deterministic offline evaluator.

The implementation uses synthetic local data only. Real student work, a
method-specific evaluator, AI assistance against these project records, and any
hosted use still require the applicable consent, privacy, reviewer, provider,
and deployment gates. See [D-109](decisions.md#d-109-pull-forward-a-local-only-student-project-api-foundation).

### Platform API semantic closure

Before describing Evidrilo as a complete online platform, the API roadmap must
close these boundaries:

- keep the current submission's two premium cases bundled/local and gated by
  verified RevenueCat `CustomerInfo`; the current published-case bridge is
  limited to the free M0 content and cannot silently replace evaluator rules;
  if a later release serves premium cases remotely, add server-side entitlement
  authorization before exposing them;
- keep client analytics as telemetry unless server-owned attempt/evaluation
  records are introduced for teacher-facing assessment;
- keep the approved AI credit ledger and entitlement-period grants server-owned
  (implemented in migrations `031_ai_credit_ledger` and
  `033_ai_credit_request_fingerprint`); keep live AI disabled and
  omit live-provider claims until privacy, cost, and provider evidence exist;
- verify RevenueCat product identifiers and safely classify test/diagnostic
  events before granting access;
- keep organization roles separate from infrastructure operators and do not
  claim an unimplemented global admin role;
- define account export, retention, and the boundary between platform deletion
  and Supabase Auth identity deletion;
- distinguish local diagnostic health responses from a hosted readiness contract.

These are platform correctness gates, not reasons to delay the offline-first
submission core.

### Retry and concurrency contract

Every retryable mutation must have a durable ownership boundary. The API does
not rely on a process-local cache, timing assumption, or client-side success
flag to suppress duplicates.

- Sync, analytics, recommendation, billing, and deletion use durable
  account/provider-scoped command or event identities and compare replay
  payloads before returning an idempotent result.
- The shared `Idempotency-Key` header is syntax-validated before route
  execution. A key reused for a different AI request is rejected using a
  redacted request fingerprint.
- AI reservations are serialized by the account ledger, cannot overspend the
  balance, and end in a terminal consumed or released state. A terminal replay
  never invokes the provider again.
- Authoring state changes and membership owner changes are protected by row
  locks and conditional transitions; only one concurrent request can win a
  single-winner transition.
- Worker jobs use bounded attempts and an explicit lease token. Only the
  current lease holder may complete or fail a job.
- Migrations `032_worker_lease_fencing` and
  `033_ai_credit_request_fingerprint` are part of the forward-only schema
  contract.

The local concurrency matrix is recorded in the evidence index. It proves the
implemented local scenarios only; hosted load, chaos, provider, and production
capacity evidence remain separate gates.

### Learning history and recommendations

- Start with transparent, deterministic summaries of completed attempts and
  recurring skill gaps.
- Recommend a next case only when there is enough reviewed content and a clear
  reason the learner can inspect.
- Do not infer learning efficacy, retention, or ability from a small formative
  sample or raw completion counts.

### Governed content operations

- Version case schemas and content.
- Validate references and evaluator fixtures automatically.
- Require human review before publishing a case; retain approval, version, and
  rollback history.
- Build authoring tools before teacher dashboards so the content and review
  lifecycle has a trustworthy foundation.

### Teacher-facing views

- Add role-based educator tools only after authorization, consent, data
  minimization, and classroom workflows are validated.
- Prefer aggregate progress and skill summaries over unrestricted access to
  learner drafts.
- Include suppression, auditability, retention, and deletion controls from the
  beginning.

### Bounded AI assistance and credits

AI is a deliberately narrow premium extension described in
[D-106](decisions.md#d-106-use-an-evidence-grounded-ai-loop-for-project-assistance). It may explain a
deterministic feedback item, ask a reflection question, or suggest a
meaning-preserving language alternative.

- A verified free account receives 10 AI credits once after explicit consent.
- An active `evidrilo_pro` account receives 100 credits per entitlement month;
  yearly subscriptions receive the same monthly grant while active.
- Credits do not roll over. Lifetime allowances and paid top-ups are not part
  of the initial plan.
- One accepted standard assist costs one credit; failed, cancelled, timed-out,
  malformed, rejected, or unavailable requests release the reservation.
- The server ledger and verified RevenueCat entitlement are authoritative. The
  client cannot grant or mutate credits.
- Explicit context selection, redaction, schema validation, timeout,
  cancellation, rate/cost limits, metadata-only audit, and provider-data
  disclosure are required.
- AI must never grade truth, invent evidence, accept requirements, or mutate
  deterministic evaluator output. When unavailable, the free local workflow
  remains complete.

The first provider adapter is planned server-side behind `IAiProvider`; the
repository's disabled provider remains the safe default until privacy, cost,
credit-ledger, and provider evidence are complete.

The implementation sequence is deliberately vertical rather than chatbot-first:

```text
deterministic issue
  → selected case context and explicit consent
  → bounded contextual conversation
  → grounded explanation/question/proposal
  → explicit learner review and manual revision
  → deterministic re-check
  → Evidence Delta / History
```

The first slice is `Verification Detail → Help me understand this feedback`.
Reflection questions, wording alternatives, next-action guidance, and “what
changed” explanations reuse the same server context and output validator. The
approved workflow is the Evidence-Grounded AI Loop:

```text
workspace/case
  → requirement + Evidence Lens + selected evidence/limitations
  → claim + scope + limitation + next action
  → deterministic feedback/gap/action/delta
  → context preview + explicit consent + credit state
  → server-rebuilt case context
  → bounded conversation for this case/revision
  → grounded explanation/question/draft proposal
  → learner reviews, edits, applies, or dismisses
  → existing draft reducer only after explicit apply
  → deterministic re-check
  → Evidence Delta / History
```

This is the D-106 case-AI path. D-119 separately defines project-bound help
across supported project stages and an unlinked General chat mode; those modes
must not inherit case context and remain implementation- and provider-gated.

The repository requires an opted-in assist request to carry typed context (case
version, deterministic feedback status, selected anchors, limitations, and
bounded learner fields). The API rehydrates the published case, validates case
identity and every referenced anchor/limitation, and builds a redacted
canonical prompt before the credit/provider boundary. Every conversation turn
must rebuild that context and reject a stale case/draft/evaluation fingerprint.

AI may propose changes only to learner-authored claim text, claim scope,
limitation wording, or next-action notes. Proposals show before/after values and
approved anchors; the learner must explicitly apply them through the existing
draft reducer, after which deterministic verification, Evidence Delta, and
History run normally. AI never adds evidence, changes evaluator status,
completes an action, changes billing/credits, or edits repository source code.

The provider remains disabled by default, and provider-output validation,
privacy approval, and live-provider evidence are still open. The deterministic
workflow and safe disabled-provider fallback remain the current behavior. A
bounded contextual conversation is allowed, but there is no generic global chat
destination, unlimited AI chat, automatic rewrite, AI score, or provider-
generated evidence in the approved roadmap.

### Managed operations

- Treat ASP.NET Core, PostgreSQL, and the worker as the optional online platform
  boundary; they are not prerequisites for the bundled free case.
- Activate managed hosting only through a separately approved release plan
  with secrets, migrations, backups, monitoring, rollback, and ownership
  defined.
- Add queues, caches, or service decomposition only in response to measured
  product or operational needs.

## Explicit non-goals until justified

- Arbitrary document ingestion, OCR, or claims of source verification.
- A generic scientific-truth grader, plagiarism detector, or automatic report
  writer.
- An automatic academic grader or a universal evaluator for every method and
  discipline.
- A full course marketplace or learning-management system before the applied
  student-project loop is validated.
- A mandatory hypothesis field for every kind of academic project.
- AI as the primary evaluator.
- Unlimited AI chat, anonymous AI access, or AI credit balances controlled by the
  mobile client.
- Teacher surveillance or unrestricted draft access.
- Realtime infrastructure, Redis, microservices, or scale work without a
  measured requirement.
- Feature breadth that weakens the core evidence-to-claim experience.

## Acceptance principles

- Every displayed assessment has an inspectable rule and evidence anchor.
- Draft completeness, evidence support, and claim assessment remain distinct.
- Missing or ambiguous evidence produces an explicit limitation, not a
  fabricated pass.
- Paid access changes content availability, never evaluator standards.
- Plans, mocks, compilation, and local integration tests are not substitutes
  for device, provider, human, or managed-environment evidence.

## Related engineering documents

- [Product contract](product/m0-product-contract.md)
- [System architecture](architecture/repository-structure.md)
- [D-106 AI assistance decision](decisions.md#d-106-use-an-evidence-grounded-ai-loop-for-project-assistance)
- [Testing strategy](testing.md)
- [Next Gen release checklist](release.md)
- [RevenueCat integration](architecture/revenuecat.md)
