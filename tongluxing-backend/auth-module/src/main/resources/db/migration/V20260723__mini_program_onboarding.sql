-- 小程序首次登录状态机。
-- 该项目当前未启用 Flyway，需要在现有数据库中手动执行本文件一次。
-- 使用 information_schema 判断字段是否存在，因此重复执行也不会报 Duplicate column。

SET @tlx_column_exists = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'auth_account'
      AND COLUMN_NAME = 'mini_invite_onboarding_completed'
);

SET @tlx_ddl = IF(
    @tlx_column_exists = 0,
    'ALTER TABLE auth_account ADD COLUMN mini_invite_onboarding_completed TINYINT NOT NULL DEFAULT 0 COMMENT ''0 pending, 1 completed'' AFTER account_status',
    'SELECT ''mini_invite_onboarding_completed already exists'''
);

PREPARE tlx_stmt FROM @tlx_ddl;
EXECUTE tlx_stmt;
DEALLOCATE PREPARE tlx_stmt;

-- 已有联调账号默认视为已完成，避免升级后再次被强制引导。
UPDATE auth_account
SET mini_invite_onboarding_completed = 1
WHERE phone = '13888888888' AND deleted = 0;
