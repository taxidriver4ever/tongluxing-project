-- 行程推荐模块增量迁移。
-- 已有数据库执行本文件；空数据库直接执行 01_reset_and_create_all_tables.sql。

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
