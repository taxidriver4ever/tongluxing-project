-- 同路行 P0 主链路升级脚本
-- 适用范围：已有数据库增量升级。空库请直接执行 01_reset_and_create_all_tables.sql。

ALTER TABLE trip
    MODIFY COLUMN vehicle_id BIGINT NULL COMMENT '发布者车辆ID；乘客需求为空',
    ADD COLUMN trip_type VARCHAR(24) NOT NULL DEFAULT 'DRIVER_TRIP' COMMENT '行程类型：DRIVER_TRIP车主行程、PASSENGER_DEMAND乘客需求' AFTER user_id,
    ADD COLUMN publisher_role VARCHAR(16) NOT NULL DEFAULT 'DRIVER' COMMENT '发布者身份：DRIVER、PASSENGER' AFTER trip_type,
    ADD COLUMN captain_user_id BIGINT NULL COMMENT '当前队长用户ID；乘客需求未匹配时为空' AFTER publisher_role,
    ADD COLUMN auto_start_enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否到点自动出发' AFTER status,
    ADD COLUMN arrival_status VARCHAR(24) NOT NULL DEFAULT 'NOT_ARRIVED' COMMENT '到达状态：NOT_ARRIVED、DWELLING、AWAITING_DECISION、CONTINUING、ENDED' AFTER auto_start_enabled,
    ADD COLUMN arrival_entered_at DATETIME NULL COMMENT '首次进入终点5公里范围时间' AFTER arrival_status,
    ADD COLUMN arrival_decision_deadline DATETIME NULL COMMENT '到达后最迟处理时间' AFTER arrival_entered_at,
    ADD COLUMN continue_count INT NOT NULL DEFAULT 0 COMMENT '继续行程次数' AFTER arrival_decision_deadline,
    ADD KEY idx_trip_captain_status (captain_user_id, status, departure_time),
    ADD KEY idx_trip_auto_start (auto_start_enabled, status, departure_time),
    ADD KEY idx_trip_arrival_deadline (arrival_status, arrival_decision_deadline);

UPDATE trip
SET trip_type = 'DRIVER_TRIP',
    publisher_role = 'DRIVER',
    captain_user_id = user_id
WHERE captain_user_id IS NULL AND vehicle_id IS NOT NULL;

ALTER TABLE team
    ADD COLUMN recruitment_status VARCHAR(16) NOT NULL DEFAULT 'OPEN' COMMENT '招募状态：OPEN、PAUSED、CLOSED' AFTER join_mode,
    ADD COLUMN allow_midway_join TINYINT(1) NOT NULL DEFAULT 0 COMMENT '行进中是否允许申请加入' AFTER recruitment_status,
    ADD COLUMN deviation_warning_distance_m INT NOT NULL DEFAULT 50000 COMMENT '一级脱队距离阈值，米' AFTER allow_midway_join,
    ADD COLUMN deviation_warning_minutes INT NOT NULL DEFAULT 30 COMMENT '一级脱队持续时间，分钟' AFTER deviation_warning_distance_m,
    ADD COLUMN severe_deviation_distance_m INT NOT NULL DEFAULT 100000 COMMENT '严重脱队距离阈值，米' AFTER deviation_warning_minutes,
    ADD COLUMN severe_deviation_minutes INT NOT NULL DEFAULT 60 COMMENT '严重脱队持续时间，分钟' AFTER severe_deviation_distance_m,
    ADD COLUMN missing_location_minutes INT NOT NULL DEFAULT 720 COMMENT '失联阈值，分钟' AFTER severe_deviation_minutes,
    ADD COLUMN join_radius_m INT NOT NULL DEFAULT 100000 COMMENT '出发/途中加入范围阈值，米' AFTER missing_location_minutes,
    ADD COLUMN privacy_level VARCHAR(24) NOT NULL DEFAULT 'STANDARD' COMMENT '成员资料公开级别：OPEN、STANDARD、PRIVATE' AFTER join_radius_m,
    ADD KEY idx_team_recruitment (team_status, recruitment_status, allow_midway_join);

