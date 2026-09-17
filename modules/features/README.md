# Features module

This Kotlin Multiplatform module owns framework-neutral presentation decisions
for the user-facing learning flow:

- onboarding copy and the free-learning policy;
- guide topics and their ordered explanations;
- home-state actions, observation preview, and recommendation surface state;
- history/cloud-sync availability and accessibility disclosure semantics.

It consumes domain, application, and local-data contracts. It owns no
evaluator truth, credentials, network transport, billing SDK, or platform
storage. Compose screen implementations remain in `apps/mobile-shared` until
the remaining audio and design-system seams can move without introducing a
cycle.
