#!/usr/bin/env sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
MIGRATIONS_DIR=${MIGRATIONS_DIR:-"$SCRIPT_DIR/migrations"}
DB_HOST=${DB_HOST:-127.0.0.1}
DB_PORT=${DB_PORT:-3306}
DB_NAME=${DB_NAME:-neostride}
MODE=apply

usage() {
	cat <<USAGE
Usage: $0 [--dry-run|--status|--baseline|--verify]

Environment:
  DB_HOST                         MySQL host, default 127.0.0.1
  DB_PORT                         MySQL port, default 3306
  DB_NAME                         MySQL database, default neostride
  DB_USERNAME                     MySQL user, required unless USE_CONTAINER_MYSQL_ENV=true
  DB_PASSWORD                     MySQL password, optional when MYSQL_PWD is set
  MYSQL_CONTAINER                 Optional Docker container name to run mysql inside
  USE_CONTAINER_MYSQL_ENV=true    With MYSQL_CONTAINER, use MYSQL_ROOT_PASSWORD and MYSQL_DATABASE from the container
  MIGRATIONS_DIR                  Migration directory, default deploy/mysql/migrations

Modes:
  --dry-run    Show unapplied migrations without executing SQL.
  --status     Show all up migrations with applied/pending status.
  --baseline   Mark current up migrations as applied without executing them.
  --verify     Run matching *.verify.sql checks for applied migrations.

Examples:
  DB_USERNAME=neostride_app DB_PASSWORD=... deploy/mysql/apply-migrations.sh
  MYSQL_CONTAINER=neostride-mysql USE_CONTAINER_MYSQL_ENV=true deploy/mysql/apply-migrations.sh --status
  MYSQL_CONTAINER=neostride-mysql USE_CONTAINER_MYSQL_ENV=true deploy/mysql/apply-migrations.sh --verify
USAGE
}

case "${1:-}" in
	"") MODE=apply ;;
	--dry-run) MODE=dry-run ;;
	--status) MODE=status ;;
	--baseline) MODE=baseline ;;
	--verify) MODE=verify ;;
	-h|--help) usage; exit 0 ;;
	*) usage >&2; exit 2 ;;
esac

if [ ! -d "$MIGRATIONS_DIR" ]; then
	echo "Migration directory not found: $MIGRATIONS_DIR" >&2
	exit 1
fi

if [ -z "${MYSQL_CONTAINER:-}" ] || [ "${USE_CONTAINER_MYSQL_ENV:-false}" != "true" ]; then
	if [ -z "${DB_USERNAME:-}" ]; then
		echo "DB_USERNAME is required unless USE_CONTAINER_MYSQL_ENV=true is used with MYSQL_CONTAINER." >&2
		exit 1
	fi
fi

run_mysql() {
	if [ -n "${MYSQL_CONTAINER:-}" ]; then
		if [ "${USE_CONTAINER_MYSQL_ENV:-false}" = "true" ]; then
			docker exec -i "$MYSQL_CONTAINER" sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql --batch --raw --skip-column-names -uroot "$MYSQL_DATABASE"'
		else
			docker exec -i -e MYSQL_PWD="${DB_PASSWORD:-${MYSQL_PWD:-}}" "$MYSQL_CONTAINER" \
				mysql --batch --raw --skip-column-names -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USERNAME" "$DB_NAME"
		fi
	else
		MYSQL_PWD="${DB_PASSWORD:-${MYSQL_PWD:-}}" mysql --batch --raw --skip-column-names \
			-h "$DB_HOST" -P "$DB_PORT" -u "$DB_USERNAME" "$DB_NAME"
	fi
}

sql_escape() {
	printf "%s" "$1" | sed "s/'/''/g"
}

checksum_file() {
	if command -v sha256sum >/dev/null 2>&1; then
		sha256sum "$1" | awk '{print $1}'
	else
		shasum -a 256 "$1" | awk '{print $1}'
	fi
}

