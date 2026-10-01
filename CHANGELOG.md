# Changelog

All notable repository changes are recorded here. The current marketing
version is defined in [`version.props`](version.props).

## [1.0.0] - 2026-10-01

### Added and improved

- Student project workspace, catalog-first creation, evidence/claim mapping,
  review, portability, and three bundled Practice cases (one Free, two Pro).
- Language-first introduction, English/Indonesian UI, concise account profile,
  and Google connection inside account details.
- Hosted Render Staging API/worker with Supabase Auth/PostgreSQL and bounded,
  consent-based Experiential Luna 6 chat. Project edits remain reviewable proposals.
- RevenueCat Test Store monthly/annual access, restore, and repeat-safe
  200-credit subscription periods; Free receives a one-time 20-credit allowance.
- Downloadable Android judging APK and a CI-built iOS simulator package.

### Release boundary

This is the 1.0.0 judging / Staging release, not Production store distribution.
Payments are Test Store only. iOS simulator packages require macOS/Xcode and
are not installable iPhone IPAs; Apple signing is not available. Device/provider
acceptance and remaining risks are recorded in the release notes.

## [0.1.0] - Historical development baseline

### Changed

- Extracted the framework-independent learning domain into
  `modules/domain` while retaining stable Kotlin package names and the
  compatibility tasks `:composeApp` and `:androidApp`.
- Extracted the shared Compose visual system and its runtime fonts/assets into
  `modules/design-system`, with an isolated generated-resource namespace for
  safe Android packaging.
- Extracted local draft, history, onboarding, codec, and corruption-recovery
  persistence into `modules/data` with unchanged package names and storage
  formats across Android, iOS, and JVM verification targets.
- Extracted `modules/core`, `modules/application`, and `modules/features` from
  the shared host without changing package names or product behavior.
- Extended the local and CI verification lanes to run every extracted module and
  added a regression check for release-version alignment.
- Fixed API contract-boundary tests to read the canonical root
  `contracts/schemas/` layout.
- Separated versioned contract schemas from fixtures under `contracts/schemas`,
  and organized verification, security, release, CI, GitHub, and worktree
  scripts under explicit ownership lanes.
- Added repository governance, source-only hygiene, and public-package safety
  documents for the next structural migration phase.

### Verification boundary

This is not a production or store release. Device runtime, iOS/Xcode,
RevenueCat transactions, managed deployment, accessibility sign-off, human
validation, and submission evidence remain separate gates.
