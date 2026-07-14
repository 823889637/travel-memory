CREATE DATABASE IF NOT EXISTS travel_memory
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE travel_memory;

DROP TABLE IF EXISTS memory_companion;
DROP TABLE IF EXISTS memory_photo;
DROP TABLE IF EXISTS trip_companion;
DROP TABLE IF EXISTS travel_memory;
DROP TABLE IF EXISTS travel_trip;
DROP TABLE IF EXISTS app_user;

CREATE TABLE app_user (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  username VARCHAR(64) DEFAULT NULL COMMENT 'Lowercase username',
  display_name VARCHAR(100) NOT NULL COMMENT 'Display name',
  password_hash VARCHAR(100) NOT NULL COMMENT 'BCrypt password hash',
  role VARCHAR(16) NOT NULL COMMENT 'ADMIN or USER',
  enabled TINYINT NOT NULL DEFAULT 1 COMMENT 'Enabled flag',
  must_change_password TINYINT NOT NULL DEFAULT 0 COMMENT 'Require password change',
  failed_login_count INT NOT NULL DEFAULT 0,
  locked_until DATETIME DEFAULT NULL,
  last_login_time DATETIME DEFAULT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_app_user_username (username),
  KEY idx_app_user_role_enabled (role, enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Application user';

INSERT INTO app_user (id, username, display_name, password_hash, role, enabled, must_change_password)
VALUES (1, NULL, 'Initial administrator', '!', 'ADMIN', 0, 1);

CREATE TABLE travel_trip (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  user_id BIGINT NOT NULL COMMENT 'Owner user ID',
  title VARCHAR(100) NOT NULL COMMENT 'Trip title',
  description VARCHAR(500) DEFAULT NULL COMMENT 'Trip description',
  destination VARCHAR(100) DEFAULT NULL COMMENT 'Destination',
  start_date DATE DEFAULT NULL COMMENT 'Start date',
  end_date DATE DEFAULT NULL COMMENT 'End date',
  cover_photo_url VARCHAR(255) DEFAULT NULL COMMENT 'Cover photo URL',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Create time',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Update time',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT 'Deleted flag: 0 no, 1 yes',
  PRIMARY KEY (id),
  KEY idx_travel_trip_start_date (start_date),
  KEY idx_travel_trip_user_deleted_start (user_id, deleted, start_date),
  KEY idx_travel_trip_create_time (create_time),
  CONSTRAINT fk_travel_trip_user FOREIGN KEY (user_id) REFERENCES app_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Travel trip';

CREATE TABLE travel_memory (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  trip_id BIGINT NOT NULL COMMENT 'Trip ID',
  content VARCHAR(300) DEFAULT NULL COMMENT 'Text content',
  photo_url VARCHAR(255) DEFAULT NULL COMMENT 'Photo access URL',
  photo_path VARCHAR(255) DEFAULT NULL COMMENT 'Local photo path',
  latitude DECIMAL(10, 7) DEFAULT NULL COMMENT 'Latitude',
  longitude DECIMAL(10, 7) DEFAULT NULL COMMENT 'Longitude',
  location_name VARCHAR(255) DEFAULT NULL COMMENT 'Location name',
  record_time DATETIME NOT NULL COMMENT 'Record time',
  is_favorite TINYINT NOT NULL DEFAULT 0 COMMENT 'Favorite flag: 0 no, 1 yes',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Create time',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Update time',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT 'Deleted flag: 0 no, 1 yes',
  PRIMARY KEY (id),
  KEY idx_travel_memory_trip_id (trip_id),
  KEY idx_travel_memory_record_time (record_time),
  KEY idx_travel_memory_is_favorite (is_favorite),
  KEY idx_travel_memory_location (latitude, longitude),
  CONSTRAINT fk_travel_memory_trip
    FOREIGN KEY (trip_id) REFERENCES travel_trip (id)
    ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Travel memory';

CREATE TABLE trip_companion (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  trip_id BIGINT NOT NULL COMMENT 'Trip ID',
  name VARCHAR(50) NOT NULL COMMENT 'Companion display name',
  sort_order INT NOT NULL DEFAULT 0 COMMENT 'Display order',
  active TINYINT NOT NULL DEFAULT 1 COMMENT 'Available for new memories',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Create time',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Update time',
  PRIMARY KEY (id),
  UNIQUE KEY uk_trip_companion_name (trip_id, name),
  KEY idx_trip_companion_trip_active_sort (trip_id, active, sort_order),
  CONSTRAINT fk_trip_companion_trip
    FOREIGN KEY (trip_id) REFERENCES travel_trip (id)
    ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='People travelling together';

CREATE TABLE memory_photo (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  memory_id BIGINT NOT NULL COMMENT 'Memory ID',
  photo_url VARCHAR(255) NOT NULL COMMENT 'Photo access URL',
  sort_order INT NOT NULL DEFAULT 1 COMMENT 'Display order, first is primary',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Create time',
  PRIMARY KEY (id),
  KEY idx_memory_photo_memory_sort (memory_id, sort_order),
  CONSTRAINT fk_memory_photo_memory
    FOREIGN KEY (memory_id) REFERENCES travel_memory (id)
    ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Memory photos';

CREATE TABLE memory_companion (
  memory_id BIGINT NOT NULL COMMENT 'Memory ID',
  companion_id BIGINT NOT NULL COMMENT 'Trip companion ID',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Create time',
  PRIMARY KEY (memory_id, companion_id),
  KEY idx_memory_companion_companion (companion_id, memory_id),
  CONSTRAINT fk_memory_companion_memory
    FOREIGN KEY (memory_id) REFERENCES travel_memory (id)
    ON DELETE CASCADE,
  CONSTRAINT fk_memory_companion_companion
    FOREIGN KEY (companion_id) REFERENCES trip_companion (id)
    ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Memory participants';
