# Data module

This KMP module currently owns local persistence for the free learning core:
draft/session snapshots, comparison history, onboarding completion, corruption
recovery, and the Android/iOS/JVM storage adapters. It keeps the existing
`dev.nextgen.mobile.storage` package and serialized format so installed app
data remains compatible across the move.

The module depends on the framework-independent domain only. Network, sync,
AI, documents, and billing adapters remain in the shared app until each has a
separate interface and regression coverage.

Run the focused checks with:

```bash
./gradlew :modules:data:jvmTest :modules:data:compileKotlinJvm
```
