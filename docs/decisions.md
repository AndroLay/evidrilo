# Evidrilo Decision Log

This log records current architectural and delivery decisions. Dated evidence
records remain the authoritative detail for historical increments; decisions
below are kept append-only and must not be used to claim runtime or provider
verification that was not observed.

## D-077 — Add optional offline audio without changing the learning boundary

Decision: add an optional audio layer to Evidrilo with two separate channels:
reviewed bundled English narration for fixed product/case copy, and restrained
short sound effects for meaningful interactions. Dynamic text may use native
offline TTS only when an installed voice does not require a network connection.
Audio must never become a prerequisite for the free core, evaluator truth,
draft/history persistence, entitlement, sync, or analytics.

The implementation must use stable asset IDs, source-text checksums, explicit
license/provenance metadata, bounded package size, serialized playback, stale
callback cancellation, visible text fallback, and separate Android/iOS runtime
and human naturalness/accessibility evidence.

See the [offline audio design](superpowers/specs/2026-09-14-evidrilo-offline-audio-design.md)
and the [feature-gate map](superpowers/specs/2026-09-14-evidrilo-feature-gates-design.md).

## D-078 — Bind local restoration to supported workflow identities

Decision: local conclusion-session snapshots are untrusted input and must be
bound to the workflow case IDs before restoration. Main-case phases may restore
only the main case; evidence-change phases may restore only a main-case base
and the evidence-change case. Securely stored account identifiers also require
a bounded ASCII-safe identifier shape before they can reach billing, sync, or
request boundaries. Malformed records fail closed while safe legacy records
remain readable.

This is repository hardening only; provider-backed accounts, native
secure-storage runtime, managed infrastructure, and device runtime remain
external gates.

## D-079 — Guard asynchronous sync and billing state transitions

Decision: asynchronous sync responses must commit only when the stored queue
snapshot and request generation are still current. Account/session boundaries
route queue clearing through the coordinator, and visible sync results are
bound to the current account and consent generation. Billing state must retain
the complete approved monthly/yearly catalog when access and offers arrive in
separate callbacks. Lifetime remains fail-closed.

These local guards do not replace provider, runtime, managed, staging, human,
publication, or submission evidence.

## D-080 — Keep remote response and refresh boundaries fail-closed

Decision: a mobile sync response is accepted only when its cursor and server
sequences are monotonic and its push results exactly cover the requested
commands. Cursor regressions, duplicate/missing/unrelated result IDs, and
impossible server sequences retain the local queue. When auth refresh reports
that email confirmation is no longer present, the stale secure session is
cleared before returning the confirmation-required state. Direct billing
presentations also enforce the approved `monthly`/`yearly` product allowlist.

## D-081 — Require pull changes to advance the requested cursor

Decision: a sync pull response must contain only changes with
`serverSequence > cursor` and `serverSequence <= nextCursor`, in strictly
ascending order. A change equal to the requested cursor is a replay of already
observed state and must be rejected before it reaches the queue or local
projection.

## D-082 — Bound request cancellation and input validation at trust boundaries

Decision: every mobile HTTP callback bridge must be cancellation-aware and
ignore late completion after its coroutine is cancelled. JVM and Android
connections are interrupted/disconnected, and iOS tasks are cancelled. API
write endpoints must parse malformed JSON through a bounded strict reader so
the stable versioned error envelope is preserved. Sync command IDs must be
unique before client push construction and before server storage access.
Recommendation and auth redirect state must restore a retryable, non-busy state
when cancellation interrupts the operation.

This is a repository-local safety decision. It does not imply Android/iOS host
runtime, provider, managed, staging, human, publication, or submission
verification.

## D-083 — Validate the requested sync page size

Decision: the mobile sync pull parser must validate a response against the
validated `limit` sent by the caller. A response may contain at most that many
changes, and a response with `hasMore=true` must contain exactly that many.
The maximum page size is not a substitute for the requested page size.

This keeps pagination bounded and fail-closed while allowing callers to use a
smaller page for constrained devices or future incremental sync flows.

## D-084 — Use strict API matches and recognize staging explicitly

Decision: API identifiers, digests, actions, reason codes, case-version IDs,
and request IDs require a full input match using `\A…\z`; a trailing newline
must not be accepted by a helper-level validator. The platform environment
normalizer accepts the conventional ASP.NET Core `Staging` value and maps it to
canonical `staging`. Production-only configuration guards remain unchanged.

This hardens repository boundaries without claiming that a managed staging
deployment or provider-backed staging configuration has been observed.

## D-085 — Cancel and serialize consent-bound sync work

Decision: sync orchestration must not push after its pull has been deferred or
invalidated, and the active sync job must be cancelled when account or sync
consent changes. Consent revocation reports the actual local clear result and
retains the reloaded pending count when clearing fails. Sync enablement has one
state-driven auto-sync owner so a consent change cannot launch duplicate work.

This protects the local-first and consent boundary. It does not claim that an
already transmitted provider/network request can be recalled, nor does it
replace runtime or managed-system verification.

## D-086 — Keep mobile release preparation optimized and signing-explicit

Decision: Android release candidates use R8/resource shrinking, explicit
version inputs, backup and cleartext safeguards, and a fail-closed local
signing task. A local unsigned AAB is valid only for packaging verification;
Play upload requires the owner's upload keystore. The iOS Release target uses
distribution signing with an owner-supplied `TEAM_ID` through ignored local
configuration. CI may compile the iOS Release host with signing disabled, but
that result is not an archive, device, or App Store release.

No provider key, signing file, receipt, or account credential belongs in the
repository. Native runtime, signing, store, accessibility, and owner approval
remain separate evidence gates.

## D-087 — Use native Compose semantics for conclusion choices

Decision: single-select conclusion, scope, limitation, and next-action choices
use `Modifier.selectable` with a radio role. Multi-select evidence facts use
`Modifier.toggleable` with a checkbox role. The choice card merges its label and
state for assistive technology while preserving the existing visual and
local-first behavior.

This closes a repository-level semantics defect without claiming TalkBack,
VoiceOver, or human accessibility validation. Device and assistive-technology
runtime gates remain separate.

## D-088 — Use the approved global $1/$10 subscription anchors

Decision: the initial Evidrilo subscription reference prices are USD 1.00 per
month for `monthly` and USD 10.00 per year for `yearly`. The `lifetime` package
is not part of the approved offering. Store-localized prices, taxes, fees,
refunds, proceeds, and transaction outcomes remain external evidence gates.

The free core remains usable without purchase, and the app-side billing
allowlist continues to accept only `monthly` and `yearly`.

## D-089 — Separate local practice identity from server case identity

Decision: bundled cases keep a stable local identifier for offline drafts,
history, replay, and corruption recovery. Sync and analytics use an explicit
canonical server case-version identifier; an unmapped local case fails closed.
New sync commands must reference a published server case version. An existing
idempotent command replay is checked first so a previously accepted command
remains safely replayable even if the case is later retired. The API preflight
and database migration 030 enforce the same boundary.

This preserves offline independence while preventing local identifiers from
silently crossing the API/database foreign-key boundary.
