USE travel_memory;

ALTER TABLE travel_memory
  ADD COLUMN is_favorite TINYINT NOT NULL DEFAULT 0 COMMENT 'Favorite flag: 0 no, 1 yes'
  AFTER record_time;

CREATE INDEX idx_travel_memory_is_favorite
  ON travel_memory (is_favorite);
