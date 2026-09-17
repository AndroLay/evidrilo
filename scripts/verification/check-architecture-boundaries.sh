#!/usr/bin/env bash

set -Eeuo pipefail

repository_root=${1:-.}
repository_root=$(CDPATH= cd -- "$repository_root" && pwd)
failed=0

for domain_root in \
  "$repository_root/apps/mobile-shared/src/commonMain/kotlin/dev/nextgen/mobile/domain" \
  "$repository_root/modules/domain"; do
  [[ -d "$domain_root" ]] || continue
  pattern='^(import (android\\.|androidx\\.|com\\.revenuecat\\.|io\\.ktor\\.|org\\.postgresql\\.|org\\.jetbrains\\.exposed\\.|java\\.sql\\.|javax\\.sql\\.|dev\\.nextgen\\.mobile\\.(billing|storage|sync|account|analytics|audio|content|security)\\.))'
  if rg -n --glob '*.kt' "$pattern" "$domain_root" >/dev/null 2>&1; then
    printf 'ARCHITECTURE_BOUNDARY_VIOLATION: domain imports infrastructure (%s)\n' "$domain_root" >&2
    rg -n --glob '*.kt' "$pattern" "$domain_root" | sed -n '1,20p' >&2
    failed=1
  fi
done

core_root="$repository_root/modules/core/src/commonMain"
if [[ -d "$core_root" ]]; then
  pattern='^(import (android\.|androidx\.|com\.revenuecat\.|io\.ktor\.|org\.postgresql\.|org\.jetbrains\.exposed\.|java\.sql\.|javax\.sql\.|dev\.nextgen\.mobile\.(domain|data|application|billing|sync|account)\.))'
  if rg -n --glob '*.kt' "$pattern" "$core_root" >/dev/null 2>&1; then
    printf 'ARCHITECTURE_BOUNDARY_VIOLATION: core imports an upper or platform layer\n' >&2
    rg -n --glob '*.kt' "$pattern" "$core_root" | sed -n '1,20p' >&2
    failed=1
  fi
fi

application_root="$repository_root/modules/application/src/commonMain"
if [[ -d "$application_root" ]]; then
  pattern='^(import (android\.|androidx\.|com\.revenuecat\.|io\.ktor\.|org\.postgresql\.|org\.jetbrains\.exposed\.|java\.sql\.|javax\.sql\.|dev\.nextgen\.mobile\.(billing|audio|content|surfaces|design)\.))'
  if rg -n --glob '*.kt' "$pattern" "$application_root" >/dev/null 2>&1; then
    printf 'ARCHITECTURE_BOUNDARY_VIOLATION: application imports UI/provider/infrastructure\n' >&2
    rg -n --glob '*.kt' "$pattern" "$application_root" | sed -n '1,20p' >&2
    failed=1
  fi
fi

feature_root="$repository_root/modules/features/src/commonMain"
if [[ -d "$feature_root" ]]; then
  pattern='^(import (android\.|androidx\.|com\.revenuecat\.|io\.ktor\.|org\.postgresql\.|org\.jetbrains\.exposed\.|java\.sql\.|javax\.sql\.|dev\.nextgen\.mobile\.(billing|security|account|audio)\.))'
  if rg -n --glob '*.kt' "$pattern" "$feature_root" >/dev/null 2>&1; then
    printf 'ARCHITECTURE_BOUNDARY_VIOLATION: features imports provider/platform infrastructure\n' >&2
    rg -n --glob '*.kt' "$pattern" "$feature_root" | sed -n '1,20p' >&2
    failed=1
  fi
fi

for feature_root in \
  "$repository_root/apps/mobile-shared/src/commonMain/kotlin/dev/nextgen/mobile/surfaces" \
  "$repository_root/modules/features" \
  "$repository_root/modules/design-system"; do
  [[ -d "$feature_root" ]] || continue
  pattern='^(import (com\\.revenuecat\\.|io\\.ktor\\.|org\\.postgresql\\.|java\\.sql\\.|javax\\.sql\\.))'
  if rg -n --glob '*.kt' "$pattern" "$feature_root" >/dev/null 2>&1; then
    printf 'ARCHITECTURE_BOUNDARY_VIOLATION: feature/design layer imports infrastructure (%s)\n' "$feature_root" >&2
    rg -n --glob '*.kt' "$pattern" "$feature_root" | sed -n '1,20p' >&2
    failed=1
  fi
done

if [[ "$failed" -ne 0 ]]; then
  exit 1
fi

printf '%s\n' 'ARCHITECTURE_BOUNDARY_PASS'
