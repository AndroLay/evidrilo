# Evidrilo iOS host

This folder contains the thin Xcode host for the shared Kotlin/Compose app.
The product UI, reducer, feedback engine, and RevenueCat adapter remain in
`apps/mobile-shared`; the Swift layer only presents the shared `App()` composable.

## Run on macOS

1. Install a macOS/Xcode version that supports the project's iOS deployment
   target.
2. Open `apps/ios/iosApp.xcodeproj` in Xcode.
3. Select the currently committed host scheme and an iOS simulator or device.
   The committed host target and displayed product name are Evidrilo.
4. Build and run. The Xcode build phase invokes
   `:composeApp:embedAndSignAppleFrameworkForXcode` from the repository root.
5. If testing the premium path, set the Xcode build setting
   `REVENUECAT_PUBLIC_SDK_KEY` to an authorized RevenueCat Test Store public
   key, `REVENUECAT_ENTITLEMENT_ID` to the owner-created entitlement, and
   `REVENUECAT_PRODUCT_IDS` to a comma-separated list of owner-created product
   IDs using a local setting or ignored xcconfig. Never commit these values.

The free flow does not require a RevenueCat key. A missing key must leave the
free rehearsal usable and the premium offer unavailable.

## Release candidate on macOS

1. Copy `Configuration/Release.xcconfig.example` to the ignored
   `Configuration/Release.xcconfig` or `Configuration/Local.xcconfig`.
2. Set the Apple `TEAM_ID` and the provider values only in that local file.
   The checked-in Release target already selects `Apple Distribution` and
   takes the team from `TEAM_ID`.
3. Build the unsigned host first when validating the free path:

   ```bash
   xcodebuild \
     -project apps/ios/iosApp.xcodeproj \
     -scheme Evidrilo \
     -configuration Release \
     -destination 'generic/platform=iOS Simulator' \
     -xcconfig apps/ios/Configuration/Local.xcconfig \
     CODE_SIGNING_ALLOWED=NO CODE_SIGNING_REQUIRED=NO build
   ```

4. After device, accessibility, StoreKit, and configuration checks pass,
   archive with the owner's Apple distribution account. Keep the resulting
   archive/export outside the repository; it is release evidence, not source.

The repository contract can be checked before opening Xcode with:

```bash
bash scripts/release/check-mobile-release.sh .
```

## Maintainer verification matrix

On a macOS host, verify the same unconfigured free path first:

```text
Launch → Home → free case → feedback → one revision → summary → history
```

Then, with an authorized RevenueCat Test Store configuration, verify the
following separately on an iOS simulator/device: offer loading, purchase,
cancelled purchase, pending/unknown recovery, restore, revoked entitlement,
relaunch, and duplicate-tap protection. Record the product/entitlement
configuration only in the private release evidence; do not put keys or
receipts in this repository.

Before an iOS release claim, also inspect Keychain failure/recovery, VoiceOver
roles and focus order, Dynamic Type at 130% and larger, contrast/tap targets,
offline free-core behavior, screenshots from the demonstrated build, and
signed archive/export with the maintainer's Apple account. These are runtime
and account gates, not consequences of Linux shared-target compilation.

## Verification boundary

The Linux workspace can compile `iosSimulatorArm64` and `iosArm64`, but it
cannot run Xcode, an iOS simulator, or an iOS device. Do not treat those
compilation results as an iOS runtime observation. Record a real macOS run in
the release evidence owned by the maintainer before claiming iOS launch
support; runtime evidence is not stored in this public repository.

The product name Evidrilo is approved. A prism artwork is present in the
shared/Android resources and the iOS host includes a 1024×1024 AppIcon source;
final visual approval, provenance/legal clearance, build-matched screenshot,
and the complete submission asset package remain open. This host scaffold is
not itself final submission evidence.

## Versioning and handoff

Set `MARKETING_VERSION` to the canonical marketing version in the repository
root `version.props`. Increase `CURRENT_PROJECT_VERSION` for every new iOS
archive or upload; a published build number must never be reused. The alignment
checker verifies the checked-in baseline and release template before packaging.

After the free flow and device checks pass on macOS, archive and export the
`Release` scheme with the owner's Apple distribution identity. Keep the
`.xcarchive`, exported package, provisioning data, and signing credentials
outside the repository. A GitHub-hosted `.ipa` is useful only when its signing
and provisioning profile authorize the target devices; TestFlight or another
Apple-managed distribution path is required for general tester access.
