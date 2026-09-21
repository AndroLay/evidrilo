# Platform Decision

## Decision

Build the mobile application with Kotlin Multiplatform and Compose
Multiplatform. Keep Android and iOS as separate platform hosts while sharing
the app experience and deterministic product rules through
`apps/mobile-shared` and the verified Kotlin modules.

Use ASP.NET Core/.NET and PostgreSQL for optional online capabilities. Keep
the API a capability-organized modular monolith and the worker a separate
bounded process. The bundled free learning loop must not require an account,
network, API, database, AI provider, or purchase.

RevenueCat is isolated behind the mobile billing boundary. It controls
premium content access only; evaluator behavior and free learning remain
available independently.

## Why this fits

- It matches the owner's preference for Kotlin and shares core rules across
  Android and iOS.
- Compose Multiplatform lets the first product keep a consistent interaction
  model without creating two independent feature implementations.
- ASP.NET Core and PostgreSQL provide a single online platform foundation
  without splitting a small product into microservices.
- A narrow RevenueCat adapter keeps purchase-provider details out of domain
  logic.

## Accepted trade-offs

- iOS still requires a macOS/Xcode host for simulator/device runtime and
  distribution evidence.
- Shared Kotlin compilation is not proof that the iOS host launches or that
  native accessibility behavior is correct.
- Android and iOS still need their own store configuration and billing
  provider setup.
- Optional online capabilities introduce authentication, consent, privacy,
  database, and operational requirements; they must not be activated by
  merely compiling the code.

## Alternatives not selected

- Android-only Kotlin would not meet the intended Android+iOS direction.
- Kotlin business logic with SwiftUI would duplicate the first UI and is not
  the current Kotlin-first choice.
- Flutter or React Native are viable technologies but do not match the
  selected Kotlin stack.
- Separate .NET Application/Domain/Infrastructure projects are deferred
  until a verified dependency split requires them.

## Evidence boundary

This document records an architecture decision, not runtime status. A
platform claim must be supported by a test on that platform. A local API or
PostgreSQL integration does not establish managed hosting. Current gate
status is maintained in the private project source of truth, not duplicated
here.

## References

- [Repository structure](repository-structure.md)
- [RevenueCat boundary](revenuecat.md)
- [Product roadmap](../roadmap.md)
- [Kotlin Multiplatform](https://kotlinlang.org/docs/multiplatform.html)
- [Compose Multiplatform](https://www.jetbrains.com/compose-multiplatform/)
