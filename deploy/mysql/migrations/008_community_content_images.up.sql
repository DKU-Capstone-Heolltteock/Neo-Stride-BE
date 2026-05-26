CREATE TABLE IF NOT EXISTS community_content_images (
	image_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
	content_id BIGINT NOT NULL,
	image_order INT NOT NULL,
	image_url VARCHAR(1000) NOT NULL,
	created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
	CONSTRAINT fk_cci_content FOREIGN KEY (content_id)
		REFERENCES community_contents(content_id)
		ON DELETE CASCADE,
	UNIQUE KEY uq_cci_content_order (content_id, image_order),
	KEY idx_cci_content_order (content_id, image_order)
);

INSERT INTO community_content_images (content_id, image_order, image_url)
SELECT content_id, 0, TRIM(SUBSTRING_INDEX(image, CONCAT(CHAR(10), '---NEOSTRIDE-IMAGE---', CHAR(10)), 1)) AS image_url
FROM community_contents
WHERE image IS NOT NULL AND image <> ''
  AND TRIM(SUBSTRING_INDEX(image, CONCAT(CHAR(10), '---NEOSTRIDE-IMAGE---', CHAR(10)), 1)) <> ''
ON DUPLICATE KEY UPDATE image_url = VALUES(image_url);

INSERT INTO community_content_images (content_id, image_order, image_url)
SELECT content_id, 1, TRIM(SUBSTRING_INDEX(SUBSTRING_INDEX(image, CONCAT(CHAR(10), '---NEOSTRIDE-IMAGE---', CHAR(10)), 2), CONCAT(CHAR(10), '---NEOSTRIDE-IMAGE---', CHAR(10)), -1)) AS image_url
FROM community_contents
WHERE image IS NOT NULL AND image <> ''
  AND INSTR(image, CONCAT(CHAR(10), '---NEOSTRIDE-IMAGE---', CHAR(10))) > 0
  AND TRIM(SUBSTRING_INDEX(SUBSTRING_INDEX(image, CONCAT(CHAR(10), '---NEOSTRIDE-IMAGE---', CHAR(10)), 2), CONCAT(CHAR(10), '---NEOSTRIDE-IMAGE---', CHAR(10)), -1)) <> ''
ON DUPLICATE KEY UPDATE image_url = VALUES(image_url);

INSERT INTO community_content_images (content_id, image_order, image_url)
SELECT content_id, 2, TRIM(SUBSTRING_INDEX(SUBSTRING_INDEX(image, CONCAT(CHAR(10), '---NEOSTRIDE-IMAGE---', CHAR(10)), 3), CONCAT(CHAR(10), '---NEOSTRIDE-IMAGE---', CHAR(10)), -1)) AS image_url
FROM community_contents
WHERE image IS NOT NULL AND image <> ''
  AND ((CHAR_LENGTH(image) - CHAR_LENGTH(REPLACE(image, CONCAT(CHAR(10), '---NEOSTRIDE-IMAGE---', CHAR(10)), ''))) / CHAR_LENGTH(CONCAT(CHAR(10), '---NEOSTRIDE-IMAGE---', CHAR(10)))) >= 2
  AND TRIM(SUBSTRING_INDEX(SUBSTRING_INDEX(image, CONCAT(CHAR(10), '---NEOSTRIDE-IMAGE---', CHAR(10)), 3), CONCAT(CHAR(10), '---NEOSTRIDE-IMAGE---', CHAR(10)), -1)) <> ''
ON DUPLICATE KEY UPDATE image_url = VALUES(image_url);
