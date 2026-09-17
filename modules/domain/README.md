# Domain module

This module owns Evidrilo's framework-independent learning domain:

- deterministic conclusion-chain cases and evaluation;
- bounded feedback and revision state;
- practice reducers and domain models;
- the `evidrilo_pro` entitlement identifier used by domain access rules.

The module has no Compose, RevenueCat, network, database, platform SDK, or
provider dependency. Mobile, API, persistence, and billing code consume these
contracts through an explicit project dependency. The existing Kotlin package
names remain stable during this first extraction so the migration is reversible.

Run the focused checks with:

```bash
./gradlew :modules:domain:jvmTest :modules:domain:compileKotlinJvm
```
