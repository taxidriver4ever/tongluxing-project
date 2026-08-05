-- 同路行 MVP v3 自升级脚本：由 Spring SQL init 每次启动幂等执行。

-- P0 行程状态机字段。trip-schema.sql 只能保证新库结构；旧库必须在启动时幂等补列。
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='trip' AND column_name='trip_type')=0,
  'ALTER TABLE trip ADD COLUMN trip_type VARCHAR(24) NOT NULL DEFAULT ''DRIVER_TRIP'' COMMENT ''行程类型：DRIVER_TRIP、PASSENGER_DEMAND'' AFTER user_id', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='trip' AND column_name='publisher_role')=0,
  'ALTER TABLE trip ADD COLUMN publisher_role VARCHAR(16) NOT NULL DEFAULT ''DRIVER'' COMMENT ''发布身份：DRIVER、PASSENGER'' AFTER trip_type', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='trip' AND column_name='captain_user_id')=0,
  'ALTER TABLE trip ADD COLUMN captain_user_id BIGINT NULL COMMENT ''当前队长用户ID'' AFTER publisher_role', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='trip' AND column_name='auto_start_enabled')=0,
  'ALTER TABLE trip ADD COLUMN auto_start_enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT ''是否到点自动出发'' AFTER status', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='trip' AND column_name='arrival_status')=0,
  'ALTER TABLE trip ADD COLUMN arrival_status VARCHAR(24) NOT NULL DEFAULT ''NOT_ARRIVED'' COMMENT ''到达状态'' AFTER auto_start_enabled', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='trip' AND column_name='arrival_entered_at')=0,
  'ALTER TABLE trip ADD COLUMN arrival_entered_at DATETIME NULL COMMENT ''首次进入终点范围时间'' AFTER arrival_status', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='trip' AND column_name='arrival_decision_deadline')=0,
  'ALTER TABLE trip ADD COLUMN arrival_decision_deadline DATETIME NULL COMMENT ''到达后最迟处理时间'' AFTER arrival_entered_at', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='trip' AND column_name='continue_count')=0,
  'ALTER TABLE trip ADD COLUMN continue_count INT NOT NULL DEFAULT 0 COMMENT ''继续行程次数'' AFTER arrival_decision_deadline', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='trip' AND column_name='vehicle_id' AND is_nullable='NO')=1,
  'ALTER TABLE trip MODIFY COLUMN vehicle_id BIGINT NULL COMMENT ''发布者车辆ID；乘客需求为空''', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 仅修正迁移前的旧数据，不在每次启动时覆盖已经形成的乘客需求/队长关系。
UPDATE trip
SET trip_type = CASE
        WHEN (trip_type IS NULL OR trip_type = '' OR (trip_type = 'DRIVER_TRIP' AND vehicle_id IS NULL AND captain_user_id IS NULL))
            THEN CASE WHEN vehicle_id IS NULL THEN 'PASSENGER_DEMAND' ELSE 'DRIVER_TRIP' END
        ELSE trip_type
    END,
    publisher_role = CASE
        WHEN publisher_role IS NULL OR publisher_role = ''
             OR (publisher_role = 'DRIVER' AND vehicle_id IS NULL AND captain_user_id IS NULL)
            THEN CASE WHEN vehicle_id IS NULL THEN 'PASSENGER' ELSE 'DRIVER' END
        ELSE publisher_role
    END,
    captain_user_id = CASE
        WHEN vehicle_id IS NOT NULL AND captain_user_id IS NULL THEN user_id
        ELSE captain_user_id
    END,
    auto_start_enabled = CASE
        WHEN vehicle_id IS NULL AND captain_user_id IS NULL THEN 0
        ELSE COALESCE(auto_start_enabled, 1)
    END,
    arrival_status = COALESCE(NULLIF(arrival_status, ''), 'NOT_ARRIVED'),
    continue_count = COALESCE(continue_count, 0)
