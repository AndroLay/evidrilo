# RevenueCat Integration Boundary

Latest repository increment: E186 / BACKEND_ENGINE_SYNC_BOUNDARY_HARDENED / E185 / REVENUECAT_OFFERING_MIGRATION_OBSERVED / E183 / NATIVE_CHOICE_ACCESSIBILITY_SEMANTICS_HARDENED / E182 / SYNC_CURSOR_CONTRACT_BOUNDARY_ALIGNED / E181 / CASE_TRANSITION_CONTRACT_BOUNDARY_HARDENED / E180 / MOBILE_RELEASE_CANDIDATE_PREPARATION / E179 / SYNC_CONSENT_CANCELLATION_BOUNDARY_HARDENED / E178 / API_INPUT_AND_STAGING_BOUNDARY_HARDENED / E177 / SYNC_PULL_PAGE_SIZE_BOUNDARY_HARDENED / E176 / REQUEST_LIFECYCLE_AND_INPUT_BOUNDARIES_HARDENED / E175 / SYNC_PULL_CURSOR_LOWER_BOUND_GUARDED / E174 / FAIL_CLOSED_RESPONSE_REFRESH_BOUNDARIES / E173 / ASYNC_STATE_BOUNDARIES_HARDENED / E172 / LOCAL_SESSION_BOUNDARIES_HARDENED / E171 / OFFLINE_AUDIO_REPOSITORY_IMPLEMENTATION / E170 / MEMBERSHIP_ROLE_ASSIGNMENT_POLICY_COVERAGE / E169 / AUTH_PROVIDER_CONFIRMATION_TYPE_BOUNDED / E168 / CURRENT_STATUS_SNAPSHOTS_SYNCHRONIZED.

Status: E186 / BACKEND_ENGINE_SYNC_BOUNDARY_HARDENED / E185 / REVENUECAT_OFFERING_MIGRATION_OBSERVED / E183 / NATIVE_CHOICE_ACCESSIBILITY_SEMANTICS_HARDENED / E182 / SYNC_CURSOR_CONTRACT_BOUNDARY_ALIGNED / E181 / CASE_TRANSITION_CONTRACT_BOUNDARY_HARDENED / E180 / MOBILE_RELEASE_CANDIDATE_PREPARATION / E179 / SYNC_CONSENT_CANCELLATION_BOUNDARY_HARDENED / E178 / API_INPUT_AND_STAGING_BOUNDARY_HARDENED / E177 / SYNC_PULL_PAGE_SIZE_BOUNDARY_HARDENED / E176 / REQUEST_LIFECYCLE_AND_INPUT_BOUNDARIES_HARDENED / E175 / SYNC_PULL_CURSOR_LOWER_BOUND_GUARDED / E174 / FAIL_CLOSED_RESPONSE_REFRESH_BOUNDARIES / E173 / ASYNC_STATE_BOUNDARIES_HARDENED / E172 / LOCAL_SESSION_BOUNDARIES_HARDENED / E171 / OFFLINE_AUDIO_REPOSITORY_IMPLEMENTATION / E170 / MEMBERSHIP_ROLE_ASSIGNMENT_POLICY_COVERAGE / E169 / AUTH_PROVIDER_CONFIRMATION_TYPE_BOUNDED / E168 / CURRENT_STATUS_SNAPSHOTS_SYNCHRONIZED / RUNTIME_GATES_OPEN / OFFERING_MIGRATION_OBSERVED / PRICE_MIGRATION_OPEN / EXTERNAL_GATES_OPEN
<!-- Historical status chain retained below for provenance.
Status: `E170 / MEMBERSHIP_ROLE_ASSIGNMENT_POLICY_COVERAGE / E169 / AUTH_PROVIDER_CONFIRMATION_TYPE_BOUNDED / E168 / CURRENT_STATUS_SNAPSHOTS_SYNCHRONIZED / E167 / BILLING_EMPTY_ACCOUNT_GUARDED / E166 / LAST_OWNER_ROLE_CHANGE_GUARDED / E165 / COHORT_LEARNER_ROLE_BOUNDARY_HARDENED / E164 / COHORT_MEMBERSHIP_BOUNDARY_HARDENED / E163 / LOCAL_SESSION_DECODER_HARDENED / E162 / NODE_VERIFICATION_SNAPSHOT_SYNCHRONIZED / E161 / RECOMMENDATION_SCHEMA_ALIGNMENT / E160 / CONTENT_RUNBOOK_CHALLENGE_REQUIRED / E159 / CASE_SCHEMA_IDENTIFIER_BOUNDS / E158 / CONTRACT_SCHEMA_ALIGNMENT / E157 / MOBILE_CONTENT_CHALLENGE_REQUIRED / E154 / AUTH_CALLBACK_INPUT_BOUNDED / E153 / CLIENT_BILLING_BOUNDARY_HARDENED / E152 / ACCOUNT_DELETION_OWNER_GUARDED / E151 / REVENUECAT_PRODUCT_ALLOWLIST_GUARDED / E150 / RUNBOOK_STATUS_SYNCHRONIZED / E149 / CANONICAL_ENTITLEMENT_GUARDED / E148 / E147 / E146 / E140 / APP_ALLOWLIST_VERIFIED / BILLING_CALLBACK_IDENTITY_GUARDED / BILLING_CONFIG_STATUS_TRUTHFUL / BILLING_UNKNOWN_STATE_ALLOWLISTED / REVENUECAT_UI_BOUNDARY_COMPILED / REVENUECAT_RUNBOOK_ALIGNED / DASHBOARD_LIFETIME_MIGRATION_OPEN / TEST_STORE_TRANSACTION_NOT_RUN / DEPLOYMENT_PREPARATION_PRESENT`

