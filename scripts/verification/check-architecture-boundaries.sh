#!/usr/bin/env bash

set -Eeuo pipefail

repository_root=${1:-.}
repository_root=$(CDPATH= cd -- "$repository_root" && pwd)
failed=0

if ! command -v rg >/dev/null 2>&1; then
  printf '%s\n' 'ARCHITECTURE_BOUNDARY_SCAN_UNAVAILABLE: rg is required for a complete architecture scan' >&2
  exit 2
fi

scan_forbidden_imports() {
  local description=$1
  local root=$2
  local pattern=$3
  local matches
  local status

  [[ -d "$root" ]] || return 0

  if matches=$(rg -n --glob '*.kt' "$pattern" "$root" 2>&1); then
    printf 'ARCHITECTURE_BOUNDARY_VIOLATION: %s (%s)\n' "$description" "$root" >&2
    printf '%s\n' "$matches" | sed -n '1,20p' >&2
    return 1
  else
    status=$?
    if [[ "$status" -eq 1 ]]; then
      return 0
    fi
    printf 'ARCHITECTURE_BOUNDARY_SCAN_ERROR: %s (%s), rg exited %s\n' "$description" "$root" "$status" >&2
    printf '%s\n' "$matches" | sed -n '1,20p' >&2
    return 2
  fi
}

for domain_root in \
  "$repository_root/apps/mobile-shared/src/commonMain/kotlin/dev/nextgen/mobile/domain" \
  "$repository_root/modules/domain"; do
  [[ -d "$domain_root" ]] || continue
  pattern='^(import (android\.|androidx\.|com\.revenuecat\.|io\.ktor\.|org\.postgresql\.|org\.jetbrains\.exposed\.|java\.sql\.|javax\.sql\.|dev\.nextgen\.mobile\.(billing|storage|sync|account|analytics|audio|content|security)\.))'
  if ! scan_forbidden_imports 'domain imports infrastructure' "$domain_root" "$pattern"; then
    failed=1
  fi
done

core_root="$repository_root/modules/core/src/commonMain"
if [[ -d "$core_root" ]]; then
  pattern='^(import (android\.|androidx\.|com\.revenuecat\.|io\.ktor\.|org\.postgresql\.|org\.jetbrains\.exposed\.|java\.sql\.|javax\.sql\.|dev\.nextgen\.mobile\.(domain|data|application|billing|sync|account)\.))'
  if ! scan_forbidden_imports 'core imports an upper or platform layer' "$core_root" "$pattern"; then
    failed=1
  fi
fi

application_root="$repository_root/modules/application/src/commonMain"
if [[ -d "$application_root" ]]; then
  pattern='^(import (android\.|androidx\.|com\.revenuecat\.|io\.ktor\.|org\.postgresql\.|org\.jetbrains\.exposed\.|java\.sql\.|javax\.sql\.|dev\.nextgen\.mobile\.(billing|audio|content|surfaces|design)\.))'
  if ! scan_forbidden_imports 'application imports UI/provider/infrastructure' "$application_root" "$pattern"; then
    failed=1
  fi
fi

feature_root="$repository_root/modules/features/src/commonMain"
if [[ -d "$feature_root" ]]; then
  pattern='^(import (android\.|androidx\.|com\.revenuecat\.|io\.ktor\.|org\.postgresql\.|org\.jetbrains\.exposed\.|java\.sql\.|javax\.sql\.|dev\.nextgen\.mobile\.(billing|security|account|audio)\.))'
  if ! scan_forbidden_imports 'features imports provider/platform infrastructure' "$feature_root" "$pattern"; then
    failed=1
  fi
fi

for feature_root in \
  "$repository_root/apps/mobile-shared/src/commonMain/kotlin/dev/nextgen/mobile/surfaces" \
  "$repository_root/modules/features" \
  "$repository_root/modules/design-system"; do
  [[ -d "$feature_root" ]] || continue
  pattern='^(import (com\.revenuecat\.|io\.ktor\.|org\.postgresql\.|java\.sql\.|javax\.sql\.))'
  if ! scan_forbidden_imports 'feature/design layer imports infrastructure' "$feature_root" "$pattern"; then
    failed=1
  fi
done

if [[ "$failed" -ne 0 ]]; then
  exit 1
fi

printf '%s\n' 'ARCHITECTURE_BOUNDARY_PASS'
