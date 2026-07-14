USE travel_memory;

CREATE TABLE IF NOT EXISTS memory_draft (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  user_id BIGINT NOT NULL COMMENT 'Draft owner user ID',
  trip_id BIGINT NOT NULL COMMENT 'Trip ID',
  memory_id BIGINT DEFAULT NULL COMMENT 'Edited Memory ID; null for a new Memory',
  draft_key VARCHAR(80) NOT NULL COMMENT 'create:{tripId} or edit:{memoryId}',
  content VARCHAR(300) DEFAULT NULL COMMENT 'Draft text content',
  location_name VARCHAR(255) DEFAULT NULL COMMENT 'Draft location name',
  record_time DATETIME DEFAULT NULL COMMENT 'Draft record time',
  latitude DECIMAL(10, 7) DEFAULT NULL COMMENT 'Draft latitude',
  longitude DECIMAL(10, 7) DEFAULT NULL COMMENT 'Draft longitude',
  photo_urls TEXT DEFAULT NULL COMMENT 'Newline-separated protected upload URLs',
  companion_ids TEXT DEFAULT NULL COMMENT 'Comma-separated Trip companion IDs',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_memory_draft_user_key (user_id, draft_key),
  KEY idx_memory_draft_trip_update (trip_id, update_time),
  KEY idx_memory_draft_memory (memory_id),
  CONSTRAINT fk_memory_draft_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE,
  CONSTRAINT fk_memory_draft_trip FOREIGN KEY (trip_id) REFERENCES travel_trip (id) ON DELETE CASCADE,
  CONSTRAINT fk_memory_draft_memory FOREIGN KEY (memory_id) REFERENCES travel_memory (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Memory form drafts';
