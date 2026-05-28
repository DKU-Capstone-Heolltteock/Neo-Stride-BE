-- Neo-Stride MySQL schema baseline.
-- Generated from the operational schema after migration 016 on 2026-05-28.
-- Data rows are intentionally excluded; import into an empty database, then run apply-migrations.sh --baseline and --verify.

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;
DROP TABLE IF EXISTS `community_content_images`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `community_content_images` (
  `image_id` bigint NOT NULL AUTO_INCREMENT,
  `content_id` bigint NOT NULL,
  `image_order` int NOT NULL,
  `image_url` varchar(1000) COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`image_id`),
  UNIQUE KEY `uq_cci_content_order` (`content_id`,`image_order`),
  KEY `idx_cci_content_order` (`content_id`,`image_order`),
  CONSTRAINT `fk_cci_content` FOREIGN KEY (`content_id`) REFERENCES `community_contents` (`content_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `community_content_stats`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `community_content_stats` (
  `content_id` bigint NOT NULL,
  `tagged_count` int NOT NULL DEFAULT '0',
  `like_count` int NOT NULL DEFAULT '0',
  `comment_count` int NOT NULL DEFAULT '0',
  `bookmark_count` int NOT NULL DEFAULT '0',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`content_id`),
  CONSTRAINT `fk_ccs_content` FOREIGN KEY (`content_id`) REFERENCES `community_contents` (`content_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `community_contents`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `community_contents` (
  `content_id` bigint NOT NULL AUTO_INCREMENT,
  `author_user_id` bigint NOT NULL,
  `running_record_id` bigint DEFAULT NULL,
  `include_route_detail` tinyint(1) NOT NULL DEFAULT '0',
  `content_type` enum('POST','TIP') COLLATE utf8mb4_unicode_ci NOT NULL,
  `tip_type` enum('TRAINING','COURSE','GEAR','ETC') COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `feed_scope` enum('PUBLIC','FRIENDS','PRIVATE') COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PUBLIC',
  `content_text` text COLLATE utf8mb4_unicode_ci NOT NULL,
  `image` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`content_id`),
  KEY `fk_community_contents_running_record` (`running_record_id`),
  KEY `idx_cc_feed_list` (`content_type`,`feed_scope`,`created_at` DESC,`content_id` DESC),
  KEY `idx_cc_author_type_created` (`author_user_id`,`content_type`,`created_at` DESC,`content_id` DESC),
  CONSTRAINT `fk_community_contents_author` FOREIGN KEY (`author_user_id`) REFERENCES `users` (`user_id`) ON DELETE CASCADE,
  CONSTRAINT `fk_community_contents_running_record` FOREIGN KEY (`running_record_id`) REFERENCES `running_records` (`run_record_id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!50003 SET @saved_cs_client      = @@character_set_client */ ;
/*!50003 SET @saved_cs_results     = @@character_set_results */ ;
/*!50003 SET @saved_col_connection = @@collation_connection */ ;
/*!50003 SET character_set_client  = latin1 */ ;
/*!50003 SET character_set_results = latin1 */ ;
/*!50003 SET collation_connection  = latin1_swedish_ci */ ;
/*!50003 SET @saved_sql_mode       = @@sql_mode */ ;
/*!50003 SET sql_mode              = 'ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION' */ ;
DELIMITER ;;
/*!50003 CREATE*/ /*!50003 TRIGGER `trg_cc_after_insert_stats` AFTER INSERT ON `community_contents` FOR EACH ROW BEGIN
    INSERT INTO community_content_stats (content_id)
    VALUES (NEW.content_id)
    ON DUPLICATE KEY UPDATE updated_at = updated_at;
END */;;
DELIMITER ;
/*!50003 SET sql_mode              = @saved_sql_mode */ ;
/*!50003 SET character_set_client  = @saved_cs_client */ ;
/*!50003 SET character_set_results = @saved_cs_results */ ;
/*!50003 SET collation_connection  = @saved_col_connection */ ;
DROP TABLE IF EXISTS `community_interactions`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `community_interactions` (
  `interaction_id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `content_id` bigint NOT NULL,
  `interaction_type` enum('LIKE','COMMENT','BOOKMARK','TAG') COLLATE utf8mb4_unicode_ci NOT NULL,
  `comment_text` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `tagged_user_id` bigint DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `action_user_id` bigint GENERATED ALWAYS AS ((case when (`interaction_type` in (_utf8mb4'LIKE',_utf8mb4'BOOKMARK')) then `user_id` else NULL end)) VIRTUAL,
  `action_tagged_user_id` bigint GENERATED ALWAYS AS ((case when (`interaction_type` = _utf8mb4'TAG') then `tagged_user_id` else NULL end)) VIRTUAL,
  PRIMARY KEY (`interaction_id`),
  UNIQUE KEY `uq_ci_action_user` (`action_user_id`,`content_id`,`interaction_type`),
  UNIQUE KEY `uq_ci_tagged_user` (`action_tagged_user_id`,`content_id`,`interaction_type`),
  KEY `idx_ci_content_type` (`content_id`,`interaction_type`),
  KEY `idx_ci_user_type_content` (`user_id`,`interaction_type`,`content_id`),
  KEY `idx_ci_tagged_type_content` (`tagged_user_id`,`interaction_type`,`content_id`),
  KEY `idx_ci_content_type_created` (`content_id`,`interaction_type`,`created_at`,`interaction_id`),
  CONSTRAINT `fk_community_interactions_content` FOREIGN KEY (`content_id`) REFERENCES `community_contents` (`content_id`) ON DELETE CASCADE,
  CONSTRAINT `fk_community_interactions_tagged_user` FOREIGN KEY (`tagged_user_id`) REFERENCES `users` (`user_id`) ON DELETE SET NULL,
  CONSTRAINT `fk_community_interactions_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!50003 SET @saved_cs_client      = @@character_set_client */ ;
/*!50003 SET @saved_cs_results     = @@character_set_results */ ;
/*!50003 SET @saved_col_connection = @@collation_connection */ ;
/*!50003 SET character_set_client  = latin1 */ ;
/*!50003 SET character_set_results = latin1 */ ;
/*!50003 SET collation_connection  = latin1_swedish_ci */ ;
/*!50003 SET @saved_sql_mode       = @@sql_mode */ ;
/*!50003 SET sql_mode              = 'ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION' */ ;
DELIMITER ;;
/*!50003 CREATE*/ /*!50003 TRIGGER `trg_ci_after_insert_stats` AFTER INSERT ON `community_interactions` FOR EACH ROW BEGIN
	INSERT INTO community_content_stats (content_id, tagged_count, like_count, comment_count, bookmark_count)
	VALUES (
		NEW.content_id,
		IF(NEW.interaction_type = 'TAG', 1, 0),
		IF(NEW.interaction_type = 'LIKE', 1, 0),
		IF(NEW.interaction_type = 'COMMENT', 1, 0),
		IF(NEW.interaction_type = 'BOOKMARK', 1, 0)
	)
	ON DUPLICATE KEY UPDATE
		tagged_count = tagged_count + IF(NEW.interaction_type = 'TAG', 1, 0),
		like_count = like_count + IF(NEW.interaction_type = 'LIKE', 1, 0),
		comment_count = comment_count + IF(NEW.interaction_type = 'COMMENT', 1, 0),
		bookmark_count = bookmark_count + IF(NEW.interaction_type = 'BOOKMARK', 1, 0);
END */;;
DELIMITER ;
/*!50003 SET sql_mode              = @saved_sql_mode */ ;
/*!50003 SET character_set_client  = @saved_cs_client */ ;
/*!50003 SET character_set_results = @saved_cs_results */ ;
/*!50003 SET collation_connection  = @saved_col_connection */ ;
/*!50003 SET @saved_cs_client      = @@character_set_client */ ;
/*!50003 SET @saved_cs_results     = @@character_set_results */ ;
/*!50003 SET @saved_col_connection = @@collation_connection */ ;
/*!50003 SET character_set_client  = latin1 */ ;
/*!50003 SET character_set_results = latin1 */ ;
/*!50003 SET collation_connection  = latin1_swedish_ci */ ;
/*!50003 SET @saved_sql_mode       = @@sql_mode */ ;
/*!50003 SET sql_mode              = 'ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION' */ ;
DELIMITER ;;
/*!50003 CREATE*/ /*!50003 TRIGGER `trg_ci_after_update_stats` AFTER UPDATE ON `community_interactions` FOR EACH ROW BEGIN
	IF NOT (OLD.content_id <=> NEW.content_id AND OLD.interaction_type <=> NEW.interaction_type) THEN
		UPDATE community_content_stats
		SET tagged_count = GREATEST(tagged_count - IF(OLD.interaction_type = 'TAG', 1, 0), 0),
			like_count = GREATEST(like_count - IF(OLD.interaction_type = 'LIKE', 1, 0), 0),
			comment_count = GREATEST(comment_count - IF(OLD.interaction_type = 'COMMENT', 1, 0), 0),
			bookmark_count = GREATEST(bookmark_count - IF(OLD.interaction_type = 'BOOKMARK', 1, 0), 0)
		WHERE content_id = OLD.content_id;

		INSERT INTO community_content_stats (content_id, tagged_count, like_count, comment_count, bookmark_count)
		VALUES (
			NEW.content_id,
			IF(NEW.interaction_type = 'TAG', 1, 0),
			IF(NEW.interaction_type = 'LIKE', 1, 0),
			IF(NEW.interaction_type = 'COMMENT', 1, 0),
			IF(NEW.interaction_type = 'BOOKMARK', 1, 0)
		)
		ON DUPLICATE KEY UPDATE
			tagged_count = tagged_count + IF(NEW.interaction_type = 'TAG', 1, 0),
			like_count = like_count + IF(NEW.interaction_type = 'LIKE', 1, 0),
			comment_count = comment_count + IF(NEW.interaction_type = 'COMMENT', 1, 0),
			bookmark_count = bookmark_count + IF(NEW.interaction_type = 'BOOKMARK', 1, 0);
	END IF;
END */;;
DELIMITER ;
/*!50003 SET sql_mode              = @saved_sql_mode */ ;
/*!50003 SET character_set_client  = @saved_cs_client */ ;
/*!50003 SET character_set_results = @saved_cs_results */ ;
/*!50003 SET collation_connection  = @saved_col_connection */ ;
/*!50003 SET @saved_cs_client      = @@character_set_client */ ;
/*!50003 SET @saved_cs_results     = @@character_set_results */ ;
/*!50003 SET @saved_col_connection = @@collation_connection */ ;
/*!50003 SET character_set_client  = latin1 */ ;
/*!50003 SET character_set_results = latin1 */ ;
/*!50003 SET collation_connection  = latin1_swedish_ci */ ;
/*!50003 SET @saved_sql_mode       = @@sql_mode */ ;
/*!50003 SET sql_mode              = 'ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION' */ ;
DELIMITER ;;
/*!50003 CREATE*/ /*!50003 TRIGGER `trg_ci_after_delete_stats` AFTER DELETE ON `community_interactions` FOR EACH ROW BEGIN
	UPDATE community_content_stats
	SET tagged_count = GREATEST(tagged_count - IF(OLD.interaction_type = 'TAG', 1, 0), 0),
		like_count = GREATEST(like_count - IF(OLD.interaction_type = 'LIKE', 1, 0), 0),
		comment_count = GREATEST(comment_count - IF(OLD.interaction_type = 'COMMENT', 1, 0), 0),
		bookmark_count = GREATEST(bookmark_count - IF(OLD.interaction_type = 'BOOKMARK', 1, 0), 0)
	WHERE content_id = OLD.content_id;
END */;;
DELIMITER ;
/*!50003 SET sql_mode              = @saved_sql_mode */ ;
/*!50003 SET character_set_client  = @saved_cs_client */ ;
/*!50003 SET character_set_results = @saved_cs_results */ ;
/*!50003 SET collation_connection  = @saved_col_connection */ ;
DROP TABLE IF EXISTS `community_users`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `community_users` (
  `user_id` bigint NOT NULL,
  `community_profile_name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `profile_photo` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status_message` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `badge` enum('NONE','BRONZE','SILVER','GOLD','PLATINUM','DIAMOND','MASTER','CHALLENGER') COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'NONE',
  PRIMARY KEY (`user_id`),
  CONSTRAINT `fk_community_users_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `goals`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `goals` (
  `goal_id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `duration_weeks` int NOT NULL,
  `running_day` tinyint NOT NULL,
  `target_distance` decimal(8,2) NOT NULL,
  `target_pace` int NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_active` tinyint(1) NOT NULL DEFAULT '1',
  `is_achieved` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`goal_id`),
  KEY `idx_goals_user_active_created` (`user_id`,`is_active`,`created_at` DESC,`goal_id` DESC),
  CONSTRAINT `fk_goals_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `gps_traces`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `gps_traces` (
  `trace_id` bigint NOT NULL AUTO_INCREMENT,
  `run_record_id` bigint NOT NULL,
  `longitude` decimal(10,7) NOT NULL,
  `latitude` decimal(10,7) NOT NULL,
  `recorded_time` datetime NOT NULL,
  `heart_rate` decimal(5,1) DEFAULT NULL,
  `cadence` decimal(5,1) DEFAULT NULL,
  PRIMARY KEY (`trace_id`),
  KEY `idx_gps_record_time` (`run_record_id`,`recorded_time`,`trace_id`),
  CONSTRAINT `fk_gps_traces_running_record` FOREIGN KEY (`run_record_id`) REFERENCES `running_records` (`run_record_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `notifications`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `notifications` (
  `notification_id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `notification_type` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `message` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL,
  `endpoint` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `is_read` tinyint(1) NOT NULL DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`notification_id`),
  KEY `fk_notifications_user` (`user_id`),
  CONSTRAINT `fk_notifications_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `plans`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `plans` (
  `plan_id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `goal_id` bigint NOT NULL,
  `plan_date` date NOT NULL,
  `target_distance` decimal(8,2) NOT NULL,
  `target_pace` int NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_completed` tinyint(1) NOT NULL DEFAULT '0',
  `feedback` text COLLATE utf8mb4_unicode_ci,
  PRIMARY KEY (`plan_id`),
  KEY `idx_plans_user_goal_date` (`user_id`,`goal_id`,`plan_date`,`plan_id`),
  KEY `idx_plans_goal_date` (`goal_id`,`plan_date`,`plan_id`),
  KEY `idx_plans_user_date` (`user_id`,`plan_date`,`plan_id`),
  CONSTRAINT `fk_plans_goal` FOREIGN KEY (`goal_id`) REFERENCES `goals` (`goal_id`) ON DELETE CASCADE,
  CONSTRAINT `fk_plans_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `relationships`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `relationships` (
  `relation_id` bigint NOT NULL AUTO_INCREMENT,
  `user1_id` bigint NOT NULL,
  `user2_id` bigint NOT NULL,
  `status` enum('REQUESTED','ACCEPTED','REJECTED','BLOCKED') COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'REQUESTED',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`relation_id`),
  UNIQUE KEY `uq_relationship_pair` (`user1_id`,`user2_id`),
  KEY `idx_rel_user1_status_user2` (`user1_id`,`status`,`user2_id`),
  KEY `idx_rel_user2_status_user1` (`user2_id`,`status`,`user1_id`),
  CONSTRAINT `fk_relationships_user1` FOREIGN KEY (`user1_id`) REFERENCES `users` (`user_id`) ON DELETE CASCADE,
  CONSTRAINT `fk_relationships_user2` FOREIGN KEY (`user2_id`) REFERENCES `users` (`user_id`) ON DELETE CASCADE,
  CONSTRAINT `chk_relationship_not_self` CHECK ((`user1_id` <> `user2_id`))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `running_records`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `running_records` (
  `run_record_id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `plan_id` bigint DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `total_distance` decimal(8,2) NOT NULL,
  `duration` int DEFAULT NULL,
  `pace` int DEFAULT NULL,
  `calories` int DEFAULT NULL,
  `route_detail` text COLLATE utf8mb4_unicode_ci,
  PRIMARY KEY (`run_record_id`),
  KEY `fk_running_records_plan` (`plan_id`),
  KEY `idx_rr_user_created` (`user_id`,`created_at` DESC,`run_record_id` DESC),
  CONSTRAINT `fk_running_records_plan` FOREIGN KEY (`plan_id`) REFERENCES `plans` (`plan_id`) ON DELETE SET NULL,
  CONSTRAINT `fk_running_records_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `schema_migrations`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `schema_migrations` (
  `version` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `description` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `filename` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `checksum_sha256` char(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `applied_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `users`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `users` (
  `user_id` bigint NOT NULL AUTO_INCREMENT,
  `email` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `password` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `community_profile_name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `profile_photo` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`user_id`),
  UNIQUE KEY `email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;
