
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
  `title` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `body_text` text COLLATE utf8mb4_unicode_ci,
  `route_map_image_url` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `course_address` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `distance_km` decimal(8,2) DEFAULT NULL,
  `running_time_text` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `pace_text` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `image` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`content_id`),
  KEY `fk_community_contents_running_record` (`running_record_id`),
  KEY `idx_cc_feed_list` (`content_type`,`feed_scope`,`created_at` DESC,`content_id` DESC),
  KEY `idx_cc_author_type_created` (`author_user_id`,`content_type`,`created_at` DESC,`content_id` DESC),
  KEY `idx_cc_type_created` (`content_type`,`created_at` DESC,`content_id` DESC),
  FULLTEXT KEY `ft_cc_content_search` (`title`,`body_text`,`content_text`),
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
  UNIQUE KEY `uq_community_users_community_profile_name` (`community_profile_name`),
  FULLTEXT KEY `ft_cu_search` (`community_profile_name`,`status_message`),
  CONSTRAINT `fk_community_users_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `crew_chat_messages`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `crew_chat_messages` (
  `message_id` bigint NOT NULL AUTO_INCREMENT,
  `crew_id` bigint DEFAULT NULL,
  `instant_crew_id` bigint DEFAULT NULL,
  `sender_user_id` bigint NOT NULL,
  `message_type` enum('TEXT') COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'TEXT',
  `message_text` varchar(1000) COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`message_id`),
  KEY `idx_crew_chat_messages_crew_cursor` (`crew_id`,`message_id` DESC),
  KEY `idx_crew_chat_messages_instant_cursor` (`instant_crew_id`,`message_id` DESC),
  KEY `idx_crew_chat_messages_sender_created` (`sender_user_id`,`created_at` DESC),
  CONSTRAINT `fk_crew_chat_messages_crew` FOREIGN KEY (`crew_id`) REFERENCES `crews` (`crew_id`) ON DELETE CASCADE,
  CONSTRAINT `fk_crew_chat_messages_instant` FOREIGN KEY (`instant_crew_id`) REFERENCES `instant_crews` (`instant_crew_id`) ON DELETE CASCADE,
  CONSTRAINT `fk_crew_chat_messages_sender` FOREIGN KEY (`sender_user_id`) REFERENCES `users` (`user_id`) ON DELETE CASCADE,
  CONSTRAINT `chk_crew_chat_messages_one_target` CHECK ((((`crew_id` is not null) and (`instant_crew_id` is null)) or ((`crew_id` is null) and (`instant_crew_id` is not null))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `crew_event_participants`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `crew_event_participants` (
  `crew_event_participant_id` bigint NOT NULL AUTO_INCREMENT,
  `crew_event_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `status` enum('REQUESTED','ACCEPTED','DECLINED','CANCELLED','ATTENDED') COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ACCEPTED',
  `running_record_id` bigint DEFAULT NULL,
  `requested_at` timestamp NULL DEFAULT NULL,
  `responded_at` timestamp NULL DEFAULT NULL,
  `attended_at` timestamp NULL DEFAULT NULL,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`crew_event_participant_id`),
  UNIQUE KEY `uq_crew_event_participants_event_user` (`crew_event_id`,`user_id`),
  KEY `idx_crew_event_participants_user_status` (`user_id`,`status`,`crew_event_id`),
  KEY `idx_crew_event_participants_record` (`running_record_id`),
  CONSTRAINT `fk_crew_event_participants_event` FOREIGN KEY (`crew_event_id`) REFERENCES `crew_events` (`crew_event_id`) ON DELETE CASCADE,
  CONSTRAINT `fk_crew_event_participants_record` FOREIGN KEY (`running_record_id`) REFERENCES `running_records` (`run_record_id`) ON DELETE SET NULL,
  CONSTRAINT `fk_crew_event_participants_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `crew_events`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `crew_events` (
  `crew_event_id` bigint NOT NULL AUTO_INCREMENT,
  `crew_id` bigint NOT NULL,
  `host_user_id` bigint NOT NULL,
  `title` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL,
  `description` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `event_type` enum('OFFLINE','VIRTUAL') COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'OFFLINE',
  `status` enum('SCHEDULED','IN_PROGRESS','COMPLETED','CANCELLED') COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'SCHEDULED',
  `starts_at` datetime NOT NULL,
  `ends_at` datetime DEFAULT NULL,
  `location_label` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `meeting_place` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `capacity` int DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`crew_event_id`),
  KEY `idx_crew_events_crew_time` (`crew_id`,`starts_at`,`crew_event_id`),
  KEY `idx_crew_events_status_time` (`status`,`starts_at`),
  KEY `fk_crew_events_host` (`host_user_id`),
  CONSTRAINT `fk_crew_events_crew` FOREIGN KEY (`crew_id`) REFERENCES `crews` (`crew_id`) ON DELETE CASCADE,
  CONSTRAINT `fk_crew_events_host` FOREIGN KEY (`host_user_id`) REFERENCES `users` (`user_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `crew_members`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `crew_members` (
  `crew_member_id` bigint NOT NULL AUTO_INCREMENT,
  `crew_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `role` enum('OWNER','ADMIN','MEMBER') COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'MEMBER',
  `status` enum('REQUESTED','ACCEPTED','REJECTED','INVITED','LEFT','REMOVED') COLLATE utf8mb4_unicode_ci NOT NULL,
  `requested_at` timestamp NULL DEFAULT NULL,
  `responded_at` timestamp NULL DEFAULT NULL,
  `joined_at` timestamp NULL DEFAULT NULL,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`crew_member_id`),
  UNIQUE KEY `uq_crew_members_crew_user` (`crew_id`,`user_id`),
  KEY `idx_crew_members_user_status` (`user_id`,`status`,`crew_id`),
  KEY `idx_crew_members_crew_status_role` (`crew_id`,`status`,`role`),
  CONSTRAINT `fk_crew_members_crew` FOREIGN KEY (`crew_id`) REFERENCES `crews` (`crew_id`) ON DELETE CASCADE,
  CONSTRAINT `fk_crew_members_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `crews`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `crews` (
  `crew_id` bigint NOT NULL AUTO_INCREMENT,
  `owner_user_id` bigint NOT NULL,
  `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `description` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `visibility` enum('PUBLIC','PRIVATE') COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PUBLIC',
  `join_policy` enum('OPEN','APPROVAL','INVITE') COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'OPEN',
  `region` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `profile_image_url` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `member_count` int NOT NULL DEFAULT '1',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`crew_id`),
  UNIQUE KEY `uq_crews_name` (`name`),
  KEY `idx_crews_visibility_created` (`visibility`,`created_at` DESC,`crew_id` DESC),
  KEY `idx_crews_owner_created` (`owner_user_id`,`created_at` DESC),
  CONSTRAINT `fk_crews_owner` FOREIGN KEY (`owner_user_id`) REFERENCES `users` (`user_id`) ON DELETE CASCADE
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
DROP TABLE IF EXISTS `instant_crew_participants`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `instant_crew_participants` (
  `instant_crew_participant_id` bigint NOT NULL AUTO_INCREMENT,
  `instant_crew_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `status` enum('REQUESTED','ACCEPTED','REJECTED','CANCELLED','ATTENDED') COLLATE utf8mb4_unicode_ci NOT NULL,
  `requested_at` timestamp NULL DEFAULT NULL,
  `responded_at` timestamp NULL DEFAULT NULL,
  `joined_at` timestamp NULL DEFAULT NULL,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`instant_crew_participant_id`),
  UNIQUE KEY `uq_instant_crew_participants_crew_user` (`instant_crew_id`,`user_id`),
  KEY `idx_instant_crew_participants_user_status` (`user_id`,`status`,`instant_crew_id`),
  KEY `idx_instant_crew_participants_crew_status` (`instant_crew_id`,`status`),
  CONSTRAINT `fk_instant_crew_participants_crew` FOREIGN KEY (`instant_crew_id`) REFERENCES `instant_crews` (`instant_crew_id`) ON DELETE CASCADE,
  CONSTRAINT `fk_instant_crew_participants_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `instant_crews`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `instant_crews` (
  `instant_crew_id` bigint NOT NULL AUTO_INCREMENT,
  `crew_id` bigint DEFAULT NULL,
  `host_user_id` bigint NOT NULL,
  `title` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL,
  `description` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status` enum('OPEN','CLOSED','CANCELLED','COMPLETED','EXPIRED') COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'OPEN',
  `region` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `location_label` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL,
  `meeting_place_private` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL,
  `starts_at` datetime NOT NULL,
  `recruit_until` datetime NOT NULL,
  `capacity` int NOT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`instant_crew_id`),
  KEY `idx_instant_crews_status_region_time` (`status`,`region`,`starts_at`,`instant_crew_id`),
  KEY `idx_instant_crews_host_created` (`host_user_id`,`created_at` DESC),
  KEY `idx_instant_crews_crew_time` (`crew_id`,`starts_at`),
  CONSTRAINT `fk_instant_crews_crew` FOREIGN KEY (`crew_id`) REFERENCES `crews` (`crew_id`) ON DELETE SET NULL,
  CONSTRAINT `fk_instant_crews_host` FOREIGN KEY (`host_user_id`) REFERENCES `users` (`user_id`) ON DELETE CASCADE
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
  KEY `idx_notifications_user_created` (`user_id`,`created_at` DESC,`notification_id` DESC),
  KEY `idx_notifications_user_read` (`user_id`,`is_read`),
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
DROP TABLE IF EXISTS `refresh_tokens`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `refresh_tokens` (
  `refresh_token_id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `token_id_hash` char(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `expires_at` datetime NOT NULL,
  `revoked_at` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`refresh_token_id`),
  UNIQUE KEY `uq_refresh_tokens_token_id_hash` (`token_id_hash`),
  KEY `idx_refresh_tokens_user_active` (`user_id`,`revoked_at`,`expires_at`),
  CONSTRAINT `fk_refresh_tokens_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`) ON DELETE CASCADE
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
DROP TABLE IF EXISTS `operator_accounts`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `operator_accounts` (
  `operator_account_id` bigint NOT NULL AUTO_INCREMENT,
  `email` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `password` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `role` enum('SUPER_ADMIN','OPERATOR_ADMIN','MODERATOR','SUPPORT','DEVELOPER','AUDITOR') COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` enum('ACTIVE','DISABLED') COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ACTIVE',
  `last_login_at` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`operator_account_id`),
  UNIQUE KEY `uq_operator_accounts_email` (`email`),
  KEY `idx_operator_accounts_status_role` (`status`,`role`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `operator_account_permissions`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `operator_account_permissions` (
  `operator_account_id` bigint NOT NULL,
  `permission` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`operator_account_id`,`permission`),
  KEY `idx_operator_account_permissions_permission` (`permission`),
  CONSTRAINT `fk_operator_account_permissions_account` FOREIGN KEY (`operator_account_id`) REFERENCES `operator_accounts` (`operator_account_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `operator_refresh_tokens`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `operator_refresh_tokens` (
  `operator_refresh_token_id` bigint NOT NULL AUTO_INCREMENT,
  `operator_account_id` bigint NOT NULL,
  `token_id_hash` char(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `expires_at` datetime NOT NULL,
  `revoked_at` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`operator_refresh_token_id`),
  UNIQUE KEY `uq_operator_refresh_tokens_hash` (`token_id_hash`),
  KEY `idx_operator_refresh_tokens_operator_active` (`operator_account_id`,`revoked_at`,`expires_at`),
  CONSTRAINT `fk_operator_refresh_tokens_operator` FOREIGN KEY (`operator_account_id`) REFERENCES `operator_accounts` (`operator_account_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `operator_audit_logs`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `operator_audit_logs` (
  `operator_audit_log_id` bigint NOT NULL AUTO_INCREMENT,
  `actor_operator_account_id` bigint DEFAULT NULL,
  `action` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `target_type` varchar(80) COLLATE utf8mb4_unicode_ci NOT NULL,
  `target_id` varchar(120) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `reason` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `before_summary` text COLLATE utf8mb4_unicode_ci,
  `after_summary` text COLLATE utf8mb4_unicode_ci,
  `request_id` varchar(120) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `ip_address` varchar(80) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `user_agent` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`operator_audit_log_id`),
  KEY `idx_operator_audit_logs_actor_created` (`actor_operator_account_id`,`created_at` DESC),
  KEY `idx_operator_audit_logs_target_created` (`target_type`,`target_id`,`created_at` DESC),
  KEY `idx_operator_audit_logs_action_created` (`action`,`created_at` DESC),
  CONSTRAINT `fk_operator_audit_logs_actor` FOREIGN KEY (`actor_operator_account_id`) REFERENCES `operator_accounts` (`operator_account_id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `admin_reports`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `admin_reports` (
  `report_id` bigint NOT NULL AUTO_INCREMENT,
  `reporter_user_id` bigint DEFAULT NULL,
  `target_user_id` bigint DEFAULT NULL,
  `target_type` varchar(80) COLLATE utf8mb4_unicode_ci NOT NULL,
  `target_id` varchar(120) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `category` varchar(80) COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` enum('PENDING','RESOLVED','REJECTED') COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING',
  `reason` varchar(1000) COLLATE utf8mb4_unicode_ci NOT NULL,
  `resolution` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `assigned_operator_account_id` bigint DEFAULT NULL,
  `resolved_at` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`report_id`),
  KEY `idx_admin_reports_status_created` (`status`,`created_at` DESC),
  KEY `idx_admin_reports_target` (`target_type`,`target_id`),
  KEY `idx_admin_reports_reporter` (`reporter_user_id`,`created_at` DESC),
  KEY `idx_admin_reports_target_user` (`target_user_id`,`created_at` DESC),
  KEY `fk_admin_reports_assigned_operator` (`assigned_operator_account_id`),
  CONSTRAINT `fk_admin_reports_assigned_operator` FOREIGN KEY (`assigned_operator_account_id`) REFERENCES `operator_accounts` (`operator_account_id`) ON DELETE SET NULL,
  CONSTRAINT `fk_admin_reports_reporter` FOREIGN KEY (`reporter_user_id`) REFERENCES `users` (`user_id`) ON DELETE SET NULL,
  CONSTRAINT `fk_admin_reports_target_user` FOREIGN KEY (`target_user_id`) REFERENCES `users` (`user_id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `operator_broadcasts`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `operator_broadcasts` (
  `broadcast_id` bigint NOT NULL AUTO_INCREMENT,
  `sender_operator_account_id` bigint DEFAULT NULL,
  `title` varchar(160) COLLATE utf8mb4_unicode_ci NOT NULL,
  `message` varchar(2000) COLLATE utf8mb4_unicode_ci NOT NULL,
  `target_type` enum('ALL','USER') COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ALL',
  `target_user_id` bigint DEFAULT NULL,
  `recipient_count` int NOT NULL DEFAULT '0',
  `status` enum('SENT','PARTIAL','FAILED') COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'SENT',
  `discord_status` varchar(40) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `discord_error` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `reason` varchar(1000) COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`broadcast_id`),
  KEY `idx_operator_broadcasts_sender_created` (`sender_operator_account_id`,`created_at` DESC),
  KEY `idx_operator_broadcasts_created` (`created_at` DESC),
  KEY `fk_operator_broadcasts_target_user` (`target_user_id`),
  CONSTRAINT `fk_operator_broadcasts_sender` FOREIGN KEY (`sender_operator_account_id`) REFERENCES `operator_accounts` (`operator_account_id`) ON DELETE SET NULL,
  CONSTRAINT `fk_operator_broadcasts_target_user` FOREIGN KEY (`target_user_id`) REFERENCES `users` (`user_id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `api_request_metrics`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `api_request_metrics` (
  `api_request_metric_id` bigint NOT NULL AUTO_INCREMENT,
  `method` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL,
  `path` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `status_code` int NOT NULL,
  `duration_ms` bigint NOT NULL,
  `occurred_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`api_request_metric_id`),
  KEY `idx_api_request_metrics_occurred` (`occurred_at` DESC),
  KEY `idx_api_request_metrics_path_status` (`path`,`status_code`,`occurred_at` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `server_error_events`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `server_error_events` (
  `server_error_event_id` bigint NOT NULL AUTO_INCREMENT,
  `method` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL,
  `path` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `status_code` int NOT NULL,
  `error_type` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL,
  `message_summary` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `request_id` varchar(120) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`server_error_event_id`),
  KEY `idx_server_error_events_created` (`created_at` DESC),
  KEY `idx_server_error_events_path_created` (`path`,`created_at` DESC),
  KEY `idx_server_error_events_status_created` (`status_code`,`created_at` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `operator_alert_rules`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `operator_alert_rules` (
  `alert_rule_id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL,
  `metric_type` enum('API_ERROR_RATE','API_TRAFFIC','SERVER_ERROR_COUNT') COLLATE utf8mb4_unicode_ci NOT NULL,
  `threshold_value` decimal(12,2) NOT NULL,
  `window_minutes` int NOT NULL,
  `channel` enum('DISCORD') COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'DISCORD',
  `enabled` tinyint(1) NOT NULL DEFAULT '1',
  `discord_status` varchar(40) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `discord_error` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `last_tested_at` datetime DEFAULT NULL,
  `created_by_operator_account_id` bigint DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`alert_rule_id`),
  KEY `idx_operator_alert_rules_enabled_metric` (`enabled`,`metric_type`),
  KEY `fk_operator_alert_rules_creator` (`created_by_operator_account_id`),
  CONSTRAINT `fk_operator_alert_rules_creator` FOREIGN KEY (`created_by_operator_account_id`) REFERENCES `operator_accounts` (`operator_account_id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `bug_reports`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `bug_reports` (
  `bug_report_id` bigint NOT NULL AUTO_INCREMENT,
  `reporter_user_id` bigint DEFAULT NULL,
  `title` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL,
  `description` text COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` enum('OPEN','TRIAGED','IN_PROGRESS','RESOLVED','REJECTED') COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'OPEN',
  `severity` enum('LOW','MEDIUM','HIGH','CRITICAL') COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'MEDIUM',
  `app_version` varchar(80) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `device_model` varchar(120) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`bug_report_id`),
  KEY `idx_bug_reports_status_created` (`status`,`created_at` DESC),
  KEY `idx_bug_reports_reporter_created` (`reporter_user_id`,`created_at` DESC),
  CONSTRAINT `fk_bug_reports_reporter` FOREIGN KEY (`reporter_user_id`) REFERENCES `users` (`user_id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
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
  `account_status` enum('ACTIVE','SUSPENDED') COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ACTIVE',
  `suspended_at` datetime DEFAULT NULL,
  `suspended_until` datetime DEFAULT NULL,
  `suspended_reason` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `suspended_by_operator_id` bigint DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`user_id`),
  UNIQUE KEY `uq_users_email` (`email`),
  UNIQUE KEY `uq_users_name` (`name`),
  UNIQUE KEY `uq_users_community_profile_name` (`community_profile_name`),
  FULLTEXT KEY `ft_users_search` (`name`,`community_profile_name`),
  KEY `idx_users_account_status_created` (`account_status`,`created_at`),
  KEY `idx_users_suspended_by_operator` (`suspended_by_operator_id`),
  CONSTRAINT `fk_users_suspended_by_operator` FOREIGN KEY (`suspended_by_operator_id`) REFERENCES `operator_accounts` (`operator_account_id`) ON DELETE SET NULL
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
