CREATE TABLE IF NOT EXISTS growth_account (
  id BIGINT NOT NULL comment '记录主键',
  user_id BIGINT NOT NULL comment '平台用户ID',
  total_points INT NOT NULL DEFAULT 0 comment '累计成长值',
  level_code VARCHAR(16) NOT NULL DEFAULT 'LV1' comment '等级编码',
  version INT NOT NULL DEFAULT 0 comment '版本',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP comment '记录创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP comment '记录最后更新时间',
  deleted TINYINT NOT NULL DEFAULT 0 comment '逻辑删除标记：0未删除、1已删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_growth_account_user (user_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 comment='成长值账号表';
CREATE TABLE IF NOT EXISTS growth_log (
  id BIGINT NOT NULL comment '记录主键',
  user_id BIGINT NOT NULL comment '平台用户ID',
  biz_type VARCHAR(32) NOT NULL comment '关联业务类型',
  biz_id VARCHAR(64) NOT NULL comment '关联业务单据标识',
  point_delta INT NOT NULL comment '本次成长值变动值',
  balance_after INT NOT NULL comment '本次成长值变动后的余额',
  remark VARCHAR(255) NULL comment '业务备注',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP comment '记录创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP comment '记录最后更新时间',
  deleted TINYINT NOT NULL DEFAULT 0 comment '逻辑删除标记：0未删除、1已删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_growth_log_biz (biz_type, biz_id, user_id),
  KEY idx_growth_log_user_time (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 comment='成长值流水表';
CREATE TABLE IF NOT EXISTS growth_level_rule (
  id BIGINT NOT NULL comment '记录主键',
  level_code VARCHAR(16) NOT NULL comment '等级编码',
  level_name VARCHAR(32) NOT NULL comment '等级名称',
  min_points INT NOT NULL comment '等级所需最低成长值',
  max_points INT NULL comment '等级所需最高成长值',
  benefit_json JSON NOT NULL comment '权益JSON数据',
  enabled_flag TINYINT NOT NULL DEFAULT 1 comment '是否启用：0否、1是',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP comment '记录创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP comment '记录最后更新时间',
  deleted TINYINT NOT NULL DEFAULT 0 comment '逻辑删除标记：0未删除、1已删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_growth_level_code (level_code, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 comment='成长值等级规则表';
CREATE TABLE IF NOT EXISTS growth_badge (
  id BIGINT NOT NULL comment '记录主键',
  badge_code VARCHAR(32) NOT NULL comment '徽章编码',
  badge_name VARCHAR(64) NOT NULL comment '徽章名称',
  badge_image_key VARCHAR(512) NULL comment '徽章图片标识或存储Key',
  condition_description VARCHAR(128) NOT NULL comment '条件说明',
  event_type VARCHAR(32) NOT NULL comment '事件类型',
  threshold INT NOT NULL comment '业务达成阈值',
  enabled_flag TINYINT NOT NULL DEFAULT 1 comment '是否启用：0否、1是',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP comment '记录创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP comment '记录最后更新时间',
  deleted TINYINT NOT NULL DEFAULT 0 comment '逻辑删除标记：0未删除、1已删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_growth_badge_code (badge_code, deleted),
  KEY idx_growth_badge_event_threshold (event_type, enabled_flag, deleted, threshold)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 comment='成长值徽章表';
CREATE TABLE IF NOT EXISTS growth_user_badge (
  id BIGINT NOT NULL comment '记录主键',
  user_id BIGINT NOT NULL comment '平台用户ID',
  badge_id BIGINT NOT NULL comment '徽章ID',
  source_biz_id VARCHAR(64) NULL comment '来源业务ID',
  awarded_at DATETIME NOT NULL comment '获得时间',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP comment '记录创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP comment '记录最后更新时间',
  deleted TINYINT NOT NULL DEFAULT 0 comment '逻辑删除标记：0未删除、1已删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_growth_user_badge (user_id, badge_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 comment='成长值用户徽章表';

INSERT IGNORE INTO growth_level_rule (
  id, level_code, level_name, min_points, max_points, benefit_json, enabled_flag, deleted
) VALUES
  (900001, 'LV1', '同路新手', 0, 499, JSON_OBJECT(), 1, 0),
  (900002, 'LV2', '同路行者', 500, 1999, JSON_OBJECT(), 1, 0),
  (900003, 'LV3', '同路先锋', 2000, 4999, JSON_OBJECT(), 1, 0),
  (900004, 'LV4', '同路达人', 5000, 9999, JSON_OBJECT(), 1, 0),
  (900005, 'LV5', '同路大使', 10000, 19999, JSON_OBJECT(), 1, 0),
  (900006, 'LV6', '同路传奇', 20000, NULL, JSON_OBJECT(), 1, 0);

INSERT IGNORE INTO growth_badge (
  id, badge_code, badge_name, badge_image_key, condition_description, event_type, threshold, enabled_flag, deleted
) VALUES
  (910001, 'FIRST_DEPARTURE', '第一次出发', NULL, '完成首次组队行程', 'TEAM_TRIP_COMPLETED', 1, 1, 0),
  (910002, 'DISTANCE_100KM', '百公里', NULL, '累计有效行驶 100 公里', 'TRIP_MILEAGE', 10, 1, 0),
  (910003, 'DISTANCE_1000KM', '千里马', NULL, '累计有效行驶 1000 公里', 'TRIP_MILEAGE', 100, 1, 0),
  (910004, 'DISTANCE_10000KM', '万里行', NULL, '累计有效行驶 10000 公里', 'TRIP_MILEAGE', 1000, 1, 0),
  (910005, 'INVITE_10', '社交达人', NULL, '成功邀请 10 位新用户注册', 'INVITE_USER_REGISTER', 10, 1, 0),
  (910006, 'INVITE_50', '人气王', NULL, '成功邀请 50 位新用户注册', 'INVITE_USER_REGISTER', 50, 1, 0),
  (910007, 'HELP_10', '助人为乐', NULL, '完成 10 次互助', 'HELP_COMPLETED', 10, 1, 0),
  (910008, 'HELP_50', '救急先锋', NULL, '完成 50 次互助', 'HELP_COMPLETED', 50, 1, 0),
  (910009, 'ROUTE_G318', '318勇士', NULL, '完成 G318 川藏线路线', 'ROUTE_G318_COMPLETED', 1, 1, 0),
  (910010, 'FOUR_SEASONS', '四季行者', NULL, '春夏秋冬各完成一次组队', 'FOUR_SEASONS_TRIP', 1, 1, 0);

UPDATE growth_badge SET condition_description = '完成首次组队行程', event_type = 'TEAM_TRIP_COMPLETED', threshold = 1 WHERE badge_code = 'FIRST_DEPARTURE' AND deleted = 0;
UPDATE growth_badge SET condition_description = '累计有效行驶 100 公里', event_type = 'TRIP_MILEAGE', threshold = 10 WHERE badge_code = 'DISTANCE_100KM' AND deleted = 0;
UPDATE growth_badge SET condition_description = '累计有效行驶 1000 公里', event_type = 'TRIP_MILEAGE', threshold = 100 WHERE badge_code = 'DISTANCE_1000KM' AND deleted = 0;
UPDATE growth_badge SET condition_description = '累计有效行驶 10000 公里', event_type = 'TRIP_MILEAGE', threshold = 1000 WHERE badge_code = 'DISTANCE_10000KM' AND deleted = 0;
UPDATE growth_badge SET condition_description = '成功邀请 10 位新用户注册', event_type = 'INVITE_USER_REGISTER', threshold = 10 WHERE badge_code = 'INVITE_10' AND deleted = 0;
UPDATE growth_badge SET condition_description = '成功邀请 50 位新用户注册', event_type = 'INVITE_USER_REGISTER', threshold = 50 WHERE badge_code = 'INVITE_50' AND deleted = 0;
UPDATE growth_badge SET condition_description = '完成 10 次互助' WHERE badge_code = 'HELP_10' AND deleted = 0;
UPDATE growth_badge SET condition_description = '完成 50 次互助' WHERE badge_code = 'HELP_50' AND deleted = 0;
UPDATE growth_badge SET condition_description = '完成 G318 川藏线路线' WHERE badge_code = 'ROUTE_G318' AND deleted = 0;
UPDATE growth_badge SET condition_description = '春夏秋冬各完成一次组队' WHERE badge_code = 'FOUR_SEASONS' AND deleted = 0;
