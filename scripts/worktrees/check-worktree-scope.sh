#!/usr/bin/env bash

set -Eeuo pipefail

usage() {
  printf '%s\n' "usage: $0 <lane> [--base REF] [--allow-structural] [--paths PATH ...]" >&2
}

[[ "$#" -ge 1 ]] || { usage; exit 2; }
lane=$1
shift
base=${EVIDRILO_SCOPE_BASE:-integration/full-vision}
allow_structural=0
explicit_paths=()

while [[ "$#" -gt 0 ]]; do
  case "$1" in
    --base)
      [[ "$#" -ge 2 ]] || { usage; exit 2; }
      base=$2
      shift 2
      ;;
    --allow-structural)
      allow_structural=1
      shift
      ;;
    --paths)
      shift
      while [[ "$#" -gt 0 ]]; do
        explicit_paths+=("$1")
        shift
      done
      ;;
    *)
      usage
      exit 2
      ;;
  esac
done

case "$lane" in
  integration|domain|mobile-ui|ingestion|platform|revenuecat|qa) ;;
  *)
    printf 'unknown lane: %s\n' "$lane" >&2
    exit 2
    ;;
esac

root=$(git rev-parse --show-toplevel 2>/dev/null) || {
  printf '%s\n' 'WORKTREE_SCOPE_BLOCKED: a Git worktree is required' >&2
  exit 2
}

changed_paths=()
if [[ "${#explicit_paths[@]}" -gt 0 ]]; then
  changed_paths=("${explicit_paths[@]}")
else
  git rev-parse --verify "$base" >/dev/null 2>&1 || {
    printf 'WORKTREE_SCOPE_BLOCKED: baseline does not resolve: %s\n' "$base" >&2
    exit 2
  }
  while IFS= read -r -d '' path; do changed_paths+=("$path"); done < <(git diff --name-only -z "$base...HEAD")
  while IFS= read -r -d '' path; do changed_paths+=("$path"); done < <(git diff --name-only -z)
  while IFS= read -r -d '' path; do changed_paths+=("$path"); done < <(git diff --cached --name-only -z)
  while IFS= read -r -d '' path; do changed_paths+=("$path"); done < <(git ls-files --others --exclude-standard -z)
fi

if [[ "${#changed_paths[@]}" -eq 0 ]]; then
  printf 'WORKTREE_SCOPE_PASS lane=%s changed=0\n' "$lane"
  exit 0
fi

