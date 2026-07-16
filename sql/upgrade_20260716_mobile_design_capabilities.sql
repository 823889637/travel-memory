USE travel_memory;

ALTER TABLE app_user
  ADD COLUMN avatar_url VARCHAR(255) DEFAULT NULL COMMENT 'Protected avatar URL' AFTER display_name;

ALTER TABLE travel_trip
  ADD COLUMN notes VARCHAR(1000) DEFAULT NULL COMMENT 'Private trip notes' AFTER description,
  ADD COLUMN is_favorite TINYINT NOT NULL DEFAULT 0 COMMENT 'Trip favorite flag' AFTER cover_photo_url;

ALTER TABLE trip_companion
  ADD COLUMN avatar_url VARCHAR(255) DEFAULT NULL COMMENT 'Protected companion avatar URL' AFTER name,
  ADD COLUMN is_self TINYINT NOT NULL DEFAULT 0 COMMENT 'Current user marker' AFTER avatar_url;

CREATE TABLE IF NOT EXISTS trip_draft (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  user_id BIGINT NOT NULL COMMENT 'Draft owner user ID',
  title VARCHAR(100) DEFAULT NULL COMMENT 'Draft trip title',
  destination VARCHAR(100) DEFAULT NULL COMMENT 'Draft destination',
  start_date DATE DEFAULT NULL COMMENT 'Draft start date',
  end_date DATE DEFAULT NULL COMMENT 'Draft end date',
  description VARCHAR(500) DEFAULT NULL COMMENT 'Draft description',
  notes VARCHAR(1000) DEFAULT NULL COMMENT 'Draft private notes',
  cover_photo_url VARCHAR(255) DEFAULT NULL COMMENT 'Protected draft cover URL',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_trip_draft_user (user_id),
  CONSTRAINT fk_trip_draft_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Trip creation draft';

-- Run this migration once on an existing database. MySQL versions that do not
-- support ADD COLUMN IF NOT EXISTS intentionally fail on a second execution so
-- operators notice that the schema has already been upgraded.