-->
Current authority: [Evidrilo Source of Truth](../../research/next-gen/SOURCE_OF_TRUTH.md),
[E118 all-area closure](../../audit/evidence/evidrilo-all-areas-closure-2026-09-13.md),
[E119 analytics boundary](../../audit/evidence/evidrilo-analytics-funnel-closure-2026-09-13.md),
[E120 server-owned write boundary](../../audit/evidence/evidrilo-server-owned-write-boundary-2026-09-13.md),
[E121 recommendation write boundary](../../audit/evidence/evidrilo-server-owned-recommendation-boundary-2026-09-13.md),
[E122 billing configuration boundary](../../audit/evidence/evidrilo-billing-config-readiness-2026-09-13.md),
[E123 billing offer allowlist boundary](../../audit/evidence/evidrilo-billing-offer-allowlist-2026-09-13.md),
[E124 authenticated transport boundary](../../audit/evidence/evidrilo-auth-redirect-safety-2026-09-13.md),
[E125 mobile host boundary](../../audit/evidence/evidrilo-android-network-permission-2026-09-13.md),
[E126 account-deletion access boundary](../../audit/evidence/evidrilo-account-deletion-access-2026-09-13.md),
[E129 billing callback identity boundary](../../audit/evidence/evidrilo-billing-callback-identity-2026-09-13.md),
[E130 trigger privilege hardening](../../audit/evidence/evidrilo-trigger-function-privileges-2026-09-13.md),
[E132 migration-runner concurrency hardening](../../audit/evidence/evidrilo-migration-runner-concurrency-2026-09-13.md),
[E133 production CORS boundary](../../audit/evidence/evidrilo-production-cors-boundary-2026-09-13.md),
[E134 runbook catalog boundary](../../audit/evidence/evidrilo-revenuecat-runbook-catalog-boundary-2026-09-13.md),
[E136 runbook observation boundary](../../audit/evidence/evidrilo-runbook-observation-boundary-2026-09-13.md),
[E137 backend register synchronization](../../audit/evidence/evidrilo-backend-register-synchronization-2026-09-13.md),
[E138 managed RevenueCat UI boundary](../../audit/evidence/evidrilo-revenuecat-managed-ui-2026-09-13.md),
[E139 canonical content reader](../../audit/evidence/evidrilo-canonical-content-reader-2026-09-13.md),
[E140 provider-claim documentation boundary](../../audit/evidence/evidrilo-revenuecat-documentation-claim-boundary-2026-09-13.md),
[E149 canonical entitlement guard](../../audit/evidence/evidrilo-billing-entitlement-allowlist-2026-09-13.md),
[E150 runbook status synchronization](../../audit/evidence/evidrilo-runbook-status-synchronization-2026-09-13.md),
[E151 server product allowlist](../../audit/evidence/evidrilo-billing-product-allowlist-2026-09-14.md),
[E152 account-deletion owner guard](../../audit/evidence/evidrilo-account-deletion-owner-guard-2026-09-14.md),
[E153 client/request boundary hardening](../../audit/evidence/evidrilo-client-boundary-hardening-2026-09-14.md),
and [E154 auth callback boundary](../../audit/evidence/evidrilo-auth-callback-boundary-2026-09-14.md),
and [E155 content challenge boundary](../../audit/evidence/evidrilo-content-challenge-required-2026-09-14.md),
and [E156 stored content transition guard](../../audit/evidence/evidrilo-stored-content-transition-guard-2026-09-14.md),
and [E157 mobile content challenge boundary](../../audit/evidence/evidrilo-mobile-content-challenge-required-2026-09-14.md),
and [E158 published-case schema alignment](../../audit/evidence/evidrilo-case-schema-challenge-boundary-2026-09-14.md),
and [E159 published-case identifier-boundary alignment](../../audit/evidence/evidrilo-case-schema-identifier-bounds-2026-09-14.md),
and [E160 content-authoring runbook correction](../../audit/evidence/evidrilo-content-runbook-challenge-required-2026-09-14.md),
and [E161 recommendation schema alignment](../../audit/evidence/evidrilo-recommendation-schema-alignment-2026-09-14.md),
and [Evidrilo Test Store runbook](../operations/revenuecat-test-store-runbook.md).

