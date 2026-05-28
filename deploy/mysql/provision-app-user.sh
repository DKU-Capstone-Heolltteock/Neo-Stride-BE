#!/usr/bin/env sh
set -eu

DB_HOST=${DB_HOST:-127.0.0.1}
DB_PORT=${DB_PORT:-3306}
DB_NAME=${DB_NAME:-neostride}
APP_DB_USERNAME=${APP_DB_USERNAME:-neostride_app}
APP_DB_HOST_PATTERN=${APP_DB_HOST_PATTERN:-%}

usage() {
	cat <<USAGE
Usage: APP_DB_PASSWORD=... $0

Creates or updates a least-privilege MySQL user for the backend runtime.
The script does not print passwords.

Environment:
  DB_HOST                         MySQL host, default 127.0.0.1
  DB_PORT                         MySQL port, default 3306
  DB_NAME                         MySQL database, default neostride
  ROOT_DB_USERNAME                MySQL admin user, default root
  ROOT_DB_PASSWORD                MySQL admin password, optional when MYSQL_PWD is set
  MYSQL_CONTAINER                 Optional Docker container name to run mysql inside
  USE_CONTAINER_MYSQL_ENV=true    With MYSQL_CONTAINER, use MYSQL_ROOT_PASSWORD from the container
  APP_DB_USERNAME                 Runtime app user, default neostride_app
  APP_DB_PASSWORD                 Runtime app password, required
  APP_DB_HOST_PATTERN             MySQL host pattern for app user, default %

Examples:
  MYSQL_CONTAINER=neostride-mysql USE_CONTAINER_MYSQL_ENV=true APP_DB_PASSWORD=... $0
  ROOT_DB_USERNAME=root ROOT_DB_PASSWORD=... APP_DB_PASSWORD=... $0
USAGE
}

case "${1:-}" in
	"") ;;
	-h|--help) usage; exit 0 ;;
	*) usage >&2; exit 2 ;;
esac

require_identifier() {
	value=$1
	name=$2
	case "$value" in
		*[!A-Za-z0-9_]*|"")
			echo "$name may contain only letters, numbers, and underscores." >&2
			exit 1
			;;
	esac
}

sql_string() {
	printf "%s" "$1" | sed "s/'/''/g"
}

require_identifier "$DB_NAME" "DB_NAME"
require_identifier "$APP_DB_USERNAME" "APP_DB_USERNAME"

if [ -z "${APP_DB_PASSWORD:-}" ]; then
	echo "APP_DB_PASSWORD is required." >&2
	exit 1
fi

run_mysql() {
	if [ -n "${MYSQL_CONTAINER:-}" ]; then
		if [ "${USE_CONTAINER_MYSQL_ENV:-false}" = "true" ]; then
			docker exec -i "$MYSQL_CONTAINER" sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql --batch --raw --skip-column-names -uroot'
		else
			docker exec -i -e MYSQL_PWD="${ROOT_DB_PASSWORD:-${MYSQL_PWD:-}}" "$MYSQL_CONTAINER" \
				mysql --batch --raw --skip-column-names -h "$DB_HOST" -P "$DB_PORT" -u "${ROOT_DB_USERNAME:-root}"
		fi
	else
		MYSQL_PWD="${ROOT_DB_PASSWORD:-${MYSQL_PWD:-}}" mysql --batch --raw --skip-column-names \
			-h "$DB_HOST" -P "$DB_PORT" -u "${ROOT_DB_USERNAME:-root}"
	fi
}

db_name=$(sql_string "$DB_NAME")
app_user=$(sql_string "$APP_DB_USERNAME")
app_host=$(sql_string "$APP_DB_HOST_PATTERN")
app_password=$(sql_string "$APP_DB_PASSWORD")

cat <<SQL | run_mysql
CREATE DATABASE IF NOT EXISTS \`$db_name\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS '$app_user'@'$app_host' IDENTIFIED BY '$app_password';
ALTER USER '$app_user'@'$app_host' IDENTIFIED BY '$app_password';
GRANT SELECT, INSERT, UPDATE, DELETE ON \`$db_name\`.* TO '$app_user'@'$app_host';
FLUSH PRIVILEGES;
SQL

echo "Provisioned MySQL runtime user '$APP_DB_USERNAME' for database '$DB_NAME'."