WHERE deleted = 0
  AND (
      trip_type IS NULL OR trip_type = ''
      OR publisher_role IS NULL OR publisher_role = ''
      OR (trip_type = 'DRIVER_TRIP' AND vehicle_id IS NULL AND captain_user_id IS NULL)
      OR (publisher_role = 'DRIVER' AND vehicle_id IS NULL AND captain_user_id IS NULL)
      OR (vehicle_id IS NOT NULL AND captain_user_id IS NULL)
      OR arrival_status IS NULL OR arrival_status = ''
      OR continue_count IS NULL
      OR (vehicle_id IS NULL AND captain_user_id IS NULL AND auto_start_enabled <> 0)
  );

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='trip' AND index_name='idx_trip_captain_status')=0,
  'CREATE INDEX idx_trip_captain_status ON trip(captain_user_id, status, departure_time)', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='trip' AND index_name='idx_trip_auto_start')=0,
  'CREATE INDEX idx_trip_auto_start ON trip(auto_start_enabled, status, departure_time)', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='trip' AND index_name='idx_trip_arrival_deadline')=0,
  'CREATE INDEX idx_trip_arrival_deadline ON trip(arrival_status, arrival_decision_deadline)', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS trip_execution (
  id BIGINT PRIMARY KEY comment '记录主键',
  trip_id BIGINT NOT NULL comment '行程ID',
  captain_user_id BIGINT NOT NULL comment '行程队长用户ID',
  status VARCHAR(32) NOT NULL comment '业务状态',
  planned_distance_m INT NOT NULL DEFAULT 0 comment '计划行程距离，单位为米',
  raw_gps_distance_m INT NOT NULL DEFAULT 0 comment '原始GPS距离，单位为米',
  matched_road_distance_m INT NOT NULL DEFAULT 0 comment '命中道路距离，单位为米',
  estimated_gap_distance_m INT NOT NULL DEFAULT 0 comment '估算差值距离，单位为米',
  settlement_distance_m INT NOT NULL DEFAULT 0 comment '结算距离，单位为米',
  started_at DATETIME NULL comment '行程执行开始时间',
  ended_at DATETIME NULL comment '行程执行结束时间',
  created_at DATETIME NOT NULL comment '记录创建时间',
  updated_at DATETIME NOT NULL comment '记录最后更新时间',
  deleted TINYINT NOT NULL DEFAULT 0 comment '逻辑删除标记：0未删除、1已删除',
  UNIQUE KEY uk_trip_execution_trip (trip_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 comment='行程执行表';

CREATE TABLE IF NOT EXISTS trip_execution_member (
  id BIGINT PRIMARY KEY comment '记录主键',
  execution_id BIGINT NOT NULL comment '行程执行记录ID',
  trip_id BIGINT NOT NULL comment '行程ID',
  user_id BIGINT NOT NULL comment '平台用户ID',
  member_role VARCHAR(24) NOT NULL comment '成员角色',
  member_status VARCHAR(32) NOT NULL comment '成员状态',
  ready_at DATETIME NULL comment '准备时间',
  joined_execution_at DATETIME NULL comment '加入行程执行时间',
  left_at DATETIME NULL comment '离开时间',
  eligible_flag TINYINT NOT NULL DEFAULT 0 comment '是否符合条件：0否、1是',
  ineligible_reason VARCHAR(128) NULL comment '不符合条件原因',
  created_at DATETIME NOT NULL comment '记录创建时间',
  updated_at DATETIME NOT NULL comment '记录最后更新时间',
  deleted TINYINT NOT NULL DEFAULT 0 comment '逻辑删除标记：0未删除、1已删除',
  UNIQUE KEY uk_execution_member (execution_id, user_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 comment='行程执行成员表';

CREATE TABLE IF NOT EXISTS trip_track_point (
  id BIGINT PRIMARY KEY comment '记录主键',
  execution_id BIGINT NOT NULL comment '行程执行记录ID',
  trip_id BIGINT NOT NULL comment '行程ID',
  user_id BIGINT NOT NULL comment '平台用户ID',
  device_id VARCHAR(128) NULL comment '设备ID',
  sequence_no BIGINT NULL comment '序号编号',
  longitude DECIMAL(10,6) NOT NULL comment '经度坐标',
  latitude DECIMAL(10,6) NOT NULL comment '纬度坐标',
  altitude DECIMAL(10,2) NULL comment '海拔',
  accuracy DECIMAL(10,2) NULL comment '定位精度',
  speed DECIMAL(10,2) NULL comment '速度',
  bearing DECIMAL(10,2) NULL comment '方向角',
  provider VARCHAR(16) NOT NULL DEFAULT 'fused' comment '数据或服务提供方',
  app_state VARCHAR(16) NOT NULL DEFAULT 'foreground' comment '应用状态',
  battery_level INT NULL comment '设备剩余电量百分比',
  located_at DATETIME NOT NULL comment '定位时间',
  client_send_time DATETIME NULL comment '客户端发送时间',
  server_receive_time DATETIME NULL comment '服务端接收时间',
  mock_location TINYINT NOT NULL DEFAULT 0 comment '是否疑似模拟定位：0否、1是',
  point_status VARCHAR(32) NOT NULL comment '轨迹点状态',
  valid_point TINYINT NOT NULL DEFAULT 1 comment '是否为有效轨迹点：0否、1是',
  risk_score INT NOT NULL DEFAULT 0 comment '风险评分',
  risk_flags VARCHAR(255) NULL comment '风险标记集合',
  reject_reason VARCHAR(255) NULL comment '驳回原因',
  calculated_speed_kmh DECIMAL(10,2) NOT NULL DEFAULT 0 comment '计算速度，单位为公里每小时',
  raw_distance_from_previous_m INT NOT NULL DEFAULT 0 comment '原始与上一轨迹点的距离，单位为米',
  distance_from_previous_m INT NOT NULL DEFAULT 0 comment '与上一轨迹点的距离，单位为米',
  created_at DATETIME NOT NULL comment '记录创建时间',
  deleted TINYINT NOT NULL DEFAULT 0 comment '逻辑删除标记：0未删除、1已删除',
  UNIQUE KEY uk_track_device_sequence (execution_id, user_id, device_id, sequence_no),
  KEY idx_track_execution_time (execution_id, located_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 comment='行程轨迹轨迹点表';

CREATE TABLE IF NOT EXISTS trip_waypoint_arrival (
  id BIGINT PRIMARY KEY comment '记录主键',
  execution_id BIGINT NOT NULL comment '行程执行记录ID',
  trip_id BIGINT NOT NULL comment '行程ID',
  waypoint_id BIGINT NULL comment '途经点ID',
  arrival_type VARCHAR(24) NOT NULL comment '到达类型',
  user_id BIGINT NOT NULL comment '平台用户ID',
  first_inside_at DATETIME NOT NULL comment '首次进入途经点范围时间',
  confirmed_at DATETIME NOT NULL comment '确认完成时间',
  evidence_point_count INT NOT NULL comment '证据轨迹点数量',
  distance_m INT NOT NULL comment '距离，单位为米',
  created_at DATETIME NOT NULL comment '记录创建时间',
  deleted TINYINT NOT NULL DEFAULT 0 comment '逻辑删除标记：0未删除、1已删除',
  UNIQUE KEY uk_execution_waypoint_arrival (execution_id, waypoint_id, arrival_type, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 comment='行程途经点到达记录表';

CREATE TABLE IF NOT EXISTS trip_mileage_settlement (
  id BIGINT PRIMARY KEY comment '记录主键',
  trip_id BIGINT NOT NULL comment '行程ID',
  raw_gps_distance_m INT NOT NULL DEFAULT 0 comment '原始GPS距离，单位为米',
  matched_road_distance_m INT NOT NULL DEFAULT 0 comment '命中道路距离，单位为米',
  estimated_gap_distance_m INT NOT NULL DEFAULT 0 comment '估算差值距离，单位为米',
  settlement_distance_m INT NOT NULL DEFAULT 0 comment '结算距离，单位为米',
  track_coverage_rate INT NOT NULL DEFAULT 0 comment '轨迹覆盖率比例',
  estimated_ratio INT NOT NULL DEFAULT 0 comment '估算比例',
  quality_status VARCHAR(32) NOT NULL comment '质量状态',
  settlement_status VARCHAR(32) NOT NULL comment '结算状态',
  growth_value INT NOT NULL DEFAULT 0 comment '成长值值',
  reason VARCHAR(255) NULL comment '原因说明',
  settled_at DATETIME NULL comment '结算完成时间',
  created_at DATETIME NOT NULL comment '记录创建时间',
  updated_at DATETIME NOT NULL comment '记录最后更新时间',
  deleted TINYINT NOT NULL DEFAULT 0 comment '逻辑删除标记：0未删除、1已删除',
  UNIQUE KEY uk_trip_mileage_settlement (trip_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 comment='行程里程结算表';

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='invite_relation' AND column_name='request_id')=0, 'ALTER TABLE invite_relation ADD COLUMN request_id VARCHAR(64) NULL COMMENT ''请求唯一标识，用于链路追踪或幂等控制'' AFTER bind_source_value', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='driver_track_record' AND column_name='altitude')=0, 'ALTER TABLE driver_track_record ADD COLUMN altitude DECIMAL(10,2) NULL COMMENT ''海拔'' AFTER latitude', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='driver_track_record' AND column_name='raw_distance_from_prev')=0, 'ALTER TABLE driver_track_record ADD COLUMN raw_distance_from_prev INT NOT NULL DEFAULT 0 COMMENT ''原始与上一轨迹点的距离'' AFTER accuracy', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='driver_track_record' AND column_name='calculated_speed_kmh')=0, 'ALTER TABLE driver_track_record ADD COLUMN calculated_speed_kmh DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT ''计算速度，单位为公里每小时'' AFTER distance_from_prev', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='driver_track_record' AND column_name='provider')=0, 'ALTER TABLE driver_track_record ADD COLUMN provider VARCHAR(16) NOT NULL DEFAULT ''fused'' COMMENT ''数据或服务提供方'' AFTER calculated_speed_kmh', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='driver_track_record' AND column_name='app_state')=0, 'ALTER TABLE driver_track_record ADD COLUMN app_state VARCHAR(16) NOT NULL DEFAULT ''foreground'' COMMENT ''应用状态'' AFTER provider', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='driver_track_record' AND column_name='battery_level')=0, 'ALTER TABLE driver_track_record ADD COLUMN battery_level INT NULL COMMENT ''设备剩余电量百分比'' AFTER app_state', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='driver_track_record' AND column_name='device_id')=0, 'ALTER TABLE driver_track_record ADD COLUMN device_id VARCHAR(128) NULL COMMENT ''设备ID'' AFTER battery_level', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='driver_track_record' AND column_name='sequence_no')=0, 'ALTER TABLE driver_track_record ADD COLUMN sequence_no BIGINT NULL COMMENT ''序号编号'' AFTER device_id', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='driver_track_record' AND column_name='mock_location')=0, 'ALTER TABLE driver_track_record ADD COLUMN mock_location TINYINT NOT NULL DEFAULT 0 COMMENT ''是否疑似模拟定位：0否、1是'' AFTER sequence_no', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='driver_track_record' AND column_name='point_status')=0, 'ALTER TABLE driver_track_record ADD COLUMN point_status VARCHAR(32) NOT NULL DEFAULT ''ACCEPTED'' COMMENT ''轨迹点状态'' AFTER mock_location', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='driver_track_record' AND column_name='valid_point')=0, 'ALTER TABLE driver_track_record ADD COLUMN valid_point TINYINT NOT NULL DEFAULT 1 COMMENT ''是否为有效轨迹点：0否、1是'' AFTER point_status', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='driver_track_record' AND column_name='risk_score')=0, 'ALTER TABLE driver_track_record ADD COLUMN risk_score INT NOT NULL DEFAULT 0 COMMENT ''风险评分'' AFTER valid_point', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='driver_track_record' AND column_name='risk_flags')=0, 'ALTER TABLE driver_track_record ADD COLUMN risk_flags VARCHAR(255) NULL COMMENT ''风险标记集合'' AFTER risk_score', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='driver_track_record' AND column_name='reject_reason')=0, 'ALTER TABLE driver_track_record ADD COLUMN reject_reason VARCHAR(255) NULL COMMENT ''驳回原因'' AFTER risk_flags', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='driver_track_record' AND column_name='client_send_time')=0, 'ALTER TABLE driver_track_record ADD COLUMN client_send_time DATETIME NULL COMMENT ''客户端发送时间'' AFTER record_time', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='driver_track_record' AND column_name='server_receive_time')=0, 'ALTER TABLE driver_track_record ADD COLUMN server_receive_time DATETIME NULL COMMENT ''服务端接收时间'' AFTER client_send_time', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='trip_track_point' AND column_name='altitude')=0, 'ALTER TABLE trip_track_point ADD COLUMN altitude DECIMAL(10,2) NULL COMMENT ''海拔'' AFTER latitude', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='trip_track_point' AND column_name='provider')=0, 'ALTER TABLE trip_track_point ADD COLUMN provider VARCHAR(16) NOT NULL DEFAULT ''fused'' COMMENT ''数据或服务提供方'' AFTER bearing', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='trip_track_point' AND column_name='app_state')=0, 'ALTER TABLE trip_track_point ADD COLUMN app_state VARCHAR(16) NOT NULL DEFAULT ''foreground'' COMMENT ''应用状态'' AFTER provider', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='trip_track_point' AND column_name='battery_level')=0, 'ALTER TABLE trip_track_point ADD COLUMN battery_level INT NULL COMMENT ''设备剩余电量百分比'' AFTER app_state', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='trip_track_point' AND column_name='client_send_time')=0, 'ALTER TABLE trip_track_point ADD COLUMN client_send_time DATETIME NULL COMMENT ''客户端发送时间'' AFTER located_at', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='trip_track_point' AND column_name='server_receive_time')=0, 'ALTER TABLE trip_track_point ADD COLUMN server_receive_time DATETIME NULL COMMENT ''服务端接收时间'' AFTER client_send_time', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='trip_track_point' AND column_name='risk_score')=0, 'ALTER TABLE trip_track_point ADD COLUMN risk_score INT NOT NULL DEFAULT 0 COMMENT ''风险评分'' AFTER valid_point', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='trip_track_point' AND column_name='risk_flags')=0, 'ALTER TABLE trip_track_point ADD COLUMN risk_flags VARCHAR(255) NULL COMMENT ''风险标记集合'' AFTER risk_score', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='trip_track_point' AND column_name='reject_reason')=0, 'ALTER TABLE trip_track_point ADD COLUMN reject_reason VARCHAR(255) NULL COMMENT ''驳回原因'' AFTER risk_flags', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='trip_track_point' AND column_name='calculated_speed_kmh')=0, 'ALTER TABLE trip_track_point ADD COLUMN calculated_speed_kmh DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT ''计算速度，单位为公里每小时'' AFTER reject_reason', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;