ALTER TABLE team_member
    ADD COLUMN linked_owner_user_id BIGINT NULL COMMENT '同车关联车主用户ID' AFTER vehicle_id,
    ADD COLUMN linked_vehicle_id BIGINT NULL COMMENT '同车关联车辆ID' AFTER linked_owner_user_id,
    ADD COLUMN plate_reference VARCHAR(24) NULL COMMENT '用户输入的车牌关联值，仅保存脱敏值' AFTER linked_vehicle_id,
    ADD COLUMN owner_confirm_status VARCHAR(20) NOT NULL DEFAULT 'NOT_REQUIRED' COMMENT '车主确认状态：NOT_REQUIRED、PENDING、CONFIRMED、REJECTED' AFTER plate_reference,
    ADD COLUMN removed_by_user_id BIGINT NULL COMMENT '移除该成员的队长用户ID' AFTER owner_confirm_status,
    ADD COLUMN removed_reason VARCHAR(255) NULL COMMENT '移除原因' AFTER removed_by_user_id,
    ADD KEY idx_team_member_linked_vehicle (team_id, linked_vehicle_id, member_status),
    ADD KEY idx_team_member_external_active (user_id, member_role, member_status, deleted);

ALTER TABLE team_join_application
    ADD COLUMN application_type VARCHAR(16) NOT NULL DEFAULT 'JOIN' COMMENT '申请类型：JOIN、RETURN' AFTER applicant_vehicle_id,
    ADD COLUMN join_role VARCHAR(16) NOT NULL DEFAULT 'PASSENGER' COMMENT '申请身份：DRIVER、PASSENGER' AFTER application_type,
    ADD COLUMN linked_owner_user_id BIGINT NULL COMMENT '希望关联的车主用户ID' AFTER join_role,
    ADD COLUMN linked_vehicle_id BIGINT NULL COMMENT '希望关联的队内车辆ID' AFTER linked_owner_user_id,
    ADD COLUMN plate_reference VARCHAR(24) NULL COMMENT '手动输入车牌的脱敏值' AFTER linked_vehicle_id,
    ADD COLUMN current_latitude DECIMAL(10,6) NULL COMMENT '归队申请当前位置纬度' AFTER plate_reference,
    ADD COLUMN current_longitude DECIMAL(10,6) NULL COMMENT '归队申请当前位置经度' AFTER current_latitude,
    ADD COLUMN owner_confirm_status VARCHAR(20) NOT NULL DEFAULT 'NOT_REQUIRED' COMMENT '车主确认状态' AFTER current_longitude,
    ADD KEY idx_team_apply_type (team_id, application_type, application_status, created_at);

ALTER TABLE trip_member_distance_alert
    ADD COLUMN severe_started_at DATETIME NULL COMMENT '连续超过严重偏离阈值的开始时间' AFTER started_at,
    ADD COLUMN handled_action VARCHAR(24) NULL COMMENT '队长处理动作：IGNORE、REMOVE、WAIT、CONTINUE' AFTER acknowledged_at,
    ADD COLUMN handled_by_user_id BIGINT NULL COMMENT '处理人用户ID' AFTER handled_action,
    ADD COLUMN handled_at DATETIME NULL COMMENT '处理时间' AFTER handled_by_user_id,
    ADD KEY idx_trip_member_alert_level (trip_id, alert_level, recovered_at, handled_at);

