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
cli_home="/tmp/evidrilo-api-worker-e2e-cli-$$"
container_created=0

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

worker_dll="$repo_root/platform/worker/bin/Release/net10.0/Evidrilo.Worker.dll"
if [[ ! -f "$worker_dll" ]]; then
    echo "worker Release artifact is required; build platform/worker first" >&2
    exit 2
fi

cleanup() {
    if [[ "$container_created" -eq 1 ]]; then
        docker rm -f "$container_name" >/dev/null 2>&1 || true
    fi
    if [[ -d "$cli_home" ]]; then
        rm -rf -- "$cli_home"
    fi
}
trap cleanup EXIT

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

docker exec -i "$container_name" psql -v ON_ERROR_STOP=1 -U postgres -d evidrilo_it \
    < "$repo_root/platform/database/integration/auth-shim.sql" >/dev/null
docker cp "$repo_root/platform/database/migrations" \
    "$container_name:/tmp/evidrilo-migrations" >/dev/null
docker exec -e "EVIDRILO_MIGRATION_DATABASE_URL=postgresql://postgres@127.0.0.1:5432/evidrilo_it" \
    "$container_name" sh /tmp/evidrilo-migrations/apply-migrations.sh >/dev/null

mkdir -p "$cli_home"
export DOTNET_ROOT="$dotnet_root"
export PATH="$DOTNET_ROOT:$PATH"
export DOTNET_CLI_HOME="$cli_home"
export NUGET_PACKAGES="$nuget_packages"

"$dotnet_root/dotnet" restore \
    "$repo_root/platform/integration/Evidrilo.LocalE2e.csproj" \
    --ignore-failed-sources \
    --nologo \
    --verbosity quiet

"$dotnet_root/dotnet" build \
    "$repo_root/platform/integration/Evidrilo.LocalE2e.csproj" \
    --configuration Release \
    --no-restore \
    --nologo \
    --verbosity quiet

database_url="Host=127.0.0.1;Port=${db_port};Database=evidrilo_it;Username=postgres;Timeout=5;Command Timeout=5"
EVIDRILO_E2E_DATABASE_URL="$database_url" \
EVIDRILO_DOTNET_ROOT="$dotnet_root" \
EVIDRILO_REPO_ROOT="$repo_root" \
    "$dotnet_root/dotnet" \
    "$repo_root/platform/integration/bin/Release/net10.0/Evidrilo.LocalE2e.dll"