SET @ddl = IF((SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='invite_relation' AND index_name='uk_invite_relation_request')=0,
  'CREATE UNIQUE INDEX uk_invite_relation_request ON invite_relation(request_id, deleted)', 'SELECT 1');
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

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='invite_reward_record' AND index_name='uk_invite_reward_stage')>0,
  'ALTER TABLE invite_reward_record DROP INDEX uk_invite_reward_stage', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='invite_reward_record' AND index_name='uk_invite_reward_relation_rule')=0,
  'CREATE UNIQUE INDEX uk_invite_reward_relation_rule ON invite_reward_record(relation_id, rule_code, deleted)', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

UPDATE driver_track_record
SET server_receive_time=COALESCE(server_receive_time, created_at),
    point_status=CASE WHEN point_status='VALID' THEN 'ACCEPTED' ELSE point_status END,
    raw_distance_from_prev=CASE WHEN raw_distance_from_prev=0 THEN distance_from_prev ELSE raw_distance_from_prev END
WHERE server_receive_time IS NULL OR point_status='VALID' OR raw_distance_from_prev=0;

CREATE TABLE IF NOT EXISTS trip_track_summary (
    id BIGINT PRIMARY KEY comment '记录主键',
    trip_id BIGINT NOT NULL comment '行程ID',
    primary_user_id BIGINT NOT NULL comment '主要用户ID',
    raw_distance_meters INT NOT NULL DEFAULT 0 comment '原始距离，单位为米',
    filtered_distance_meters INT NOT NULL DEFAULT 0 comment '过滤后距离，单位为米',
    approved_distance_meters INT NOT NULL DEFAULT 0 comment '审核认可距离，单位为米',
    total_point_count INT NOT NULL DEFAULT 0 comment '总计轨迹点数量',
    valid_point_count INT NOT NULL DEFAULT 0 comment '有效轨迹点数量',
    invalid_point_count INT NOT NULL DEFAULT 0 comment '无效轨迹点数量',
    location_gap_count INT NOT NULL DEFAULT 0 comment '位置差值数量',
    warning_count INT NOT NULL DEFAULT 0 comment '警告数量',
    hard_anomaly_count INT NOT NULL DEFAULT 0 comment '严重异常数量',
    risk_score INT NOT NULL DEFAULT 0 comment '风险评分',
    risk_level VARCHAR(16) NOT NULL DEFAULT 'LOW' comment '风险等级',
    settlement_status VARCHAR(32) NOT NULL DEFAULT 'PENDING' comment '结算状态',
    review_reason VARCHAR(255) NULL comment '审核原因',
    reviewer_id BIGINT NULL comment '审核人ID',
    reviewed_at DATETIME NULL comment '审核时间',
    created_at DATETIME NOT NULL comment '记录创建时间',
    updated_at DATETIME NOT NULL comment '记录最后更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 comment '逻辑删除标记：0未删除、1已删除',
    UNIQUE KEY uk_trip_track_summary_trip (trip_id, deleted),
    KEY idx_trip_track_summary_risk (risk_level, settlement_status, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 comment='行程轨迹汇总表';

CREATE TABLE IF NOT EXISTS trip_track_anomaly (
    id BIGINT PRIMARY KEY comment '记录主键',
    trip_id BIGINT NOT NULL comment '行程ID',
    user_id BIGINT NOT NULL comment '平台用户ID',
    previous_point_id BIGINT NULL comment '上一点轨迹点ID',
    current_point_id BIGINT NULL comment '当前轨迹点ID',
    anomaly_type VARCHAR(64) NOT NULL comment '异常类型',
    risk_score INT NOT NULL DEFAULT 0 comment '风险评分',
    detail_json JSON NULL comment '业务详情JSON数据',
    occurred_at DATETIME NOT NULL comment '发生时间',
    created_at DATETIME NOT NULL comment '记录创建时间',
    KEY idx_trip_track_anomaly_trip_time (trip_id, occurred_at),
    KEY idx_trip_track_anomaly_user_time (user_id, occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 comment='行程轨迹异常表';

CREATE TABLE IF NOT EXISTS invite_reward_rule (
    id BIGINT PRIMARY KEY comment '记录主键',
    rule_code VARCHAR(64) NOT NULL comment '规则编码',
    reward_type VARCHAR(32) NOT NULL DEFAULT 'GROWTH_VALUE' comment '奖励类型',
    reward_value INT NOT NULL comment '奖励值',
    status VARCHAR(16) NOT NULL DEFAULT 'ENABLED' comment '业务状态',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP comment '记录创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP comment '记录最后更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 comment '逻辑删除标记：0未删除、1已删除',
    UNIQUE KEY uk_invite_reward_rule_code (rule_code, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 comment='邀请奖励规则表';

INSERT IGNORE INTO invite_reward_rule
(id, rule_code, reward_type, reward_value, status, created_at, updated_at, deleted)
VALUES
(202607270001, 'INVITE_REGISTER_SUCCESS', 'GROWTH_VALUE', 50, 'ENABLED', NOW(), NOW(), 0),
(202607270002, 'INVITEE_FIRST_TEAM_COMPLETED', 'GROWTH_VALUE', 100, 'ENABLED', NOW(), NOW(), 0),
(202607270003, 'INVITE_STAGE_3', 'GROWTH_VALUE', 200, 'ENABLED', NOW(), NOW(), 0),
(202607270004, 'INVITE_STAGE_10', 'GROWTH_VALUE', 500, 'ENABLED', NOW(), NOW(), 0),
(202607270005, 'INVITE_STAGE_30', 'GROWTH_VALUE', 2000, 'ENABLED', NOW(), NOW(), 0),
(202607270006, 'INVITE_STAGE_50', 'GROWTH_VALUE', 5000, 'ENABLED', NOW(), NOW(), 0);

UPDATE invite_reward_record SET reward_status='ISSUED' WHERE reward_status='GRANTED' AND deleted=0;


-- 补齐邀请码奖励审计与幂等字段。
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='invite_reward_record' AND column_name='inviter_user_id')=0, 'ALTER TABLE invite_reward_record ADD COLUMN inviter_user_id BIGINT NULL COMMENT ''邀请人用户ID'' AFTER beneficiary_user_id', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='invite_reward_record' AND column_name='invitee_user_id')=0, 'ALTER TABLE invite_reward_record ADD COLUMN invitee_user_id BIGINT NULL COMMENT ''被邀请人用户ID'' AFTER inviter_user_id', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='invite_reward_record' AND column_name='reward_rule_code')=0, 'ALTER TABLE invite_reward_record ADD COLUMN reward_rule_code VARCHAR(64) NULL COMMENT ''奖励规则编码'' AFTER rule_code', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='invite_reward_record' AND column_name='reward_type')=0, 'ALTER TABLE invite_reward_record ADD COLUMN reward_type VARCHAR(32) NOT NULL DEFAULT ''GROWTH_VALUE'' COMMENT ''奖励类型'' AFTER reward_rule_code', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='invite_reward_record' AND column_name='reward_value')=0, 'ALTER TABLE invite_reward_record ADD COLUMN reward_value INT NOT NULL DEFAULT 0 COMMENT ''奖励值'' AFTER reward_type', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='invite_reward_record' AND column_name='idempotency_key')=0, 'ALTER TABLE invite_reward_record ADD COLUMN idempotency_key VARCHAR(128) NULL COMMENT ''业务幂等键，用于防止重复处理'' AFTER reward_biz_no', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='invite_reward_record' AND column_name='triggered_at')=0, 'ALTER TABLE invite_reward_record ADD COLUMN triggered_at DATETIME NULL COMMENT ''奖励触发时间'' AFTER reward_status', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='invite_reward_record' AND column_name='issued_at')=0, 'ALTER TABLE invite_reward_record ADD COLUMN issued_at DATETIME NULL COMMENT ''发放时间'' AFTER granted_at', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

UPDATE invite_reward_record r
JOIN invite_relation rel ON rel.id=r.relation_id
LEFT JOIN invite_reward_rule rr ON rr.rule_code=r.rule_code AND rr.deleted=0
SET r.inviter_user_id=COALESCE(r.inviter_user_id, rel.inviter_user_id),
    r.invitee_user_id=COALESCE(r.invitee_user_id, rel.invitee_user_id),
    r.reward_rule_code=COALESCE(r.reward_rule_code, r.rule_code),
    r.reward_type=COALESCE(r.reward_type, rr.reward_type, 'GROWTH_VALUE'),
    r.reward_value=CASE WHEN r.reward_value=0 THEN COALESCE(rr.reward_value, 0) ELSE r.reward_value END,
    r.idempotency_key=COALESCE(r.idempotency_key, r.reward_biz_no),
    r.triggered_at=COALESCE(r.triggered_at, r.created_at),
    r.issued_at=COALESCE(r.issued_at, r.granted_at)
WHERE r.deleted=0;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='invite_reward_record' AND index_name='uk_invite_reward_idempotency')=0,
  'CREATE UNIQUE INDEX uk_invite_reward_idempotency ON invite_reward_record(idempotency_key, deleted)', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
