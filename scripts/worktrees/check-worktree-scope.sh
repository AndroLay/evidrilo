#!/usr/bin/env bash

set -Eeuo pipefail

usage() {
  printf '%s\n' "usage: $0 <lane> [--base REF] [--allow-structural] [--paths PATH ...]" >&2
}

[[ "$#" -ge 1 ]] || { usage; exit 2; }
lane=$1
shift
base=${EVIDRILO_SCOPE_BASE:-main}
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
  main|frontend|backend) ;;
  *)
    printf 'unknown lane: %s\n' "$lane" >&2
    exit 2
    ;;
esac

git rev-parse --show-toplevel >/dev/null 2>&1 || {
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
    main)
      case "$path" in
        .github/*|.dockerignore|.editorconfig|.gitattributes|.gitignore|settings.gradle.kts|build.gradle.kts|version.props|Directory.Build.props|gradle.properties|gradle/*|README.md|CONTRIBUTING.md|LICENSE|SECURITY.md|CHANGELOG.md|THIRD_PARTY_NOTICES.md|worktree-ownership.yml|examples/*|docs/*|scripts/*|tooling/*|tests/*)
          return 0
          ;;
        apps/*|modules/*|contracts/*|platform/*|infra/*)
          [[ "$allow_structural" -eq 1 ]]
          return
          ;;
      esac
      ;;
    frontend)
      case "$path" in
        apps/*|modules/features/*|modules/design-system/*)
          return 0
          ;;
      esac
      ;;
    backend)
      case "$path" in
        modules/core/*|modules/domain/*|modules/application/*|modules/data/*|contracts/*|platform/*|infra/*)
          return 0
          ;;
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
    printf 'WORKTREE_SCOPE_VIOLATION lane=%s path=%s owner=main(private-boundary)\n' "$lane" "$path" >&2
    violations=$((violations + 1))
  elif ! is_owned "$path"; then
    owner='unknown'
    case "$path" in
      apps/*|modules/features/*|modules/design-system/*) owner=frontend ;;
      modules/core/*|modules/domain/*|modules/application/*|modules/data/*|contracts/*|platform/*|infra/*) owner=backend ;;
      .github/*|.dockerignore|.editorconfig|.gitattributes|.gitignore|settings.gradle.kts|build.gradle.kts|version.props|Directory.Build.props|gradle.properties|gradle/*|README.md|CONTRIBUTING.md|LICENSE|SECURITY.md|CHANGELOG.md|THIRD_PARTY_NOTICES.md|worktree-ownership.yml|examples/*|docs/*|scripts/*|tooling/*|tests/*) owner=main ;;
    esac
    printf 'WORKTREE_SCOPE_VIOLATION lane=%s path=%s owner=%s\n' "$lane" "$path" "$owner" >&2
    violations=$((violations + 1))
  fi
done

if [[ "$violations" -gt 0 ]]; then
  exit 1
fi

printf 'WORKTREE_SCOPE_PASS lane=%s changed=%s\n' "$lane" "${#seen[@]}"
