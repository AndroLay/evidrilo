#!/bin/sh
set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
migrations_dir=${EVIDRILO_MIGRATIONS_DIR:-$script_dir}
database_url=${EVIDRILO_MIGRATION_DATABASE_URL:-}
psql_bin=${EVIDRILO_PSQL_BIN:-psql}

if [ -z "$database_url" ]; then
    echo "EVIDRILO_MIGRATION_DATABASE_URL is required" >&2
    exit 2
fi

command -v "$psql_bin" >/dev/null 2>&1 || {
    echo "psql is required" >&2
    exit 2
}

if command -v sha256sum >/dev/null 2>&1; then
    checksum_for() {
        sha256sum "$1" | awk '{print $1}'
    }
elif command -v shasum >/dev/null 2>&1; then
    checksum_for() {
        shasum -a 256 "$1" | awk '{print $1}'
    }
else
    echo "sha256sum or shasum is required" >&2
    exit 2
fi

psql_base() {
    "$psql_bin" --no-psqlrc -X -v ON_ERROR_STOP=1 "$database_url" "$@"
}

psql_base --single-transaction -c '
select pg_advisory_xact_lock(814235);
create table if not exists public.evidrilo_schema_migrations (
    version text primary key,
    checksum text not null check (checksum ~ '"'"'^[a-f0-9]{64}$'"'"'),
    applied_at timestamptz not null default now()
);
revoke all on public.evidrilo_schema_migrations from public;
'

found=0
for migration in "$migrations_dir"/[0-9][0-9][0-9]_*.sql; do
    [ -f "$migration" ] || continue
    found=1
    file_name=$(basename "$migration")
    version=${file_name%.sql}
    case "$version" in
        [0-9][0-9][0-9]_[A-Za-z0-9_]*) ;;
        *)
            echo "invalid migration filename: $file_name" >&2
            exit 2
            ;;
    esac

    checksum=$(checksum_for "$migration")
    {
        printf '%s\n' 'select pg_advisory_xact_lock(814235);'
        printf '%s\n' "select exists (select 1 from public.evidrilo_schema_migrations where version = :'migration_version') as already_applied, coalesce((select checksum from public.evidrilo_schema_migrations where version = :'migration_version') = :'migration_checksum', true) as checksum_ok;"
        printf '%s\n' '\gset migration_'
        printf '%s\n' '\if :migration_checksum_ok'
        printf '%s\n' '\else'
        printf '\\echo migration checksum mismatch: %s\n' "$version"
        printf '%s\n' "do \$\$ begin raise exception 'migration checksum mismatch'; end \$\$;"
        printf '%s\n' '\endif'
        printf '%s\n' '\if :migration_already_applied'
        printf '\\echo already applied: %s\n' "$version"
        printf '%s\n' '\else'
        cat "$migration"
        printf '%s\n' "insert into public.evidrilo_schema_migrations (version, checksum) values (:'migration_version', :'migration_checksum');"
        printf '\\echo applied: %s\n' "$version"
        printf '%s\n' '\endif'
    } | "$psql_bin" --no-psqlrc -X -v ON_ERROR_STOP=1 \
        -v "migration_version=$version" \
        -v "migration_checksum=$checksum" \
        --single-transaction "$database_url"
done

if [ "$found" -eq 0 ]; then
    echo "no numbered migrations found in $migrations_dir" >&2
    exit 2
fi

echo "EVIDRILO_MIGRATION_LEDGER_PASS"
