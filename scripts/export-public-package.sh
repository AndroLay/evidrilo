#!/usr/bin/env bash
set -Eeuo pipefail

if [[ "$#" -ne 2 ]]; then
  printf 'usage: %s <repository-root> <empty-destination>\n' "$0" >&2
  exit 2
fi

source_root=$1
destination=$2
if [[ ! -d "$source_root" ]]; then
  printf 'repository root is not a directory\n' >&2
  exit 2
fi
source_root=$(CDPATH= cd -- "$source_root" && pwd)

if ! command -v rsync >/dev/null 2>&1; then
  printf 'public package export unavailable: rsync is required\n' >&2
  exit 2
fi

destination_parent=$(dirname -- "$destination")
if [[ ! -d "$destination_parent" ]]; then
  printf 'destination parent must already exist\n' >&2
  exit 2
fi
destination_parent=$(CDPATH= cd -- "$destination_parent" && pwd)
destination="$destination_parent/$(basename -- "$destination")"

case "$destination/" in
  "$source_root/"*)
    printf 'refusing to export inside the source repository\n' >&2
    exit 2
    ;;
esac

if [[ -e "$destination" ]]; then
  if [[ ! -d "$destination" ]]; then
    printf 'destination exists and is not a directory\n' >&2
    exit 2
  fi
  if find "$destination" -mindepth 1 -print -quit | grep -q .; then
    printf 'destination must be empty; refusing to overwrite it\n' >&2
    exit 2
  fi
else
  :
fi

if ! command -v node >/dev/null 2>&1; then
  printf '%s\n' 'public package export unavailable: node is required for audio and markdown validation' >&2
  exit 2
fi

if [[ ! -f "$source_root/scripts/validate-audio-assets.mjs" ]]; then
  printf '%s\n' 'public package export unavailable: audio validator is missing from the source repository' >&2
  exit 2
fi
node "$source_root/scripts/validate-audio-assets.mjs" "$source_root"

if [[ ! -e "$destination" ]]; then
  mkdir -- "$destination"
fi

copy_options=(
  --archive
  --exclude 'build/'
  --exclude '**/build/**'
  --exclude 'bin/'
  --exclude '**/bin/**'
  --exclude 'obj/'
  --exclude '**/obj/**'
  --exclude '.gradle/'
  --exclude '**/.gradle/**'
  --exclude '.gradle-local/'
  --exclude '.dotnet-local/'
  --exclude '.tmp/'
  --exclude '.local/'
  --exclude '.kotlin/'
  --exclude 'internal/'
  --exclude '**/internal/**'
  --exclude '**/.idea/**'
  --exclude '**/.playwright/**'
  --exclude '**/.playwright-cli/**'
  --exclude '**/TestResults/**'
  --exclude '**/coverage/**'
  --exclude '**/.cache/**'
  --exclude '**/.direnv/**'
  --exclude '**/.envrc'
  --exclude '**/local.properties'
  --include '**/*.env.example'
  --include '*.env.example'
  --exclude '**/*.env'
  --exclude '**/*.env.*'
  --exclude '**/*.keystore'
  --exclude '**/*.jks'
  --exclude '**/*.p12'
  --exclude '**/*.pem'
  --exclude '**/*.p8'
  --exclude '**/*.key'
  --exclude '**/*.crt'
  --exclude '**/*.der'
  --exclude '**/*.mobileconfig'
  --exclude '**/*.secret'
  --exclude '**/*.secrets'
  --exclude '**/*.credentials'
  --exclude '**/*.mobileprovision'
  --exclude '**/*.cer'
  --exclude '**/google-services.json'
  --exclude '**/GoogleService-Info.plist'
  --exclude '**/service-account*.json'
  --exclude '**/credentials*.json'
  --include 'Configuration/Config.xcconfig'
  --exclude '**/*.xcconfig'
  --exclude 'xcuserdata/'
  --exclude 'DerivedData/'
)

public_paths=(
  README.md
  LICENSE
  CONTRIBUTING.md
  .gitignore
  worktree-ownership.yml
  gradlew
  gradle
  gradle.properties
  build.gradle.kts
  settings.gradle.kts
  local.properties.example
  .dockerignore
  .github
  composeApp
  apps/android
  iosApp
  contracts
  platform
  deploy
  scripts
  docs/README.md
  docs/development.md
  docs/decisions.md
  docs/release.md
  docs/roadmap.md
  docs/testing.md
  docs/architecture/platform-decision.md
  docs/architecture/repository-structure.md
  docs/architecture/revenuecat.md
  docs/licenses/SourceSans3-OFL-1.1.md
  docs/licenses/audio-assets.md
  docs/product/m0-product-contract.md
)

for relative_path in "${public_paths[@]}"; do
  source_path="$source_root/$relative_path"
  [[ -e "$source_path" ]] || continue
  destination_path="$destination/$relative_path"
  if [[ -d "$source_path" ]]; then
    mkdir -p -- "$destination_path"
    rsync "${copy_options[@]}" "$source_path/" "$destination_path/"
  else
    mkdir -p -- "$(dirname -- "$destination_path")"
    rsync "${copy_options[@]}" "$source_path" "$(dirname -- "$destination_path")/"
  fi
done

node "$destination/scripts/validate-audio-assets.mjs" "$destination"
node "$source_root/scripts/sanitize-public-markdown-links.mjs" "$destination"

bash "$source_root/scripts/check-public-package.sh" "$destination"
bash "$source_root/scripts/check-deployment.sh" "$destination"
printf '%s\n' 'PUBLIC_PACKAGE_EXPORT_PASS'