CREATE TABLE IF NOT EXISTS trip_departure_exception (
    id BIGINT PRIMARY KEY COMMENT '记录主键',
    trip_id BIGINT NOT NULL COMMENT '行程ID',
    member_user_id BIGINT NOT NULL COMMENT '异常成员用户ID',
    distance_m INT NULL COMMENT '成员与队长距离，米',
    exception_type VARCHAR(32) NOT NULL COMMENT '异常类型：OUT_OF_RANGE、LOCATION_MISSING',
    exception_status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '处理状态：PENDING、WAITING、IGNORED、RESOLVED',
    handled_action VARCHAR(20) NULL COMMENT '处理动作：WAIT、CONTINUE',
    detected_at DATETIME NOT NULL COMMENT '检测时间',
    handled_at DATETIME NULL COMMENT '处理时间',
    created_at DATETIME NOT NULL COMMENT '创建时间',
    updated_at DATETIME NOT NULL COMMENT '更新时间',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    UNIQUE KEY uk_trip_departure_exception (trip_id, member_user_id, deleted),
    KEY idx_trip_departure_pending (trip_id, exception_status, detected_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='自动出发成员范围异常表';

-- 兼容曾执行过旧版迁移的数据库：每个行程成员只保留一条异常状态记录。
-- 先保留更新时间较新的记录，再更换唯一索引，避免旧版 PENDING/WAITING 重复数据阻断迁移。
DELETE older
FROM trip_departure_exception older
JOIN trip_departure_exception newer
  ON newer.trip_id = older.trip_id
 AND newer.member_user_id = older.member_user_id
 AND newer.deleted = older.deleted
 AND (newer.updated_at > older.updated_at
      OR (newer.updated_at = older.updated_at AND newer.id > older.id));
ALTER TABLE trip_departure_exception
    DROP INDEX uk_trip_departure_exception,
    ADD UNIQUE KEY uk_trip_departure_exception (trip_id, member_user_id, deleted);

CREATE TABLE IF NOT EXISTS trip_arrival_state (
    id BIGINT PRIMARY KEY COMMENT '记录主键',
    trip_id BIGINT NOT NULL COMMENT '行程ID',
    captain_user_id BIGINT NOT NULL COMMENT '队长用户ID',
    state VARCHAR(24) NOT NULL DEFAULT 'NOT_ARRIVED' COMMENT 'NOT_ARRIVED、DWELLING、AWAITING_DECISION、CONTINUING、ENDED',
    first_entered_at DATETIME NULL COMMENT '首次进入终点范围时间',
    prompted_at DATETIME NULL COMMENT '满足停留时间后的提示时间',
    decision_deadline DATETIME NULL COMMENT '超时自动结束时间',
    last_distance_m INT NULL COMMENT '最近一次到终点距离',
    decision_action VARCHAR(20) NULL COMMENT 'END、CONTINUE、AUTO_END',
    decided_at DATETIME NULL COMMENT '决策时间',
    created_at DATETIME NOT NULL COMMENT '创建时间',
    updated_at DATETIME NOT NULL COMMENT '更新时间',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    UNIQUE KEY uk_trip_arrival_state (trip_id, deleted),
    KEY idx_trip_arrival_deadline (state, decision_deadline)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='行程到达停留与开放式结束状态表';

CREATE TABLE IF NOT EXISTS app_push_device (
    id BIGINT PRIMARY KEY COMMENT '记录主键',
    user_id BIGINT NOT NULL COMMENT '平台用户ID',
    device_id VARCHAR(128) NOT NULL COMMENT '设备唯一标识',
    platform VARCHAR(16) NOT NULL COMMENT 'ANDROID、IOS',
    vendor VARCHAR(32) NOT NULL DEFAULT 'GENERIC' COMMENT '推送厂商：FCM、HUAWEI、XIAOMI、OPPO、VIVO、APNS、GENERIC',
    push_token VARCHAR(512) NOT NULL COMMENT '系统推送设备Token',
    app_version VARCHAR(32) NULL COMMENT 'App版本',
    enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
    last_seen_at DATETIME NOT NULL COMMENT '最近活跃时间',
    created_at DATETIME NOT NULL COMMENT '创建时间',
    updated_at DATETIME NOT NULL COMMENT '更新时间',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    UNIQUE KEY uk_push_device (user_id, device_id, deleted),
    KEY idx_push_device_user_enabled (user_id, enabled, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='App系统推送设备表';

CREATE TABLE IF NOT EXISTS app_push_task (
    id BIGINT PRIMARY KEY COMMENT '记录主键',
    user_id BIGINT NOT NULL COMMENT '接收用户ID',
    event_type VARCHAR(64) NOT NULL COMMENT '事件类型',
    title VARCHAR(128) NOT NULL COMMENT '推送标题',
    content VARCHAR(512) NOT NULL COMMENT '推送内容',
    payload_json JSON NULL COMMENT '客户端跳转参数',
    idempotency_key VARCHAR(128) NOT NULL COMMENT '幂等键',
    delivery_status VARCHAR(24) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING、SENT、FAILED、SKIPPED',
    retry_count INT NOT NULL DEFAULT 0 COMMENT '重试次数',
    next_retry_at DATETIME NULL COMMENT '下次重试时间',
    last_error VARCHAR(512) NULL COMMENT '最后错误',
    created_at DATETIME NOT NULL COMMENT '创建时间',
    updated_at DATETIME NOT NULL COMMENT '更新时间',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    UNIQUE KEY uk_push_task_idempotency (idempotency_key, deleted),
    KEY idx_push_task_delivery (delivery_status, next_retry_at, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='App系统推送任务表';
