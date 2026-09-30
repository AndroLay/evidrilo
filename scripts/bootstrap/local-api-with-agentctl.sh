#!/usr/bin/env bash
set -Eeuo pipefail

script_directory=$(CDPATH= cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
repository_root=$(CDPATH= cd -- "$script_directory/../.." && pwd)
source "$script_directory/agentctl-supabase-env.sh"

evidrilo_agentctl_require
if ! command -v docker >/dev/null 2>&1; then
  printf '%s\n' 'docker is required for the local API bootstrap.' >&2
  exit 1
fi
if ! command -v curl >/dev/null 2>&1; then
  printf '%s\n' 'curl is required for the local API readiness check.' >&2
  exit 1
fi

project_ref=${EVIDRILO_SUPABASE_PROJECT_REF:-}
if [[ -z "$project_ref" ]]; then
  project_ref=$(evidrilo_agentctl_supabase_project_ref)
fi
supabase_url=$(evidrilo_supabase_url "$project_ref")
publishable_key=$(evidrilo_agentctl_supabase_publishable_key "$project_ref")
local_api_port_file="$repository_root/.local/api-port"
api_port=${EVIDRILO_API_PORT:-}
if [[ -z "$api_port" && -f "$local_api_port_file" ]]; then
  IFS= read -r api_port < "$local_api_port_file" || true
fi
api_port=${api_port:-5080}
if [[ ! "$api_port" =~ ^[0-9]+$ || "$api_port" -lt 1 || "$api_port" -gt 65535 ]]; then
  printf '%s\n' 'EVIDRILO_API_PORT must be between 1 and 65535.' >&2
  exit 1
fi

compose_file="$repository_root/infra/environments/local/docker-compose.yml"
compose_files=(-f "$compose_file")
local_ai_compose_file="$repository_root/.local/docker-compose.ai.yml"
if [[ -f "$local_ai_compose_file" ]]; then
  compose_files+=(-f "$local_ai_compose_file")
fi
compose_build=()
if [[ ${EVIDRILO_LOCAL_SKIP_BUILD:-0} != 1 ]]; then
  compose_build+=(--build)
fi

EVIDRILO_API_PORT="$api_port" \
SUPABASE_URL="$supabase_url" \
SUPABASE_PUBLISHABLE_KEY="$publishable_key" \
docker compose "${compose_files[@]}" up "${compose_build[@]}" -d

readiness_json=''
api_ready=0
for attempt in $(seq 1 30); do
  if readiness_json=$(curl --silent --fail --max-time 3 "http://127.0.0.1:${api_port}/health/ready" 2>/dev/null) \
    && printf '%s' "$readiness_json" | node -e '
let input = "";
process.stdin.on("data", chunk => { input += chunk; });
process.stdin.on("end", () => {
  try {
    const result = JSON.parse(input);
    if (result.status !== "ready") process.exitCode = 1;
  } catch {
    process.exitCode = 1;
  }
});
'; then
    api_ready=1
    break
  fi
  sleep 1
done

if [[ "$api_ready" != 1 ]]; then
  printf '%s\n' 'LOCAL_API_READINESS_FAILED' >&2
  exit 1
fi

printf 'LOCAL_API_READY port=%s project=%s\n' "$api_port" "$project_ref"