## Purpose

RevenueCat is the monetization boundary required by Shipaton. It must support a
credible premium path without making the free Evidrilo learning loop depend on
an account, network connection, or successful purchase.

## Application boundary

Shared screens depend on an application-facing billing interface. RevenueCat
SDK types and platform initialization remain inside the adapter layer.

```text
UI -> BillingGateway -> RevenueCat adapter -> RevenueCat SDK
```

The domain evaluator does not depend on RevenueCat and must remain fully
testable without a billing key.

## iOS dependency choice

Because Evidrilo is a Kotlin Multiplatform app, the current iOS adapter uses
RevenueCat's `purchases-kmp-core` and `purchases-kmp-ui` dependencies from the
Gradle shared module. They provide the native RevenueCat bridge and managed
Compose UI boundary used by the shared app; adding `purchases-ios-spm` to the
Xcode host as a second SDK would duplicate the dependency and create two
initialization paths. A direct Swift Package integration is appropriate only
if the app moves to a separate Swift-native billing/UI boundary.

## Current product model

- Free: one complete educational case, deterministic feedback, one revision,
  before/after comparison, one evidence-change challenge, one latest local
  comparison history entry, local reset, and offline use after content is
  available.
- Premium access: one entitlement, `evidrilo_pro`, with two approved
  subscription packages: monthly (`monthly`) and yearly (`yearly`). Both
  unlock the same two pedagogically distinct practice cases. Lifetime is not
  part of the approved product plan.
- Access: derived from the active entitlement returned by the billing adapter,
  never from a local purchase-success flag. On the server, signed events with
  a supplied product must name exact `monthly` or `yearly` products; active
  grants also require that field. `lifetime` and unknown products are ignored
  fail-closed, while product-less expiration/revocation cleanup is tolerated.

The initial global pricing hypothesis is USD 1.00/month and USD 10.00/year.
These are reference anchors, not yet verified store price points; Apple and
Google may localize them and the final proceeds depend on store fees, taxes,
refunds, and regional availability. The detailed business decision is in the
[monetization and pricing note](../business/monetization-and-pricing.md).

Current state: the active Evidrilo UI has a local billing boundary, optional
RevenueCat-managed Paywall and Customer Center entry points, explicit
locked/unavailable/pending/unknown states, two premium case slots, request
generation and account-identity guards, and reducer coverage for late callbacks
and revoked access.
The Android and iOS adapters currently load and select the locally configured
packages, read `evidrilo_pro`, handle purchases/restores, and synchronize
RevenueCat's customer identity with a provider-verified Evidrilo account.
The remaining dashboard price migration and purchase/restore/revoke matrix remain
owner gates, so M3 remains open. The legacy lifetime package is no longer
eligible in app configuration, fixtures, or presentation even if a provider
returns it. The owner-authorized dashboard still needs the package removed or
disabled before the approved product model is transaction-tested. The server
webhook now applies the same monthly/yearly grant allowlist independently of
the app, so a provider payload cannot bypass the product policy. Legacy
AskReady identifiers are not valid Evidrilo configuration.
E153 additionally makes client entitlement access product-aware, rejects
unverified stored sessions before authenticated billing-adjacent reads, and
keeps the unresolved/legacy product path fail-closed. This still does not
replace dashboard migration or Test Store transaction evidence.

E167 additionally rejects the all-zero `app_user_id` before a signed billing
event reaches the entitlement store. This prevents an impossible account from
becoming a database retry/error path; it does not add provider transaction
evidence.

