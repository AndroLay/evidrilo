#!/usr/bin/env bash
set -euo pipefail

# This smoke test is deliberately self-contained and local-only. It creates an
# isolated disposable PostgreSQL container, so it cannot prove managed backup,
# restore, permissions, retention, or disaster-recovery behavior.
script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "$script_dir/../../.." && pwd)"
container_name="evidrilo-postgres-backup-restore-$$"
image="${EVIDRILO_POSTGRES_IMAGE:-postgres:16-alpine}"
database_name="evidrilo_it"
restore_database="evidrilo_restore"
backup_file="/tmp/evidrilo-backup-restore-$$.dump"
sentinel_organization="aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaab"

command -v docker >/dev/null 2>&1 || {
    echo "docker is required" >&2
    exit 2
}

docker image inspect "$image" >/dev/null 2>&1 || {
    echo "image $image is not available locally; pull it explicitly before running this local smoke test" >&2
    exit 2
}

cleanup() {
    docker rm -f "$container_name" >/dev/null 2>&1 || true
}
trap cleanup EXIT

docker run -d \
    --name "$container_name" \
    -e POSTGRES_DB="$database_name" \
    -e POSTGRES_HOST_AUTH_METHOD=trust \
    "$image" >/dev/null

for attempt in $(seq 1 30); do
    if docker exec "$container_name" pg_isready -U postgres -d "$database_name" >/dev/null 2>&1; then
        break
    fi
    if [[ "$attempt" == 30 ]]; then
        docker logs "$container_name" >&2 || true
        exit 1
    fi
    sleep 1
done

docker exec -i "$container_name" psql -v ON_ERROR_STOP=1 -U postgres -d "$database_name" \
    < "$script_dir/auth-shim.sql" >/dev/null

docker cp "$repo_root/platform/database/migrations" \
    "$container_name:/tmp/evidrilo-migrations" >/dev/null

database_url="postgresql://postgres@127.0.0.1:5432/$database_name"
docker exec -e "EVIDRILO_MIGRATION_DATABASE_URL=$database_url" \
    "$container_name" sh /tmp/evidrilo-migrations/apply-migrations.sh >/dev/null

docker exec -i "$container_name" psql -v ON_ERROR_STOP=1 -U postgres -d "$database_name" <<SQL >/dev/null
insert into auth.users (id, email_confirmed_at)
values ('11111111-1111-1111-1111-111111111111', now())
on conflict (id) do nothing;
insert into public.organizations (organization_id, name)
values ('$sentinel_organization', 'Local backup restore sentinel')
on conflict (organization_id) do update set name = excluded.name;
SQL

docker exec "$container_name" pg_dump \
    --format=custom \
    --no-owner \
    --no-acl \
    --file="$backup_file" \
    -U postgres \
    -d "$database_name" >/dev/null

docker exec "$container_name" createdb -U postgres "$restore_database"
docker exec "$container_name" pg_restore \
    --exit-on-error \
    --no-owner \
    --no-acl \
    --dbname="$restore_database" \
    -U postgres \
    "$backup_file" >/dev/null

restored_name=$(docker exec "$container_name" psql -Atq -U postgres -d "$restore_database" \
    -c "select name from public.organizations where organization_id = '$sentinel_organization';")
expected_migrations=$(find "$repo_root/platform/database/migrations" -maxdepth 1 -type f \
    -name '[0-9][0-9][0-9]_*.sql' | wc -l | tr -d ' ')
restored_migrations=$(docker exec "$container_name" psql -Atq -U postgres -d "$restore_database" \
    -c 'select count(*)::text from public.evidrilo_schema_migrations;')

if [[ "$restored_name" != "Local backup restore sentinel" ]]; then
    echo "backup restore sentinel was not recovered" >&2
    exit 1
fi
if [[ "$restored_migrations" != "$expected_migrations" ]]; then
    echo "backup restore migration ledger is incomplete" >&2
    exit 1
fi

echo "EVIDRILO_LOCAL_BACKUP_RESTORE_PASS"
