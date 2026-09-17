#!/usr/bin/env bash
set -Eeuo pipefail

if [[ "$#" -ne 1 ]]; then
  printf 'usage: %s <candidate-package-root>\n' "$0" >&2
  exit 2
fi

candidate_root=$1
if [[ ! -d "$candidate_root" ]]; then
  printf 'candidate package root is not a directory\n' >&2
  exit 2
fi

failed=0

if ! command -v rg >/dev/null 2>&1; then
  printf 'credential scan unavailable: rg is required\n' >&2
  exit 2
fi

required_paths=(
  README.md
  LICENSE
  CONTRIBUTING.md
  .gitignore
  worktree-ownership.yml
  gradlew
  settings.gradle.kts
  apps/mobile-shared
  apps/android
  apps/ios
  contracts
  platform
  scripts
)

for required_path in "${required_paths[@]}"; do
  if [[ ! -e "$candidate_root/$required_path" ]]; then
    printf 'missing required public path: %s\n' "$required_path" >&2
    failed=1
  fi
done

forbidden_paths=(
  audit
  research
  next-gen
  video-notes
  Gurwi
  design
  internal
  docs/licenses/audio-narration-source-inventory.md
  docs/submission
  docs/operations
  docs/business
  docs/superpowers
  docs/architecture/revenuecat-integration.md
  .agents
  .superpowers
  .codex
  .local
  .kotlin
  .gradle-local
  .dotnet-local
  .tmp
)

for forbidden_path in "${forbidden_paths[@]}"; do
  if [[ -e "$candidate_root/$forbidden_path" ]]; then
    printf 'forbidden private path present: %s\n' "$forbidden_path" >&2
    failed=1
  fi
done

while IFS= read -r sensitive_path; do
  relative_path=${sensitive_path#"$candidate_root"/}
  case "$relative_path" in
    local.properties.example|*.env.example|apps/ios/Configuration/Config.xcconfig)
      continue
      ;;
  esac
  printf 'credential-bearing file present: %s\n' "$relative_path" >&2
  failed=1
done < <(find "$candidate_root" -type f \( -name 'local.properties' -o -name '*.keystore' -o -name '*.jks' -o -name '*.p12' -o -name '*.mobileprovision' -o -name '*.cer' -o -name '*.pem' -o -name '*.p8' -o -name '*.key' -o -name '*.crt' -o -name '*.der' -o -name '*.mobileconfig' -o -name '*.secret' -o -name '*.secrets' -o -name '*.credentials' -o -name '.env' -o -name '*.env.*' -o -name 'google-services.json' -o -name 'GoogleService-Info.plist' -o -name 'service-account*.json' -o -name 'credentials*.json' -o -name '*.xcconfig' -o -name '*.xcconfig.local' \) -print)

# Report only the fact that a match exists. Never print a credential-shaped value.
if rg -l -i --hidden \
  --glob '!**/.git/**' \
  --glob '!scripts/check-public-package.sh' \
  --glob '!scripts/check-public-package.test.mjs' \
  '(sk_(live|test)_[A-Za-z0-9]{20,}|sb_(secret|publishable)_[A-Za-z0-9_-]{20,}|service_role\s*[:=]\s*[A-Za-z0-9._-]{20,}|client_secret\s*[:=]\s*[A-Za-z0-9._-]{20,})' \
  "$candidate_root" >/dev/null 2>&1; then
  printf 'credential-shaped value detected in candidate package\n' >&2
  failed=1
else
  scan_exit=$?
  if [[ "$scan_exit" -ne 1 ]]; then
    printf 'credential scan could not complete\n' >&2
    failed=1
  fi
fi

if [[ "$failed" -ne 0 ]]; then
  exit 1
fi

printf '%s\n' 'PUBLIC_PACKAGE_CHECK_PASS'
