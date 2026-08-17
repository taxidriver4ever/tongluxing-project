-- 同路行：匹配路线 RDP 简化字段升级
-- 适用：已执行 21_route_match_performance_refactor.sql 的现有数据库。
-- 新路线由应用在规划时直接写入 match_polyline；历史路线在首次参与匹配时懒回填。
-- MySQL 8.0，可重复执行。

USE tongluxing;
SET NAMES utf8mb4;

SET @sql := IF(
    EXISTS(
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'trip_route'
          AND column_name = 'match_polyline'
    ),
    'SELECT 1',
    'ALTER TABLE trip_route ADD COLUMN match_polyline TEXT NULL COMMENT ''RDP简化后的匹配专用路线，默认最多60个关键点'' AFTER polyline'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 历史数据不在 SQL 中复制完整 polyline 到 match_polyline，避免再次制造大字段冗余。
-- 应用第一次需要这些历史路线参与顺路率精算时，会仅对缺失记录读取完整 polyline，
-- 用固定 epsilon 做 RDP，必要时再压缩到最多 60 个关键点并回写 match_polyline。

SELECT COUNT(*) AS route_rows_waiting_match_polyline_backfill
FROM trip_route
WHERE deleted = 0
  AND polyline IS NOT NULL
  AND CHAR_LENGTH(polyline) > 0
  AND (match_polyline IS NULL OR CHAR_LENGTH(match_polyline) = 0);
