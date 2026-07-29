-- 推荐行程页面联调数据
-- 前置：已执行 01_reset_and_create_all_tables.sql、02_create_test_users.sql。
-- 仅用于本地开发/测试库。脚本可重复执行，不会重复生成记录。

SELECT DATABASE() AS current_database;
SET NAMES utf8mb4;
START TRANSACTION;

-- 旅行达人安安（13888888888）最新一条公开活跃行程作为推荐源行程。
SET @source_trip_id := (
    SELECT id
    FROM trip
    WHERE user_id = 900000000000000108
      AND public_flag = 1
      AND status IN ('PUBLISHED', 'READY', 'CONFIRMING', 'RUNNING', 'ONGOING')
      AND deleted = 0
    ORDER BY created_at DESC
    LIMIT 1
);

-- 六条公开招募行程，出发时间以执行时刻为基准，避免演示数据过期。
INSERT INTO trip (
    id, trip_number, user_id, vehicle_id, title, description, cover_image_key, expected_people,
    start_name, start_lat, start_lng, start_location_name, start_location_address,
    start_latitude, start_longitude, end_name, end_lat, end_lng,
    end_location_name, end_location_address, end_latitude, end_longitude,
    route_summary, route_polyline_key, route_distance, route_duration,
    route_polyline, waypoints_json, departure_time, estimated_days,
    total_distance_meters, max_vehicle_count, joined_vehicle_count, travel_depth,
    public_flag, status, remark, actual_start_time, actual_end_time,
    created_at, updated_at, deleted
) VALUES
(
    920000000000010201, 'TLX6ZMOS5CVX8EX', 900000000000000102, 900000000000008002,
    '周末深圳湾轻松自驾', '广州出发，经东莞松山湖前往深圳湾。节奏轻松，适合第一次参加同行自驾的车友。',
    '', 4, '广州天河体育中心', 23.134700, 113.361200,
    '广州天河体育中心', '广东省广州市天河区天河路299号', 23.134700, 113.361200,
    '深圳湾公园', 22.486900, 113.945600,
    '深圳湾公园', '广东省深圳市南山区滨海大道', 22.486900, 113.945600,
    '广州天河 → 东莞松山湖 → 深圳湾', '', 145000, 9000,
    JSON_ARRAY(
        JSON_OBJECT('name','广州天河体育中心','address','广东省广州市天河区天河路299号','latitude',23.134700,'longitude',113.361200),
        JSON_OBJECT('name','东莞松山湖','address','广东省东莞市松山湖科技产业园区','latitude',22.906000,'longitude',113.876000),
        JSON_OBJECT('name','深圳湾公园','address','广东省深圳市南山区滨海大道','latitude',22.486900,'longitude',113.945600)
    ),
    JSON_ARRAY(JSON_OBJECT('name','东莞松山湖','address','广东省东莞市松山湖科技产业园区','latitude',22.906000,'longitude',113.876000,'sortOrder',1)),
    DATE_ADD(NOW(), INTERVAL 36 HOUR), 2, 145000, 4, 1, 'LIGHT', 1, 'PUBLISHED',
    '费用 AA，沿途可协商停靠拍照。', NULL, NULL, NOW(), NOW(), 0
),
(
    920000000000010202, 'TLX6ZMOS5CVX8EY', 900000000000000103, 900000000000008003,
    '深圳出发广州美食之旅', '从南山科技园集合，经莲花山短暂停留，傍晚抵达广州北京路品尝本地美食。',
    '', 5, '深圳南山科技园', 22.540500, 113.934500,
    '深圳南山科技园', '广东省深圳市南山区粤海街道', 22.540500, 113.934500,
    '广州北京路步行街', 23.125200, 113.269500,
    '广州北京路步行街', '广东省广州市越秀区北京路', 23.125200, 113.269500,
    '深圳南山 → 深圳莲花山 → 广州北京路', '', 138000, 8400,
    JSON_ARRAY(
        JSON_OBJECT('name','深圳南山科技园','address','广东省深圳市南山区粤海街道','latitude',22.540500,'longitude',113.934500),
        JSON_OBJECT('name','深圳莲花山公园','address','广东省深圳市福田区红荔路6030号','latitude',22.554900,'longitude',114.064800),
        JSON_OBJECT('name','广州北京路步行街','address','广东省广州市越秀区北京路','latitude',23.125200,'longitude',113.269500)
    ),
    JSON_ARRAY(JSON_OBJECT('name','深圳莲花山公园','address','广东省深圳市福田区红荔路6030号','latitude',22.554900,'longitude',114.064800,'sortOrder',1)),
    DATE_ADD(NOW(), INTERVAL 38 HOUR), 2, 138000, 5, 2, 'LIGHT', 1, 'PUBLISHED',
    '欢迎摄影和美食爱好者。', NULL, NULL, NOW(), NOW(), 0
),
(
    920000000000010203, 'TLX6ZMOS5CVX8EZ', 900000000000000104, 900000000000008004,
    '粤北丹霞山两日车队', '广州集合，途经英德服务区前往丹霞山，第二天看日出后返程。',
    '', 6, '广州白云山', 23.184100, 113.298800,
    '广州白云山', '广东省广州市白云区广园中路801号', 23.184100, 113.298800,
    '丹霞山风景名胜区', 25.031000, 113.744000,
    '丹霞山风景名胜区', '广东省韶关市仁化县丹霞街道', 25.031000, 113.744000,
    '广州白云 → 英德 → 韶关丹霞山', '', 285000, 14400,
    JSON_ARRAY(
        JSON_OBJECT('name','广州白云山','address','广东省广州市白云区广园中路801号','latitude',23.184100,'longitude',113.298800),
        JSON_OBJECT('name','英德服务区','address','广东省清远市英德市京港澳高速','latitude',24.185000,'longitude',113.411000),
        JSON_OBJECT('name','丹霞山风景名胜区','address','广东省韶关市仁化县丹霞街道','latitude',25.031000,'longitude',113.744000)
    ),
    JSON_ARRAY(JSON_OBJECT('name','英德服务区','address','广东省清远市英德市京港澳高速','latitude',24.185000,'longitude',113.411000,'sortOrder',1)),
    DATE_ADD(NOW(), INTERVAL 40 HOUR), 2, 285000, 6, 1, 'MIDDLE', 1, 'PUBLISHED',
    '建议携带轻便徒步装备。', NULL, NULL, NOW(), NOW(), 0
),
(
    920000000000010204, 'TLX6ZMOS5CVX8F0', 900000000000000105, 900000000000008005,
    '惠州双月湾摄影同行', '深圳出发，经小梅沙观景台抵达双月湾，安排海边日落和次日日出拍摄。',
    '', 5, '深圳南山科技园', 22.540500, 113.934500,
    '深圳南山科技园', '广东省深圳市南山区粤海街道', 22.540500, 113.934500,
    '惠州双月湾观景台', 22.702000, 114.880000,
    '惠州双月湾观景台', '广东省惠州市惠东县港口镇', 22.702000, 114.880000,
    '深圳南山 → 小梅沙 → 惠州双月湾', '', 176000, 10200,
    JSON_ARRAY(
        JSON_OBJECT('name','深圳南山科技园','address','广东省深圳市南山区粤海街道','latitude',22.540500,'longitude',113.934500),
        JSON_OBJECT('name','小梅沙海滨公园','address','广东省深圳市盐田区盐梅路','latitude',22.600000,'longitude',114.334000),
        JSON_OBJECT('name','惠州双月湾观景台','address','广东省惠州市惠东县港口镇','latitude',22.702000,'longitude',114.880000)
    ),
    JSON_ARRAY(JSON_OBJECT('name','小梅沙海滨公园','address','广东省深圳市盐田区盐梅路','latitude',22.600000,'longitude',114.334000,'sortOrder',1)),
    DATE_ADD(NOW(), INTERVAL 42 HOUR), 2, 176000, 5, 1, 'LIGHT', 1, 'PUBLISHED',
    '有相机或无人机的车友优先。', NULL, NULL, NOW(), NOW(), 0
),
(
    920000000000010205, 'TLX6ZMOS5CVX8F1', 900000000000000106, 900000000000008006,
    '佛山顺德露营车队', '广州塔集合，经顺峰山公园采购补给后前往营地，适合亲子和露营新手。',
    '', 6, '广州塔', 23.106500, 113.324500,
    '广州塔', '广东省广州市海珠区阅江西路222号', 23.106500, 113.324500,
    '顺德逢简水乡', 22.807000, 113.148000,
    '顺德逢简水乡', '广东省佛山市顺德区杏坛镇', 22.807000, 113.148000,
    '广州塔 → 顺峰山公园 → 逢简水乡', '', 72000, 5400,
    JSON_ARRAY(
        JSON_OBJECT('name','广州塔','address','广东省广州市海珠区阅江西路222号','latitude',23.106500,'longitude',113.324500),
        JSON_OBJECT('name','顺峰山公园','address','广东省佛山市顺德区南国东路','latitude',22.827000,'longitude',113.305000),
        JSON_OBJECT('name','顺德逢简水乡','address','广东省佛山市顺德区杏坛镇','latitude',22.807000,'longitude',113.148000)
    ),
    JSON_ARRAY(JSON_OBJECT('name','顺峰山公园','address','广东省佛山市顺德区南国东路','latitude',22.827000,'longitude',113.305000,'sortOrder',1)),
    DATE_ADD(NOW(), INTERVAL 44 HOUR), 2, 72000, 6, 2, 'LIGHT', 1, 'PUBLISHED',
    '可提供一套备用天幕。', NULL, NULL, NOW(), NOW(), 0
),
(
    920000000000010206, 'TLX6ZMOS5CVX8F2', 900000000000000107, 900000000000008007,
    '珠海情侣路新手友好行程', '广州出发，经中山岐江公园休息，抵达珠海情侣路。全程高速为主，新手友好。',
    '', 4, '广州天河体育中心', 23.134700, 113.361200,
    '广州天河体育中心', '广东省广州市天河区天河路299号', 23.134700, 113.361200,
    '珠海情侣路', 22.277000, 113.588000,
    '珠海情侣路', '广东省珠海市香洲区情侣中路', 22.277000, 113.588000,
    '广州天河 → 中山岐江公园 → 珠海情侣路', '', 132000, 7800,
    JSON_ARRAY(
        JSON_OBJECT('name','广州天河体育中心','address','广东省广州市天河区天河路299号','latitude',23.134700,'longitude',113.361200),
        JSON_OBJECT('name','中山岐江公园','address','广东省中山市西区街道中山一路','latitude',22.516000,'longitude',113.365000),
        JSON_OBJECT('name','珠海情侣路','address','广东省珠海市香洲区情侣中路','latitude',22.277000,'longitude',113.588000)
    ),
    JSON_ARRAY(JSON_OBJECT('name','中山岐江公园','address','广东省中山市西区街道中山一路','latitude',22.516000,'longitude',113.365000,'sortOrder',1)),
    DATE_ADD(NOW(), INTERVAL 46 HOUR), 2, 132000, 4, 1, 'LIGHT', 1, 'PUBLISHED',
    '控制车速，统一在服务区集合。', NULL, NULL, NOW(), NOW(), 0
)
ON DUPLICATE KEY UPDATE
    trip_number = VALUES(trip_number),
    title = VALUES(title),
    description = VALUES(description),
    start_name = VALUES(start_name),
    start_lat = VALUES(start_lat),
    start_lng = VALUES(start_lng),
    start_location_name = VALUES(start_location_name),
    start_location_address = VALUES(start_location_address),
    start_latitude = VALUES(start_latitude),
    start_longitude = VALUES(start_longitude),
    end_name = VALUES(end_name),
    end_lat = VALUES(end_lat),
    end_lng = VALUES(end_lng),
    end_location_name = VALUES(end_location_name),
    end_location_address = VALUES(end_location_address),
    end_latitude = VALUES(end_latitude),
    end_longitude = VALUES(end_longitude),
    route_summary = VALUES(route_summary),
    route_distance = VALUES(route_distance),
    route_duration = VALUES(route_duration),
    route_polyline = VALUES(route_polyline),
    waypoints_json = VALUES(waypoints_json),
    departure_time = VALUES(departure_time),
    total_distance_meters = VALUES(total_distance_meters),
    max_vehicle_count = VALUES(max_vehicle_count),
    joined_vehicle_count = VALUES(joined_vehicle_count),
    travel_depth = VALUES(travel_depth),
    public_flag = 1,
    status = 'PUBLISHED',
    remark = VALUES(remark),
    updated_at = NOW(),
    deleted = 0;

