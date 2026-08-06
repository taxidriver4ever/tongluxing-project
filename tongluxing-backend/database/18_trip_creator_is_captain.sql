-- 行程创建者即队长：历史数据修复脚本
-- 正常情况下后端启动会通过 mvp-v3-migration.sql 自动执行；此文件用于手工升级。

USE tongluxing;

START TRANSACTION;

UPDATE trip
SET captain_user_id = user_id,
    updated_at = CURRENT_TIMESTAMP
WHERE deleted = 0
  AND (captain_user_id IS NULL OR captain_user_id <> user_id);

COMMIT;
