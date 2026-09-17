# Core module

This Kotlin Multiplatform module owns the framework-neutral cryptographic
foundation used by account and sync flows:

- PKCE verifier/challenge generation;
- bounded SHA-256 and URL-safe encoding helpers;
- platform secure-random implementations for Android, iOS, and JVM.

It has no Compose, network, account provider, database, or billing dependency.
Account transport/session orchestration remains in `modules/application`, which
consumes this module through an explicit API dependency.
