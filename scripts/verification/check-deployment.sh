#!/usr/bin/env bash
set -Eeuo pipefail

if [[ "$#" -gt 1 ]]; then
  printf 'usage: %s [repository-root]\n' "$0" >&2
  exit 2
fi

repository_root=${1:-.}
if [[ ! -d "$repository_root" ]]; then
  printf 'repository root is not a directory\n' >&2
  exit 2
fi
repository_root=$(CDPATH= cd -- "$repository_root" && pwd)

if ! command -v rg >/dev/null 2>&1; then
  printf 'deployment check unavailable: rg is required\n' >&2
  exit 2
fi

failed=0
require_path() {
  local relative_path=$1
  if [[ ! -e "$repository_root/$relative_path" ]]; then
    printf 'missing deployment path: %s\n' "$relative_path" >&2
    failed=1
  fi
}

require_text() {
  local relative_path=$1
  local label=$2
  local pattern=$3
  local file="$repository_root/$relative_path"
  if [[ ! -f "$file" ]]; then
    return
  fi
  if ! rg -q --fixed-strings -- "$pattern" "$file"; then
    printf 'deployment contract missing %s in %s\n' "$label" "$relative_path" >&2
    failed=1
  fi
}

for relative_path in \
  .dockerignore \
  infra/README.md \
  infra/docker/api.Dockerfile \
  infra/docker/worker.Dockerfile \
  infra/environments/local/docker-compose.yml
do
  require_path "$relative_path"
done

require_text .dockerignore local.properties local.properties
require_text .dockerignore environment-files '*.env'
require_text .dockerignore private-research research/
require_text .dockerignore private-internal internal/
require_text .dockerignore private-media Gurwi/
require_text .dockerignore generated-temp '.tmp'
require_text infra/docker/api.Dockerfile api-build-image 'mcr.microsoft.com/dotnet/sdk:10.0'
require_text infra/docker/api.Dockerfile api-runtime-image 'mcr.microsoft.com/dotnet/aspnet:10.0'
require_text infra/docker/api.Dockerfile non-root-runtime 'USER $APP_UID'
require_text infra/docker/worker.Dockerfile worker-build-image 'mcr.microsoft.com/dotnet/sdk:10.0'
require_text infra/docker/worker.Dockerfile worker-runtime-image 'mcr.microsoft.com/dotnet/runtime:10.0'
require_text infra/docker/worker.Dockerfile non-root-runtime 'USER $APP_UID'
require_text infra/environments/local/docker-compose.yml postgres-service '  postgres:'
require_text infra/environments/local/docker-compose.yml auth-bootstrap-service '  auth-shim:'
require_text infra/environments/local/docker-compose.yml auth-bootstrap-script 'platform/database/integration/auth-shim.sql'
require_text infra/environments/local/docker-compose.yml migration-service '  migrations:'
require_text infra/environments/local/docker-compose.yml api-service '  api:'
require_text infra/environments/local/docker-compose.yml worker-service '  worker:'
require_text infra/environments/local/docker-compose.yml database-healthcheck '    healthcheck:'
require_text infra/environments/local/docker-compose.yml healthy-dependency 'condition: service_healthy'
require_text infra/environments/local/docker-compose.yml auth-bootstrap-order '      auth-shim:'
require_text infra/environments/local/docker-compose.yml migration-order 'condition: service_completed_successfully'
require_text infra/environments/local/docker-compose.yml api-port-override '${EVIDRILO_API_PORT:-5080}:5080'

deployment_root="$repository_root/infra"
if [[ -d "$deployment_root" ]]; then
  if rg -n -i --hidden --glob '!README.md' '(^|:)latest([[:space:]]|$)' "$deployment_root" >/dev/null 2>&1; then
    printf 'floating latest image tag is not allowed in deployment artifacts\n' >&2
    failed=1
  fi

  if rg -l -i --hidden \
    '(DATABASE_PASSWORD|POSTGRES_PASSWORD|SUPABASE_SERVICE_ROLE_KEY|REVENUECAT_WEBHOOK_(SECRET|AUTHORIZATION)|CLIENT_SECRET|PRIVATE_KEY)[[:space:]]*[:=][[:space:]]*[^[:space:]#]+' \
    "$deployment_root" >/dev/null 2>&1; then
    printf 'credential-shaped assignment detected in deployment artifacts\n' >&2
    failed=1
  else
    scan_exit=$?
    if [[ "$scan_exit" -ne 1 ]]; then
      printf 'deployment credential scan could not complete\n' >&2
      failed=1
    fi
  fi

  if find "$deployment_root" -type f \( -name '*.env' -o -name '*.pem' -o -name '*.p12' -o -name '*.jks' -o -name '*.keystore' \) -print -quit | grep -q .; then
    printf 'credential-bearing file extension detected under infra/\n' >&2
    failed=1
  fi
fi

if [[ "$failed" -ne 0 ]]; then
  exit 1
fi

printf '%s\n' 'DEPLOYMENT_CHECK_PASS'
