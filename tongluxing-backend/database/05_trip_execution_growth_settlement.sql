-- 行程执行、实际轨迹结算和队友距离告警（修订版方案）
-- 规划路线只用于导航展示；以下结算字段只能由实际 GPS 轨迹产生。
-- 成长值：单次行程 floor(settlement_distance_m / 5000) * 1，不跨行程结转。

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='driver_track_record' AND column_name='device_id')=0,
  'ALTER TABLE driver_track_record ADD COLUMN device_id VARCHAR(128) NULL', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='driver_track_record' AND column_name='sequence_no')=0,
  'ALTER TABLE driver_track_record ADD COLUMN sequence_no BIGINT NULL', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='driver_track_record' AND column_name='mock_location')=0,
  'ALTER TABLE driver_track_record ADD COLUMN mock_location TINYINT NOT NULL DEFAULT 0', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='driver_track_record' AND column_name='point_status')=0,
  'ALTER TABLE driver_track_record ADD COLUMN point_status VARCHAR(32) NOT NULL DEFAULT ''VALID''', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='driver_track_record' AND column_name='valid_point')=0,
  'ALTER TABLE driver_track_record ADD COLUMN valid_point TINYINT NOT NULL DEFAULT 1', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS trip_mileage_settlement (
  id BIGINT NOT NULL PRIMARY KEY,
  trip_id BIGINT NOT NULL,
  raw_gps_distance_m INT NOT NULL DEFAULT 0,
  matched_road_distance_m INT NOT NULL DEFAULT 0,
  estimated_gap_distance_m INT NOT NULL DEFAULT 0,
  settlement_distance_m INT NOT NULL DEFAULT 0,
  track_coverage_rate INT NOT NULL DEFAULT 0,
  estimated_ratio INT NOT NULL DEFAULT 0,
  quality_status VARCHAR(32) NOT NULL,
  settlement_status VARCHAR(32) NOT NULL,
  growth_value INT NOT NULL DEFAULT 0,
  reason VARCHAR(255) NULL,
  settled_at DATETIME NULL,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  deleted TINYINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_trip_mileage_settlement (trip_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS trip_member_distance_alert (
  id BIGINT NOT NULL PRIMARY KEY,
  trip_id BIGINT NOT NULL,
  captain_user_id BIGINT NOT NULL,
  member_user_id BIGINT NOT NULL,
  alert_level VARCHAR(24) NOT NULL,
  distance_m INT NOT NULL,
  started_at DATETIME NOT NULL,
  notified_at DATETIME NULL,
  recovered_at DATETIME NULL,
  acknowledged_at DATETIME NULL,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  deleted TINYINT NOT NULL DEFAULT 0,
  KEY idx_trip_member_alert_active (trip_id, member_user_id, recovered_at, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS trip_execution (
  id BIGINT NOT NULL PRIMARY KEY,
  trip_id BIGINT NOT NULL,
  captain_user_id BIGINT NOT NULL,
  status VARCHAR(32) NOT NULL,
  planned_distance_m INT NOT NULL DEFAULT 0,
  raw_gps_distance_m INT NOT NULL DEFAULT 0,
  matched_road_distance_m INT NOT NULL DEFAULT 0,
  estimated_gap_distance_m INT NOT NULL DEFAULT 0,
  settlement_distance_m INT NOT NULL DEFAULT 0,
  started_at DATETIME NULL,
  ended_at DATETIME NULL,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  deleted TINYINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_trip_execution_trip (trip_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS trip_execution_member (
  id BIGINT NOT NULL PRIMARY KEY,
  execution_id BIGINT NOT NULL,
  trip_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  member_role VARCHAR(24) NOT NULL,
  member_status VARCHAR(32) NOT NULL,
  ready_at DATETIME NULL,
  joined_execution_at DATETIME NULL,
  left_at DATETIME NULL,
  eligible_flag TINYINT NOT NULL DEFAULT 0,
  ineligible_reason VARCHAR(128) NULL,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  deleted TINYINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_execution_member (execution_id, user_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS trip_track_point (
  id BIGINT NOT NULL PRIMARY KEY,
  execution_id BIGINT NOT NULL,
  trip_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  device_id VARCHAR(128) NULL,
  sequence_no BIGINT NULL,
  longitude DECIMAL(10,6) NOT NULL,
  latitude DECIMAL(10,6) NOT NULL,
  altitude DECIMAL(10,2) NULL,
  accuracy DECIMAL(10,2) NULL,
  speed DECIMAL(10,2) NULL,
  bearing DECIMAL(10,2) NULL,
  provider VARCHAR(16) NOT NULL DEFAULT 'fused',
  app_state VARCHAR(16) NOT NULL DEFAULT 'foreground',
  battery_level INT NULL,
  located_at DATETIME NOT NULL,
  client_send_time DATETIME NULL,
  server_receive_time DATETIME NULL,
  mock_location TINYINT NOT NULL DEFAULT 0,
  point_status VARCHAR(32) NOT NULL,
  valid_point TINYINT NOT NULL DEFAULT 1,
  risk_score INT NOT NULL DEFAULT 0,
  risk_flags VARCHAR(255) NULL,
  reject_reason VARCHAR(255) NULL,
  calculated_speed_kmh DECIMAL(10,2) NOT NULL DEFAULT 0,
  raw_distance_from_previous_m INT NOT NULL DEFAULT 0,
  distance_from_previous_m INT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL,
  deleted TINYINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_track_device_sequence (execution_id, user_id, device_id, sequence_no),
  KEY idx_track_execution_time (execution_id, located_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS trip_route_plan_version (
  id BIGINT NOT NULL PRIMARY KEY,
  execution_id BIGINT NOT NULL,
  trip_id BIGINT NOT NULL,
  version_no INT NOT NULL,
  route_polyline LONGTEXT NOT NULL,
  planned_distance_m INT NOT NULL DEFAULT 0,
  required_waypoints_json JSON NULL,
  effective_at DATETIME NOT NULL,
  created_by BIGINT NOT NULL,
  created_at DATETIME NOT NULL,
  deleted TINYINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_execution_route_version (execution_id, version_no, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS trip_waypoint_arrival (
  id BIGINT NOT NULL PRIMARY KEY,
  execution_id BIGINT NOT NULL,
  trip_id BIGINT NOT NULL,
  waypoint_id BIGINT NULL,
  arrival_type VARCHAR(24) NOT NULL,
  user_id BIGINT NOT NULL,
  first_inside_at DATETIME NOT NULL,
  confirmed_at DATETIME NOT NULL,
  evidence_point_count INT NOT NULL,
  distance_m INT NOT NULL,
  created_at DATETIME NOT NULL,
  deleted TINYINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_execution_waypoint_arrival (execution_id, waypoint_id, arrival_type, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS trip_track_source_switch (
  id BIGINT NOT NULL PRIMARY KEY,
  execution_id BIGINT NOT NULL,
  from_user_id BIGINT NULL,
  to_user_id BIGINT NOT NULL,
  switch_reason VARCHAR(64) NOT NULL,
  switched_at DATETIME NOT NULL,
  created_at DATETIME NOT NULL,
  deleted TINYINT NOT NULL DEFAULT 0,
  KEY idx_track_source_execution (execution_id, switched_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


CREATE TABLE IF NOT EXISTS trip_track_summary (
  id BIGINT PRIMARY KEY,
  trip_id BIGINT NOT NULL,
  primary_user_id BIGINT NOT NULL,
  raw_distance_meters INT NOT NULL DEFAULT 0,
  filtered_distance_meters INT NOT NULL DEFAULT 0,
  approved_distance_meters INT NOT NULL DEFAULT 0,
  total_point_count INT NOT NULL DEFAULT 0,
  valid_point_count INT NOT NULL DEFAULT 0,
  invalid_point_count INT NOT NULL DEFAULT 0,
  location_gap_count INT NOT NULL DEFAULT 0,
  warning_count INT NOT NULL DEFAULT 0,
  hard_anomaly_count INT NOT NULL DEFAULT 0,
  risk_score INT NOT NULL DEFAULT 0,
  risk_level VARCHAR(16) NOT NULL DEFAULT 'LOW',
  settlement_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  review_reason VARCHAR(255) NULL,
  reviewer_id BIGINT NULL,
  reviewed_at DATETIME NULL,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  deleted TINYINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_trip_track_summary_trip (trip_id, deleted),
  KEY idx_trip_track_summary_risk (risk_level, settlement_status, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS trip_track_anomaly (
  id BIGINT PRIMARY KEY,
  trip_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  previous_point_id BIGINT NULL,
  current_point_id BIGINT NULL,
  anomaly_type VARCHAR(64) NOT NULL,
  risk_score INT NOT NULL DEFAULT 0,
  detail_json JSON NULL,
  occurred_at DATETIME NOT NULL,
  created_at DATETIME NOT NULL,
  KEY idx_trip_track_anomaly_trip_time (trip_id, occurred_at),
  KEY idx_trip_track_anomaly_user_time (user_id, occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
