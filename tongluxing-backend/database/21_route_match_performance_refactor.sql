-- 同路行：推荐与路线性能整改迁移
-- 适用：已有数据库升级到“轻量候选 + trip_route 单一完整路线来源 + 路线签名”版本。
-- MySQL 8.0，可重复执行。

USE tongluxing;
SET NAMES utf8mb4;

-- 1) trip_route 增加路线节点签名。新生成的草稿/正式路线由应用写入 SHA-256。
SET @sql := IF(
    EXISTS(
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'trip_route'
          AND column_name = 'route_signature'
    ),
    'SELECT 1',
    'ALTER TABLE trip_route ADD COLUMN route_signature VARCHAR(64) NULL COMMENT ''起终点和途经点坐标签名，用于精准判断路线是否失效'' AFTER route_status'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2) 兼容历史数据：历史版本若只在 trip.route_polyline 有完整路线，补到 trip_route。
--    route id 与 trip id 位于不同表，历史补偿时可以安全复用 trip.id 作为 route.id。
INSERT IGNORE INTO trip_route (
    id, trip_id, draft_id, route_plan_id, origin, destination, waypoints, polyline,
    plan_distance, plan_duration, provider_type, route_status, route_signature,
    created_at, updated_at, deleted
)
SELECT
    t.id,
    t.id,
    NULL,
    NULL,
    JSON_OBJECT(
        'name', COALESCE(t.start_location_name, t.start_name, ''),
        'address', COALESCE(t.start_location_address, ''),
        'latitude', COALESCE(t.start_latitude, t.start_lat),
        'longitude', COALESCE(t.start_longitude, t.start_lng)
    ),
    JSON_OBJECT(
        'name', COALESCE(t.end_location_name, t.end_name, ''),
        'address', COALESCE(t.end_location_address, ''),
        'latitude', COALESCE(t.end_latitude, t.end_lat),
        'longitude', COALESCE(t.end_longitude, t.end_lng)
    ),
    CASE
        WHEN t.waypoints_json IS NULL OR TRIM(t.waypoints_json) = '' OR JSON_VALID(t.waypoints_json) = 0
            THEN JSON_ARRAY()
        ELSE CAST(t.waypoints_json AS JSON)
    END,
    t.route_polyline,
    t.route_distance,
    t.route_duration,
    'LEGACY',
    'VALID',
    NULL,
    COALESCE(t.created_at, NOW()),
    NOW(),
    0
FROM trip t
WHERE t.deleted = 0
  AND t.route_polyline IS NOT NULL
  AND CHAR_LENGTH(t.route_polyline) > 0
  AND NOT EXISTS (
      SELECT 1 FROM trip_route r
      WHERE r.trip_id = t.id AND r.deleted = 0
  );

-- 已有 trip_route 但 polyline 为空时，用旧主表数据补齐。
UPDATE trip_route r
JOIN trip t ON t.id = r.trip_id AND t.deleted = 0
SET r.polyline = t.route_polyline,
    r.plan_distance = COALESCE(r.plan_distance, t.route_distance),
    r.plan_duration = COALESCE(r.plan_duration, t.route_duration),
    r.updated_at = NOW()
WHERE r.deleted = 0
  AND (r.polyline IS NULL OR CHAR_LENGTH(r.polyline) = 0)
  AND t.route_polyline IS NOT NULL
  AND CHAR_LENGTH(t.route_polyline) > 0;

-- 3) 完整路线只保留在 trip_route。保留旧字段本身仅用于滚动升级兼容，数据清空避免重复存储。
UPDATE trip
SET route_polyline = NULL
WHERE route_polyline IS NOT NULL AND CHAR_LENGTH(route_polyline) > 0;

-- 4) 批量路线精算和驾驶认证 EXISTS 查询需要的索引（使用 information_schema 保证幂等）。
SET @sql := IF(
    EXISTS(
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'trip_route'
          AND index_name = 'idx_trip_route_status_trip'
    ),
    'SELECT 1',
    'ALTER TABLE trip_route ADD INDEX idx_trip_route_status_trip (route_status, trip_id, deleted)'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := IF(
    EXISTS(
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'user_driving_license_certification'
          AND index_name = 'idx_driver_cert_user_status_deleted'
    ),
    'SELECT 1',
    'ALTER TABLE user_driving_license_certification ADD INDEX idx_driver_cert_user_status_deleted (user_id, certification_status, deleted)'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 5) 迁移后校验：主表不应再保留完整 polyline；正式路线应尽可能有 trip_route 记录。
SELECT COUNT(*) AS legacy_trip_polyline_rows
FROM trip
WHERE route_polyline IS NOT NULL AND CHAR_LENGTH(route_polyline) > 0;

SELECT COUNT(*) AS formal_route_rows
FROM trip_route
WHERE trip_id IS NOT NULL AND deleted = 0;
