SET @idx_notifications_user_created_exists := (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'notifications'
      AND index_name = 'idx_notifications_user_created'
);
SET @sql := IF(
    @idx_notifications_user_created_exists = 0,
    'CREATE INDEX idx_notifications_user_created ON notifications (user_id, created_at DESC, notification_id DESC)',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_notifications_user_read_exists := (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'notifications'
      AND index_name = 'idx_notifications_user_read'
);
SET @sql := IF(
    @idx_notifications_user_read_exists = 0,
    'CREATE INDEX idx_notifications_user_read ON notifications (user_id, is_read)',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
