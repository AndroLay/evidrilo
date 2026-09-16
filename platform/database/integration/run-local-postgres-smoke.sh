#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "$script_dir/../../.." && pwd)"
container_name="evidrilo-postgres-smoke-$$"
image="${EVIDRILO_POSTGRES_IMAGE:-postgres:16-alpine}"

command -v docker >/dev/null 2>&1 || {
    echo "docker is required" >&2
    exit 2
}

docker image inspect "$image" >/dev/null 2>&1 || {
    echo "image $image is not available locally; pull it explicitly before running this offline smoke test" >&2
    exit 2
}

cleanup() {
    docker rm -f "$container_name" >/dev/null 2>&1 || true
    rm -f -- "${first_migration_log:-}" "${second_migration_log:-}"
}
trap cleanup EXIT

docker run -d \
    --name "$container_name" \
    -e POSTGRES_DB=evidrilo_it \
    -e POSTGRES_HOST_AUTH_METHOD=trust \
    "$image" >/dev/null

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
    < "$script_dir/auth-shim.sql" >/dev/null

docker cp "$repo_root/platform/database/migrations" \
    "$container_name:/tmp/evidrilo-migrations" >/dev/null

database_url="postgresql://postgres@127.0.0.1:5432/evidrilo_it"
first_migration_log="/tmp/evidrilo-migration-race-first-$$.log"
second_migration_log="/tmp/evidrilo-migration-race-second-$$.log"
docker exec -e "EVIDRILO_MIGRATION_DATABASE_URL=$database_url" \
    "$container_name" sh /tmp/evidrilo-migrations/apply-migrations.sh \
    >"$first_migration_log" 2>&1 &
first_migration_pid=$!
docker exec -e "EVIDRILO_MIGRATION_DATABASE_URL=$database_url" \
    "$container_name" sh /tmp/evidrilo-migrations/apply-migrations.sh \
    >"$second_migration_log" 2>&1 &
second_migration_pid=$!
set +e
wait "$first_migration_pid"
first_migration_status=$?
wait "$second_migration_pid"
second_migration_status=$?
set -e
if [[ "$first_migration_status" -ne 0 || "$second_migration_status" -ne 0 ]]; then
    echo "concurrent migration runners did not both complete" >&2
    tail -40 "$first_migration_log" >&2 || true
    tail -40 "$second_migration_log" >&2 || true
    exit 1
fi
echo "EVIDRILO_MIGRATION_CONCURRENCY_PASS"

docker exec -e "EVIDRILO_MIGRATION_DATABASE_URL=$database_url" \
    "$container_name" sh /tmp/evidrilo-migrations/apply-migrations.sh
docker exec -e "EVIDRILO_MIGRATION_DATABASE_URL=$database_url" \
    "$container_name" sh /tmp/evidrilo-migrations/apply-migrations.sh

docker exec "$container_name" sh -c \
    "cp -R /tmp/evidrilo-migrations /tmp/evidrilo-migrations-tampered && printf '\\n-- tampered checksum fixture\\n' >> /tmp/evidrilo-migrations-tampered/001_platform_sync.sql"
if docker exec \
    -e "EVIDRILO_MIGRATION_DATABASE_URL=$database_url" \
    -e EVIDRILO_MIGRATIONS_DIR=/tmp/evidrilo-migrations-tampered \
    "$container_name" sh /tmp/evidrilo-migrations/apply-migrations.sh >/dev/null 2>&1; then
    echo "migration checksum guard failed" >&2
    exit 1
else
    echo "EVIDRILO_MIGRATION_CHECKSUM_GUARD_PASS"
fi

docker exec -i "$container_name" psql -v ON_ERROR_STOP=1 -U postgres -d evidrilo_it \
    < "$script_dir/rls-smoke.sql"

echo "EVIDRILO_POSTGRES_INTEGRATION_PASS"
