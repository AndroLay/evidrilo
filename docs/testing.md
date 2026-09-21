# Testing Strategy

This document describes how to verify the repository. It is a procedure, not
a record that a command or device test has passed. Keep dated results in the
appropriate evidence record and do not copy test counts into multiple guides.

Page, modal, transient-state, accessibility, and end-to-end UI closure are
tracked in the private control-pack UI Acceptance Matrix; the public package
does not expose internal evidence or owner-gated QA records.

## Canonical repository check

Run the full local harness from the repository root:

```
bash
bash scripts/ci/verify-local.sh
```

The harness covers Kotlin module tests and supported target compilation, an
Android release bundle when JDK 21 is available, Node contract and repository
guards, asset and deployment checks, and .NET API/worker tests when their
toolchains are available. The output explicitly reports unavailable toolchains;
do not treat a skipped/unavailable check as a pass.

## Focused checks

Use focused checks while iterating, then run the canonical harness for a
cross-boundary change:

- Kotlin domain or shared UI: run the relevant Gradle module's JVM tests;
  use task aliases defined in `settings.gradle.kts`.
- Contracts and repository guards:
  `node --test contracts/contracts.test.mjs` plus the specific
  verification test affected by the change.
- API: `dotnet test platform/api.Tests/Evidrilo.Api.Tests.csproj`.
- Worker: `dotnet test platform/worker.Tests/Evidrilo.Worker.Tests.csproj --configuration Release`.
- Asset dimensions/provenance: use the repository-owned validator referenced
  by [the release checklist](release.md).

For changes spanning mobile, contracts, database, and .NET, prefer the full
script over a hand-built subset so required guards are not omitted.

## Platform API semantic closure

The API route surface is not sufficient evidence that the online platform is
complete. Cross-boundary verification must also cover:

- current contract assertions and evidence snapshot/register references;
- the current bundled premium boundary: RevenueCat-gated local cases are not
  presented as API-served premium content; future remote premium cases require
  active, expired, unverified, and database-unavailable API tests;
- the distinction between metadata-only sync and full workspace restoration;
- protection against treating client-submitted analytics outcomes as grades or
  authoritative learning results;
- server-owned AI credit grants, reservations, consumption, release, expiry,
  entitlement-period idempotency, and balance reporting;
- configured RevenueCat product IDs, monthly/yearly-only policy, replay/order
  handling, and safe classification of test or diagnostic events;
- organization roles versus infrastructure operators, without an unimplemented
  global admin role;
- account export, retention, deletion, and the Supabase Auth identity boundary;
- readiness HTTP status semantics and dependency failure behavior.
- first-launch guide completion/skip, offline continuation, value-gated account
  prompt dismissal, Google OAuth callback/cancellation, and local notification
  permission/scheduling/disable behavior;

Historical local integration results remain tied to their original revision. A
current checkout with unavailable .NET tooling or failing contract assertions
must be reported as `NOT_RUN` or `FAILED`, never as a fresh backend pass.

## Test layers

| Layer | What it can establish | What it cannot establish alone |
| --- | --- | --- |
| Unit and property tests | Deterministic rules, state transitions, and edge cases covered by fixtures | Device behavior or user comprehension |
| Contract tests | Schema and payload compatibility for checked fixtures | A live client/server deployment |
| API/worker tests | Server logic and worker behavior under configured test conditions | Managed database/provider behavior |
| Local PostgreSQL integration | Migrations, row-level controls, and local API/worker integration exercised by the harness | Hosted Supabase configuration or production recovery |
| Kotlin target compilation | Source compatibility for the compiled targets | iOS simulator/device launch or accessibility |
| Android runtime | The exact tested build and route on the named device/emulator | Other devices, iOS, provider transactions, or learning outcomes |
| Provider test | The exact test-store configuration and transaction path exercised | Production revenue or store approval |
| Human formative session | Observed usability and comprehension for the tested protocol/sample | Population-level efficacy or general learning transfer |

## Evaluator and product invariants

Tests for the conclusion workflow should cover:

- active case identity across base work, revision, challenge, and history;
- immutable original input and the permitted revision count;
- evidence anchors, claim scope, stated limitations, and action rationale;
- the difference between draft completeness, evidence support, and claim
  assessment;
- `INCOMPLETE`, `ACTION_REQUIRED`, `PASS`, and `CANNOT_ASSESS` outcomes;
- ambiguous, removed, stale, contradictory, and unavailable evidence;
- abstention instead of fabricated support or a generic conflict label;
- free-core operation when offline or when billing/provider state is
  unavailable;
- premium access derived from the approved entitlement, never a local
  success flag.

AI and credit tests must additionally cover:

- one-time 10-credit free grant only for a verified, consented account;
- 100-credit monthly grant for both monthly and yearly active entitlements;
- no rollover, lifetime allowance, anonymous grant, or paid top-up;
- RevenueCat period/webhook replay idempotency and grant expiry;
- concurrent reservation cannot overspend or double-charge an account;
- accepted schema-valid output consumes exactly one credit;
- timeout, cancellation, provider failure, malformed output, policy rejection,
  and disabled-provider fallback release the reservation;
- account isolation, deletion behavior, request idempotency, rate limits, and
  configurable spend ceilings;
- selected-context consent, secret/PII redaction, metadata-only audit, bounded
  output schema, and no evaluator-status mutation;
- provider-unavailable operation leaves the free deterministic workflow usable.

Use adversarial fixtures to prove that plausible-sounding but unsupported
claims do not pass. A green fixture suite establishes only the implemented
rules exercised by those fixtures.

## Runtime, accessibility, and provider checks

- Test Android on a named emulator or device; capture build identity and
  observable route results.
- Test iOS only on an available macOS/Xcode simulator or device. Shared
  Kotlin/Native compilation is not an iOS runtime result.
- Review keyboard/switch and screen-reader behavior, focus order, text scaling,
  contrast, touch targets, and error/loading/empty states on the claimed
  platform.
- RevenueCat provider verification requires authorized access and a real
  Test Store run covering offering load, purchase success, cancellation or
  failure, entitlement, restore, relaunch, and supported revocation/expiry
  cases. See the
  [`RevenueCat Test Store runbook`](operations/revenuecat-test-store-runbook.md).
- Do not use a provider fixture, mocked customer info, or local account flag
  as proof of a real transaction.
- A passing AI gateway or mocked provider does not prove provider privacy,
  credit grants, cost behavior, or user value. Those require the corresponding
  configured environment and evidence.

## Recording results

For each meaningful check, record the source revision, exact command or
scenario, environment/device, observed outcome, and limitation. Keep these
result classes distinct:

- implementation present;
- locally tested or compiled;
- device/runtime observed;
- provider transaction observed;
- managed environment observed;
- human/reviewer observed;
- not run, unavailable, or owner-gated.

Never upgrade a result to a broader claim than the test supports. A video,
screenshot, compile, local integration, or small user test is not a substitute
for a different acceptance layer.