is_private_or_generated() {
  case "$1" in
    internal|internal/*|audit|audit/*|research|research/*|next-gen|next-gen/*|Gurwi|Gurwi/*|video-notes|video-notes/*|.local|.local/*|.gradle-local|.gradle-local/*|.dotnet-local|.dotnet-local/*|build|build/*|*/build|*/build/*|bin|bin/*|*/bin|*/bin/*|obj|obj/*|*/obj|*/obj/*|.tmp|.tmp/*|*/.tmp|*/.tmp/*|.worktrees|.worktrees/*)
      return 0
      ;;
  esac
  return 1
}

is_owned() {
  local path=$1
  case "$lane" in
    integration)
      case "$path" in
        .github/*|.dockerignore|.gitignore|settings.gradle.kts|build.gradle.kts|gradle.properties|gradle/*|README.md|CONTRIBUTING.md|LICENSE|worktree-ownership.yml|docs/architecture/*|scripts/*|tooling/*|composeApp/src/commonMain/kotlin/dev/nextgen/mobile/EvidriloApp.kt|composeApp/src/commonMain/kotlin/dev/nextgen/mobile/App.kt|composeApp/src/commonMain/kotlin/dev/nextgen/mobile/Main.kt|modules/core/*|modules/application/access/*)
          return 0
          ;;
        composeApp/*|apps/mobile-shared/*|androidApp/*|iosApp/*|platform/contracts/*|contracts/*|deploy/*|docs/*|platform/README.md|platform/api.Tests/ContractBoundaryTests.cs)
          [[ "$allow_structural" -eq 1 ]]
          return
          ;;
      esac
      ;;
    domain)
      case "$path" in
        composeApp/src/*/kotlin/dev/nextgen/mobile/domain/*|apps/mobile-shared/src/*/kotlin/dev/nextgen/mobile/domain/*|modules/domain/*|modules/application/evidence-graph/*|modules/application/verification/*)
          return 0
          ;;
      esac
      ;;
    mobile-ui)
      case "$path" in
        composeApp/src/*/kotlin/dev/nextgen/mobile/surfaces/*|apps/mobile-shared/src/*/kotlin/dev/nextgen/mobile/surfaces/*|composeApp/src/*/kotlin/dev/nextgen/mobile/navigation/*|apps/mobile-shared/src/*/kotlin/dev/nextgen/mobile/navigation/*|composeApp/src/*/kotlin/dev/nextgen/mobile/resources/*|apps/mobile-shared/src/*/kotlin/dev/nextgen/mobile/resources/*|composeApp/src/commonMain/kotlin/dev/nextgen/mobile/EvidriloDesignSystem.kt|apps/mobile-shared/src/commonMain/kotlin/dev/nextgen/mobile/EvidriloDesignSystem.kt|composeApp/src/*/composeResources/*|apps/mobile-shared/src/*/composeResources/*|androidApp/src/main/AndroidManifest.xml|androidApp/src/main/res/*|apps/android/src/main/AndroidManifest.xml|apps/android/src/main/res/*|iosApp/iosApp/Assets.xcassets/*|iosApp/README.md|modules/features/*|modules/design-system/*)
          return 0
          ;;
      esac
      ;;
    ingestion)
      case "$path" in
        composeApp/src/*/kotlin/dev/nextgen/mobile/content/*|apps/mobile-shared/src/*/kotlin/dev/nextgen/mobile/content/*|composeApp/src/*/kotlin/dev/nextgen/mobile/storage/*|apps/mobile-shared/src/*/kotlin/dev/nextgen/mobile/storage/*|composeApp/src/*/kotlin/dev/nextgen/mobile/audio/*|apps/mobile-shared/src/*/kotlin/dev/nextgen/mobile/audio/*|modules/application/ingestion/*|modules/data/documents/*|modules/data/ai/*)
          return 0
          ;;
      esac
      ;;
    platform)
      case "$path" in
        platform/api/Billing/*) return 1 ;;
        platform/*|contracts/*) return 0 ;;
      esac
      ;;
    revenuecat)
      case "$path" in
        composeApp/src/*/kotlin/dev/nextgen/mobile/billing/*|apps/mobile-shared/src/*/kotlin/dev/nextgen/mobile/billing/*|modules/data/billing/*|modules/features/paywall/*|platform/api/Billing/*)
          return 0
          ;;
      esac
      ;;
    qa)
      case "$path" in
        docs/architecture/*) return 1 ;;
        tests/*|docs/*|audit/*|docs/submission/*|docs/operations/*) return 0 ;;
      esac
      ;;
  esac
  return 1
}

violations=0
seen=()
for path in "${changed_paths[@]}"; do
  duplicate=0
  for prior in "${seen[@]}"; do
    if [[ "$prior" == "$path" ]]; then
      duplicate=1
      break
    fi
  done
  [[ "$duplicate" -eq 1 ]] && continue
  seen+=("$path")
  if is_private_or_generated "$path"; then
    printf 'WORKTREE_SCOPE_VIOLATION lane=%s path=%s owner=integration(private-boundary)\n' "$lane" "$path" >&2
    violations=$((violations + 1))
  elif ! is_owned "$path"; then
    owner='unknown'
    case "$path" in
      composeApp/src/*/kotlin/dev/nextgen/mobile/domain/*|apps/mobile-shared/src/*/kotlin/dev/nextgen/mobile/domain/*) owner=domain ;;
      composeApp/src/*/kotlin/dev/nextgen/mobile/billing/*|apps/mobile-shared/src/*/kotlin/dev/nextgen/mobile/billing/*|platform/api/Billing/*) owner=revenuecat ;;
      platform/*|contracts/*) owner=platform ;;
      composeApp/src/*/kotlin/dev/nextgen/mobile/content/*|apps/mobile-shared/src/*/kotlin/dev/nextgen/mobile/content/*|composeApp/src/*/kotlin/dev/nextgen/mobile/storage/*|apps/mobile-shared/src/*/kotlin/dev/nextgen/mobile/storage/*|composeApp/src/*/kotlin/dev/nextgen/mobile/audio/*|apps/mobile-shared/src/*/kotlin/dev/nextgen/mobile/audio/*) owner=ingestion ;;
      composeApp/src/*/kotlin/dev/nextgen/mobile/surfaces/*|apps/mobile-shared/src/*/kotlin/dev/nextgen/mobile/surfaces/*|composeApp/src/*/kotlin/dev/nextgen/mobile/navigation/*|apps/mobile-shared/src/*/kotlin/dev/nextgen/mobile/navigation/*|composeApp/src/*/composeResources/*|apps/mobile-shared/src/*/composeResources/*|androidApp/src/main/res/*|apps/android/src/main/res/*|iosApp/iosApp/Assets.xcassets/*) owner=mobile-ui ;;
      tests/*|docs/*|audit/*) owner=qa ;;
      .github/*|gradle/*|scripts/*|tooling/*|settings.gradle.kts|build.gradle.kts|worktree-ownership.yml) owner=integration ;;
    esac
    printf 'WORKTREE_SCOPE_VIOLATION lane=%s path=%s owner=%s\n' "$lane" "$path" "$owner" >&2
    violations=$((violations + 1))
  fi
done

if [[ "$violations" -gt 0 ]]; then
  exit 1
fi

printf 'WORKTREE_SCOPE_PASS lane=%s changed=%s\n' "$lane" "${#seen[@]}"
