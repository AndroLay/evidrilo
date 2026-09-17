# Application module

This Kotlin Multiplatform module owns the provider-neutral application layer
that sits above `modules/domain` and `modules/data` and below the Compose host.

It currently contains:

- account/session contracts, PKCE and bounded HTTP/auth transport;
- secure-session adapters and platform storage factories;
- consented analytics event/gateway contracts;
- offline-safe sync queue/coordinator and request gates;
- recommendation parsing, retry, lifecycle, and interaction orchestration.

The package names remain `dev.nextgen.mobile.*` to avoid a behavior-changing
namespace migration. The module deliberately has no Compose, RevenueCat, or
backend dependency. The shared app consumes this layer through
`api(project(":modules:application"))`; UI screens and billing adapters remain
in the host until their boundaries can be extracted without a cycle.
