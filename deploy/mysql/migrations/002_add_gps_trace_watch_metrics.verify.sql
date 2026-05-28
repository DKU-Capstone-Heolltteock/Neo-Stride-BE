-- Verification queries should return no rows. Any row indicates a failed check.
SELECT '002_missing_gps_trace_watch_metrics' AS check_name, COUNT(*) AS failures
FROM (
    SELECT 'heart_rate' AS column_name UNION ALL
    SELECT 'cadence'
) expected
WHERE NOT EXISTS (
    SELECT 1
    FROM information_schema.columns c
    WHERE c.table_schema = DATABASE()
      AND c.table_name = 'gps_traces'
      AND c.column_name = expected.column_name
)
HAVING failures > 0;
