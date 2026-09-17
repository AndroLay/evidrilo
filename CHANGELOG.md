# Changelog

All notable repository changes are recorded here. The current marketing
version is defined in [`version.props`](version.props).

## [0.1.0] - Unreleased

### Changed

- Extracted the framework-independent learning domain into
  `modules/domain` while retaining stable Kotlin package names and the
  compatibility tasks `:composeApp` and `:androidApp`.
- Separated versioned contract schemas from fixtures under `contracts/schemas`,
  and organized verification, security, release, CI, GitHub, and worktree
  scripts under explicit ownership lanes.
- Added repository governance, source-only hygiene, and public-package safety
  documents for the next structural migration phase.

### Verification boundary

This is not a production or store release. Device runtime, iOS/Xcode,
RevenueCat transactions, managed deployment, accessibility sign-off, human
validation, and submission evidence remain separate gates.
