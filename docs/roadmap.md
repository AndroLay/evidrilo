# Evidrilo Roadmap

This roadmap describes product direction and sequencing. It is not a live
progress ledger, delivery promise, or proof that a listed capability is already
available. Current implementation status belongs in the project's private
evidence records; technical procedures live in the linked engineering docs.

## Product outcome

Evidrilo is an evidence-grounded academic workspace. It helps a learner connect
a task requirement to supplied evidence, make a bounded claim, choose a
defensible next action, and revisit that reasoning when the evidence changes.

The initial proof is deliberately narrow: one fictional tablet-dissolution
case and a deterministic feedback loop. The product direction may extend beyond
STEM, but broader subject coverage must be demonstrated with reviewed cases
before it is claimed.

## Competition-sized product slice

The first complete slice should show one coherent learner journey:

1. Read the task, supplied observations, and stated limitations.
2. Trace the requirement to relevant evidence.
3. Write a learner-owned claim, scope, limitation, and next action.
4. Review transparent, fact-anchored checks, including abstention when evidence
   is insufficient.
5. Make one revision and compare it with the original.
6. Reconsider the claim after a controlled evidence change and inspect local
   history.

The free reasoning loop must remain usable without an account, network,
backend, AI provider, or purchase. RevenueCat may unlock additional reviewed
cases and an optional bounded AI allowance; it must not sell truth, safer
feedback, or the core verification rules.

First launch is a short guide followed by the local Home flow. The learner can
start or skip the guide and continue offline. After meaningful free value, a
dismissible account prompt may explain verified benefits without becoming a
login wall. Email and Google sign-in use the provider-neutral PKCE account
boundary. Local reminders are opt-in, off by default, configurable by category
and cadence/time, and disableable from Settings; remote push is deferred.

For the current Next Gen candidate, the two premium cases remain bundled in
the mobile app and are not fetched from the platform API. RevenueCat
`CustomerInfo` controls access to those bundled cases. The API, sync, analytics,
and AI boundaries follow D-101; a future online platform must not be implied by
the submission MVP.

## Longer-term sequence

### 1. Reliable local learning loop

- Keep task, evidence, claim boundary, action, revision, and comparison state
  connected to one active case identity.
- Preserve drafts and history locally, support reset/recovery, and make offline
  behavior explicit.
- Keep deterministic evaluation explainable and test abstention, ambiguity,
  removed evidence, and unfair input conditions.
- Validate accessibility and user comprehension before broadening the feature
  set.

### 2. Optional account and sync

- Add accounts only when cross-device continuity is a demonstrated need.
- Make sync opt-in and consented; send the minimum data necessary.
- Define conflict resolution, export, deletion, retention, and recovery before
  enabling real learner data.
- Preserve the complete local free workflow when authentication or the API is
  unavailable.

The current platform sync is intentionally metadata-only: it records bounded
commands and snapshot digests rather than learner-authored drafts. It must not
be marketed as full cloud workspace restoration until server-owned workspace,
draft, evidence, feedback, revision, and conflict-resolution APIs exist.

### 2A. Platform API semantic closure

Before describing Evidrilo as a complete online platform, the API roadmap must
close these boundaries:

- keep the current submission's two premium cases bundled/local and gated by
  verified RevenueCat `CustomerInfo`; if a later release serves premium cases
  remotely, add server-side entitlement authorization before exposing them;
- keep client analytics as telemetry unless server-owned attempt/evaluation
  records are introduced for teacher-facing assessment;
- implement the approved AI credit ledger and entitlement-period grants, or
  keep live AI disabled and remove live-credit claims;
- verify RevenueCat product identifiers and safely classify test/diagnostic
  events before granting access;
- keep organization roles separate from infrastructure operators and do not
  claim an unimplemented global admin role;
- define account export, retention, and the boundary between platform deletion
  and Supabase Auth identity deletion;
- distinguish local diagnostic health responses from a hosted readiness contract.

These are platform correctness gates, not reasons to delay the offline-first
submission core.

### 3. Learning history and recommendations

- Start with transparent, deterministic summaries of completed attempts and
  recurring skill gaps.
- Recommend a next case only when there is enough reviewed content and a clear
  reason the learner can inspect.
- Do not infer learning efficacy, retention, or ability from a small formative
  sample or raw completion counts.

### 4. Governed content operations

- Version case schemas and content.
- Validate references and evaluator fixtures automatically.
- Require human review before publishing a case; retain approval, version, and
  rollback history.
- Build authoring tools before teacher dashboards so the content and review
  lifecycle has a trustworthy foundation.

### 5. Teacher-facing views

- Add role-based educator tools only after authorization, consent, data
  minimization, and classroom workflows are validated.
- Prefer aggregate progress and skill summaries over unrestricted access to
  learner drafts.
- Include suppression, auditability, retention, and deletion controls from the
  beginning.

### 6. Bounded AI assistance and credits

AI is a deliberately narrow premium extension described in the
[AI assistance contract](architecture/ai-assistance.md). It may explain a
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
  → one grounded assist
  → manual learner revision
  → deterministic re-check
  → Evidence Delta / History
```

The first slice is `Verification Detail → Help me understand this feedback`.
Reflection questions, wording alternatives, and “what changed” explanations
reuse the same server context and output validator. A response is accepted only
when its anchor references belong to the active case projection; otherwise the
credit reservation is released and the deterministic workflow remains intact.
There is no generic chat destination, automatic rewrite, AI score, or provider
generated evidence in the approved roadmap.

### 7. Managed operations

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
- [AI assistance and credit contract](architecture/ai-assistance.md)
- [Testing strategy](testing.md)
- [Next Gen release checklist](release.md)
- [RevenueCat integration](architecture/revenuecat.md)