-- 行程路线快照。
INSERT INTO trip_route (
    id, trip_id, draft_id, route_plan_id, origin, destination, waypoints,
    polyline, plan_distance, plan_duration, provider_type, route_status,
    created_at, updated_at, deleted
)
SELECT
    920000000000020000 + (t.id - 920000000000010200),
    t.id, NULL, 920000000000030000 + (t.id - 920000000000010200),
    JSON_OBJECT('name',t.start_location_name,'address',t.start_location_address,'latitude',t.start_latitude,'longitude',t.start_longitude),
    JSON_OBJECT('name',t.end_location_name,'address',t.end_location_address,'latitude',t.end_latitude,'longitude',t.end_longitude),
    CAST(t.waypoints_json AS JSON), CAST(t.route_polyline AS CHAR),
    t.route_distance, t.route_duration, 'MOCK', 'VALID', NOW(), NOW(), 0
FROM trip t
WHERE t.id BETWEEN 920000000000010201 AND 920000000000010206
ON DUPLICATE KEY UPDATE
    origin = VALUES(origin),
    destination = VALUES(destination),
    waypoints = VALUES(waypoints),
    polyline = VALUES(polyline),
    plan_distance = VALUES(plan_distance),
    plan_duration = VALUES(plan_duration),
    route_status = 'VALID',
    updated_at = NOW(),
    deleted = 0;

