# Contributing to Evidrilo

## Scope

Contributions should improve the current milestone without silently expanding
the product boundary. Read the relevant documents in `docs/` before changing
domain rules, billing, persistence, or platform behavior.

## Branches and commits

- Use a focused branch for a coherent change.
- Keep commits small enough to review and explain.
- Use imperative English commit messages, for example:
  `Add deterministic evidence-anchor evaluator`.
- Do not commit local configuration, credentials, receipts, participant data,
  research notebooks, audit records, media, or submission drafts.

## Code changes

- Put shared behavior in pure Kotlin when platform behavior is not required.
- Keep platform APIs behind small adapters.
- Add deterministic common tests for every domain rule and edge case.
- Preserve explicit abstention when the evaluator cannot safely assess input.
- Do not use an opaque AI service as an untested grading oracle.

## Documentation changes

Public documentation must be in English, self-contained, and free of links to
local-only evidence. Update `docs/decisions.md` when a durable architecture,
scope, privacy, or verification decision changes.

## Required checks

Run the relevant focused tests and, after staging only the intended public
files and before opening a review, run:

```bash
./gradlew :composeApp:jvmTest :composeApp:compileKotlinJvm
./gradlew :androidApp:assembleDebug
bash scripts/security/check-github-safety.sh .
```

The Git safety check must pass before committing or pushing. It is designed to
fail when a non-ignored untracked file, private/generated path, credential file,
secret-shaped value, or non-allowlisted tracked path could enter the public
repository. In a mixed workspace without usable Git metadata, export a fresh
candidate with `bash scripts/github/export-public-package.sh` and validate that
candidate instead.

If a platform or environment cannot be tested, report it as `NOT_RUN` or
`UNKNOWN`; do not infer a successful runtime result from compilation.

## Review expectations

Reviewers should check behavior, tests, scope, accessibility, privacy, and
claim accuracy. A change is not complete merely because it builds.
