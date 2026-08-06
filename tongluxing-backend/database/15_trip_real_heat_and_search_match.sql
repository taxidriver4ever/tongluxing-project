-- 行程搜索顺路率与真实热度增量迁移。
-- 已有数据库执行本文件；空数据库直接执行 01_reset_and_create_all_tables.sql。

CREATE TABLE IF NOT EXISTS trip_leader_rating_summary (
  leader_user_id BIGINT NOT NULL comment '队长用户ID',
  rating DECIMAL(3,2) NOT NULL DEFAULT 0.00 comment '队长真实综合评分，范围0~5；无评价为0',
  positive_rate DECIMAL(5,4) NOT NULL DEFAULT 0.0000 comment '真实好评率，范围0~1；无评价为0',
  rating_count INT NOT NULL DEFAULT 0 comment '有效评价数量',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP comment '汇总更新时间',
  deleted TINYINT(1) NOT NULL DEFAULT 0 comment '逻辑删除标记',
  PRIMARY KEY (leader_user_id),
  KEY idx_trip_leader_rating (rating, positive_rate)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 comment='行程队长评分汇总表';

-- 去掉旧版本“无评价默认5分/100%好评”的演示值。
ALTER TABLE trip_leader_rating_summary
  MODIFY COLUMN rating DECIMAL(3,2) NOT NULL DEFAULT 0.00 comment '队长真实综合评分，范围0~5；无评价为0',
  MODIFY COLUMN positive_rate DECIMAL(5,4) NOT NULL DEFAULT 0.0000 comment '真实好评率，范围0~1；无评价为0';

UPDATE trip_leader_rating_summary
SET rating = 0.00, positive_rate = 0.0000
WHERE rating_count = 0;

-- 热度聚合按 trip_id 批量统计，为现有库补齐查询索引。
SET @ddl = IF(
  (SELECT COUNT(*) FROM information_schema.statistics
   WHERE table_schema = DATABASE() AND table_name = 'trip_favorite'
     AND index_name = 'idx_trip_favorite_trip_time') = 0,
  'ALTER TABLE trip_favorite ADD KEY idx_trip_favorite_trip_time (trip_id, created_at)',
  'SELECT 1'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
  (SELECT COUNT(*) FROM information_schema.statistics
   WHERE table_schema = DATABASE() AND table_name = 'team_join_application'
     AND index_name = 'idx_team_apply_trip_user') = 0,
  'ALTER TABLE team_join_application ADD KEY idx_team_apply_trip_user (trip_id, applicant_user_id, deleted)',
  'SELECT 1'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
