-- 同路行开发/联调测试账号
-- 执行前置：先执行 01_reset_and_create_all_tables.sql。
--
-- 所有账号统一密码：12345678
-- 手机号范围：13888888881 ~ 13888888888
-- 这些账号、车辆和证件信息均为虚构测试数据，不得用于生产环境。

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
