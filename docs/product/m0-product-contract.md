# Evidrilo M0 Product Contract

## Scope and authority

This file is the approved base-case and deterministic-evaluator contract. It
defines the supplied tablet-dissolution scenario, supported inputs, feedback
rules, and fairness constraints; it is not a live implementation or milestone
status report. It is the current Shipaton/M0 contract and is intentionally not a
specification for future courses or student-authored research projects. The
long-term direction is recorded in D-108 and the
[current roadmap](../roadmap.md), while release and test
procedures are in the [roadmap](../roadmap.md), [testing guide](../testing.md),
and [release checklist](../release.md).

## Product intent

Within Evidrilo's academic workspace, this case helps a student connect
supplied observations and limitations to a properly scoped claim, an
explainable next action, and one revision. The case is a bounded first example
of the product mechanism, not the full product identity. The evaluator is not a
grading tool, scientific-truth oracle, or answer generator.

The intended product user is an active student working on an evidence-heavy
academic task. In implementation contracts, `learner` is the internal role
name for that student; it does not expand the primary audience to unspecified
adult learners or professional users. Formative research participation has
separate adult-consent requirements.

## M0 case: tablet dissolution

The application supplies one low-risk educational case:

> Compare the time required for the same tablet to dissolve in warm water,
> room-temperature water, and cold water.

The supplied facts are:

| ID | Type | Fact | Boundary |
|---|---|---|---|
| `AIM-01` | Aim | Compare dissolution time across warm, room-temperature, and cold water. | The only case goal. |
| `HYP-01` | Supplied context | The case supplies a hypothesis that expects faster dissolution in warmer water. | Not a student-authored hypothesis and not evidence. |
| `OBS-WARM-01` | Observation | Warm water: 32 seconds. | One supplied observation. |
| `OBS-ROOM-01` | Observation | Room temperature: 58 seconds. | One supplied observation. |
| `OBS-COLD-01` | Observation | Cold water: 92 seconds. | One supplied observation. |
| `LIMIT-TRIAL-01` | Limitation | Each condition was measured once. | Limits generalization and certainty. |
| `LIMIT-STIR-01` | Limitation | Stirring speed was not measured with an instrument. | Limits causal claims about temperature. |
| `BOUND-01` | Boundary | The case does not establish a general causal effect. | Rejects unsupported universal or causal claims. |

A strong conclusion can say that the warm-water sample dissolved faster than the
room-temperature and cold-water samples in these observations, while limiting
the claim because each condition was measured once and stirring was not
controlled. A relevant next action is repeating the trials and/or controlling
stirring speed.

The product must not state that temperature definitely causes faster dissolution
in all conditions, invent new measurements, or add an explanation not present in
the fact card.

## Supported input contract

The hybrid input model makes important relationships auditable while preserving
learner-authored language.

| Field | Type and constraint |
|---|---|
| `case_id` | Fixed value `evidrilo-m0-t2-v1`. |
| `relation` | `observed_difference`, `limited_observation`, or `cannot_conclude_from_case`. |
| `evidence_refs` | One to three supplied `OBS-*` IDs, consistent with the relation. |
| `claim_text` | One to three learner-authored sentences, 20–320 characters. |
| `scope` | `this_observation`, `limited_comparison`, or `general_causal_claim`. |
| `limitation_refs` | One or two supplied `LIMIT-*` IDs when the selected scope requires them. |
| `limitation_note` | Learner-authored explanation, 10–240 characters. |
| `implication` | `repeat_trials`, `control_stirring`, `limit_claim`, or `not_applicable`. |
| `implication_reason` | Required for `not_applicable`; otherwise 10–240 characters tied to a relevant limitation. |
| `revision` | One mutable copy after feedback; the initial draft remains immutable. |

Fact IDs are the primary anchors. The learner may paraphrase a fact, but the
corresponding ID must be selected. New numbers, units, conditions, causes, or
universal claims are not validated by the application. If a conservative check
detects an unsupported literal or contradiction that the bounded evaluator
cannot safely interpret, it returns `CANNOT_ASSESS`.