version_from_file() {
	base=$(basename "$1")
	printf "%s" "${base%%_*}"
}

description_from_file() {
	base=$(basename "$1")
	name=${base%.up.sql}
	printf "%s" "${name#*_}"
}

ensure_migrations_table() {
	cat <<SQL | run_mysql
CREATE TABLE IF NOT EXISTS schema_migrations (
  version VARCHAR(32) NOT NULL PRIMARY KEY,
  description VARCHAR(255) NOT NULL,
  filename VARCHAR(255) NOT NULL,
  checksum_sha256 CHAR(64) NOT NULL,
  applied_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
SQL
}

applied_checksum() {
	version=$(sql_escape "$1")
	printf "SELECT checksum_sha256 FROM schema_migrations WHERE version='%s';\n" "$version" | run_mysql
}

insert_migration_row() {
	version=$(sql_escape "$1")
	description=$(sql_escape "$2")
	filename=$(sql_escape "$3")
	checksum=$(sql_escape "$4")
	printf "INSERT INTO schema_migrations (version, description, filename, checksum_sha256) VALUES ('%s', '%s', '%s', '%s');\n" \
		"$version" "$description" "$filename" "$checksum" | run_mysql
}

print_status_row() {
	printf "%s\t%s\t%s\n" "$1" "$2" "$3"
}

ensure_migrations_table

found=0
pending=0
verify_found=0
verify_failed=0
print_status_row "version" "status" "file"
for file in "$MIGRATIONS_DIR"/*.up.sql; do
	[ -e "$file" ] || continue
	found=1
	version=$(version_from_file "$file")
	description=$(description_from_file "$file")
	filename=$(basename "$file")
	checksum=$(checksum_file "$file")
	existing=$(applied_checksum "$version")

	if [ -n "$existing" ]; then
		if [ "$existing" != "$checksum" ]; then
			echo "Checksum mismatch for migration $version ($filename)." >&2
			echo "  database: $existing" >&2
			echo "  file:     $checksum" >&2
			exit 1
		fi
		if [ "$MODE" = "verify" ]; then
			verify_file=${file%.up.sql}.verify.sql
			if [ -f "$verify_file" ]; then
				verify_found=$((verify_found + 1))
				verify_output=$(run_mysql < "$verify_file")
				if [ -n "$verify_output" ]; then
					print_status_row "$version" "verify-failed" "$(basename "$verify_file")"
					printf "%s\n" "$verify_output" >&2
					verify_failed=1
				else
					print_status_row "$version" "verified" "$(basename "$verify_file")"
				fi
			else
				print_status_row "$version" "no-verify" "$filename"
			fi
		else
			print_status_row "$version" "applied" "$filename"
		fi
		continue
	fi

	pending=$((pending + 1))
	case "$MODE" in
		status)
			print_status_row "$version" "pending" "$filename"
			;;
		dry-run)
			print_status_row "$version" "would-apply" "$filename"
			;;
		baseline)
			insert_migration_row "$version" "$description" "$filename" "$checksum"
			print_status_row "$version" "baselined" "$filename"
			;;
		verify)
			print_status_row "$version" "pending" "$filename"
			verify_failed=1
			;;
		apply)
			print_status_row "$version" "applying" "$filename"
			run_mysql < "$file"
			insert_migration_row "$version" "$description" "$filename" "$checksum"
			print_status_row "$version" "applied-now" "$filename"
			;;
	esac
done

if [ "$found" -eq 0 ]; then
	echo "No .up.sql migration files found in $MIGRATIONS_DIR" >&2
	exit 1
fi

if [ "$MODE" = "apply" ] && [ "$pending" -eq 0 ]; then
	echo "No pending migrations."
fi

if [ "$MODE" = "verify" ]; then
	if [ "$verify_found" -eq 0 ]; then
		echo "No .verify.sql files found for applied migrations in $MIGRATIONS_DIR" >&2
		exit 1
	fi
	if [ "$verify_failed" -ne 0 ]; then
		exit 1
	fi
fi
