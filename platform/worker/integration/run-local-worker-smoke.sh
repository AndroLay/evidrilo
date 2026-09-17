#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "$script_dir/../../.." && pwd)"
cd "$repo_root"
# shellcheck source=../../scripts/bootstrap/toolchain-paths.sh
source "$repo_root/scripts/bootstrap/toolchain-paths.sh"
container_name="evidrilo-worker-smoke-$$"
image="${EVIDRILO_POSTGRES_IMAGE:-postgres:16-alpine}"
db_port="${EVIDRILO_WORKER_DB_PORT:-55434}"
log_file="/tmp/evidrilo-worker-smoke-$$.log"
worker_pid=""
container_created=0
worker_cli_home=""

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
    echo "EVIDRILO_WORKER_DB_PORT must be numeric and between 1 and 65535" >&2
    exit 2
fi

if docker container inspect "$container_name" >/dev/null 2>&1; then
    echo "refusing to reuse existing container $container_name" >&2
    exit 2
fi

show_worker_log() {
    if [[ -f "$log_file" ]]; then
        echo "--- worker log ---" >&2
        sed -n '1,240p' "$log_file" >&2 || true
        echo "--- end worker log ---" >&2
    fi
}

cleanup() {
    if [[ -n "$worker_pid" ]]; then
        kill -TERM "$worker_pid" >/dev/null 2>&1 || true
        wait "$worker_pid" >/dev/null 2>&1 || true
    fi
    if [[ "$container_created" -eq 1 ]]; then
        docker rm -f "$container_name" >/dev/null 2>&1 || true
    fi
    if [[ -n "$worker_cli_home" && -d "$worker_cli_home" ]]; then
        rm -rf -- "$worker_cli_home"
    fi
    rm -f "$log_file"
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
    if docker exec "$container_name" pg_isready -U postgres -d evidrilo_it >/dev/null 2>&1; then
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

container_database_url="postgresql://postgres@127.0.0.1:5432/evidrilo_it"
docker exec -e "EVIDRILO_MIGRATION_DATABASE_URL=$container_database_url" \
    "$container_name" sh /tmp/evidrilo-migrations/apply-migrations.sh >/dev/null

docker exec -i "$container_name" psql -v ON_ERROR_STOP=1 -U postgres -d evidrilo_it \
    < "$script_dir/worker-seed.sql"

worker_dll="$repo_root/platform/worker/bin/Release/net10.0/Evidrilo.Worker.dll"
dotnet_bin="$dotnet_root/dotnet"
if [[ ! -x "$dotnet_bin" || ! -f "$worker_dll" ]]; then
    echo 'worker Release artifact and the resolved .NET SDK are required: build the worker first' >&2
    exit 2
fi

worker_cli_home="/tmp/evidrilo-worker-cli-$$"
mkdir -p "$worker_cli_home"
worker_database_url="Host=127.0.0.1;Port=${db_port};Database=evidrilo_it;Username=postgres;Timeout=5;Command Timeout=5"

(
    cd "$repo_root"
    DOTNET_ROOT="$dotnet_root" \
    DOTNET_CLI_HOME="$worker_cli_home" \
    NUGET_PACKAGES="$nuget_packages" \
    DATABASE_URL="$worker_database_url" \
    WORKER_POLL_INTERVAL_SECONDS=1 \
    WORKER_BATCH_SIZE=1 \
    "$dotnet_bin" "$worker_dll"
) >"$log_file" 2>&1 &
worker_pid=$!

completed=0
for attempt in $(seq 1 30); do
    if ! kill -0 "$worker_pid" >/dev/null 2>&1; then
        wait "$worker_pid" >/dev/null 2>&1 || true
        show_worker_log
        echo "worker process exited before completing the seeded job" >&2
        exit 1
    fi
    status="$(docker exec "$container_name" psql -AtX -U postgres -d evidrilo_it -c \
        "select status from public.worker_jobs where job_type = 'analytics_projection' and account_id = '99999999-9999-9999-9999-999999999999';")"
    if [[ "$status" == "succeeded" ]]; then
        completed=1
        break
    fi
    sleep 1
done

if [[ "$completed" -ne 1 ]]; then
    show_worker_log
    echo "worker did not complete the seeded job within 30 seconds" >&2
    exit 1
fi

docker exec -i "$container_name" psql -v ON_ERROR_STOP=1 -U postgres -d evidrilo_it \
    < "$script_dir/worker-assertions.sql"

kill -TERM "$worker_pid" >/dev/null 2>&1 || true
wait "$worker_pid" >/dev/null 2>&1 || true
worker_pid=""

echo "EVIDRILO_WORKER_INTEGRATION_PASS"
