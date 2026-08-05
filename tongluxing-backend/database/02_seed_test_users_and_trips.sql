-- 同路行开发环境种子数据：用户、车辆、认证、公开行程和推荐关系
-- MySQL 8.0+
--
-- 执行顺序：
--   1. 01_reset_and_create_all_tables.sql
--   2. 02_seed_test_users_and_trips.sql
--
-- 测试手机号：13888888881 ~ 13888888888
-- 统一密码：12345678
-- 仅用于开发/测试环境，禁止用于生产环境。

-- 第一部分：开发/联调测试用户、资料、车辆与认证
-- 前置：先执行 01_reset_and_create_all_tables.sql。
--
-- 所有账号统一密码：12345678
-- 手机号范围：13888888881 ~ 13888888888
-- 这些账号、车辆和证件信息均为虚构测试数据，不得用于生产环境。

USE `tongluxing`;
SELECT DATABASE() AS current_database;
SET NAMES utf8mb4;
START TRANSACTION;

-- 1. 登录账号
INSERT INTO auth_account
    (id, user_id, phone, account_status, mini_invite_onboarding_completed,
     last_login_time, last_login_ip, created_at, updated_at, deleted)
VALUES
    (900000000000001001, 900000000000000101, '13888888881', 1, 1, NULL, NULL, NOW(), NOW(), 0),
    (900000000000001002, 900000000000000102, '13888888882', 1, 1, NULL, NULL, NOW(), NOW(), 0),
    (900000000000001003, 900000000000000103, '13888888883', 1, 1, NULL, NULL, NOW(), NOW(), 0),
    (900000000000001004, 900000000000000104, '13888888884', 1, 1, NULL, NULL, NOW(), NOW(), 0),
    (900000000000001005, 900000000000000105, '13888888885', 1, 1, NULL, NULL, NOW(), NOW(), 0),
    (900000000000001006, 900000000000000106, '13888888886', 1, 1, NULL, NULL, NOW(), NOW(), 0),
    (900000000000001007, 900000000000000107, '13888888887', 1, 1, NULL, NULL, NOW(), NOW(), 0),
    (900000000000001008, 900000000000000108, '13888888888', 1, 1, NULL, NULL, NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE
    account_status = VALUES(account_status),
    mini_invite_onboarding_completed = VALUES(mini_invite_onboarding_completed),
    updated_at = NOW(),
    deleted = 0;

-- BCrypt 对应明文密码：12345678
INSERT INTO auth_password_credential
    (id, user_id, password_hash, password_version, password_status,
     last_set_time, created_at, updated_at, deleted)
VALUES
    (900000000000002001, 900000000000000101, '$2a$10$csNtGZEuy.EdI9XA9ql8tu2PfMbncIKGsQ49QKFC2ACXLlFcPUGg.', 'BCRYPT', 1, NOW(), NOW(), NOW(), 0),
    (900000000000002002, 900000000000000102, '$2a$10$csNtGZEuy.EdI9XA9ql8tu2PfMbncIKGsQ49QKFC2ACXLlFcPUGg.', 'BCRYPT', 1, NOW(), NOW(), NOW(), 0),
    (900000000000002003, 900000000000000103, '$2a$10$csNtGZEuy.EdI9XA9ql8tu2PfMbncIKGsQ49QKFC2ACXLlFcPUGg.', 'BCRYPT', 1, NOW(), NOW(), NOW(), 0),
    (900000000000002004, 900000000000000104, '$2a$10$csNtGZEuy.EdI9XA9ql8tu2PfMbncIKGsQ49QKFC2ACXLlFcPUGg.', 'BCRYPT', 1, NOW(), NOW(), NOW(), 0),
    (900000000000002005, 900000000000000105, '$2a$10$csNtGZEuy.EdI9XA9ql8tu2PfMbncIKGsQ49QKFC2ACXLlFcPUGg.', 'BCRYPT', 1, NOW(), NOW(), NOW(), 0),
    (900000000000002006, 900000000000000106, '$2a$10$csNtGZEuy.EdI9XA9ql8tu2PfMbncIKGsQ49QKFC2ACXLlFcPUGg.', 'BCRYPT', 1, NOW(), NOW(), NOW(), 0),
    (900000000000002007, 900000000000000107, '$2a$10$csNtGZEuy.EdI9XA9ql8tu2PfMbncIKGsQ49QKFC2ACXLlFcPUGg.', 'BCRYPT', 1, NOW(), NOW(), NOW(), 0),
    (900000000000002008, 900000000000000108, '$2a$10$csNtGZEuy.EdI9XA9ql8tu2PfMbncIKGsQ49QKFC2ACXLlFcPUGg.', 'BCRYPT', 1, NOW(), NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE
    password_hash = VALUES(password_hash),
    password_version = 'BCRYPT',
    password_status = 1,
    last_set_time = NOW(),
    updated_at = NOW(),
    deleted = 0;

INSERT IGNORE INTO auth_user_role (user_id, role_code, granted_at)
VALUES
    (900000000000000101, 'USER', NOW()),
    (900000000000000102, 'USER', NOW()),
    (900000000000000103, 'USER', NOW()),
    (900000000000000104, 'USER', NOW()),
    (900000000000000105, 'USER', NOW()),
    (900000000000000106, 'USER', NOW()),
    (900000000000000107, 'USER', NOW()),
    (900000000000000108, 'USER', NOW());

-- 2. 用户公开资料
INSERT INTO user_profile
    (id, user_id, tongluxing_id, nickname, avatar_image_key, gender, birthday,
     city_code, city_name, bio, profile_status, created_at, updated_at, deleted)
VALUES
    (900000000000003001, 900000000000000101, 'TLX000101', '测试队长阿航', '', 1, '1998-05-12', '440100', '广州', '周末自驾，路线规划测试账号', 'ACTIVE', NOW(), NOW(), 0),
    (900000000000003002, 900000000000000102, 'TLX000102', '广州车友小陈', '', 1, '2000-08-21', '440100', '广州', '可用于申请加入行程和聊天测试', 'ACTIVE', NOW(), NOW(), 0),
    (900000000000003003, 900000000000000103, 'TLX000103', '深圳车友小林', '', 2, '1999-03-16', '440300', '深圳', '偏好短途与城市周边路线', 'ACTIVE', NOW(), NOW(), 0),
    (900000000000003004, 900000000000000104, 'TLX000104', '成都车友老周', '', 1, '1996-11-02', '510100', '成都', '川西路线与经停点测试账号', 'ACTIVE', NOW(), NOW(), 0),
    (900000000000003005, 900000000000000105, 'TLX000105', '摄影搭子小雨', '', 2, '2001-06-08', '440100', '广州', '喜欢风景路线和摄影打卡', 'ACTIVE', NOW(), NOW(), 0),
    (900000000000003006, 900000000000000106, 'TLX000106', '露营玩家阿森', '', 1, '1997-09-19', '440300', '深圳', '露营、拼车与群聊测试账号', 'ACTIVE', NOW(), NOW(), 0),
    (900000000000003007, 900000000000000107, 'TLX000107', '新手司机小唐', '', 1, '2002-01-25', '440600', '佛山', '用于新用户和低里程场景测试', 'ACTIVE', NOW(), NOW(), 0),
    (900000000000003008, 900000000000000108, 'TLX000108', '旅行达人安安', '', 2, '1998-12-30', '330100', '杭州', '用于高里程、关注和推荐测试', 'ACTIVE', NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE
    tongluxing_id = VALUES(tongluxing_id),
    nickname = VALUES(nickname),
    gender = VALUES(gender),
    birthday = VALUES(birthday),
    city_code = VALUES(city_code),
    city_name = VALUES(city_name),
    bio = VALUES(bio),
    profile_status = 'ACTIVE',
    updated_at = NOW(),
    deleted = 0;

INSERT INTO user_privacy_setting
    (id, user_id, profile_visibility, vehicle_visibility, invite_enabled_flag,
     created_at, updated_at, deleted)
VALUES
    (900000000000004001, 900000000000000101, 'PUBLIC', 'PUBLIC', 1, NOW(), NOW(), 0),
    (900000000000004002, 900000000000000102, 'PUBLIC', 'PUBLIC', 1, NOW(), NOW(), 0),
    (900000000000004003, 900000000000000103, 'PUBLIC', 'PUBLIC', 1, NOW(), NOW(), 0),
    (900000000000004004, 900000000000000104, 'PUBLIC', 'PUBLIC', 1, NOW(), NOW(), 0),
    (900000000000004005, 900000000000000105, 'PUBLIC', 'TEAM_ONLY', 1, NOW(), NOW(), 0),
    (900000000000004006, 900000000000000106, 'PUBLIC', 'TEAM_ONLY', 1, NOW(), NOW(), 0),
    (900000000000004007, 900000000000000107, 'PUBLIC', 'TEAM_ONLY', 1, NOW(), NOW(), 0),
    (900000000000004008, 900000000000000108, 'PUBLIC', 'PUBLIC', 1, NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE
    profile_visibility = VALUES(profile_visibility),
    vehicle_visibility = VALUES(vehicle_visibility),
    invite_enabled_flag = 1,
    updated_at = NOW(),
    deleted = 0;

INSERT INTO user_statistics
    (user_id, total_trip_count, total_distance_meters, total_duration_minutes,
     completed_waypoint_count, updated_at)
VALUES
    (900000000000000101, 12, 2860000, 3900, 34, NOW()),
    (900000000000000102, 5, 820000, 1260, 12, NOW()),
    (900000000000000103, 8, 1520000, 2300, 20, NOW()),
    (900000000000000104, 18, 6680000, 9100, 58, NOW()),
    (900000000000000105, 4, 610000, 980, 15, NOW()),
    (900000000000000106, 9, 1910000, 2860, 24, NOW()),
    (900000000000000107, 1, 68000, 110, 2, NOW()),
    (900000000000000108, 31, 12860000, 17300, 96, NOW())
ON DUPLICATE KEY UPDATE
    total_trip_count = VALUES(total_trip_count),
    total_distance_meters = VALUES(total_distance_meters),
    total_duration_minutes = VALUES(total_duration_minutes),
    completed_waypoint_count = VALUES(completed_waypoint_count),
    updated_at = NOW();

-- 3. 成长与邀请码
INSERT INTO growth_account
    (id, user_id, total_points, level_code, version, created_at, updated_at, deleted)
VALUES
    (900000000000005001, 900000000000000101, 3600, 'LV3', 0, NOW(), NOW(), 0),
    (900000000000005002, 900000000000000102, 920, 'LV2', 0, NOW(), NOW(), 0),
    (900000000000005003, 900000000000000103, 1780, 'LV2', 0, NOW(), NOW(), 0),
    (900000000000005004, 900000000000000104, 7200, 'LV4', 0, NOW(), NOW(), 0),
    (900000000000005005, 900000000000000105, 680, 'LV2', 0, NOW(), NOW(), 0),
    (900000000000005006, 900000000000000106, 2250, 'LV3', 0, NOW(), NOW(), 0),
    (900000000000005007, 900000000000000107, 120, 'LV1', 0, NOW(), NOW(), 0),
    (900000000000005008, 900000000000000108, 12800, 'LV5', 0, NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE
    total_points = VALUES(total_points),
    level_code = VALUES(level_code),
    updated_at = NOW(),
    deleted = 0;

INSERT INTO invite_code
    (id, user_id, invite_code, enabled_flag, created_at, updated_at, deleted)
VALUES
    (900000000000006001, 900000000000000101, 'TEST0101', 1, NOW(), NOW(), 0),
    (900000000000006002, 900000000000000102, 'TEST0102', 1, NOW(), NOW(), 0),
    (900000000000006003, 900000000000000103, 'TEST0103', 1, NOW(), NOW(), 0),
    (900000000000006004, 900000000000000104, 'TEST0104', 1, NOW(), NOW(), 0),
    (900000000000006005, 900000000000000105, 'TEST0105', 1, NOW(), NOW(), 0),
    (900000000000006006, 900000000000000106, 'TEST0106', 1, NOW(), NOW(), 0),
    (900000000000006007, 900000000000000107, 'TEST0107', 1, NOW(), NOW(), 0),
    (900000000000006008, 900000000000000108, 'TEST0108', 1, NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE
    invite_code = VALUES(invite_code),
    enabled_flag = 1,
    updated_at = NOW(),
    deleted = 0;

-- 4. 关注关系：用于粉丝、关注、互关和私聊权限测试
INSERT IGNORE INTO user_follow (id, follower_user_id, followed_user_id, created_at)
VALUES
    (900000000000007001, 900000000000000102, 900000000000000101, NOW()),
    (900000000000007002, 900000000000000103, 900000000000000101, NOW()),
    (900000000000007003, 900000000000000104, 900000000000000101, NOW()),
    (900000000000007004, 900000000000000101, 900000000000000102, NOW()),
    (900000000000007005, 900000000000000101, 900000000000000103, NOW()),
    (900000000000007006, 900000000000000105, 900000000000000106, NOW()),
    (900000000000007007, 900000000000000106, 900000000000000105, NOW()),
    (900000000000007008, 900000000000000108, 900000000000000101, NOW());

-- 5. 测试车辆：全部设为已认证，便于直接创建行程
INSERT INTO vehicle_profile
    (id, user_id, plate_no_cipher, plate_no_mask, brand, model, vehicle_type, color,
     seat_count, energy_type, vehicle_photo_image_key, certification_status,
     is_default, created_at, updated_at, deleted)
VALUES
    (900000000000008001, 900000000000000101, 'TEST_PLATE_0101', '粤A·T101', '丰田', 'RAV4', 'SUV', '白色', 5, 'FUEL', '', 'APPROVED', 1, NOW(), NOW(), 0),
    (900000000000008002, 900000000000000102, 'TEST_PLATE_0102', '粤A·T102', '本田', 'CR-V', 'SUV', '黑色', 5, 'FUEL', '', 'APPROVED', 1, NOW(), NOW(), 0),
    (900000000000008003, 900000000000000103, 'TEST_PLATE_0103', '粤B·T103', '比亚迪', '宋PLUS', 'SUV', '灰色', 5, 'HYBRID', '', 'APPROVED', 1, NOW(), NOW(), 0),
    (900000000000008004, 900000000000000104, 'TEST_PLATE_0104', '川A·T104', '坦克', '300', 'SUV', '绿色', 5, 'FUEL', '', 'APPROVED', 1, NOW(), NOW(), 0),
    (900000000000008005, 900000000000000105, 'TEST_PLATE_0105', '粤A·T105', '大众', '途岳', 'SUV', '蓝色', 5, 'FUEL', '', 'APPROVED', 1, NOW(), NOW(), 0),
    (900000000000008006, 900000000000000106, 'TEST_PLATE_0106', '粤B·T106', '特斯拉', 'Model Y', 'SUV', '白色', 5, 'EV', '', 'APPROVED', 1, NOW(), NOW(), 0),
    (900000000000008007, 900000000000000107, 'TEST_PLATE_0107', '粤E·T107', '吉利', '星越L', 'SUV', '银色', 5, 'FUEL', '', 'APPROVED', 1, NOW(), NOW(), 0),
    (900000000000008008, 900000000000000108, 'TEST_PLATE_0108', '浙A·T108', '理想', 'L7', 'SUV', '黑色', 5, 'HYBRID', '', 'APPROVED', 1, NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE
    brand = VALUES(brand),
    model = VALUES(model),
    vehicle_type = VALUES(vehicle_type),
    color = VALUES(color),
    seat_count = VALUES(seat_count),
    energy_type = VALUES(energy_type),
    certification_status = 'APPROVED',
    is_default = 1,
    updated_at = NOW(),
    deleted = 0;

INSERT INTO vehicle_certification
    (id, vehicle_id, user_id, owner_name, plate_no_cipher, plate_no_mask, vehicle_type,
     vin_cipher, vin_mask, engine_no_cipher, engine_no_mask, register_date, issue_date,
     issuing_authority, license_front_image_key, license_back_image_key,
     recognition_source, status, reject_reason, submitted_at, reviewed_at, reviewer_id)
VALUES
    (900000000000009001, 900000000000008001, 900000000000000101, '测试用户101', 'TEST_PLATE_0101', '粤A·T101', 'SUV', 'TEST_VIN_0101', '****0101', 'TEST_ENGINE_0101', '****0101', '2021-01-01', '2021-01-02', '测试车管所', 'test/vehicle/front-0101.jpg', NULL, 'TEST_SEED', 'APPROVED', '', NOW(), NOW(), 10001),
    (900000000000009002, 900000000000008002, 900000000000000102, '测试用户102', 'TEST_PLATE_0102', '粤A·T102', 'SUV', 'TEST_VIN_0102', '****0102', 'TEST_ENGINE_0102', '****0102', '2021-02-01', '2021-02-02', '测试车管所', 'test/vehicle/front-0102.jpg', NULL, 'TEST_SEED', 'APPROVED', '', NOW(), NOW(), 10001),
    (900000000000009003, 900000000000008003, 900000000000000103, '测试用户103', 'TEST_PLATE_0103', '粤B·T103', 'SUV', 'TEST_VIN_0103', '****0103', 'TEST_ENGINE_0103', '****0103', '2022-03-01', '2022-03-02', '测试车管所', 'test/vehicle/front-0103.jpg', NULL, 'TEST_SEED', 'APPROVED', '', NOW(), NOW(), 10001),
    (900000000000009004, 900000000000008004, 900000000000000104, '测试用户104', 'TEST_PLATE_0104', '川A·T104', 'SUV', 'TEST_VIN_0104', '****0104', 'TEST_ENGINE_0104', '****0104', '2020-04-01', '2020-04-02', '测试车管所', 'test/vehicle/front-0104.jpg', NULL, 'TEST_SEED', 'APPROVED', '', NOW(), NOW(), 10001),
    (900000000000009005, 900000000000008005, 900000000000000105, '测试用户105', 'TEST_PLATE_0105', '粤A·T105', 'SUV', 'TEST_VIN_0105', '****0105', 'TEST_ENGINE_0105', '****0105', '2022-05-01', '2022-05-02', '测试车管所', 'test/vehicle/front-0105.jpg', NULL, 'TEST_SEED', 'APPROVED', '', NOW(), NOW(), 10001),
    (900000000000009006, 900000000000008006, 900000000000000106, '测试用户106', 'TEST_PLATE_0106', '粤B·T106', 'SUV', 'TEST_VIN_0106', '****0106', 'TEST_ENGINE_0106', '****0106', '2023-06-01', '2023-06-02', '测试车管所', 'test/vehicle/front-0106.jpg', NULL, 'TEST_SEED', 'APPROVED', '', NOW(), NOW(), 10001),
    (900000000000009007, 900000000000008007, 900000000000000107, '测试用户107', 'TEST_PLATE_0107', '粤E·T107', 'SUV', 'TEST_VIN_0107', '****0107', 'TEST_ENGINE_0107', '****0107', '2024-07-01', '2024-07-02', '测试车管所', 'test/vehicle/front-0107.jpg', NULL, 'TEST_SEED', 'APPROVED', '', NOW(), NOW(), 10001),
    (900000000000009008, 900000000000008008, 900000000000000108, '测试用户108', 'TEST_PLATE_0108', '浙A·T108', 'SUV', 'TEST_VIN_0108', '****0108', 'TEST_ENGINE_0108', '****0108', '2021-08-01', '2021-08-02', '测试车管所', 'test/vehicle/front-0108.jpg', NULL, 'TEST_SEED', 'APPROVED', '', NOW(), NOW(), 10001)
ON DUPLICATE KEY UPDATE
    status = 'APPROVED',
    reviewed_at = NOW(),
    reviewer_id = 10001;

-- 6. 驾驶证认证：用于个人主页认证状态展示
INSERT INTO user_driving_license_certification
    (id, user_id, holder_name_cipher, license_no_cipher, license_no_mask, vehicle_class,
     first_issue_date, valid_from, valid_to, issuing_authority,
     license_front_image_key, license_back_image_key, recognition_source,
     certification_status, reject_reason, reviewer_id, submitted_at, reviewed_at,
     created_at, updated_at, deleted)
VALUES
    (900000000000010001, 900000000000000101, 'TEST_HOLDER_0101', 'TEST_LICENSE_0101', '****0101', 'C1', '2018-05-01', '2018-05-01', '2028-05-01', '测试车管所', 'test/license/front-0101.jpg', NULL, 'TEST_SEED', 'APPROVED', NULL, 10001, NOW(), NOW(), NOW(), NOW(), 0),
    (900000000000010002, 900000000000000102, 'TEST_HOLDER_0102', 'TEST_LICENSE_0102', '****0102', 'C1', '2019-06-01', '2019-06-01', '2029-06-01', '测试车管所', 'test/license/front-0102.jpg', NULL, 'TEST_SEED', 'APPROVED', NULL, 10001, NOW(), NOW(), NOW(), NOW(), 0),
    (900000000000010003, 900000000000000103, 'TEST_HOLDER_0103', 'TEST_LICENSE_0103', '****0103', 'C2', '2020-07-01', '2020-07-01', '2030-07-01', '测试车管所', 'test/license/front-0103.jpg', NULL, 'TEST_SEED', 'APPROVED', NULL, 10001, NOW(), NOW(), NOW(), NOW(), 0),
    (900000000000010004, 900000000000000104, 'TEST_HOLDER_0104', 'TEST_LICENSE_0104', '****0104', 'C1', '2016-08-01', '2016-08-01', '2036-08-01', '测试车管所', 'test/license/front-0104.jpg', NULL, 'TEST_SEED', 'APPROVED', NULL, 10001, NOW(), NOW(), NOW(), NOW(), 0),
    (900000000000010005, 900000000000000105, 'TEST_HOLDER_0105', 'TEST_LICENSE_0105', '****0105', 'C2', '2021-09-01', '2021-09-01', '2031-09-01', '测试车管所', 'test/license/front-0105.jpg', NULL, 'TEST_SEED', 'APPROVED', NULL, 10001, NOW(), NOW(), NOW(), NOW(), 0),
    (900000000000010006, 900000000000000106, 'TEST_HOLDER_0106', 'TEST_LICENSE_0106', '****0106', 'C1', '2017-10-01', '2017-10-01', '2027-10-01', '测试车管所', 'test/license/front-0106.jpg', NULL, 'TEST_SEED', 'APPROVED', NULL, 10001, NOW(), NOW(), NOW(), NOW(), 0),
    (900000000000010007, 900000000000000107, 'TEST_HOLDER_0107', 'TEST_LICENSE_0107', '****0107', 'C2', '2024-01-01', '2024-01-01', '2034-01-01', '测试车管所', 'test/license/front-0107.jpg', NULL, 'TEST_SEED', 'APPROVED', NULL, 10001, NOW(), NOW(), NOW(), NOW(), 0),
    (900000000000010008, 900000000000000108, 'TEST_HOLDER_0108', 'TEST_LICENSE_0108', '****0108', 'C1', '2015-12-01', '2015-12-01', '2035-12-01', '测试车管所', 'test/license/front-0108.jpg', NULL, 'TEST_SEED', 'APPROVED', NULL, 10001, NOW(), NOW(), NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE
    certification_status = 'APPROVED',
    reviewed_at = NOW(),
    reviewer_id = 10001,
    updated_at = NOW(),
    deleted = 0;

COMMIT;

-- 登录清单
SELECT
    a.phone,
    p.nickname,
    p.tongluxing_id,
    a.user_id,
    v.id AS default_vehicle_id,
    v.certification_status
FROM auth_account a
JOIN user_profile p ON p.user_id = a.user_id AND p.deleted = 0
LEFT JOIN vehicle_profile v ON v.user_id = a.user_id AND v.is_default = 1 AND v.deleted = 0
WHERE a.phone BETWEEN '13888888881' AND '13888888888'
  AND a.deleted = 0
ORDER BY a.phone;


-- 第二部分：公开行程、路线概览、车队与推荐结果
-- 前置：本文件第一部分已经写入测试用户和车辆。
-- 仅用于本地开发/测试库。脚本可重复执行，不会重复生成记录。

SELECT DATABASE() AS current_database;
SET NAMES utf8mb4;
START TRANSACTION;

-- 使用第一条测试行程作为稳定推荐源；目标行程写入后再参与匹配结果生成。
SET @source_trip_id := 920000000000010201;

-- 六条公开招募行程，出发时间以执行时刻为基准，避免测试数据因固定日期过期。
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

UPDATE trip
SET trip_type = 'DRIVER_TRIP',
    publisher_role = 'DRIVER',
    captain_user_id = user_id,
    auto_start_enabled = 1,
    arrival_status = 'NOT_ARRIVED'
WHERE id BETWEEN 920000000000010201 AND 920000000000010206;

-- 行程路线快照。这里只保存起点、途经点、终点构成的概览节点，不保存具体道路形状。
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

-- 每条测试行程都补一条群主成员快照。
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

-- 发现行程的申请加入链路依赖公开车队；为每条测试行程建立真实车队与队长成员。
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
    id, team_id, user_id, vehicle_id, owner_confirm_status, member_role, member_status,
    joined_at, nickname_snapshot, vehicle_snapshot, created_at, updated_at, deleted
)
SELECT
    920000000000080000 + (t.id - 920000000000010200),
    team.id, t.user_id, t.vehicle_id, 'NOT_REQUIRED', 'OWNER', 'ACTIVE',
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

-- 推荐页面读取 match_result；直接为当前测试源行程生成稳定的推荐结果。
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
        'seeded', TRUE
    ),
    'VALID', NOW(), NOW(), NOW(), 0
FROM trip t
JOIN trip source_trip ON source_trip.id = @source_trip_id
WHERE t.id BETWEEN 920000000000010201 AND 920000000000010206
  AND t.id <> @source_trip_id
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