-- 详情页途经点表数据。
DELETE FROM trip_waypoint
WHERE trip_id BETWEEN 920000000000010201 AND 920000000000010206;

INSERT INTO trip_waypoint (
    id, trip_id, draft_id, seq_no, place_name, place_address, waypoint_type,
    lat, lng, stay_minutes, created_at, updated_at, deleted
)
SELECT
    920000000000040000 + (t.id - 920000000000010200),
    t.id, NULL, 1,
    JSON_UNQUOTE(JSON_EXTRACT(t.waypoints_json, '$[0].name')),
    JSON_UNQUOTE(JSON_EXTRACT(t.waypoints_json, '$[0].address')),
    'REST',
    CAST(JSON_UNQUOTE(JSON_EXTRACT(t.waypoints_json, '$[0].latitude')) AS DECIMAL(10,6)),
    CAST(JSON_UNQUOTE(JSON_EXTRACT(t.waypoints_json, '$[0].longitude')) AS DECIMAL(10,6)),
    30, NOW(), NOW(), 0
FROM trip t
WHERE t.id BETWEEN 920000000000010201 AND 920000000000010206;

-- 每条 Mock 行程都补一条群主成员快照。
INSERT INTO trip_member_snapshot (
    id, trip_id, user_id, vehicle_id, member_role, join_status,
    nickname_snapshot, vehicle_snapshot, joined_at, created_at, updated_at
)
SELECT
    920000000000050000 + (t.id - 920000000000010200),
    t.id, t.user_id, t.vehicle_id, 'OWNER', 'OWNER',
    p.nickname, CONCAT(v.brand, ' ', v.model), NOW(), NOW(), NOW()
