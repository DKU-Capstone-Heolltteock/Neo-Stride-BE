# MySQL Schema Baseline

`latest.sql` is a schema-only baseline generated from the operational MySQL schema after migration `016_community_content_stats_insert_trigger`.

It intentionally excludes application data and volatile auto-increment values. Trigger definers are omitted so the importing MySQL user becomes the definer in the target environment.

Fresh database bootstrap:

```bash
mysql -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USERNAME" -p"$DB_PASSWORD" "$DB_NAME" < deploy/mysql/schema/latest.sql
DB_USERNAME=neostride_app DB_PASSWORD=change-me deploy/mysql/apply-migrations.sh --baseline
DB_USERNAME=neostride_app DB_PASSWORD=change-me deploy/mysql/apply-migrations.sh --verify
```

Refresh command used for this snapshot:

```bash
docker exec neostride-mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysqldump --no-data --routines --triggers --events --skip-comments --set-gtid-purged=OFF -uroot "$MYSQL_DATABASE"' > deploy/mysql/schema/latest.sql
```

After refreshing, remove `AUTO_INCREMENT=<number>` table options and trigger `DEFINER` clauses before committing, then restore into a temporary database and run `apply-migrations.sh --baseline` followed by `--verify`.
