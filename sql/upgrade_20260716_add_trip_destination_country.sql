USE travel_memory;

SET @travel_trip_country_exists = (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'travel_trip'
    AND COLUMN_NAME = 'destination_country'
);
SET @travel_trip_country_sql = IF(
  @travel_trip_country_exists = 0,
  'ALTER TABLE travel_trip ADD COLUMN destination_country VARCHAR(100) DEFAULT NULL COMMENT ''Destination country parsed from selected city'' AFTER destination',
  'SELECT 1'
);
PREPARE travel_trip_country_stmt FROM @travel_trip_country_sql;
EXECUTE travel_trip_country_stmt;
DEALLOCATE PREPARE travel_trip_country_stmt;

SET @trip_draft_country_exists = (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'trip_draft'
    AND COLUMN_NAME = 'destination_country'
);
SET @trip_draft_country_sql = IF(
  @trip_draft_country_exists = 0,
  'ALTER TABLE trip_draft ADD COLUMN destination_country VARCHAR(100) DEFAULT NULL COMMENT ''Draft destination country parsed from selected city'' AFTER destination',
  'SELECT 1'
);
PREPARE trip_draft_country_stmt FROM @trip_draft_country_sql;
EXECUTE trip_draft_country_stmt;
DEALLOCATE PREPARE trip_draft_country_stmt;

-- Existing trips remain unchanged. A country is stored only after the user
-- confirms a city candidate or map selection in the create/edit form.
