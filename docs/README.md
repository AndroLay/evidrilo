# Engineering Documentation

This directory contains the public engineering contract for Evidrilo. It is
deliberately small: each document exists to help a contributor build, test, or
review the application.

## Start here

1. [`development.md`](development.md) — local setup and useful commands.
2. [`roadmap.md`](roadmap.md) — end-to-end goals, milestones, gates, and
   definition of done, including the current evidence-change challenge and
   Next Gen submission gates.
3. [`product/m0-product-contract.md`](product/m0-product-contract.md) — the
   approved M0 boundary and its implementation limits.
4. [`architecture/repository-structure.md`](architecture/repository-structure.md)
   — ownership and dependency direction.
5. [`testing.md`](testing.md) — verification levels and evidence limits.
6. [`decisions.md`](decisions.md) — durable decisions and their consequences.

## Reference

- [`architecture/platform-decision.md`](architecture/platform-decision.md) —
  Android/iOS platform choice.
- [`architecture/revenuecat.md`](architecture/revenuecat.md) — billing
  boundary and RevenueCat integration contract.
- [`release.md`](release.md) — release-readiness gates.
- [`../CONTRIBUTING.md`](../CONTRIBUTING.md) — contribution and review rules.

## Documentation boundary

Research notebooks, audit records, participant or reviewer material, video
notes, candidate comparisons, submission drafts, and internal planning files
are kept outside the public Git history. They may remain in a local workspace,
but they are not part of the application repository and must not be referenced
as public evidence.

All documentation committed to this repository is written in English and must
be understandable from a clean clone.
