# MySQL Schema Baseline

`latest.sql` is a schema-only baseline generated from the operational MySQL schema after migration `027_running_record_badge`. It includes refresh token persistence, normalized community content columns, privacy-safe fulltext search indexes, feed ordering support, crew core tables, running-record badge snapshots for badge detail selection, and migration verification coverage through 027.

It intentionally excludes application data and volatile auto-increment values. Trigger definers are omitted so the importing MySQL user becomes the definer in the target environment.

Fresh database bootstrap:

```bash
mysql -h "$DB_HOST" -P "$DB_PORT" -u "$MIGRATION_DB_USERNAME" -p"$MIGRATION_DB_PASSWORD" "$DB_NAME" < deploy/mysql/schema/latest.sql
DB_USERNAME="$MIGRATION_DB_USERNAME" DB_PASSWORD="$MIGRATION_DB_PASSWORD" deploy/mysql/apply-migrations.sh --baseline
DB_USERNAME="$MIGRATION_DB_USERNAME" DB_PASSWORD="$MIGRATION_DB_PASSWORD" deploy/mysql/apply-migrations.sh --verify
```

Refresh command used for this snapshot:

```bash
docker exec neostride-mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysqldump --no-data --routines --triggers --events --skip-comments --set-gtid-purged=OFF -uroot "$MYSQL_DATABASE"' > deploy/mysql/schema/latest.sql
```

After refreshing, remove `AUTO_INCREMENT=<number>` table options and trigger `DEFINER` clauses before committing, then restore into a temporary database and run `apply-migrations.sh --baseline` followed by `--verify`.
