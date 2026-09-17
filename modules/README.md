# Kotlin module map

The `modules/` tree contains reusable Kotlin boundaries. A directory becomes a
production module only when it owns code, tests, and an enforceable dependency
direction; directories are not created as decorative placeholders.

## Current extraction

- [`core/`](core/) is a real multiplatform foundation module for PKCE and
  platform secure-random primitives.
- [`domain/`](domain/) is a real multiplatform module for deterministic
  learning rules, conclusion-chain evaluation, bounded feedback, practice
  reducers, and entitlement identifiers.
- [`application/`](application/) is a real multiplatform application module for
  account/session orchestration, consented analytics, sync coordination, and
  recommendation workflows.
- [`design-system/`](design-system/) is a real Compose Multiplatform module for
  shared visual tokens, components, icons, fonts, and reviewed visual assets.
- [`data/`](data/) is a real multiplatform module for local persistence,
  recovery status, and platform storage adapters.
- [`features/`](features/) is a real multiplatform presentation-contract module
  for onboarding, guide, home, history, and disclosure state.

## Remaining boundaries

Compose screen files that still require host-owned audio, billing, and navigation
adapters remain in `apps/mobile-shared`. Network/content-reader, audio, and
RevenueCat adapters remain intentionally at that host boundary until their
interfaces can be extracted without a dependency cycle. No module is a claim
of provider, device, managed-deployment, or human-runtime evidence.
