ALTER TABLE community_contents
	ADD COLUMN title VARCHAR(255) NULL AFTER content_text,
	ADD COLUMN body_text TEXT NULL AFTER title,
	ADD COLUMN route_map_image_url VARCHAR(1000) NULL AFTER body_text,
	ADD COLUMN course_address VARCHAR(500) NULL AFTER route_map_image_url,
	ADD COLUMN distance_km DECIMAL(8,2) NULL AFTER course_address,
	ADD COLUMN running_time_text VARCHAR(32) NULL AFTER distance_km,
	ADD COLUMN pace_text VARCHAR(32) NULL AFTER running_time_text;

UPDATE community_contents
SET title = NULLIF(SUBSTRING_INDEX(content_text, CONCAT(CHAR(10), '---NEOSTRIDE-FEED---', CHAR(10)), 1), ''),
	body_text = NULLIF(SUBSTRING_INDEX(SUBSTRING_INDEX(content_text, CONCAT(CHAR(10), '---NEOSTRIDE-ROUTE---', CHAR(10)), 1), CONCAT(CHAR(10), '---NEOSTRIDE-FEED---', CHAR(10)), -1), ''),
	route_map_image_url = NULLIF(SUBSTRING_INDEX(SUBSTRING_INDEX(content_text, CONCAT(CHAR(10), '---NEOSTRIDE-METRICS---', CHAR(10)), 1), CONCAT(CHAR(10), '---NEOSTRIDE-ROUTE---', CHAR(10)), -1), '')
WHERE content_type = 'POST'
  AND INSTR(content_text, CONCAT(CHAR(10), '---NEOSTRIDE-FEED---', CHAR(10))) > 0;

UPDATE community_contents
SET distance_km = CAST(NULLIF(SUBSTRING_INDEX(SUBSTRING_INDEX(content_text, CONCAT(CHAR(10), '---NEOSTRIDE-METRICS---', CHAR(10)), -1), CONCAT(CHAR(10), '---NEOSTRIDE-METRIC---', CHAR(10)), 1), '') AS DECIMAL(8,2)),
	running_time_text = NULLIF(SUBSTRING_INDEX(SUBSTRING_INDEX(SUBSTRING_INDEX(content_text, CONCAT(CHAR(10), '---NEOSTRIDE-METRICS---', CHAR(10)), -1), CONCAT(CHAR(10), '---NEOSTRIDE-METRIC---', CHAR(10)), 2), CONCAT(CHAR(10), '---NEOSTRIDE-METRIC---', CHAR(10)), -1), ''),
	pace_text = NULLIF(SUBSTRING_INDEX(SUBSTRING_INDEX(content_text, CONCAT(CHAR(10), '---NEOSTRIDE-METRICS---', CHAR(10)), -1), CONCAT(CHAR(10), '---NEOSTRIDE-METRIC---', CHAR(10)), -1), '')
WHERE content_type = 'POST'
  AND INSTR(content_text, CONCAT(CHAR(10), '---NEOSTRIDE-METRICS---', CHAR(10))) > 0;

UPDATE community_contents
SET title = NULLIF(SUBSTRING_INDEX(content_text, CONCAT(CHAR(10), '---NEOSTRIDE-TIP---', CHAR(10)), 1), ''),
	body_text = NULLIF(SUBSTRING_INDEX(SUBSTRING_INDEX(content_text, CONCAT(CHAR(10), '---NEOSTRIDE-ROUTE---', CHAR(10)), 1), CONCAT(CHAR(10), '---NEOSTRIDE-TIP---', CHAR(10)), -1), ''),
	route_map_image_url = NULLIF(SUBSTRING_INDEX(SUBSTRING_INDEX(content_text, CONCAT(CHAR(10), '---NEOSTRIDE-ADDR---', CHAR(10)), 1), CONCAT(CHAR(10), '---NEOSTRIDE-ROUTE---', CHAR(10)), -1), ''),
	course_address = NULLIF(SUBSTRING_INDEX(content_text, CONCAT(CHAR(10), '---NEOSTRIDE-ADDR---', CHAR(10)), -1), '')
WHERE content_type = 'TIP'
  AND INSTR(content_text, CONCAT(CHAR(10), '---NEOSTRIDE-TIP---', CHAR(10))) > 0;
