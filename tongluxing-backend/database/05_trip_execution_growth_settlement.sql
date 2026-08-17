-- 行程执行、实际轨迹结算和队友距离告警（修订版方案）
-- 规划路线只用于导航展示；以下结算字段只能由实际 GPS 轨迹产生。
-- 成长值：单次行程 floor(settlement_distance_m / 5000) * 1，不跨行程结转。

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='driver_track_record' AND column_name='device_id')=0,
  'ALTER TABLE driver_track_record ADD COLUMN device_id VARCHAR(128) NULL COMMENT ''设备ID''', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='driver_track_record' AND column_name='sequence_no')=0,
  'ALTER TABLE driver_track_record ADD COLUMN sequence_no BIGINT NULL COMMENT ''序号编号''', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='driver_track_record' AND column_name='mock_location')=0,
  'ALTER TABLE driver_track_record ADD COLUMN mock_location TINYINT NOT NULL DEFAULT 0 COMMENT ''是否疑似模拟定位：0否、1是''', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='driver_track_record' AND column_name='point_status')=0,
  'ALTER TABLE driver_track_record ADD COLUMN point_status VARCHAR(32) NOT NULL DEFAULT ''VALID'' COMMENT ''轨迹点状态''', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='driver_track_record' AND column_name='valid_point')=0,
  'ALTER TABLE driver_track_record ADD COLUMN valid_point TINYINT NOT NULL DEFAULT 1 COMMENT ''是否为有效轨迹点：0否、1是''', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS trip_mileage_settlement (
  id BIGINT NOT NULL PRIMARY KEY comment '记录主键',
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

CREATE TABLE IF NOT EXISTS trip_member_distance_alert (
  id BIGINT NOT NULL PRIMARY KEY comment '记录主键',
  trip_id BIGINT NOT NULL comment '关联行程ID',
  captain_user_id BIGINT NOT NULL comment '行程队长用户ID',
  member_user_id BIGINT NOT NULL comment '行程成员用户ID',
  alert_level VARCHAR(24) NOT NULL comment '成员距离告警等级',
  distance_m INT NOT NULL comment '成员距离，单位为米',
  started_at DATETIME NOT NULL comment '告警开始时间',
  notified_at DATETIME NULL comment '告警通知时间',
  recovered_at DATETIME NULL comment '距离恢复正常时间',
  acknowledged_at DATETIME NULL comment '告警确认时间',
  created_at DATETIME NOT NULL comment '记录创建时间',
  updated_at DATETIME NOT NULL comment '记录最后更新时间',
  deleted TINYINT NOT NULL DEFAULT 0 comment '逻辑删除标记：0未删除、1已删除',
  KEY idx_trip_member_alert_active (trip_id, member_user_id, recovered_at, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 comment='trip_member_distance_alert业务表';

CREATE TABLE IF NOT EXISTS trip_execution (
  id BIGINT NOT NULL PRIMARY KEY comment '记录主键',
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
  id BIGINT NOT NULL PRIMARY KEY comment '记录主键',
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
  id BIGINT NOT NULL PRIMARY KEY comment '记录主键',
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

CREATE TABLE IF NOT EXISTS trip_route_plan_version (
  id BIGINT NOT NULL PRIMARY KEY comment '记录主键',
  execution_id BIGINT NOT NULL comment '行程执行记录ID',
  trip_id BIGINT NOT NULL comment '关联行程ID',
  version_no INT NOT NULL comment '路线规划版本号',
  route_polyline LONGTEXT NOT NULL comment '路线折线编码数据',
  planned_distance_m INT NOT NULL DEFAULT 0 comment '计划路线距离，单位为米',
  required_waypoints_json JSON NULL comment '必须经过的途经点列表JSON数据',
  effective_at DATETIME NOT NULL comment '路线版本生效时间',
  created_by BIGINT NOT NULL comment '路线版本创建人用户ID',
  created_at DATETIME NOT NULL comment '记录创建时间',
  deleted TINYINT NOT NULL DEFAULT 0 comment '逻辑删除标记：0未删除、1已删除',
  UNIQUE KEY uk_execution_route_version (execution_id, version_no, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 comment='trip_route_plan_version业务表';

CREATE TABLE IF NOT EXISTS trip_waypoint_arrival (
  id BIGINT NOT NULL PRIMARY KEY comment '记录主键',
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

CREATE TABLE IF NOT EXISTS trip_track_source_switch (
  id BIGINT NOT NULL PRIMARY KEY comment '记录主键',
  execution_id BIGINT NOT NULL comment '行程执行记录ID',
  from_user_id BIGINT NULL comment '切换前轨迹来源用户ID',
  to_user_id BIGINT NOT NULL comment '切换后轨迹来源用户ID',
  switch_reason VARCHAR(64) NOT NULL comment '轨迹来源切换原因',
  switched_at DATETIME NOT NULL comment '轨迹来源切换时间',
  created_at DATETIME NOT NULL comment '记录创建时间',
  deleted TINYINT NOT NULL DEFAULT 0 comment '逻辑删除标记：0未删除、1已删除',
  KEY idx_track_source_execution (execution_id, switched_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='行程轨迹来源切换记录表';


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
  track_quality VARCHAR(20) NOT NULL DEFAULT 'NORMAL' comment '客户端轨迹完整度',
  raw_point_count INT NOT NULL DEFAULT 0 comment '客户端原始采集点数',
  uploaded_point_count INT NOT NULL DEFAULT 0 comment '确认上传点数',
  compressed_point_count INT NOT NULL DEFAULT 0 comment '客户端主动压缩点数',
  client_degraded_segment_count INT NOT NULL DEFAULT 0 comment '客户端降级路段数',
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
