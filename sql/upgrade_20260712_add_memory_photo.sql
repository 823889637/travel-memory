USE travel_memory;

CREATE TABLE IF NOT EXISTS memory_photo (
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

SET @drop_photo_path = (
  SELECT IF(COUNT(*) = 1, 'ALTER TABLE memory_photo DROP COLUMN photo_path', 'SELECT 1')
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'memory_photo' AND column_name = 'photo_path'
);
PREPARE drop_photo_path_statement FROM @drop_photo_path;
EXECUTE drop_photo_path_statement;
DEALLOCATE PREPARE drop_photo_path_statement;

INSERT INTO memory_photo (memory_id, photo_url, sort_order)
SELECT memory.id, memory.photo_url, 1
FROM travel_memory memory
WHERE memory.photo_url IS NOT NULL
  AND TRIM(memory.photo_url) <> ''
  AND NOT EXISTS (
    SELECT 1 FROM memory_photo photo WHERE photo.memory_id = memory.id
  );
