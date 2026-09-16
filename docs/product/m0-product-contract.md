# Evidrilo M0 Product Contract

## Status

`APPROVED_FOR_M1 / CURRENT_M2_OVERLAY` — owner-approved foundation; not
standalone runtime, billing, or user evidence.

The owner approved the complete M0 contract on 9 September 2026. This approval
authorizes the pure Kotlin M1 domain slice only. It does not claim a working
mobile product, iOS runtime support, billing success, reviewer agreement,
participant results, or submission readiness.

This document defines the first bounded Evidrilo slice. It does not claim an
implemented product loop, iOS runtime support, billing success, reviewer
agreement, participant results, or submission readiness.

The current implementation adds one evidence-change challenge that omits
`OBS-COLD-01` and one latest local comparison-history entry. Those additions are
specified in [`docs/roadmap.md`](../roadmap.md) and are the active M2 target;
this M0 document remains the base contract and is not rewritten as a runtime
record.

## Product intent

Evidrilo helps a learner turn supplied observations and limitations into one
appropriately scoped conclusion, receive feedback that can be traced to those
facts, and revise the conclusion once.

The first user is an adult learner who has a practical result set but needs to
practise connecting evidence, claim scope, limitations, and a next action. The
experience is a reasoning and revision exercise, not a report editor, grading
tool, scientific truth oracle, or answer generator.

## M0 case: tablet dissolution

The application supplies one low-risk educational case:

> Compare the time required for the same tablet to dissolve in warm water,
> room-temperature water, and cold water.

The supplied facts are:

| ID | Type | Fact | Boundary |
|---|---|---|---|
| `AIM-01` | Aim | Compare dissolution time across warm, room-temperature, and cold water. | The only case goal. |
| `HYP-01` | Context | The practice hypothesis expects faster dissolution in warmer water. | Not evidence. |
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
purchase. The proposed premium pack contains two distinct practice cases:

- spring load-extension, focusing on tested range and extrapolation limits;
- pendulum length-period, focusing on trend interpretation and manual
  measurement limitations.

Premium content is not evidence of willingness to pay until real usage and
billing observations are collected. The current subscription direction and
price hypothesis are recorded in the [monetization and pricing note](../business/monetization-and-pricing.md).
RevenueCat integration follows the contract in
[`../architecture/revenuecat.md`](../architecture/revenuecat.md).

## Care, privacy, and accessibility

- Keep the first flow local-first; no account, upload, or required network.
- Do not store participant identity, reviewer contact details, or private draft
  data in the public repository.
- Communicate abstention as “cannot assess from the supplied information,” not
  as a grade or diagnosis.
- Expose status through text and semantic labels, not color alone.
- Test text scaling, screen readers, focus order, contrast, tap targets, reset,
  back navigation, relaunch, and offline free-core behavior.

## Milestone gates

- **M0:** this contract is reviewed and approved; no product code is implied.
- **M1:** pure Kotlin domain models, evaluator, fixtures, and common tests.
- **M2:** free-core Android/iOS UI with offline and accessibility checks.
- **M3:** RevenueCat Test Store integration and failure matrix.
- **M4:** frozen protocol, two human reviewer preflights, and participant
  access authorization.
- **M5:** reproducible repository, assets, English description, and demo video.

Each milestone requires its own evidence. Passing an earlier milestone does not
prove a later platform, billing, human-review, or participant result.