E168 synchronizes this document's active status header with the current
repository evidence. The snapshot contract passes `23/23`; provider dashboard,
transaction, device, and production gates remain open.

E169 hardens the adjacent account-provider boundary: malformed non-string
confirmation metadata cannot become a verified local session. Focused auth
coverage passes `13/13` and Kotlin/JVM `271/271`; RevenueCat dashboard,
transaction, device, and production gates remain open.

E170 adds explicit membership role-assignment regression coverage without
changing billing behavior. Focused access-policy coverage passes `9/9` and the
full API suite passes `151/151`; RevenueCat dashboard, transaction, device, and
production gates remain open. See the [E170 membership role-assignment record](../../audit/evidence/evidrilo-membership-role-assignment-coverage-2026-09-14.md).

Product and entitlement identifiers are supplied through local configuration
and must match the owner-created dashboard catalog. The historical Test Store
observation is recorded in [E116](../../audit/evidence/evidrilo-revenuecat-dashboard-audit-2026-09-12.md)
and included the legacy `lifetime` package. Current provider observation [E185](../../audit/evidence/evidrilo-revenuecat-offering-migration-2026-09-16.md)
confirms that the active `default` offering now contains only `monthly` and
`yearly`; the `monthly` product still displays USD 9.99/month and the approved
replacement prices remain open. They must not be committed together with
private keys or server secrets.

The adjacent E171 audio implementation does not alter RevenueCat entitlement
rules. Audio remains optional, and the premium boundary continues to accept
only the canonical `evidrilo_pro` entitlement with monthly/yearly products;
device transaction evidence remains unobserved.

E172 also leaves the billing boundary unchanged. Restored local sessions must
pass the common phase/case and safe-account checks before any authenticated
billing, sync, or request path can consume them.

E173 closes a repository-local billing state-loss path: if access and offer
callbacks arrive separately, the reducer now keeps the complete approved
monthly/yearly catalog. The async sync guard is independent of entitlement
truth, and lifetime remains rejected by the existing product allowlist. E185
now covers the current offering configuration; real RevenueCat transactions
and approved price presentation remain unobserved.

E174 adds a presentation-layer defense in depth: even a directly constructed
billing presentation can expose and purchase only approved monthly/yearly
products. The same increment also hardens adjacent sync response and auth
refresh boundaries; live RevenueCat transactions, dashboard configuration, and
native runtime behavior remain unobserved.

## Customer identity and server projection

The free core may still be used anonymously. After the auth adapter produces a
provider-verified account, the mobile billing adapter calls RevenueCat
`logIn(account UUID)` so future webhook events can map to the server account;
sign-out, expiry, and account recovery reset the RevenueCat customer with
`logOut`. Anonymous or non-UUID provider events are acknowledged but are not
projected into an account entitlement. This prevents a caller-supplied id from
becoming billing identity and keeps the server projection authoritative.

## Required failure behavior

The free flow remains usable when the SDK is missing, initialization fails, no
offer is available, a purchase is cancelled, or the network is unavailable.
Unknown transaction state must not be described as a successful or failed
purchase until it is reconciled. Restore must refresh entitlement state rather
than blindly unlock the UI.

The app disables purchase and restore actions while an operation is busy, and
results from a request whose premium surface has been closed are ignored. An
active entitlement is the only transition into the premium catalog; a later
inactive entitlement returns the surface to locked state. These behaviors are
covered by the common billing race/recovery tests.

The shared app now exposes Customer Center only when the platform adapter is
configured with a public key and the approved catalog. The same guard exposes
the managed RevenueCat Paywall from the locked premium surface; the existing
product-owned premium screen remains the fallback. The repository proves the
adapter and cross-target compilation, not dashboard configuration, transaction
behavior, or real-device rendering.

## Test Store boundary

The current Test Store offering was observed in E185 with only the monthly and
yearly packages. The existing monthly product still shows USD 9.99/month, so
the approved price migration remains open; no yearly price or transaction
result is claimed here. Credentials and results are environment-specific. Use
local configuration and label all observations as sandbox observations. A
successful sandbox purchase does not prove production revenue, willingness to
pay, or long-term retention. The current app-side allowlist is already
fail-closed against lifetime.

## Privacy and configuration

Do not send conclusion drafts, reviewer contacts, participant identity, or
private research data as customer attributes. Keep local keys, signing files,
receipts, and production configuration outside source control.
