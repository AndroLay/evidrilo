# Engineering Documentation

This directory contains the public engineering contract for Evidrilo. It is
deliberately small: each document exists to help a contributor build, test, or
review the application.

## Choose documentation by task

| Task | Start with |
| --- | --- |
| Latest observed runtime and remaining release work | [`development/current-status.md`](development/current-status.md) |
| Understand current product scope | [`product/m0-product-contract.md`](product/m0-product-contract.md), then [`decisions.md`](decisions.md) |
| Follow student, account, project, AI, and billing journeys | [`product/workflows.md`](product/workflows.md) — includes the detailed Mermaid diagrams and marks gated/target paths |
| Trace runtime calls between mobile, API, providers, and storage | [`architecture/system-execution-flows.md`](architecture/system-execution-flows.md) — sequence, context, data-boundary, and local-topology diagrams |
| Home-first entry, concise first-use UX, and contextual sign-in | [D-120](decisions.md#d-120-keep-project-workspace-local-first-and-available-without-an-account), [D-121](decisions.md#d-121-adopt-home-first-local-first-ux-with-contextual-access), [D-123](decisions.md#d-123-simplify-first-use-and-project-workspace-interactions), and [`product/workflows.md`](product/workflows.md) |
| Check goals or future direction | [`roadmap.md`](roadmap.md); future direction is not implementation evidence |
| Continue the active project-template catalog work | [`roadmap.md`](roadmap.md#active-catalog-workstream-d-113), then [D-112](decisions.md#d-112-set-five-selectable-academic-project-template-catalog-families), [D-113](decisions.md#d-113-start-the-five-family-project-template-catalog-work-now), and [D-124](decisions.md#d-124-keep-the-research-core-free-and-make-pro-optional-depth-and-capacity) |
| Change mobile architecture or UI | [`architecture/repository-structure.md`](architecture/repository-structure.md), the affected module's README, and the relevant product contract |
| Accessibility or inclusive UX | [`product/m0-product-contract.md`](product/m0-product-contract.md), [`product/workflows.md`](product/workflows.md), then [`testing.md`](testing.md) and [`release.md`](release.md) for verification gates |
| Change API, database, or platform boundaries | [`../platform/README.md`](../platform/README.md), [`api/README.md`](api/README.md), and the relevant platform tests |
| Configure Google or Apple sign-in | [`architecture/apple-auth-setup.md`](architecture/apple-auth-setup.md), [D-128](decisions.md#d-128-add-apple-sign-in-without-creating-duplicate-evidrilo-accounts), [D-129](decisions.md#d-129-restore-optional-account-sign-in-while-local-guest-access-remains-active), and [D-132](decisions.md#d-132-defer-apple-sign-in-and-keep-apple-provider-configuration-empty) |
| Change AI or billing | [D-106](decisions.md#d-106-use-an-evidence-grounded-ai-loop-for-project-assistance), [D-119](decisions.md#d-119-make-project-ai-available-at-supported-stages-with-explicit-project-binding), [D-124](decisions.md#d-124-keep-the-research-core-free-and-make-pro-optional-depth-and-capacity), [`architecture/revenuecat.md`](architecture/revenuecat.md), and applicable decisions |
| Verify behavior or prepare a release | [`testing.md`](testing.md), [`release.md`](release.md), and the relevant test instructions |
| Run an operational procedure | [`operations/README.md`](operations/README.md) |

For setup commands, use [`development/`](development/). Read only the routed
documents and files required for the task; do not treat this index as a request
to read every Markdown file.

Local agent-generated plans/specs may exist under the ignored
`docs/superpowers/` directory in a development checkout; they are not part of
the public repository. They preserve task-specific history, not current product
scope or permission to resume work. Reconcile any resumed task against the
current roadmap, decision log, M0 contract, and release checklist first.

## Reference

- [`product/workflows.md`](product/workflows.md) — student-facing product
  journeys and Mermaid diagrams; target or gated paths are labeled explicitly.
- [`architecture/system-execution-flows.md`](architecture/system-execution-flows.md)
  — system context, data ownership, local runtime topology, project data model,
  and component-to-component sequence diagrams.
- [`architecture/platform-decision.md`](architecture/platform-decision.md) —
  Android/iOS platform choice.
- [`architecture/revenuecat.md`](architecture/revenuecat.md) — billing
  boundary and RevenueCat integration contract.
- [`decisions.md`](decisions.md#d-106-use-an-evidence-grounded-ai-loop-for-project-assistance)
  — the public AI boundary, including grounding, manual review, and evaluator
  authority. Detailed provider implementation notes are not public status proof.
- [`release.md`](release.md) — release-readiness gates.
- [`../CONTRIBUTING.md`](../CONTRIBUTING.md) — contribution and review rules.

## Documentation boundary

Research notebooks, audit records, participant or reviewer material, video
notes, candidate comparisons, submission drafts, and internal planning files
are kept outside the public Git history. They may remain in a local workspace,
but they are not part of the application repository and must not be referenced
as public evidence.

All documentation committed to this repository is written in English and must
be understandable from a clean clone. Public setup and builds must not depend
on the ignored local `internal/` or `audit/` directories.
