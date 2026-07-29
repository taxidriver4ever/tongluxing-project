-- 同路行密码登录最小 Mock 数据
-- 前置条件：auth_account、auth_password_credential 表已经创建。
-- 仅写入登录账号和 BCrypt 密码凭证，不创建用户资料、角色或其他业务数据。
--
-- 统一登录密码：12345678
-- 登录手机号：13910000001 ~ 13910000010

SET NAMES utf8mb4;
START TRANSACTION;

INSERT INTO auth_account
    (id, user_id, phone, account_status, mini_invite_onboarding_completed,
     last_login_time, last_login_ip, created_at, updated_at, deleted)
VALUES
    (910000000000001001, 910000000000000101, '13910000001', 1, 0, NULL, NULL, NOW(), NOW(), 0),
    (910000000000001002, 910000000000000102, '13910000002', 1, 0, NULL, NULL, NOW(), NOW(), 0),
    (910000000000001003, 910000000000000103, '13910000003', 1, 0, NULL, NULL, NOW(), NOW(), 0),
    (910000000000001004, 910000000000000104, '13910000004', 1, 0, NULL, NULL, NOW(), NOW(), 0),
    (910000000000001005, 910000000000000105, '13910000005', 1, 0, NULL, NULL, NOW(), NOW(), 0),
    (910000000000001006, 910000000000000106, '13910000006', 1, 0, NULL, NULL, NOW(), NOW(), 0),
    (910000000000001007, 910000000000000107, '13910000007', 1, 0, NULL, NULL, NOW(), NOW(), 0),
    (910000000000001008, 910000000000000108, '13910000008', 1, 0, NULL, NULL, NOW(), NOW(), 0),
    (910000000000001009, 910000000000000109, '13910000009', 1, 0, NULL, NULL, NOW(), NOW(), 0),
    (910000000000001010, 910000000000000110, '13910000010', 1, 0, NULL, NULL, NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE
    account_status = 1,
    updated_at = NOW(),
    deleted = 0;

-- 以下 10 个哈希均由项目使用的 BCryptPasswordEncoder 生成，
-- 并已使用 matches("12345678", hash) 逐一校验。
INSERT INTO auth_password_credential
    (id, user_id, password_hash, password_version, password_status,
     last_set_time, created_at, updated_at, deleted)
VALUES
    (910000000000002001, 910000000000000101, '$2a$10$6aOMz.3mrG2.Js.Y0pph8uA41T3m58VXfwPne/15FBUFd30KTlHOG', 'BCRYPT', 1, NOW(), NOW(), NOW(), 0),
    (910000000000002002, 910000000000000102, '$2a$10$xse1tU4Q5VKW3F06gH29d.u9A.gJ.2Jw0Fd2io79KPaHRGszlKXF6', 'BCRYPT', 1, NOW(), NOW(), NOW(), 0),
    (910000000000002003, 910000000000000103, '$2a$10$NzxoItIojF58bx84Yv8GF.DRNRzFWrj5bbUoDs1SWeb06xw4fR5WC', 'BCRYPT', 1, NOW(), NOW(), NOW(), 0),
    (910000000000002004, 910000000000000104, '$2a$10$rMYzObEfjjQK1kAFoymbc.Hwe8K6fT8yU6BZYdF2Oek5wLvl3cmka', 'BCRYPT', 1, NOW(), NOW(), NOW(), 0),
    (910000000000002005, 910000000000000105, '$2a$10$UqGxTJ/i/ztfnXAofjMTqe4tDTYNKLsW7ukSANRRCuJN0j5bYIg9u', 'BCRYPT', 1, NOW(), NOW(), NOW(), 0),
    (910000000000002006, 910000000000000106, '$2a$10$g9ZuomjS2PaO9yRiCCQyLuyLy4wMC37vpurJibYPCYvDpmTduKL8e', 'BCRYPT', 1, NOW(), NOW(), NOW(), 0),
    (910000000000002007, 910000000000000107, '$2a$10$ouRS/LS.AF4q4V6vdLg4Nuych2l2Kr4sYQmfeWRIsEeaVviIlk9iC', 'BCRYPT', 1, NOW(), NOW(), NOW(), 0),
    (910000000000002008, 910000000000000108, '$2a$10$nTihG/UyhtF4RM0XdI81Au0XjUkSDCl8Z16rDu.vMWiKVdnHL0ig2', 'BCRYPT', 1, NOW(), NOW(), NOW(), 0),
    (910000000000002009, 910000000000000109, '$2a$10$M.w2FjuSXuxwDl0TESHL2OWB2KbxGLLYzQE.mJ3KePyEy2IhBljHm', 'BCRYPT', 1, NOW(), NOW(), NOW(), 0),
    (910000000000002010, 910000000000000110, '$2a$10$d8JhJTv7d5AUvqfXBCXbIOZle8DaKewhmCG0rVx.HVvQSgtbEB6gC', 'BCRYPT', 1, NOW(), NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE
    password_hash = VALUES(password_hash),
    password_version = 'BCRYPT',
    password_status = 1,
    last_set_time = NOW(),
    updated_at = NOW(),
    deleted = 0;

COMMIT;

SELECT phone, user_id
FROM auth_account
WHERE phone BETWEEN '13910000001' AND '13910000010'
  AND account_status = 1
  AND deleted = 0
ORDER BY phone;
