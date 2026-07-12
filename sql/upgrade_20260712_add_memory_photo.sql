USE travel_memory;

CREATE TABLE memory_photo (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  memory_id BIGINT NOT NULL COMMENT 'Memory ID',
  photo_url VARCHAR(255) NOT NULL COMMENT 'Photo access URL',
  photo_path VARCHAR(255) DEFAULT NULL COMMENT 'Local photo path',
  sort_order INT NOT NULL DEFAULT 1 COMMENT 'Display order, first is primary',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Create time',
  PRIMARY KEY (id),
  KEY idx_memory_photo_memory_sort (memory_id, sort_order),
  CONSTRAINT fk_memory_photo_memory
    FOREIGN KEY (memory_id) REFERENCES travel_memory (id)
    ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Memory photos';

INSERT INTO memory_photo (memory_id, photo_url, photo_path, sort_order)
SELECT memory.id, memory.photo_url, memory.photo_path, 1
FROM travel_memory memory
WHERE memory.photo_url IS NOT NULL
  AND TRIM(memory.photo_url) <> ''
  AND NOT EXISTS (
    SELECT 1 FROM memory_photo photo WHERE photo.memory_id = memory.id
  );
