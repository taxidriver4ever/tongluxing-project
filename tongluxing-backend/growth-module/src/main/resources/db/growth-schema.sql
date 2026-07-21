CREATE TABLE IF NOT EXISTS growth_account (
  id BIGINT NOT NULL, user_id BIGINT NOT NULL, total_points INT NOT NULL DEFAULT 0,
  level_code VARCHAR(16) NOT NULL DEFAULT 'LV1', version INT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0, PRIMARY KEY (id),
  UNIQUE KEY uk_growth_account_user (user_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS growth_log (
  id BIGINT NOT NULL, user_id BIGINT NOT NULL, biz_type VARCHAR(32) NOT NULL,
  biz_id VARCHAR(64) NOT NULL, point_delta INT NOT NULL, balance_after INT NOT NULL,
  remark VARCHAR(255) NULL, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0, PRIMARY KEY (id),
  UNIQUE KEY uk_growth_log_biz (biz_type, biz_id, user_id),
  KEY idx_growth_log_user_time (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS growth_level_rule (
  id BIGINT NOT NULL, level_code VARCHAR(16) NOT NULL, level_name VARCHAR(32) NOT NULL,
  min_points INT NOT NULL, max_points INT NULL, benefit_json JSON NOT NULL,
  enabled_flag TINYINT NOT NULL DEFAULT 1, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0, PRIMARY KEY (id),
  UNIQUE KEY uk_growth_level_code (level_code, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS growth_badge (
  id BIGINT NOT NULL, badge_code VARCHAR(32) NOT NULL, badge_name VARCHAR(64) NOT NULL,
  badge_image_key VARCHAR(512) NULL, condition_description VARCHAR(128) NOT NULL,
  event_type VARCHAR(32) NOT NULL, threshold INT NOT NULL,
  enabled_flag TINYINT NOT NULL DEFAULT 1, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0, PRIMARY KEY (id),
  UNIQUE KEY uk_growth_badge_code (badge_code, deleted),
  KEY idx_growth_badge_event_threshold (event_type, enabled_flag, deleted, threshold)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
SET @growth_add_badge_description = IF(
  (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
    AND table_name='growth_badge' AND column_name='condition_description')=0,
  'ALTER TABLE growth_badge ADD COLUMN condition_description VARCHAR(128) NULL AFTER badge_image_key',
  'SELECT 1');
PREPARE growth_stmt FROM @growth_add_badge_description;
EXECUTE growth_stmt;
DEALLOCATE PREPARE growth_stmt;
CREATE TABLE IF NOT EXISTS growth_user_badge (
  id BIGINT NOT NULL, user_id BIGINT NOT NULL, badge_id BIGINT NOT NULL,
  source_biz_id VARCHAR(64) NULL, awarded_at DATETIME NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0, PRIMARY KEY (id),
  UNIQUE KEY uk_growth_user_badge (user_id, badge_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

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
