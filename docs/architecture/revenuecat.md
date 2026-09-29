# RevenueCat Integration Boundary

This document defines how monetization fits Evidrilo. It records intended
product and code boundaries, not proof of a live store transaction.

## Product model

- Free: one complete M0 learning loop, including evidence, transparent
  feedback, one revision, the evidence-change challenge, comparison, and local
  history. M0 case learning requires a signed-in account; local project work
  and anonymous catalog browsing do not.
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

D-108's post-competition academic learning/project direction did not itself
alter this RevenueCat boundary. D-124 now sets the product-tier policy for that
direction: all catalog guides and published-template previews stay Free;
reviewed baseline templates remain Free within each family when available;
Pro may later add reviewed specialist templates without locking an entire
method family or core project workflow. Essential report export and full
project-data portability remain Free. Existing entitlement, prices, case
benefits, project limits, and AI-credit quantities are unchanged. This policy
does not mean those template entitlements are implemented or that any reviewed
template is currently selectable.

Future cloud sync/backup is not included in the current Pro promise. It requires
the separate R2 consent, privacy, retention, security, quota, cost, operations,
and acceptance gates before it can be offered as a subscription benefit.

Remaining dashboard price migration and purchase/restore/revoke matrix remain
owner gates; this document does not claim that the provider dashboard is
configured or that a transaction has been observed.
The historical Test Store observation is recorded in the private evidence
ledger and is not current provider proof.

## Next Gen payment decision

Evidrilo currently targets the Shipaton Next Gen Award. For that category, the
RevenueCat Test Store/sandbox is sufficient; a real App Store or Google Play
payment, production transaction, production revenue, store listing, or paid
developer account is not required. This does not remove the RevenueCat
requirement: the official RevenueCat SDK must still power a working purchase
and entitlement flow in the candidate build.

The minimum honest demo is:

```text
offering loads
  → monthly/yearly Test Store purchase
  → CustomerInfo activates evidrilo_pro
  → premium evidence case unlocks
  → restore or failure remains entitlement-driven
```

All evidence must be labelled `sandbox/Test Store`. It proves an integration
flow, not real payment or production revenue. The official basis is the
[Next Gen rules](https://revenuecat-shipaton-2026.devpost.com/rules) and the
[RevenueCat manager clarification](https://revenuecat-shipaton-2026.devpost.com/forum_topics/44695-next-gen-eligibility-is-a-test-store-only-purchase-sufficient).
Non-Next-Gen categories and future store releases are separate gates and must
not be inferred from this exception. No automatic or numeric Next Gen score
deduction for using Test Store is published; the meaningful risk is an
incomplete, mocked, or misleading purchase flow.

RevenueCat also authorizes the optional AI allowance; it does not store or
directly mutate the AI credit balance. D-126 sets the target grant amounts and
D-130 makes them additive: a verified Free account gets 20 credits once, and
each earned active entitlement month adds 200 credits for either monthly or
yearly `evidrilo_pro`. Yearly plans do not receive an upfront annual grant.
Earned credits do not expire when a grant period ends or when Pro ends. Failed
AI requests release their reservation. D-127 prices successful requests from
verified provider-token usage; there is no fixed credit price per AI operation.
See [D-126](../decisions.md#d-126-increase-the-free-and-pro-ai-credit-allowances)
for target grant amounts, [D-127](../decisions.md#d-127-charge-ai-credits-from-token-usage)
for pricing, [D-130](../decisions.md#d-130-accumulate-pro-ai-credits-without-resetting-the-balance)
for accumulation, and [D-106](../decisions.md#d-106-use-an-evidence-grounded-ai-loop-for-project-assistance)
for the assistance boundary.

## Mobile access flow

For the detailed purchase, restore, local-access, and webhook sequence, see
[`system-execution-flows.md`](system-execution-flows.md).

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
        → one-time free grant of 20, or active evidrilo_pro period grant of 200
        → reserve estimated maximum token cost
        → bounded server-side AI assist
        → settle actual uncached/cached/cache-write input and output usage / release unused reserve
```

Monthly and yearly packages add the same 200-credit grant for each earned
entitlement month. A yearly entitlement does not receive a single 2,400-credit
balance; each earned monthly grant adds to the existing balance and does not
expire. AI cost is priced by uncached,
cached, and cache-write input plus output token categories and rounded up at
the approved credit-to-dollar rate. Reasoning tokens are included in output.
Entitlement-period grants must be
created from verified provider state or an idempotent server projection; a
client success flag, webhook replay, restore callback, or duplicate request
must not create credits twice. The mobile client can display a balance but
cannot grant, transfer, or edit it.

The AI ledger is separate from premium case access. A billing outage or disabled
AI provider must leave the free case and deterministic verification usable. A
provider timeout, cancellation, malformed response, invalid usage, or
unavailable configuration releases the reserved credit and returns a truthful
fallback. A valid generated preview is charged even if the student later
dismisses it.

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

Use this integration boundary and the owner-authorized provider procedure for
the controlled Test Store run. Current provider status is kept in the private
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
