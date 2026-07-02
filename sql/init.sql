CREATE DATABASE IF NOT EXISTS travel_memory
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE travel_memory;

DROP TABLE IF EXISTS travel_memory;
DROP TABLE IF EXISTS travel_trip;

CREATE TABLE travel_trip (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
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
  KEY idx_travel_trip_create_time (create_time)
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
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Create time',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Update time',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT 'Deleted flag: 0 no, 1 yes',
  PRIMARY KEY (id),
  KEY idx_travel_memory_trip_id (trip_id),
  KEY idx_travel_memory_record_time (record_time),
  KEY idx_travel_memory_location (latitude, longitude),
  CONSTRAINT fk_travel_memory_trip
    FOREIGN KEY (trip_id) REFERENCES travel_trip (id)
    ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Travel memory';