## Evaluator contract

The evaluator performs four bounded checks:

1. **Goal connection** — the selected relation addresses `AIM-01`.
2. **Evidence anchoring** — selected observation IDs support the relation.
3. **Scope and uncertainty** — the scope respects the supplied limitations and
   does not turn an observation into an unsupported causal or universal claim.
4. **Actionable implication** — the next action is tied to a limitation or the
   learner explicitly explains why no further action is applicable.

Each check returns a status, anchor IDs, and a short reason. The evaluator may
return `PASS`, `ACTION_REQUIRED`, `INCOMPLETE`, or `CANNOT_ASSESS`.

### Feedback priority

Only one primary feedback item is shown in M0. Priority is deterministic:

1. `INCOMPLETE` required input (`P0`)
2. unsupported or contradictory content that cannot be safely assessed (`P1`)
3. scope or causal overclaim (`P1`)
4. missing or mismatched evidence anchor (`P2`)
5. missing limitation or implication (`P2`)
6. goal connection improvement (`P3`)

Ties are resolved by the order above and then by the stable evaluator rule ID.
Every non-pass result must name the relevant fact ID or input field. If the
evaluator cannot justify a result from the case contract, it abstains instead
of guessing.

## Adversarial fixture requirements

The common test suite must include at least:

- a complete supported observation and limited-comparison conclusion;
- empty and partially completed input;
- invalid or external fact IDs;
- a missing comparator;
- a causal overclaim with one or both limitations omitted;
- a safe paraphrase that does not depend on exact keywords;
- a negation that the bounded parser must not guess;
- an unsupported number or external cause;
- conflicting issues where only the highest-priority feedback is shown;
- an immutable initial draft and a rejected second revision.

Expected status, priority, anchors, and reason must be written independently of
the evaluator implementation.

## Fairness contract

Any future comparison must give the baseline conditions and Evidrilo the same:

- fact card and visible IDs;
- output schema and task wording;
- time budget and revision opportunity;
- draft capture and revision explanation prompt.

The comparison may test structure and feedback sequence, but not hidden facts,
extra time, or a revision prompt available only to Evidrilo. Synthetic fixtures
and automated review are not human ground truth. Two independent qualified
reviewers must review the same frozen protocol before participant sessions begin.

## Free core and premium boundary

The free case includes the full learning loop and is not crippled to force a
purchase. The premium case pack uses two additional bounded cases:

- tablet form — whole versus crushed;
- water volume — 100 mL versus 200 mL.

