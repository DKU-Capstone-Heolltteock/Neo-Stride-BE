CREATE TABLE IF NOT EXISTS notifications (
	notification_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
	user_id BIGINT NOT NULL,
	notification_type VARCHAR(50) NOT NULL,
	message VARCHAR(500) NOT NULL,
	endpoint VARCHAR(500) NULL,
	is_read BOOLEAN NOT NULL DEFAULT FALSE,
	created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
	KEY idx_notifications_user_created (user_id, created_at DESC, notification_id DESC),
	KEY idx_notifications_user_read (user_id, is_read)
);