FROM trip t
JOIN user_profile p ON p.user_id = t.user_id AND p.deleted = 0
JOIN vehicle_profile v ON v.id = t.vehicle_id AND v.deleted = 0
WHERE t.id BETWEEN 920000000000010201 AND 920000000000010206
  AND NOT EXISTS (
      SELECT 1 FROM trip_member_snapshot s
      WHERE s.trip_id = t.id AND s.user_id = t.user_id
  );

-- 发现行程的申请加入链路依赖公开车队；为每条 Mock 行程建立真实车队与队长成员。
INSERT INTO team (
    id, trip_id, owner_user_id, owner_vehicle_id, team_name, team_desc,
    start_name, end_name, departure_time, max_member_count, current_member_count,
    join_mode, team_status, public_flag, notice, created_at, updated_at, deleted
)
SELECT
    920000000000070000 + (t.id - 920000000000010200),
    t.id, t.user_id, t.vehicle_id, CONCAT(t.title, '车队'),
    '公开行程招募车队，可在发现行程详情中提交加入申请',
    t.start_name, t.end_name, t.departure_time,
    GREATEST(2, LEAST(20, COALESCE(t.max_vehicle_count, 4))), 1,
    'APPLICATION', 'ACTIVE', 1, '', NOW(), NOW(), 0
FROM trip t
WHERE t.id BETWEEN 920000000000010201 AND 920000000000010206
ON DUPLICATE KEY UPDATE
    team_name = VALUES(team_name),
    max_member_count = VALUES(max_member_count),
    team_status = 'ACTIVE',
    public_flag = 1,
    updated_at = NOW(),
    deleted = 0;

