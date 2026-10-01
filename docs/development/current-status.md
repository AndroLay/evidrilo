# Current acceptance status

Observed on 1 October 2026 for application candidate `cfa22860`, version 1.0.0 / build 2.
The v1.0.0 tag also includes landing-page and documentation changes; these do
not change the verified native application inputs.
This is a dated snapshot, not continuous monitoring or a production guarantee.

**Decision: READY WITH RISKS for Android and iOS Simulator judging builds / Staging / Test Store.**
Public store distribution and full native iOS online acceptance remain unproven.
Download scope and limits: [Release 1.0.0](https://github.com/AndroLay/evidrilo/releases/tag/v1.0.0).

## Observed

- Render API and worker are live from `87d2c06d` (server inputs unchanged in
  the application candidate), with separate Supabase
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
- [Verify passed](https://github.com/AndroLay/evidrilo/actions/runs/36876562081)
  on `cfa22860`, including backend, contracts/migrations, mobile/JVM, and public
  package checks.
- [iOS Simulator passed](https://github.com/AndroLay/evidrilo/actions/runs/36876561915):
  unsigned Debug judging host, install/launch, guest Home → Projects → catalog →
  blank project → Project basics / Project name, and package validation.
  Build: Apple Silicon / arm64, Xcode 26.0.1, version 1.0.0 / build 2.
  Google/email and Test Store are configured; native provider transactions have
  not been demonstrated. No Apple signing or installable iPhone IPA is available.
- The landing page now uses Android 1.0.0 captures and current project, Practice,
  AI and Free/Pro information. Captures use synthetic demo data and Test Store.


The former local API, local worker/PostgreSQL containers, and idle build
daemons were stopped; Android uses the HTTPS Render endpoint. These operational
changes do not delete local project data. Hosted API pooling is disabled to avoid
retaining connections in independent store pools on the small Staging pooler.

## Remaining work

1. Verify native iOS Google/email sign-in and billing transactions separately.
   iOS project attachment picking/storage and DOCX text extraction remain unavailable.
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
