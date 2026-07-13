-- Compatible with MySQL 8.0 versions that do not support ALTER TABLE ... ADD COLUMN IF NOT EXISTS.
-- Safe to re-run: each schema change is guarded by information_schema checks.
-- Do not run sql/init.sql on production data.
-- Existing trips and legacy /uploads/yyyy/MM files become owned by the initial administrator (id=1).
CREATE TABLE IF NOT EXISTS app_user (
  id BIGINT NOT NULL AUTO_INCREMENT,
  username VARCHAR(64) DEFAULT NULL,
  display_name VARCHAR(100) NOT NULL,
  password_hash VARCHAR(100) NOT NULL,
  role VARCHAR(16) NOT NULL,
  enabled TINYINT NOT NULL DEFAULT 1,
  must_change_password TINYINT NOT NULL DEFAULT 0,
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
VALUES (1, NULL, 'Initial administrator', '!', 'ADMIN', 0, 1)
ON DUPLICATE KEY UPDATE id = id;

SET @column_exists = (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'travel_trip' AND column_name = 'user_id'
);
SET @sql = IF(@column_exists = 0,
  'ALTER TABLE travel_trip ADD COLUMN user_id BIGINT NULL COMMENT ''Owner user ID'' AFTER id',
  'SELECT 1');
PREPARE statement FROM @sql;
EXECUTE statement;
DEALLOCATE PREPARE statement;

UPDATE travel_trip SET user_id = 1 WHERE user_id IS NULL;
ALTER TABLE travel_trip MODIFY COLUMN user_id BIGINT NOT NULL COMMENT 'Owner user ID';

SET @index_exists = (
  SELECT COUNT(*) FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'travel_trip'
    AND index_name = 'idx_travel_trip_user_deleted_start'
);
SET @sql = IF(@index_exists = 0,
  'ALTER TABLE travel_trip ADD INDEX idx_travel_trip_user_deleted_start (user_id, deleted, start_date)',
  'SELECT 1');
PREPARE statement FROM @sql;
EXECUTE statement;
DEALLOCATE PREPARE statement;

SET @foreign_key_exists = (
  SELECT COUNT(*) FROM information_schema.table_constraints
  WHERE constraint_schema = DATABASE() AND table_name = 'travel_trip'
    AND constraint_name = 'fk_travel_trip_user' AND constraint_type = 'FOREIGN KEY'
);
SET @sql = IF(@foreign_key_exists = 0,
  'ALTER TABLE travel_trip ADD CONSTRAINT fk_travel_trip_user FOREIGN KEY (user_id) REFERENCES app_user (id)',
  'SELECT 1');
PREPARE statement FROM @sql;
EXECUTE statement;
DEALLOCATE PREPARE statement;
