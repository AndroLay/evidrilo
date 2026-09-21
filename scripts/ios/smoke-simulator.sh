#!/usr/bin/env bash
set -Eeuo pipefail

if [[ "$#" -lt 3 || "$#" -gt 4 ]]; then
  printf 'usage: %s <app-path> <bundle-id> <artifact-directory> [simulator-udid]\n' "$0" >&2
  exit 2
fi

app_path=$1
bundle_id=$2
artifact_directory=$3
simulator_udid=${4:-}

if ! command -v xcrun >/dev/null 2>&1; then
  printf '%s\n' 'xcrun is required for the iOS simulator smoke test' >&2
  exit 2
fi
if [[ ! -d "$app_path" ]]; then
  printf 'iOS app bundle does not exist: %s\n' "$app_path" >&2
  exit 2
fi

mkdir -p "$artifact_directory"

if [[ -z "$simulator_udid" ]]; then
  simulator_udid=$(xcrun simctl list devices available | sed -n '/iPhone/ s/.*(\([A-Fa-f0-9]\{8\}-[A-Fa-f0-9]\{4\}-[A-Fa-f0-9]\{4\}-[A-Fa-f0-9]\{4\}-[A-Fa-f0-9]\{12\}\)).*/\1/p' | head -n 1)
fi

if [[ -z "$simulator_udid" ]]; then
  printf '%s\n' 'No available iPhone simulator was found' >&2
  xcrun simctl list devices available > "$artifact_directory/available-devices.txt" || true
  exit 1
fi

collect_evidence() {
  xcrun simctl list devices > "$artifact_directory/devices-final.txt" 2>&1 || true
  xcrun simctl spawn "$simulator_udid" log show --last 5m --style compact \
    --predicate 'process == "Evidrilo"' > "$artifact_directory/simulator.log" 2>&1 || true
  xcrun simctl shutdown "$simulator_udid" >/dev/null 2>&1 || true
}
trap collect_evidence EXIT

printf 'simulator_udid=%s\n' "$simulator_udid" > "$artifact_directory/run-metadata.txt"
xcrun simctl list devices available > "$artifact_directory/devices-available.txt"
xcrun simctl boot "$simulator_udid" >/dev/null 2>&1 || true
xcrun simctl bootstatus "$simulator_udid" -b
xcrun simctl install "$simulator_udid" "$app_path"
xcrun simctl launch "$simulator_udid" "$bundle_id" | tee "$artifact_directory/launch.txt"
xcrun simctl io "$simulator_udid" screenshot "$artifact_directory/ios-launch.png"

printf 'IOS_SIMULATOR_SMOKE_PASS\n'
