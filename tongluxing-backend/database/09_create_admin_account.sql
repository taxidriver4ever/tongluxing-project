-- 同路行 Admin 运营账号初始化脚本
-- 默认登录名：admin
-- 默认密码：Admin@123456
--
-- 重要：
-- 当前后端的 AdminAuthServiceImpl 使用环境变量校验登录凭据，不读取本表的 password_hash。
-- 要使用上述账号登录后台，还需要在运行后端的 .env 中保持：
-- ADMIN_AUTH_USERNAME=admin
-- ADMIN_AUTH_PASSWORD=Admin@123456
-- ADMIN_AUTH_OPERATOR_ID=10001
--
-- password_hash 使用 MySQL SHA-256 保存，避免在 admin_operator 表中保存明文密码。
-- 本脚本可重复执行，会恢复账号、角色及关联关系为有效状态。

CREATE DATABASE IF NOT EXISTS `tongluxing`
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE `tongluxing`;
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

SET @admin_username = 'admin';
SET @admin_password = 'Admin@123456';
SET @admin_display_name = '超级管理员';
SET @admin_operator_id = 10001;
SET @admin_role_id = 20001;
SET @admin_relation_id = 100010001;

INSERT INTO admin_operator (
    id,
    username,
    display_name,
    phone,
    password_hash,
    operator_status,
    last_login_at,
    created_at,
    updated_at,
    deleted
)
VALUES (
    @admin_operator_id,
    @admin_username,
    @admin_display_name,
    NULL,
    SHA2(@admin_password, 256),
    'ACTIVE',
    NULL,
    NOW(),
    NOW(),
    0
)
ON DUPLICATE KEY UPDATE
    display_name = VALUES(display_name),
    password_hash = VALUES(password_hash),
    operator_status = 'ACTIVE',
    updated_at = NOW(),
    deleted = 0;

INSERT INTO admin_role (
    id,
    role_code,
    role_name,
    permission_json,
    role_status,
    created_at,
    updated_at,
    deleted
)
VALUES (
    @admin_role_id,
    'SUPER_ADMIN',
    '超级管理员',
    '{"permissions":["*"]}',
    'ACTIVE',
    NOW(),
    NOW(),
    0
)
ON DUPLICATE KEY UPDATE
    role_name = VALUES(role_name),
    permission_json = VALUES(permission_json),
    role_status = 'ACTIVE',
    updated_at = NOW(),
    deleted = 0;

SET @actual_admin_operator_id = (
    SELECT id
    FROM admin_operator
    WHERE username = @admin_username
      AND deleted = 0
    LIMIT 1
);

SET @actual_admin_role_id = (
    SELECT id
    FROM admin_role
    WHERE role_code = 'SUPER_ADMIN'
      AND deleted = 0
    LIMIT 1
);

INSERT INTO admin_operator_role (
    id,
    operator_id,
    role_id,
    created_at,
    deleted
)
VALUES (
    @admin_relation_id,
    @actual_admin_operator_id,
    @actual_admin_role_id,
    NOW(),
    0
)
ON DUPLICATE KEY UPDATE
    operator_id = VALUES(operator_id),
    role_id = VALUES(role_id),
    deleted = 0;

SELECT
    operator.id,
    operator.username,
    operator.display_name,
    operator.operator_status,
    role.role_code,
    role.role_name
FROM admin_operator operator
JOIN admin_operator_role relation
  ON relation.operator_id = operator.id
 AND relation.deleted = 0
JOIN admin_role role
  ON role.id = relation.role_id
 AND role.deleted = 0
WHERE operator.username = @admin_username
  AND operator.deleted = 0;
