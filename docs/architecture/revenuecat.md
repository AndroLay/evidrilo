# RevenueCat Integration Boundary

This document defines how monetization fits Evidrilo. It records intended
product and code boundaries, not proof of a live store transaction.

## Product model

- Free: one complete local learning loop, including evidence, transparent
  feedback, one revision, the evidence-change challenge, comparison, and
  local history.
- Premium: the canonical entitlement
`evidrilo_pro` unlocks two additional reviewed cases.
- Packages: monthly and yearly only. A lifetime package is not in the
  approved product model.
- Planning anchors: USD 1.99/month for `monthly` and USD 19.99/year for
  `yearly`, as recorded in D-100. These are not localized provider prices and
  must never be hardcoded into the runtime paywall.
- Paywall: explain the premium case value and preserve access to the free
  workflow when offers cannot load or a purchase is unavailable.

The free core must not sell more trustworthy evaluation, factual support, or
a safer outcome. Pricing and current provider catalog observations belong in
the private pricing/evidence records; do not copy changing prices into this
architecture note.

RevenueCat also authorizes the optional AI allowance; it does not store or
directly mutate the AI credit balance. The approved policy is 10 one-time
credits for a verified free account and 100 credits per active entitlement
month for both monthly and yearly `evidrilo_pro`. Credits do not roll over, and
failed AI requests release their reservation. See the [AI assistance and credit
contract](ai-assistance.md).

## Mobile access flow

```text
Premium case request
  → BillingGateway
  → platform RevenueCat adapter
  → CustomerInfo / active evidrilo_pro entitlement
  → premium case access or a truthful locked/unavailable state
```

The domain does not depend on RevenueCat. A local UI success flag is never
entitlement authority. Loading, empty catalog, pending, cancellation,
failure, restore, and offline states must be explicit. Restore is initiated
by the user and access is refreshed from the provider result.

## Server projection

The optional ASP.NET Core webhook boundary verifies the provider signature,
validates event identity and approved product identifiers, applies
idempotency/ordering rules, and updates server-owned entitlement state.
Clients cannot grant themselves access by submitting an account or
entitlement flag. The online API must fail closed when provider/database
configuration is missing.

For the current Next Gen submission, this server projection is not the delivery
path for the two bundled premium cases. The mobile app uses verified
RevenueCat `CustomerInfo` to unlock those local cases. Server-side entitlement
authorization becomes mandatory only if a later release serves premium content
from the API; the current submission must not describe local CustomerInfo as
server-enforced access.

## AI credit entitlement flow

```text
verified account + explicit AI consent
        → one-time free grant of 10, or active evidrilo_pro period grant of 100
        → server credit ledger reservation
        → bounded server-side AI assist
        → consume on accepted response / release on failure
```

Monthly and yearly packages use the same 100-credit grant for each active
entitlement month. A yearly entitlement does not receive a single 1,200-credit
balance, and unused credits do not roll over. Entitlement-period grants must be
created from verified provider state or an idempotent server projection; a
client success flag, webhook replay, restore callback, or duplicate request
must not create credits twice. The mobile client can display a balance but
cannot grant, transfer, or edit it.

The AI ledger is separate from premium case access. A billing outage or disabled
AI provider must leave the free case and deterministic verification usable. A
provider timeout, cancellation, malformed response, policy rejection, or
unavailable configuration releases the reserved credit and returns a truthful
fallback.

## Thoughtful usage acceptance

RevenueCat is product-aligned only when it extends repeated Evidrilo use without
selling evaluator truth. The implementation and final review must cover five
dimensions:

| Dimension | Required behavior |
| --- | --- |
| Product fit | `evidrilo_pro` adds two reviewed evidence cases and the optional AI allowance; the complete free case remains valuable |
| Entitlement authority | Active `CustomerInfo`/server projection controls premium access and AI grants; local flags never unlock features |
| Purchase reliability | Monthly/yearly offering, purchase, pending, cancellation, failure, restore, relaunch, and supported expiry/revocation have explicit states |
| Paywall care | Paywall appears after free value, shows localized price/period/renewal/manage guidance, and remains accessible and dismissible |
| Evidence and operations | Provider matrix, webhook signature/idempotency/order, account isolation, cost ceiling, privacy disclosure, and offline fallback are recorded |

The expected judged flow is:

```text
free case value
  → premium case value explanation
  → RevenueCat offering
  → monthly/yearly selection
  → purchase or truthful failure/unavailable state
  → evidrilo_pro entitlement
  → premium case and AI-credit access
  → restore/relaunch/offline fallback
```

Do not add a generic subscription screen, lifetime product, artificial feature
lock, or AI claim solely to make the integration appear larger. Do not claim
production revenue from Test Store evidence. The AI credit grant is valid only
when the server ledger reconciles a verified entitlement period idempotently.

## Verification boundary

Repository tests can establish reducer, allowlist, webhook, and failure
behavior under their test fixtures. A provider claim requires an authorized
Test Store run on the claimed build. The run should cover offering load,
purchase, cancellation/failure, active entitlement, restore, relaunch, and
supported expiry/revocation cases. Sandbox results are not production
revenue or store approval.

Use the [RevenueCat Test Store runbook](../operations/revenuecat-test-store-runbook.md)
for the controlled procedure. Current provider status is kept in the private
evidence ledger to avoid stale dashboard claims in public architecture docs.

## Configuration and privacy

- Keep RevenueCat public SDK keys in local platform configuration; never
  commit secret API keys, webhook secrets, receipts, or store credentials.
- Use only the configured canonical entitlement and approved monthly/yearly
  product IDs.
- Do not attach conclusion drafts, reviewer details, or participant
  information to customer attributes or analytics.
- Do not send a whole learner draft to an AI provider by default. Require
  selected context, explicit opt-in, redaction, bounded output, and a
  metadata-only audit record.
- Keep a billing outage from blocking the free local workflow.
