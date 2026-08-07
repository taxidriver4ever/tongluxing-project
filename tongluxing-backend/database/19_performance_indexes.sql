-- 同路行轨迹 / 结算 / 成员查询性能索引
-- 适用场景：已有数据库不执行 01_reset_and_create_all_tables.sql 时，执行本文件一次即可。
-- 可重复执行：索引已存在时自动跳过。

USE tongluxing;
SET NAMES utf8mb4;

DROP PROCEDURE IF EXISTS add_tlx_index_if_missing;
DELIMITER $$
CREATE PROCEDURE add_tlx_index_if_missing(
    IN p_table_name VARCHAR(64),
    IN p_index_name VARCHAR(64),
    IN p_ddl TEXT
)
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = p_table_name
          AND index_name = p_index_name
    ) THEN
        SET @tlx_index_ddl = p_ddl;
        PREPARE tlx_stmt FROM @tlx_index_ddl;
        EXECUTE tlx_stmt;
        DEALLOCATE PREPARE tlx_stmt;
    END IF;
END$$
DELIMITER ;

CALL add_tlx_index_if_missing(
    'trip_member_snapshot',
    'idx_member_trip_user_status',
    'ALTER TABLE trip_member_snapshot ADD INDEX idx_member_trip_user_status (trip_id, user_id, join_status)'
);

CALL add_tlx_index_if_missing(
    'driver_track_record',
    'idx_driver_track_trip_driver_time',
    'ALTER TABLE driver_track_record ADD INDEX idx_driver_track_trip_driver_time (trip_id, driver_id, deleted, record_time)'
);

CALL add_tlx_index_if_missing(
    'driver_track_record',
    'idx_driver_track_trip_driver_valid_time',
    'ALTER TABLE driver_track_record ADD INDEX idx_driver_track_trip_driver_valid_time (trip_id, driver_id, deleted, valid_point, record_time)'
);

CALL add_tlx_index_if_missing(
    'trip_track_anomaly',
    'idx_trip_track_anomaly_trip_user_type_time',
    'ALTER TABLE trip_track_anomaly ADD INDEX idx_trip_track_anomaly_trip_user_type_time (trip_id, user_id, anomaly_type, occurred_at)'
);

CALL add_tlx_index_if_missing(
    'trip_execution_member',
    'idx_execution_member_trip_eligible',
    'ALTER TABLE trip_execution_member ADD INDEX idx_execution_member_trip_eligible (trip_id, deleted, eligible_flag, member_status, user_id)'
);

DROP PROCEDURE IF EXISTS add_tlx_index_if_missing;

SELECT 'performance indexes ready' AS result;