Premium content is not evidence of willingness to pay until real usage and
billing observations are collected. The approved price anchors are recorded in
[D-100](../decisions.md#d-100-raise-the-monthlyyearly-planning-anchors-for-the-bounded-ai-extension).
RevenueCat integration follows the contract in
[`../architecture/revenuecat.md`](../architecture/revenuecat.md).

## Optional AI assistance contract

AI is an optional assistance layer over the deterministic result. It may explain
the primary feedback item, ask a reflection question, or offer a
meaning-preserving language alternative. It may not assess scientific truth,
invent facts/evidence/sources, accept a requirement, replace learner reasoning,
or change evaluator status, anchors, priority, or abstention.

The approved allowance is 10 AI credits once for a verified free account after
explicit consent, and 100 credits per active `evidrilo_pro` entitlement month
for monthly or yearly subscribers. Credits do not roll over; lifetime
allowances and top-ups are not initially supported. One accepted standard assist
costs one credit. Timeout, cancellation, provider failure, malformed output,
policy rejection, and unavailable-provider results release the reservation.

The server ledger and verified entitlement are authoritative. The learner
selects the context to share; the client must not silently upload the whole
draft or send provider secrets. AI output is labelled as assistance and is
shown alongside the deterministic feedback, never as a replacement for it.
When AI is disabled or unavailable, the M0 workflow remains complete.

## First launch, Get Started, authentication, and notifications

First launch opens Home. The Get Started tour is available only when the
student explicitly opens it, remains replayable from Home/Help, and targets
approximately three minutes. It
uses a clearly labeled synthetic demo, walks through a reviewed template when
one is available (otherwise it must label the example as a demo), a task
brief/question, a source, a source-linked finding, a short evidence-change
case, and a bounded output with visible limitations and next action. The demo
does not create or prefill a real student project or make academic grades or
universal-truth claims.

- A signed-out student may start the demo, choose a supported sign-in method,
  or skip Get Started. The demo is preview-only. Local Project Workspace work
  is separate and does not require authentication; the M0 learning flow remains
  account-gated.
- After successful Google sign-in/sign-up, restore the requested destination
  without forcing the full-screen tour. The Home guide entry remains available
  without interrupting local project work.
- Skipping Get Started opens the normal entry flow; it does not grant access to
  account-bound learning, AI, billing, or sync. Keep skipped and completed
  states distinct; make the tour replayable from Help/Profile, and do not
  automatically replay a completed tour.
- Sign-in does not itself upload project drafts or promise cloud backup/sync.
  Existing local-storage, consent, and data-transfer boundaries remain in
  force.

This access policy supersedes D-102's account-optional entry rule for
account-bound learning. D-120 supersedes D-118 only for local Project Workspace
and anonymous read-only catalog access; D-121 clarifies Home-first entry and
optional guide presentation. These decisions do not remove account requirements
for M0 learning, project AI, billing entitlements, or consented cloud services.
The client has a fail-closed route policy, protected account-bound operations,
a synthetic Get Started tour, and distinct persisted
`not_started`/`skipped`/`completed` states with legacy completion migration.
Local tests and Compose compilation do not prove Android provider/runtime,
accessibility, or device acceptance. See [D-120](../decisions.md#d-120-keep-project-workspace-local-first-and-available-without-an-account).

Email sign-up/sign-in/recovery and Google sign-in are supported through the
provider-neutral account boundary. Google uses the configured Supabase OAuth
authorization-code flow with PKCE and validated state/callback handling. A
cancelled, offline, unconfigured, or failed auth attempt returns safely to
the signed-out entry/demo flow and must not unlock account-bound functionality.
Local project work remains available.

When already signed in, a student can separately choose `Link Google to this
account`. This authenticated identity-link flow must preserve the current
account ID, validate the returned Google identity before saving the new
session, and reject an identity already associated with another account
without merging or switching accounts. Cancellation or failed verification
preserves the existing session. Supabase manual identity linking must be
enabled; source/tests do not by themselves prove live provider configuration or
runtime behavior.

A verified signed-in account may request a versioned export of server-owned
metadata through the account surface. The export does not include learner
drafts because drafts remain local while sync is metadata-only. Server-owned
deletion requires explicit confirmation and signs the device out; clearing the
local draft/history and deleting the managed Supabase Auth identity are separate
operations and must not be implied by one another.

Notifications are optional local reminders for `Continue an unfinished case`
and learner-enabled `Review a completed case`. Both categories are off by
default. They require explicit opt-in, a learner-selected daily/weekly cadence
and local time, are controlled from Settings with a master toggle and category
controls, and can be disabled without deleting drafts or history. Permission
denial must provide an OS-settings path and must never block the free workflow.
Remote push and promotional campaigns are deferred until a separate managed
notification boundary is approved.

## Care, privacy, and accessibility

- Keep project data local-first after authentication; authentication alone does
  not authorize upload or imply cloud sync.
- Do not store participant identity, reviewer contact details, or private draft
  data in the public repository.
- Communicate abstention as “cannot assess from the supplied information,” not
  as a grade or diagnosis.
- Expose status through text and semantic labels, not color alone.
- Test text scaling, screen readers, focus order, contrast, tap targets, reset,
  back navigation, relaunch, and offline free-core behavior.

## Verification boundary

This contract specifies expected content and evaluator behavior only. Tests
must validate the rules and adversarial cases above; device/runtime,
accessibility, RevenueCat provider, human-review, publication, and submission
claims require separate evidence. Check the current project source of truth and
dated audit records before describing implementation progress.
