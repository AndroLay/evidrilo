# Evidrilo Android release

The Android app uses `dev.nextgen.mobile` as its current application ID,
`minSdk 26`, `targetSdk 35`, and version defaults `1` / the canonical release
version in the repository root `version.props`. Only the platform build number
is configured locally with `androidVersionCode`.

## Local candidate

From the repository root:

```bash
./gradlew --no-configuration-cache :androidApp:bundleRelease
bash scripts/check-mobile-release.sh . --require-android-artifact
```

This produces an unsigned candidate at
`apps/android/build/outputs/bundle/release/androidApp-release.aab`. It is not a
Play-upload artifact until it is signed with the owner's upload key.

## Signed upload candidate

Keep the keystore and passwords outside Git. Supply all four values in ignored
`local.properties` or through the `EVIDRILO_ANDROID_RELEASE_*` environment
variables:

```text
androidReleaseKeystore=/absolute/path/to/evidrilo-upload.jks
androidReleaseStorePassword=<local-only>
androidReleaseKeyAlias=evidrilo
androidReleaseKeyPassword=<local-only>
```

Then run:

```bash
./gradlew :androidApp:verifyReleaseSigning :androidApp:bundleRelease
```

The release variant uses R8/resource shrinking, disables Android backup and
cleartext transport, and never falls back to the debug signing key. Play
Console upload, internal testing, and device/accessibility acceptance remain
owner-run release gates.

## Versioning and handoff

The Android marketing version is read from the root `version.props` and must
match iOS `MARKETING_VERSION`. Increase `androidVersionCode` for every new
Android upload; a published version code must never be reused. Run
`bash scripts/check-version-alignment.sh .` before packaging.

For a user-downloadable GitHub Release, produce a signed APK with
`:androidApp:assembleRelease`. For Play Console, produce the signed AAB with
`:androidApp:bundleRelease`. The unsigned local AAB is not a distributable
release. Attach only the final signed artifact, checksum, and release notes;
keep the keystore and all signing values outside the repository.