INSERT INTO team_member (
    id, team_id, user_id, vehicle_id, member_role, member_status,
    joined_at, nickname_snapshot, vehicle_snapshot, created_at, updated_at, deleted
)
SELECT
    920000000000080000 + (t.id - 920000000000010200),
    team.id, t.user_id, t.vehicle_id, 'OWNER', 'ACTIVE',
    NOW(), p.nickname, CONCAT(v.brand, ' ', v.model), NOW(), NOW(), 0
FROM trip t
JOIN team ON team.trip_id = t.id AND team.team_status = 'ACTIVE' AND team.deleted = 0
JOIN user_profile p ON p.user_id = t.user_id AND p.deleted = 0
JOIN vehicle_profile v ON v.id = t.vehicle_id AND v.deleted = 0
WHERE t.id BETWEEN 920000000000010201 AND 920000000000010206
ON DUPLICATE KEY UPDATE
    vehicle_id = VALUES(vehicle_id),
    member_role = 'OWNER',
    member_status = 'ACTIVE',
    nickname_snapshot = VALUES(nickname_snapshot),
    vehicle_snapshot = VALUES(vehicle_snapshot),
    updated_at = NOW(),
    deleted = 0;

UPDATE trip
SET vehicle_requirements = CASE id
        WHEN 920000000000010201 THEN 'SUV,越野车'
        WHEN 920000000000010202 THEN '轿车,SUV'
        WHEN 920000000000010203 THEN '摩托车'
        WHEN 920000000000010204 THEN '不限'
        WHEN 920000000000010205 THEN '新能源,SUV'
        ELSE 'MPV,轿车'
    END,
    budget_description = CASE id
        WHEN 920000000000010201 THEN '约1500元/人，油费路费AA'
        WHEN 920000000000010202 THEN '约800元/人，餐饮住宿自理'
        WHEN 920000000000010203 THEN '约600元/人'
        ELSE NULL
    END
WHERE id BETWEEN 920000000000010201 AND 920000000000010206;

-- 推荐页面读取 match_result；直接为当前演示源行程生成稳定的推荐结果。
INSERT INTO match_result (
    id, source_trip_id, target_trip_id, source_user_id, target_user_id,
    match_score, overlap_rate, distance_gap_meters, departure_gap_minutes,
    score_detail_json, result_status, calculated_at, created_at, updated_at, deleted
)
SELECT
    920000000000060000 + (t.id - 920000000000010200),
    @source_trip_id, t.id, source_trip.user_id, t.user_id,
    98 - ((t.id - 920000000000010201) * 3),
    92 - ((t.id - 920000000000010201) * 4),
    1200 + ((t.id - 920000000000010201) * 850),
    ABS(TIMESTAMPDIFF(MINUTE, source_trip.departure_time, t.departure_time)),
    JSON_OBJECT(
        'routeScore', 95 - ((t.id - 920000000000010201) * 3),
        'timeScore', 94 - ((t.id - 920000000000010201) * 2),
        'preferenceScore', 90 - ((t.id - 920000000000010201) * 2),
        'mock', TRUE
    ),
    'VALID', NOW(), NOW(), NOW(), 0
FROM trip t
JOIN trip source_trip ON source_trip.id = @source_trip_id
WHERE t.id BETWEEN 920000000000010201 AND 920000000000010206
  AND @source_trip_id IS NOT NULL
ON DUPLICATE KEY UPDATE
    match_score = VALUES(match_score),
    overlap_rate = VALUES(overlap_rate),
    distance_gap_meters = VALUES(distance_gap_meters),
    departure_gap_minutes = VALUES(departure_gap_minutes),
    score_detail_json = VALUES(score_detail_json),
    result_status = 'VALID',
    calculated_at = NOW(),
    updated_at = NOW(),
    deleted = 0;

COMMIT;

SELECT @source_trip_id AS recommendation_source_trip_id;
SELECT id, title, start_name, end_name, departure_time, status
FROM trip
WHERE id BETWEEN 920000000000010201 AND 920000000000010206
ORDER BY id;
SELECT source_trip_id, target_trip_id, match_score, overlap_rate, result_status
FROM match_result
WHERE target_trip_id BETWEEN 920000000000010201 AND 920000000000010206
ORDER BY match_score DESC;
