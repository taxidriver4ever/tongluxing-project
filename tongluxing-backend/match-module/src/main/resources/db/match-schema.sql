-- match-module 初始化表结构。
-- 匹配结果和行为日志属于推荐派生数据；行程与车队真值仍由对应业务模块维护。

-- 行程路线快照：在行程发布/更新时固化匹配所需的最小路线字段，避免推荐重算依赖
-- 其他模块不断变化的完整聚合。trip_id + deleted 保证每条行程只有一个有效快照。
CREATE TABLE IF NOT EXISTS match_route_snapshot (
  id BIGINT NOT NULL comment '记录主键',
  trip_id BIGINT NOT NULL comment '行程ID',
  user_id BIGINT NOT NULL comment '平台用户ID',
  vehicle_id BIGINT NOT NULL comment '车辆ID',
  start_name VARCHAR(128) NOT NULL comment '起点名称',
  start_address VARCHAR(255) NULL comment '起点地址',
  start_latitude DECIMAL(10,6) NOT NULL comment '起点纬度',
  start_longitude DECIMAL(10,6) NOT NULL comment '起点经度',
  end_name VARCHAR(128) NOT NULL comment '终点名称',
  end_address VARCHAR(255) NULL comment '终点地址',
  end_latitude DECIMAL(10,6) NOT NULL comment '终点纬度',
  end_longitude DECIMAL(10,6) NOT NULL comment '终点经度',
  -- 路线折线或途经点 JSON，用于未来更精细的路线重合计算。
  route_points_json JSON NULL comment '路线坐标点JSON数据',
  route_distance INT NULL comment '路线距离',
  route_duration INT NULL comment '路线时长',
  departure_time DATETIME NOT NULL comment '出发时间',
  travel_depth VARCHAR(16) NOT NULL comment '出行深度',
  max_vehicle_count INT NOT NULL comment '最高车辆数量',
  public_flag TINYINT(1) NOT NULL DEFAULT 1 comment '是否公开：0否、1是',
  snapshot_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' comment '快照状态',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP comment '记录创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP comment '记录最后更新时间',
  deleted TINYINT(1) NOT NULL DEFAULT 0 comment '逻辑删除标记：0未删除、1已删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_match_snapshot_trip (trip_id, deleted),
  -- 优化公开、有效、指定出发时间窗口的候选扫描。
  KEY idx_match_snapshot_public_time (public_flag, snapshot_status, departure_time),
  -- 优化起终点坐标范围粗筛；精确距离仍由应用层球面公式计算。
  KEY idx_match_snapshot_start_end (start_latitude, start_longitude, end_latitude, end_longitude),
  KEY idx_match_snapshot_user (user_id, snapshot_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 comment='匹配路线快照表';

-- 有方向的预计算推荐：source 是用户基准行程，target 是被推荐行程。
-- 同一 source/target 重算时执行 upsert，只刷新评分与计算时间。
CREATE TABLE IF NOT EXISTS match_result (
  id BIGINT NOT NULL comment '记录主键',
  source_trip_id BIGINT NOT NULL comment '来源行程ID',
  target_trip_id BIGINT NOT NULL comment '目标行程ID',
  source_user_id BIGINT NOT NULL comment '来源用户ID',
  target_user_id BIGINT NOT NULL comment '目标用户ID',
  match_score INT NOT NULL comment '匹配评分',
  overlap_rate INT NOT NULL comment '重合比例',
  distance_gap_meters INT NULL comment '距离差值，单位为米',
  departure_gap_minutes INT NULL comment '出发差值，单位为分钟',
  -- 保存路线、终点、时间、兴趣等评分分项，供解释与运营调优。
  score_detail_json JSON NULL comment '评分明细JSON数据',
  result_status VARCHAR(20) NOT NULL DEFAULT 'VALID' comment '结果状态',
  calculated_at DATETIME NOT NULL comment '计算完成时间',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP comment '记录创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP comment '记录最后更新时间',
  deleted TINYINT(1) NOT NULL DEFAULT 0 comment '逻辑删除标记：0未删除、1已删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_match_pair (source_trip_id, target_trip_id, deleted),
  -- 支持用户按源行程读取 VALID 结果并按分数排序。
  KEY idx_match_source_score (source_trip_id, result_status, match_score),
  KEY idx_match_target (target_trip_id, result_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 comment='匹配结果表';

-- 推荐漏斗事件：记录曝光、点击、申请、审核、开始和完成等只追加行为。
CREATE TABLE IF NOT EXISTS match_recommend_log (
  id BIGINT NOT NULL comment '记录主键',
  user_id BIGINT NOT NULL comment '平台用户ID',
  trip_id BIGINT NOT NULL comment '行程ID',
  target_trip_id BIGINT NULL comment '目标行程ID',
  target_team_id BIGINT NULL comment '目标队伍ID',
  scene VARCHAR(32) NOT NULL comment '业务场景',
  action_type VARCHAR(32) NOT NULL comment '操作类型',
  -- 可保存 matchId，或 NEARBY:/SEARCH: 前缀标识无预计算结果的来源。
  request_id VARCHAR(64) NULL comment '请求唯一标识，用于链路追踪或幂等控制',
  extra_json JSON NULL comment '扩展业务信息JSON数据',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP comment '记录创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP comment '记录最后更新时间',
  deleted TINYINT(1) NOT NULL DEFAULT 0 comment '逻辑删除标记：0未删除、1已删除',
  PRIMARY KEY (id),
  KEY idx_match_log_user_time (user_id, created_at),
  KEY idx_match_log_trip_scene (trip_id, scene, action_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 comment='匹配推荐行为日志表';

-- 用户公开行程收藏关系。唯一键配合 INSERT IGNORE 实现重复收藏幂等。
CREATE TABLE IF NOT EXISTS trip_favorite (
  id BIGINT NOT NULL comment '记录主键',
  user_id BIGINT NOT NULL comment '平台用户ID',
  trip_id BIGINT NOT NULL comment '行程ID',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP comment '记录创建时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_trip_favorite_user_trip (user_id, trip_id),
  KEY idx_trip_favorite_user_time (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 comment='行程收藏表';

-- 受控行程咨询请求。互关或已入队用户直接沟通，不会写入此表；单向关注者写入
-- PENDING 请求，由唯一键阻止同一行程重复发送待处理咨询。
CREATE TABLE IF NOT EXISTS trip_consultation_request (
  id BIGINT NOT NULL comment '记录主键',
  trip_id BIGINT NOT NULL comment '行程ID',
  trip_title VARCHAR(128) NOT NULL comment '行程标题',
  sender_user_id BIGINT NOT NULL comment '发送人用户ID',
  receiver_user_id BIGINT NOT NULL comment '接收方用户ID',
  content VARCHAR(500) NOT NULL comment '正文内容',
  request_status VARCHAR(20) NOT NULL DEFAULT 'PENDING' comment '请求状态',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP comment '记录创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP comment '记录最后更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_trip_consult_pending (trip_id, sender_user_id, request_status),
  KEY idx_trip_consult_receiver (receiver_user_id, request_status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 comment='行程咨询请求表';

-- 队长评分汇总。推荐页使用 rating 进行 3.0 分过滤，使用 positive_rate 计算热度。
-- 好评率按 0~1 保存；尚无评价记录的队长由查询层使用 5.0 / 1.0 中性默认值。
CREATE TABLE IF NOT EXISTS trip_leader_rating_summary (
  leader_user_id BIGINT NOT NULL comment '队长用户ID',
  rating DECIMAL(3,2) NOT NULL DEFAULT 5.00 comment '队长综合评分，范围0~5',
  positive_rate DECIMAL(5,4) NOT NULL DEFAULT 1.0000 comment '好评率，范围0~1',
  rating_count INT NOT NULL DEFAULT 0 comment '有效评价数量',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP comment '汇总更新时间',
  deleted TINYINT(1) NOT NULL DEFAULT 0 comment '逻辑删除标记',
  PRIMARY KEY (leader_user_id),
  KEY idx_trip_leader_rating (rating, positive_rate)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 comment='行程队长评分汇总表';

-- App 行程搜索历史：同一用户、关键词和搜索类型只保留一条。
CREATE TABLE IF NOT EXISTS trip_search_history (
    id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    keyword VARCHAR(80) NOT NULL,
    search_type VARCHAR(24) NOT NULL DEFAULT 'DESTINATION',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_trip_search_history_user_keyword_type (user_id, keyword, search_type),
    KEY idx_trip_search_history_user_updated (user_id, updated_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
