-- 同路行内测前增量修复（可重复执行）
-- 1. 严重脱队单独计时：100km 必须自身连续满 60 分钟。
-- 2. 自动出发改为显式开启，避免缺少出发前定位时误触发。

USE tongluxing;
SET NAMES utf8mb4;

SET @ddl = IF(
  (SELECT COUNT(*) FROM information_schema.columns
   WHERE table_schema = DATABASE() AND table_name = 'trip_member_distance_alert'
     AND column_name = 'severe_started_at') = 0,
  'ALTER TABLE trip_member_distance_alert ADD COLUMN severe_started_at DATETIME NULL COMMENT ''连续超过严重偏离阈值的开始时间'' AFTER started_at',
  'SELECT 1'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

ALTER TABLE trip
  MODIFY COLUMN auto_start_enabled TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否到点自动出发（需显式开启）';
