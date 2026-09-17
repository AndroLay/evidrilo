#!/usr/bin/env bash
set -Eeuo pipefail

if [[ "$#" -ne 1 ]]; then
  printf 'usage: %s <repository-root>\n' "$0" >&2
  exit 2
fi

repository_root=$1
if [[ ! -d "$repository_root" ]]; then
  printf 'repository root is not a directory\n' >&2
  exit 2
fi
repository_root=$(CDPATH= cd -- "$repository_root" && pwd)

failed=0
fail() {
  printf 'VERSION_ALIGNMENT_FAIL: %s\n' "$1" >&2
  failed=1
}

require_file() {
  local relative_path=$1
  if [[ ! -f "$repository_root/$relative_path" ]]; then
    fail "missing required file: $relative_path"
    return 1
  fi
}

for relative_path in \
  version.props \
  Directory.Build.props \
  build.gradle.kts \
  apps/android/build.gradle.kts \
  apps/ios/Configuration/Config.xcconfig \
  apps/ios/Configuration/Release.xcconfig.example \
  infra/docker/api.Dockerfile \
  infra/docker/worker.Dockerfile
do
  require_file "$relative_path" || true
done

version_file="$repository_root/version.props"
version_matches=$(sed -nE 's#^[[:space:]]*<EvidriloReleaseVersion>([^<]+)</EvidriloReleaseVersion>[[:space:]]*$#\1#p' "$version_file" 2>/dev/null || true)
version_line_count=$(printf '%s\n' "$version_matches" | sed '/^$/d' | wc -l | tr -d ' ')
if [[ "$version_line_count" != 1 ]]; then
  fail 'version.props must contain exactly one EvidriloReleaseVersion value'
  release_version=''
else
  release_version=$(printf '%s\n' "$version_matches" | sed -n '1p' | tr -d '\r')
fi

if [[ -n "$release_version" && ! "$release_version" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  fail "release version is not MAJOR.MINOR.PATCH: $release_version"
fi

if [[ -n "$release_version" ]]; then
  check_ios_version() {
    local relative_path=$1
    local actual
    actual=$(sed -nE 's/^[[:space:]]*MARKETING_VERSION[[:space:]]*=[[:space:]]*([^[:space:]#]+).*$/\1/p' "$repository_root/$relative_path" | sed -n '1p')
    if [[ "$actual" != "$release_version" ]]; then
      fail "$relative_path MARKETING_VERSION=$actual does not match $release_version"
    fi
  }

  check_ios_version apps/ios/Configuration/Config.xcconfig
  check_ios_version apps/ios/Configuration/Release.xcconfig.example
fi

if ! rg -q 'version = "\$releaseVersion-SNAPSHOT"' "$repository_root/build.gradle.kts"; then
  fail 'Gradle root version must derive from releaseVersion in version.props'
fi
if ! rg -q 'rootProject\.version\.toString\(\)\.removeSuffix\("-SNAPSHOT"\)' "$repository_root/apps/android/build.gradle.kts"; then
  fail 'Android versionName must derive from the Gradle root version'
fi
if ! rg -q 'Import Project="\$\(MSBuildThisFileDirectory\)version\.props"' "$repository_root/Directory.Build.props"; then
  fail '.NET must import the canonical version.props file'
fi
for dockerfile in infra/docker/api.Dockerfile infra/docker/worker.Dockerfile; do
  if ! rg -q '^ARG EVIDRILO_VERSION=' "$repository_root/$dockerfile"; then
    fail "$dockerfile must accept EVIDRILO_VERSION for release metadata"
  fi
  if ! rg -q 'org\.opencontainers\.image\.version=' "$repository_root/$dockerfile"; then
    fail "$dockerfile must label the image with EVIDRILO_VERSION"
  fi
done

if [[ "$failed" -ne 0 ]]; then
  exit 1
fi

printf 'VERSION_ALIGNMENT_PASS (releaseVersion=%s; platformBuilds=independent)\n' "$release_version"
