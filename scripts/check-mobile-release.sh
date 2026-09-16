#!/usr/bin/env bash
set -Eeuo pipefail

if [[ "$#" -lt 1 || "$#" -gt 4 ]]; then
  printf 'usage: %s <repository-root> [--require-android-artifact] [--require-ios-archive PATH]\n' "$0" >&2
  exit 2
fi

repository_root=$1
shift
if [[ ! -d "$repository_root" ]]; then
  printf 'repository root is not a directory\n' >&2
  exit 2
fi
repository_root=$(CDPATH= cd -- "$repository_root" && pwd)

require_android_artifact=0
ios_archive_path=''
while [[ "$#" -gt 0 ]]; do
  case "$1" in
    --require-android-artifact)
      require_android_artifact=1
      shift
      ;;
    --require-ios-archive)
      [[ "$#" -ge 2 ]] || { printf '%s\n' '--require-ios-archive requires a path' >&2; exit 2; }
      ios_archive_path=$2
      shift 2
      ;;
    *)
      printf 'unknown argument: %s\n' "$1" >&2
      exit 2
      ;;
  esac
done

failed=0

require_path() {
  local relative_path=$1
  if [[ ! -e "$repository_root/$relative_path" ]]; then
    printf 'missing mobile release path: %s\n' "$relative_path" >&2
    failed=1
  fi
}

require_text() {
  local relative_path=$1
  local label=$2
  local pattern=$3
  if ! rg -q -- "$pattern" "$repository_root/$relative_path"; then
    printf 'mobile release contract missing %s in %s\n' "$label" "$relative_path" >&2
    failed=1
  fi
}

android_gradle='androidApp/build.gradle.kts'
android_manifest='androidApp/src/main/AndroidManifest.xml'
ios_project='iosApp/iosApp.xcodeproj/project.pbxproj'

for relative_path in \
  "$android_gradle" \
  "$android_manifest" \
  androidApp/proguard-rules.pro \
  "$ios_project" \
  iosApp/iosApp/Info.plist \
  iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/AppIcon-1024.png \
  iosApp/Configuration/Release.xcconfig.example
do
  require_path "$relative_path"
done

require_text "$android_gradle" optimized-release 'isMinifyEnabled[[:space:]]*=[[:space:]]*true'
require_text "$android_gradle" resource-shrinking 'isShrinkResources[[:space:]]*=[[:space:]]*true'
require_text "$android_gradle" optimized-proguard 'proguard-android-optimize\.txt'
require_text "$android_gradle" signing-guard 'verifyReleaseSigning'
require_text "$android_manifest" backup-disabled 'android:allowBackup="false"'
require_text "$android_manifest" cleartext-disabled 'android:usesCleartextTraffic="false"'
require_text "$android_manifest" auth-callback 'android:scheme="evidrilo"'

if rg -q 'signingConfigs\.getByName\("debug"\)' "$repository_root/$android_gradle"; then
  printf '%s\n' 'Android release must never use the debug signing config' >&2
  failed=1
fi

ios_release_target=$(awk '
  /7555FFA7242A565B00829871 \/\* Release \*\// { in_release = 1 }
  in_release { print }
  in_release && /name = Release;/ { exit }
' "$repository_root/$ios_project")
if ! grep -Eq 'CODE_SIGN_IDENTITY = "Apple Distribution";' <<< "$ios_release_target"; then
  printf '%s\n' 'iOS Release must use Apple Distribution signing' >&2
  failed=1
fi
if ! grep -Eq 'DEVELOPMENT_TEAM = "\$\(TEAM_ID\)";' <<< "$ios_release_target"; then
  printf '%s\n' 'iOS Release must use an owner-supplied TEAM_ID' >&2
  failed=1
fi
if grep -Eq 'Apple Development|DEVELOPMENT_ASSET_PATHS|ENABLE_PREVIEWS' <<< "$ios_release_target"; then
  printf '%s\n' 'iOS Release contains development-only signing or preview settings' >&2
  failed=1
fi
require_text iosApp/Configuration/Release.xcconfig.example apple-team-placeholder 'TEAM_ID[[:space:]]*='
require_text iosApp/Configuration/Release.xcconfig.example release-identity 'CODE_SIGN_IDENTITY[[:space:]]*=[[:space:]]*Apple Distribution'

android_artifact="$repository_root/androidApp/build/outputs/bundle/release/androidApp-release.aab"
if [[ "$require_android_artifact" -eq 1 ]]; then
  if [[ -s "$android_artifact" ]]; then
    printf '%s\n' 'ANDROID_RELEASE_ARTIFACT: PASS (AAB exists; signing still must be verified separately)'
  else
    printf '%s\n' 'ANDROID_RELEASE_ARTIFACT: FAIL (release AAB is missing)' >&2
    failed=1
  fi
else
  printf '%s\n' 'ANDROID_RELEASE_ARTIFACT: NOT_REQUIRED (run the release Gradle task separately)'
fi

if [[ -n "$ios_archive_path" ]]; then
  if [[ -f "$ios_archive_path/Info.plist" ]]; then
    printf '%s\n' 'IOS_RELEASE_ARCHIVE: PASS (archive metadata exists; export/signing still requires owner verification)'
  else
    printf '%s\n' 'IOS_RELEASE_ARCHIVE: FAIL (xcarchive Info.plist is missing)' >&2
    failed=1
  fi
else
  printf '%s\n' 'IOS_RELEASE_ARCHIVE: NOT_RUN (macOS/Xcode archive path was not supplied)'
fi

if [[ "$failed" -ne 0 ]]; then
  exit 1
fi

printf '%s\n' 'ANDROID_RELEASE_SIGNING: EXTERNAL (local-only keystore required before upload)'
printf '%s\n' 'IOS_RELEASE_SIGNING: EXTERNAL (Apple Team ID and distribution profile required before upload)'
printf '%s\n' 'MOBILE_RELEASE_CONFIG_PASS'
