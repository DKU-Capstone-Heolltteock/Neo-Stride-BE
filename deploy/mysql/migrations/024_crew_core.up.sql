CREATE TABLE IF NOT EXISTS crews (
    crew_id BIGINT NOT NULL AUTO_INCREMENT,
    owner_user_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(1000) NULL,
    visibility ENUM('PUBLIC', 'PRIVATE') NOT NULL DEFAULT 'PUBLIC',
    join_policy ENUM('OPEN', 'APPROVAL', 'INVITE') NOT NULL DEFAULT 'OPEN',
    region VARCHAR(100) NULL,
    profile_image_url VARCHAR(1000) NULL,
    member_count INT NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (crew_id),
    UNIQUE KEY uq_crews_name (name),
    KEY idx_crews_visibility_created (visibility, created_at DESC, crew_id DESC),
    KEY idx_crews_owner_created (owner_user_id, created_at DESC),
    CONSTRAINT fk_crews_owner FOREIGN KEY (owner_user_id) REFERENCES users (user_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS crew_members (
    crew_member_id BIGINT NOT NULL AUTO_INCREMENT,
    crew_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    role ENUM('OWNER', 'ADMIN', 'MEMBER') NOT NULL DEFAULT 'MEMBER',
    status ENUM('REQUESTED', 'ACCEPTED', 'REJECTED', 'INVITED', 'LEFT', 'REMOVED') NOT NULL,
    requested_at TIMESTAMP NULL,
    responded_at TIMESTAMP NULL,
    joined_at TIMESTAMP NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (crew_member_id),
    UNIQUE KEY uq_crew_members_crew_user (crew_id, user_id),
    KEY idx_crew_members_user_status (user_id, status, crew_id),
    KEY idx_crew_members_crew_status_role (crew_id, status, role),
    CONSTRAINT fk_crew_members_crew FOREIGN KEY (crew_id) REFERENCES crews (crew_id) ON DELETE CASCADE,
    CONSTRAINT fk_crew_members_user FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS crew_events (
    crew_event_id BIGINT NOT NULL AUTO_INCREMENT,
    crew_id BIGINT NOT NULL,
    host_user_id BIGINT NOT NULL,
    title VARCHAR(120) NOT NULL,
    description VARCHAR(1000) NULL,
    event_type ENUM('OFFLINE', 'VIRTUAL') NOT NULL DEFAULT 'OFFLINE',
    status ENUM('SCHEDULED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED') NOT NULL DEFAULT 'SCHEDULED',
    starts_at DATETIME NOT NULL,
    ends_at DATETIME NULL,
    location_label VARCHAR(200) NULL,
    meeting_place VARCHAR(500) NULL,
    capacity INT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (crew_event_id),
    KEY idx_crew_events_crew_time (crew_id, starts_at, crew_event_id),
    KEY idx_crew_events_status_time (status, starts_at),
    CONSTRAINT fk_crew_events_crew FOREIGN KEY (crew_id) REFERENCES crews (crew_id) ON DELETE CASCADE,
    CONSTRAINT fk_crew_events_host FOREIGN KEY (host_user_id) REFERENCES users (user_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS crew_event_participants (
    crew_event_participant_id BIGINT NOT NULL AUTO_INCREMENT,
    crew_event_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    status ENUM('REQUESTED', 'ACCEPTED', 'DECLINED', 'CANCELLED', 'ATTENDED') NOT NULL DEFAULT 'ACCEPTED',
    running_record_id BIGINT NULL,
    requested_at TIMESTAMP NULL,
    responded_at TIMESTAMP NULL,
    attended_at TIMESTAMP NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (crew_event_participant_id),
    UNIQUE KEY uq_crew_event_participants_event_user (crew_event_id, user_id),
    KEY idx_crew_event_participants_user_status (user_id, status, crew_event_id),
    KEY idx_crew_event_participants_record (running_record_id),
    CONSTRAINT fk_crew_event_participants_event FOREIGN KEY (crew_event_id) REFERENCES crew_events (crew_event_id) ON DELETE CASCADE,
    CONSTRAINT fk_crew_event_participants_user FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE CASCADE,
    CONSTRAINT fk_crew_event_participants_record FOREIGN KEY (running_record_id) REFERENCES running_records (run_record_id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS instant_crews (
    instant_crew_id BIGINT NOT NULL AUTO_INCREMENT,
    crew_id BIGINT NULL,
    host_user_id BIGINT NOT NULL,
    title VARCHAR(120) NOT NULL,
    description VARCHAR(1000) NULL,
    status ENUM('OPEN', 'CLOSED', 'CANCELLED', 'COMPLETED', 'EXPIRED') NOT NULL DEFAULT 'OPEN',
    region VARCHAR(100) NOT NULL,
    location_label VARCHAR(200) NOT NULL,
    meeting_place_private VARCHAR(500) NOT NULL,
    starts_at DATETIME NOT NULL,
    recruit_until DATETIME NOT NULL,
    capacity INT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (instant_crew_id),
    KEY idx_instant_crews_status_region_time (status, region, starts_at, instant_crew_id),
    KEY idx_instant_crews_host_created (host_user_id, created_at DESC),
    KEY idx_instant_crews_crew_time (crew_id, starts_at),
    CONSTRAINT fk_instant_crews_crew FOREIGN KEY (crew_id) REFERENCES crews (crew_id) ON DELETE SET NULL,
    CONSTRAINT fk_instant_crews_host FOREIGN KEY (host_user_id) REFERENCES users (user_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS instant_crew_participants (
    instant_crew_participant_id BIGINT NOT NULL AUTO_INCREMENT,
    instant_crew_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    status ENUM('REQUESTED', 'ACCEPTED', 'REJECTED', 'CANCELLED', 'ATTENDED') NOT NULL,
    requested_at TIMESTAMP NULL,
    responded_at TIMESTAMP NULL,
    joined_at TIMESTAMP NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (instant_crew_participant_id),
    UNIQUE KEY uq_instant_crew_participants_crew_user (instant_crew_id, user_id),
    KEY idx_instant_crew_participants_user_status (user_id, status, instant_crew_id),
    KEY idx_instant_crew_participants_crew_status (instant_crew_id, status),
    CONSTRAINT fk_instant_crew_participants_crew FOREIGN KEY (instant_crew_id) REFERENCES instant_crews (instant_crew_id) ON DELETE CASCADE,
    CONSTRAINT fk_instant_crew_participants_user FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS crew_chat_messages (
    message_id BIGINT NOT NULL AUTO_INCREMENT,
    crew_id BIGINT NULL,
    instant_crew_id BIGINT NULL,
    sender_user_id BIGINT NOT NULL,
    message_type ENUM('TEXT') NOT NULL DEFAULT 'TEXT',
    message_text VARCHAR(1000) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (message_id),
    KEY idx_crew_chat_messages_crew_cursor (crew_id, message_id DESC),
    KEY idx_crew_chat_messages_instant_cursor (instant_crew_id, message_id DESC),
    KEY idx_crew_chat_messages_sender_created (sender_user_id, created_at DESC),
    CONSTRAINT chk_crew_chat_messages_one_target CHECK (
        (crew_id IS NOT NULL AND instant_crew_id IS NULL)
        OR (crew_id IS NULL AND instant_crew_id IS NOT NULL)
    ),
    CONSTRAINT fk_crew_chat_messages_crew FOREIGN KEY (crew_id) REFERENCES crews (crew_id) ON DELETE CASCADE,
    CONSTRAINT fk_crew_chat_messages_instant FOREIGN KEY (instant_crew_id) REFERENCES instant_crews (instant_crew_id) ON DELETE CASCADE,
    CONSTRAINT fk_crew_chat_messages_sender FOREIGN KEY (sender_user_id) REFERENCES users (user_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
