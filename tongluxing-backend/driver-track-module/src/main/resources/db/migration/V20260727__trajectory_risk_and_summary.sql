-- 同路行 MVP v3 轨迹风控增量迁移（MySQL 8，可重复执行）

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='driver_track_record' AND column_name='altitude')=0, 'ALTER TABLE driver_track_record ADD COLUMN altitude DECIMAL(10,2) NULL AFTER latitude', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='driver_track_record' AND column_name='raw_distance_from_prev')=0, 'ALTER TABLE driver_track_record ADD COLUMN raw_distance_from_prev INT NOT NULL DEFAULT 0 AFTER accuracy', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='driver_track_record' AND column_name='calculated_speed_kmh')=0, 'ALTER TABLE driver_track_record ADD COLUMN calculated_speed_kmh DECIMAL(10,2) NOT NULL DEFAULT 0 AFTER distance_from_prev', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='driver_track_record' AND column_name='provider')=0, 'ALTER TABLE driver_track_record ADD COLUMN provider VARCHAR(16) NOT NULL DEFAULT ''fused'' AFTER calculated_speed_kmh', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='driver_track_record' AND column_name='app_state')=0, 'ALTER TABLE driver_track_record ADD COLUMN app_state VARCHAR(16) NOT NULL DEFAULT ''foreground'' AFTER provider', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='driver_track_record' AND column_name='battery_level')=0, 'ALTER TABLE driver_track_record ADD COLUMN battery_level INT NULL AFTER app_state', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='driver_track_record' AND column_name='device_id')=0, 'ALTER TABLE driver_track_record ADD COLUMN device_id VARCHAR(128) NULL AFTER battery_level', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='driver_track_record' AND column_name='sequence_no')=0, 'ALTER TABLE driver_track_record ADD COLUMN sequence_no BIGINT NULL AFTER device_id', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='driver_track_record' AND column_name='mock_location')=0, 'ALTER TABLE driver_track_record ADD COLUMN mock_location TINYINT NOT NULL DEFAULT 0 AFTER sequence_no', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='driver_track_record' AND column_name='point_status')=0, 'ALTER TABLE driver_track_record ADD COLUMN point_status VARCHAR(32) NOT NULL DEFAULT ''ACCEPTED'' AFTER mock_location', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='driver_track_record' AND column_name='valid_point')=0, 'ALTER TABLE driver_track_record ADD COLUMN valid_point TINYINT NOT NULL DEFAULT 1 AFTER point_status', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='driver_track_record' AND column_name='risk_score')=0, 'ALTER TABLE driver_track_record ADD COLUMN risk_score INT NOT NULL DEFAULT 0 AFTER valid_point', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='driver_track_record' AND column_name='risk_flags')=0, 'ALTER TABLE driver_track_record ADD COLUMN risk_flags VARCHAR(255) NULL AFTER risk_score', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='driver_track_record' AND column_name='reject_reason')=0, 'ALTER TABLE driver_track_record ADD COLUMN reject_reason VARCHAR(255) NULL AFTER risk_flags', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='driver_track_record' AND column_name='client_send_time')=0, 'ALTER TABLE driver_track_record ADD COLUMN client_send_time DATETIME NULL AFTER record_time', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='driver_track_record' AND column_name='server_receive_time')=0, 'ALTER TABLE driver_track_record ADD COLUMN server_receive_time DATETIME NULL AFTER client_send_time', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 旧版本可能已经产生重复 sequence_no。创建唯一索引前先保留每组最早的一条，
-- 其余历史重复点保留原始坐标证据，但不再参与有效里程和抵达判断。
-- sequence_no 置为 NULL 后，MySQL 唯一索引仍允许保留这些历史记录。
UPDATE driver_track_record duplicate_record
JOIN driver_track_record canonical_record
  ON canonical_record.trip_id = duplicate_record.trip_id
 AND canonical_record.driver_id = duplicate_record.driver_id
 AND canonical_record.sequence_no = duplicate_record.sequence_no
 AND canonical_record.deleted = duplicate_record.deleted
 AND canonical_record.id < duplicate_record.id
SET duplicate_record.sequence_no = NULL,
    duplicate_record.point_status = 'REJECTED',
    duplicate_record.valid_point = 0,
    duplicate_record.risk_score = GREATEST(COALESCE(duplicate_record.risk_score, 0), 2),
    duplicate_record.risk_flags = CASE
      WHEN duplicate_record.risk_flags IS NULL OR duplicate_record.risk_flags = ''
        THEN 'DUPLICATE_POINT'
      WHEN duplicate_record.risk_flags LIKE '%DUPLICATE_POINT%'
        THEN duplicate_record.risk_flags
      ELSE CONCAT(duplicate_record.risk_flags, ',DUPLICATE_POINT')
    END,
    duplicate_record.reject_reason = COALESCE(
      NULLIF(duplicate_record.reject_reason, ''),
      '历史重复 sequenceNo，数据库迁移时标记为无效点'
    )
WHERE duplicate_record.sequence_no IS NOT NULL;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='driver_track_record' AND index_name='uk_driver_track_sequence')=0,
  'CREATE UNIQUE INDEX uk_driver_track_sequence ON driver_track_record(trip_id, driver_id, sequence_no, deleted)', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

UPDATE driver_track_record
SET server_receive_time = COALESCE(server_receive_time, created_at),
    point_status = CASE WHEN point_status = 'VALID' THEN 'ACCEPTED' ELSE point_status END,
    raw_distance_from_prev = CASE WHEN raw_distance_from_prev = 0 THEN distance_from_prev ELSE raw_distance_from_prev END
WHERE server_receive_time IS NULL OR point_status = 'VALID' OR raw_distance_from_prev = 0;

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
