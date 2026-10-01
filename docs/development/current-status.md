# Current acceptance status

Observed on 1 October 2026 for application commit `5a40b748`.
This is a dated snapshot, not continuous monitoring or a production guarantee.

**Decision: READY WITH RISKS for the Android judging build / Staging / Test Store.**
Public store release and full iOS acceptance remain unproven.

## Observed

- Render API and worker are live from the same commit, with separate Supabase
  Staging runtime roles and verified TLS. Production was not changed.
- API readiness returned 200 with Auth/database/config ready; unauthenticated
  AI credits returned 401. Signed-in credits, entitlements, progress,
  notifications, and sync returned 200.
- Android Google sign-in/session restoration and a live general AI reply through
  hosted Staging were observed. Provider: Experiential Luna 6 with default
  reasoning; limits: USD 0.01/request and USD 2/month.
- Android Test Store monthly/annual purchases, checkout cancellation/failure,
  restore/relaunch, renewal and expiry were observed. Paid credit periods grant
  200, with duplicate refresh/restore protection; future monthly anniversaries
  are fixture evidence, not elapsed live months. No real payment was made.
- The Profile identity card shows name/email/Free or Pro. Google connection is
  shown separately inside account details, not on the Profile main page.
- [Verify passed](https://github.com/AndroLay/evidrilo/actions/runs/36865870445).
  [iOS Simulator](https://github.com/AndroLay/evidrilo/actions/runs/36865870424)
  completed the unsigned Release host build; its guest-project UI test was
  still running when this snapshot was written.

The former local API, local worker/PostgreSQL containers, and idle build
daemons were stopped; Android uses the HTTPS Render endpoint. These operational
changes do not delete local project data. Hosted API pooling is disabled to avoid
retaining connections in independent store pools on the small Staging pooler.

## Remaining work

1. Record the final iOS build/simulator result and native sign-in/billing evidence.
2. Record end-to-end project AI preview/apply and file export/import/reopen on
   named devices; current source/fixtures are not complete device acceptance.
3. Validate student usability, accessibility, and educational usefulness. Human
   review of method templates and scientific claims remains necessary.
4. Before public release, configure real-store products/signing/distribution,
   authenticated Production billing events, Production OAuth/database/runtime,
   monitoring/alerts, backup/restore, and operational recovery evidence.
5. Finish and verify the submission video, form, and judge access instructions.

Worker deployment alone does not prove every background job. Google account
connection does not mean project drafts are cloud-backed-up. AI suggestions are
opinions and editable proposals, not grades or verified research conclusions.

See [release gates](../release.md), [Staging operations](../../infra/deployment/render-supabase-staging.md),
and [billing boundary](../architecture/revenuecat.md).
