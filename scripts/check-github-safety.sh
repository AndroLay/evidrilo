#!/usr/bin/env bash
set -Eeuo pipefail

if [[ "$#" -gt 1 ]]; then
  printf 'usage: %s [git-repository-root]\n' "$0" >&2
  exit 2
fi

repository_root=${1:-.}
if [[ ! -d "$repository_root" ]]; then
  printf 'git repository root is not a directory\n' >&2
  exit 2
fi
repository_root=$(CDPATH= cd -- "$repository_root" && pwd)

if ! command -v git >/dev/null 2>&1; then
  printf 'GITHUB_SAFETY_CHECK_BLOCKED: git is required\n' >&2
  exit 2
fi

git_root=$(git -C "$repository_root" rev-parse --show-toplevel 2>/dev/null || true)
if [[ -z "$git_root" ]]; then
  printf '%s\n' 'GITHUB_SAFETY_CHECK_BLOCKED: usable Git metadata is required; use the public-package exporter until this workspace has a real Git checkout.' >&2
  exit 2
fi
git_root=$(CDPATH= cd -- "$git_root" && pwd)

failed=0

reject() {
  printf '%s\n' "$1" >&2
  failed=1
}

is_forbidden_path() {
  case "$1" in
    audit|audit/*|research|research/*|next-gen|next-gen/*|video-notes|video-notes/*|Gurwi|Gurwi/*|design|design/*|internal|internal/*|docs/submission|docs/submission/*|docs/operations|docs/operations/*|docs/business|docs/business/*|docs/superpowers|docs/superpowers/*|docs/licenses/audio-narration-source-inventory.md|.agents|.agents/*|.superpowers|.superpowers/*|.codex|.codex/*|.local|.local/*|.kotlin|.kotlin/*|.gradle-local|.gradle-local/*|.dotnet-local|.dotnet-local/*|.dotnet-tmp|.dotnet-tmp/*)
      return 0
      ;;
  esac
  return 1
}

is_generated_path() {
  case "$1" in
    build|build/*|*/build|*/build/*|bin|bin/*|*/bin|*/bin/*|obj|obj/*|*/obj|*/obj/*|.gradle|.gradle/*|*/.gradle|*/.gradle/*|.idea|.idea/*|*/.idea|*/.idea/*|.playwright|.playwright/*|*/.playwright|*/.playwright/*|.playwright-cli|.playwright-cli/*|*/.playwright-cli|*/.playwright-cli/*|TestResults|TestResults/*|*/TestResults|*/TestResults/*|coverage|coverage/*|*/coverage|*/coverage/*|DerivedData|DerivedData/*|*/DerivedData|*/DerivedData/*|xcuserdata|xcuserdata/*|*/xcuserdata|*/xcuserdata/*|.tmp|.tmp/*|*/.tmp|*/.tmp/*)
      return 0
      ;;
  esac
  return 1
}

is_sensitive_path() {
  case "$1" in
    local.properties|*/local.properties|*.keystore|*.jks|*.p12|*.mobileprovision|*.cer|*.pem|*.p8|*.key|*.crt|*.der|*.mobileconfig|*.secret|*.secrets|*.credentials|*.env|*.env.*|google-services.json|*/google-services.json|GoogleService-Info.plist|*/GoogleService-Info.plist|service-account*.json|*/service-account*.json|credentials*.json|*/credentials*.json|iosApp/Configuration/*.xcconfig|iosApp/Configuration/*.xcconfig.local)
      [[ "$1" == iosApp/Configuration/Config.xcconfig ]] && return 1
      [[ "$1" == *.env.example ]] && return 1
      return 0
      ;;
  esac
  return 1
}

is_public_path() {
  case "$1" in
    README.md|LICENSE|CONTRIBUTING.md|.dockerignore|.gitignore|gradlew|gradle.properties|build.gradle.kts|settings.gradle.kts|local.properties.example)
      return 0
      ;;
    .github/*|androidApp/*|composeApp/*|deploy/*|gradle/*|iosApp/*|platform/*|scripts/*)
      return 0
      ;;
    docs/README.md|docs/decisions.md|docs/development.md|docs/release.md|docs/roadmap.md|docs/testing.md|docs/architecture/platform-decision.md|docs/architecture/repository-structure.md|docs/architecture/revenuecat.md|docs/licenses/SourceSans3-OFL-1.1.md|docs/licenses/audio-assets.md|docs/product/m0-product-contract.md)
      return 0
      ;;
  esac
  return 1
}

check_tracked_paths() {
  local record path mode
  while IFS= read -r -d '' record; do
    mode=${record%% *}
    path=${record#*$'\t'}
    if [[ "$mode" == 120000 ]]; then
      reject 'tracked symbolic link detected; inspect it before any public push'
      continue
    fi
    if is_forbidden_path "$path"; then
      reject "forbidden private path is tracked: $path"
      continue
    fi
    if is_generated_path "$path"; then
      reject "generated path is tracked: $path"
      continue
    fi
    if is_sensitive_path "$path"; then
      reject "credential-bearing path is tracked: $path"
      continue
    fi
    if ! is_public_path "$path"; then
      reject "tracked path is outside the public allowlist: $path"
    fi
  done < <(git -C "$git_root" ls-files --cached --stage -z)
}

check_ignored_probes() {
  local probe
  local probes=(
    .env
    .env.local
    local.properties
    platform/api/.env.local
    google-services.json
    iosApp/GoogleService-Info.plist
    iosApp/Configuration/Debug.xcconfig
    iosApp/Configuration/Local.xcconfig
    design/evidrilo/mockup.png
    internal/design/mockup.png
    docs/licenses/audio-narration-source-inventory.md
    audit/private-note.md
    research/next-gen/private-note.md
    Gurwi/private-image.jpeg
    docs/business/private-plan.md
    .superpowers/local-state.json
    build/output.bin
    .gradle-local/cache.bin
    .tmp/compiler-report.json
  )
  for probe in "${probes[@]}"; do
    if ! git -C "$git_root" check-ignore --no-index -q -- "$probe"; then
      reject "Git ignore rule missing for local/private probe: $probe"
    fi
  done
}

check_untracked_files() {
  local count=0 path
  while IFS= read -r -d '' path; do
    count=$((count + 1))
  done < <(git -C "$git_root" ls-files --others --exclude-standard -z)
  if [[ "$count" -ne 0 ]]; then
    reject "untracked, non-ignored files would be eligible for git add: $count"
  fi
}

check_secret_content() {
  local pattern
  pattern='(-----BEGIN[^[:cntrl:]]*PRIVATE KEY-----|sk_(live|test)_[A-Za-z0-9]{20,}|sb_secret_[A-Za-z0-9_-]{20,}|service_role[[:space:]]*[:=][[:space:]]*[A-Za-z0-9._-]{20,}|client_secret[[:space:]]*[:=][[:space:]]*[A-Za-z0-9._-]{20,}|(DATABASE_PASSWORD|POSTGRES_PASSWORD|REVENUECAT_WEBHOOK_SECRET|REVENUECAT_WEBHOOK_AUTHORIZATION|PRIVATE_KEY)[[:space:]]*[:=][[:space:]]*[^[:space:]#<][^[:space:]#]{7,})'

  if git -C "$git_root" grep --cached -I -l -E "$pattern" -- . ':(exclude)scripts/check-github-safety.sh' ':(exclude)scripts/check-github-safety.test.mjs' >/dev/null 2>&1; then
    reject 'credential-shaped content detected in the Git index'
  else
    local rc=$?
    if [[ "$rc" -ne 1 ]]; then
      reject 'Git index credential scan could not complete'
    fi
  fi

  if git -C "$git_root" grep -I -l -E "$pattern" -- . ':(exclude)scripts/check-github-safety.sh' ':(exclude)scripts/check-github-safety.test.mjs' >/dev/null 2>&1; then
    reject 'credential-shaped content detected in the working tree'
  else
    local rc=$?
    if [[ "$rc" -ne 1 ]]; then
      reject 'working-tree credential scan could not complete'
    fi
  fi
}

check_tracked_paths
check_ignored_probes
check_untracked_files
check_secret_content

if [[ "$failed" -ne 0 ]]; then
  exit 1
fi

printf '%s\n' 'GITHUB_SAFETY_CHECK_PASS'
