SELECT '024_missing_crew_tables' AS check_name, COUNT(*) AS failures
FROM (
    SELECT 'crews' AS table_name UNION ALL
    SELECT 'crew_members' UNION ALL
    SELECT 'crew_events' UNION ALL
    SELECT 'crew_event_participants' UNION ALL
    SELECT 'instant_crews' UNION ALL
    SELECT 'instant_crew_participants' UNION ALL
    SELECT 'crew_chat_messages'
) expected
WHERE NOT EXISTS (
    SELECT 1
    FROM information_schema.tables t
    WHERE t.table_schema = DATABASE()
      AND t.table_name = expected.table_name
)
HAVING failures > 0;

SELECT '024_missing_crew_indexes' AS check_name, COUNT(*) AS failures
FROM (
    SELECT 'crews' AS table_name, 'uq_crews_name' AS index_name UNION ALL
    SELECT 'crew_members', 'uq_crew_members_crew_user' UNION ALL
    SELECT 'crew_events', 'idx_crew_events_crew_time' UNION ALL
    SELECT 'crew_event_participants', 'uq_crew_event_participants_event_user' UNION ALL
    SELECT 'instant_crews', 'idx_instant_crews_status_region_time' UNION ALL
    SELECT 'instant_crew_participants', 'uq_instant_crew_participants_crew_user' UNION ALL
    SELECT 'crew_chat_messages', 'idx_crew_chat_messages_crew_cursor' UNION ALL
    SELECT 'crew_chat_messages', 'idx_crew_chat_messages_instant_cursor'
) expected
WHERE NOT EXISTS (
    SELECT 1
    FROM information_schema.statistics s
    WHERE s.table_schema = DATABASE()
      AND s.table_name = expected.table_name
      AND s.index_name = expected.index_name
)
HAVING failures > 0;
