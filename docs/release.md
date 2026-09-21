# Next Gen Submission Readiness

This checklist is for the Shipaton Next Gen submission, not an App Store or
Google Play release. Next Gen does not require a store listing or a paid Apple
or Google developer account. It does require a working-app demonstration and a
public, open-source source repository.

## Required submission package

- [ ] A clear project name, short pitch, and honest project story.
- [ ] A public demo video showing the app working; essential footage must be
      under two minutes and publicly viewable at the submitted link.
- [ ] A public source-code repository with an open-source license file and
      enough setup instructions for judges to inspect the project.
- [ ] A 1024 × 1024 app icon and a 1179 × 2556 screenshot without a device
      frame, when requested by the Devpost form.
- [ ] The required project description and supported technology tags.
- [ ] The RevenueCat project ID and any judge access instructions requested by
      the form. Never put a secret SDK key in a public field.
- [ ] A qualifying student or academic email entered privately in Devpost.
- [ ] Parent or legal-guardian consent completed for each entrant under the
      age of majority where they live; teams containing a minor enter Next Gen
      only.
- [ ] All required eligibility, country, team, and conflict-of-interest
      answers reviewed by the submitter.

## Product and integration acceptance

- [ ] The app launches from a reproducible build and demonstrates the exact
      interaction described in the story and video.
- [ ] The free learning workflow remains usable without purchase or account.
- [ ] First launch shows the short guide, both start/skip paths reach the same
      offline Home flow, and any account prompt is dismissible and shown only
      after meaningful free value.
- [ ] If account access is included in the frozen build, email and Google sign-in
      use the verified callback boundary and failure/cancellation returns safely
      to the local workflow; no full-draft cloud-restoration claim is made.
- [ ] If local notifications are included, permission, scheduling, category
      controls, disable/cancellation, denied-permission recovery, and offline
      behavior are verified on the named device; remote push is not claimed.
- [ ] RevenueCat is integrated for the intended premium purchase path; the
      app does not claim a purchase, restore, or entitlement result that was not
      observed on the actual test configuration.
- [ ] Premium content is accessible only from the verified entitlement state;
      failure, cancellation, pending, and restore states are represented
      truthfully.
- [ ] If AI is included in the candidate build, the credit ledger, consent,
      selected-context disclosure, provider output schema, reservation/release
      behavior, cost/rate limits, and provider test evidence are complete. AI is
      optional for Next Gen and must be omitted from claims when these gates are
      not verified.
- [ ] The evaluator exposes its evidence anchors and abstains when the
      supplied case does not support a safe assessment.
- [ ] The repository license, asset provenance, dependency notices, setup, and
      public export are checked before publication.
- [ ] The submitted source revision and demonstrated build are recorded
      together; screenshots and video match that revision.
- [ ] The required P0 rows in the private UI Acceptance Matrix are closed for
      the frozen build, including applicable modal, offline/error, recovery,
      back-navigation, and accessibility states.

## Local verification

Run the repository-owned verification harness from the project root:

```
bash
bash scripts/ci/verify-local.sh
```

Validate the selected non-video assets before attachment:

```
bash
bash scripts/release/validate-submission-assets.sh . --icon docs/submission/assets/evidrilo-app-icon-1024.png --screenshot docs/submission/assets/evidrilo-submission-screenshot-1179x2556.png --non-video-only
```

After a public video exists, run the complete media validator and check the
public links in a logged-out session. A local validator does not establish
visual approval, copyright permission, eligibility, public visibility, or
owner authorization.

## Claim discipline

- A Kotlin/iOS-target compilation is not an iOS simulator or device run.
- A RevenueCat SDK integration or local fixture is not a provider transaction.
- A local API/PostgreSQL integration is not a managed deployment.
- A screenshot is not a complete accessibility review or proof of every
  screen state.
- A small formative study is not learning-efficacy evidence.
- Repository availability and public visibility must be checked directly at
  submission time.

Keep competition-specific status in the private evidence ledger rather than
duplicating dated test counts across public engineering guides. Deployment,
staging, and video production may have separate work plans, but the video and
public repository remain required for the eventual Next Gen submission.

## Separate future store distribution

If Evidrilo later pursues App Store, Google Play, or Galaxy Store distribution,
that is a separate release with its own signing, privacy disclosures, store
review, platform-specific runtime checks, and rollback plan. Do not treat those
store-release steps as Next Gen eligibility requirements.
