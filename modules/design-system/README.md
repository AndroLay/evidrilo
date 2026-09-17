# Design system module

This Compose Multiplatform module owns the shared visual tokens, components,
icons, typography, fonts, and the reviewed visual assets consumed by the
mobile app. It has no domain, account, network, billing, or platform-provider
dependency.

The source-of-truth design files remain under ignored `internal/design/`; only
the selected runtime assets required by the app are kept in this public module.

Run the focused checks with:

```bash
./gradlew :modules:design-system:jvmTest :modules:design-system:compileKotlinJvm
```
