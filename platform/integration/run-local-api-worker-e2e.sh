#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "$script_dir/../.." && pwd)"
cd "$repo_root"
# shellcheck source=../../scripts/bootstrap/toolchain-paths.sh
source "$repo_root/scripts/bootstrap/toolchain-paths.sh"
container_name="evidrilo-api-worker-e2e-$$"
image="${EVIDRILO_POSTGRES_IMAGE:-postgres:16-alpine}"
db_port="${EVIDRILO_API_WORKER_E2E_DB_PORT:-55435}"
temporary_directory="${TMPDIR:-/tmp}"
artifacts_parent="${EVIDRILO_API_WORKER_E2E_ARTIFACTS_PARENT:-$repo_root/platform}"
container_created=0
artifacts_dir=""
cli_home=""

cleanup() {
    if [[ "$container_created" -eq 1 ]]; then
        docker rm -f "$container_name" >/dev/null 2>&1 || true
    fi
    if [[ -n "$artifacts_dir" && -d "$artifacts_dir" ]]; then
        rm -rf -- "$artifacts_dir"
    fi
    if [[ -n "$cli_home" && -d "$cli_home" ]]; then
        rm -rf -- "$cli_home"
    fi
}
trap cleanup EXIT

if ! dotnet_root=$(evidrilo_dotnet_root "$repo_root"); then
    echo 'an external .NET SDK is required; set EVIDRILO_DOTNET_ROOT or install dotnet' >&2
    exit 2
fi
nuget_packages=$(evidrilo_nuget_packages)

command -v docker >/dev/null 2>&1 || {
    echo "docker is required" >&2
    exit 2
}
docker image inspect "$image" >/dev/null 2>&1 || {
    echo "image $image is not available locally; pull it explicitly before running this offline smoke test" >&2
    exit 2
}

if ! [[ "$db_port" =~ ^[0-9]+$ ]] || (( 10#$db_port < 1 || 10#$db_port > 65535 )); then
    echo "EVIDRILO_API_WORKER_E2E_DB_PORT must be numeric and between 1 and 65535" >&2
    exit 2
fi

if [[ ! -x "$dotnet_root/dotnet" ]]; then
    echo 'the resolved .NET SDK is not executable' >&2
    exit 2
fi

mkdir -p "$artifacts_parent"
artifacts_dir=$(mktemp -d "${artifacts_parent%/}/.evidrilo-api-worker-e2e-artifacts.XXXXXX")
cli_home=$(mktemp -d "${temporary_directory%/}/evidrilo-api-worker-e2e-cli.XXXXXX")

docker run -d \
    --name "$container_name" \
    -p "127.0.0.1:${db_port}:5432" \
    -e POSTGRES_DB=evidrilo_it \
    -e POSTGRES_HOST_AUTH_METHOD=trust \
    "$image" >/dev/null
container_created=1

for attempt in $(seq 1 30); do
    if docker exec "$container_name" pg_isready -U postgres -d postgres >/dev/null 2>&1 \
        && docker exec "$container_name" psql -v ON_ERROR_STOP=1 -U postgres -d evidrilo_it -c 'select 1' >/dev/null 2>&1; then
        break
    fi
    if [[ "$attempt" == 30 ]]; then
        docker logs "$container_name" >&2 || true
        exit 1
    fi
    sleep 1
done

if ! docker exec -i "$container_name" psql -v ON_ERROR_STOP=1 -U postgres -d evidrilo_it \
    < "$repo_root/platform/database/integration/auth-shim.sql" >/dev/null 2>"$artifacts_dir/auth-shim.log"; then
    tail -20 "$artifacts_dir/auth-shim.log" >&2
    exit 1
fi
docker cp "$repo_root/platform/database/migrations" \
    "$container_name:/tmp/evidrilo-migrations" >/dev/null
if ! docker exec -e "EVIDRILO_MIGRATION_DATABASE_URL=postgresql://postgres@127.0.0.1:5432/evidrilo_it" \
    "$container_name" sh /tmp/evidrilo-migrations/apply-migrations.sh >/dev/null 2>"$artifacts_dir/migrations.log"; then
    tail -30 "$artifacts_dir/migrations.log" >&2
    exit 1
fi

# Credentials are disposable on a loopback-only, trust-authenticated container.
# Keep the owner connection solely for synthetic fixture seeding/assertions.
if ! docker exec -i "$container_name" psql -v ON_ERROR_STOP=1 -U postgres -d evidrilo_it \
    < "$repo_root/platform/database/roles/provision-runtime-roles.sql" >"$artifacts_dir/runtime-roles.log" 2>&1; then
    tail -20 "$artifacts_dir/runtime-roles.log" >&2
    exit 1
fi
docker exec "$container_name" psql -v ON_ERROR_STOP=1 -U postgres -d evidrilo_it \
    -c 'alter role evidrilo_api login; alter role evidrilo_worker login;' >/dev/null

mkdir -p "$cli_home"
export DOTNET_ROOT="$dotnet_root"
export PATH="$DOTNET_ROOT:$PATH"
export DOTNET_CLI_HOME="$cli_home"
export NUGET_PACKAGES="$nuget_packages"
export DOTNET_NOLOGO=1
export DOTNET_CLI_TELEMETRY_OPTOUT=1

"$dotnet_root/dotnet" restore \
    "$repo_root/platform/integration/Evidrilo.LocalE2e.csproj" \
    --artifacts-path "$artifacts_dir" \
    --packages "$nuget_packages" \
    --ignore-failed-sources \
    --nologo \
    --verbosity quiet

"$dotnet_root/dotnet" build \
    "$repo_root/platform/integration/Evidrilo.LocalE2e.csproj" \
    --artifacts-path "$artifacts_dir" \
    --configuration Release \
    --no-restore \
    -p:UseSharedCompilation=false \
    --nologo \
    --verbosity quiet

database_url="Host=127.0.0.1;Port=${db_port};Database=evidrilo_it;Username=postgres;Timeout=5;Command Timeout=5"
worker_dll="$artifacts_dir/bin/Evidrilo.Worker/release/Evidrilo.Worker.dll"
integration_dll="$artifacts_dir/bin/Evidrilo.LocalE2e/release/Evidrilo.LocalE2e.dll"
if [[ ! -f "$worker_dll" || ! -f "$integration_dll" ]]; then
    echo 'isolated API/worker E2E artifacts were not produced' >&2
    exit 1
fi

if ! EVIDRILO_E2E_DATABASE_URL="$database_url" \
    EVIDRILO_E2E_API_DATABASE_URL="${database_url/Username=postgres/Username=evidrilo_api}" \
    EVIDRILO_E2E_WORKER_DATABASE_URL="${database_url/Username=postgres/Username=evidrilo_worker}" \
    EVIDRILO_DOTNET_ROOT="$dotnet_root" \
    EVIDRILO_REPO_ROOT="$repo_root" \
    EVIDRILO_WORKER_ASSEMBLY="$worker_dll" \
    "$dotnet_root/dotnet" "$integration_dll" >"$artifacts_dir/e2e.log" 2>&1; then
    echo '--- worker diagnostics ---' >&2
    rg '^E2E_WORKER ' "$artifacts_dir/e2e.log" | tail -40 >&2 || true
    tail -80 "$artifacts_dir/e2e.log" >&2
    exit 1
fi

grep -E '^EVIDRILO_.*_E2E_PASS$' "$artifacts_dir/e2e.log"
