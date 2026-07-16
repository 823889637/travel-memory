USE travel_memory;

ALTER TABLE travel_trip
  ADD COLUMN destination_latitude DECIMAL(10, 7) DEFAULT NULL
    COMMENT 'Destination city center latitude (WGS84)' AFTER destination,
  ADD COLUMN destination_longitude DECIMAL(10, 7) DEFAULT NULL
    COMMENT 'Destination city center longitude (WGS84)' AFTER destination_latitude;

ALTER TABLE trip_draft
  ADD COLUMN destination_latitude DECIMAL(10, 7) DEFAULT NULL
    COMMENT 'Draft destination city center latitude (WGS84)' AFTER destination,
  ADD COLUMN destination_longitude DECIMAL(10, 7) DEFAULT NULL
    COMMENT 'Draft destination city center longitude (WGS84)' AFTER destination_latitude;

-- Run this migration once on an existing database. Existing trips keep their
-- destination text and receive no coordinates until the user selects a city.
