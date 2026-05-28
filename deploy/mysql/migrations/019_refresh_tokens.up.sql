CREATE TABLE IF NOT EXISTS refresh_tokens (
	refresh_token_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
	user_id BIGINT NOT NULL,
	token_id_hash CHAR(64) NOT NULL,
	expires_at DATETIME NOT NULL,
	revoked_at DATETIME NULL,
	created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
	UNIQUE KEY uq_refresh_tokens_token_id_hash (token_id_hash),
	KEY idx_refresh_tokens_user_active (user_id, revoked_at, expires_at),
	CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id)
		REFERENCES users(user_id)
		ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
