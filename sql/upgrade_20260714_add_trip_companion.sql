-- Travel Memory companion upgrade.
-- Safe to run more than once on MySQL 8.x. Existing Trip and Memory data is unchanged.

CREATE TABLE IF NOT EXISTS trip_companion (
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

CREATE TABLE IF NOT EXISTS memory_companion (
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
