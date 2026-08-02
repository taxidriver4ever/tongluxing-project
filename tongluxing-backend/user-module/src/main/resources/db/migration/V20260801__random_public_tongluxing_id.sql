-- V1.7：同路行号改为与内部 user_id 无关的随机公开编号。
-- 本脚本只处理历史资料；新用户由 UserServiceImpl 使用 SecureRandom 生成并在唯一键冲突时重试。

-- 先用 UUID、随机数和当前值共同产生不可逆的新编号。26 位随机摘要把迁移碰撞概率降到可忽略。
UPDATE user_profile
SET tongluxing_id = CONCAT(
        'TLX',
        UPPER(SUBSTRING(SHA2(CONCAT(UUID(), RAND(), tongluxing_id, NOW(6)), 256), 1, 26))
    )
WHERE deleted = 0;

-- 历史空昵称统一以新的公开同路行号兜底；用户已设置的昵称保持不变。
UPDATE user_profile
SET nickname = tongluxing_id
WHERE deleted = 0 AND (nickname IS NULL OR TRIM(nickname) = '');

-- 初始化表已包含约束；历史库若缺失则动态补齐，保证迁移可同时用于新库和旧库。
SET @has_tongluxing_id_unique = (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'user_profile'
      AND index_name = 'uk_user_profile_tongluxing_id'
);
SET @add_tongluxing_id_unique_sql = IF(
    @has_tongluxing_id_unique = 0,
    'ALTER TABLE user_profile ADD UNIQUE KEY uk_user_profile_tongluxing_id (tongluxing_id)',
    'SELECT 1'
);
PREPARE add_tongluxing_id_unique_stmt FROM @add_tongluxing_id_unique_sql;
EXECUTE add_tongluxing_id_unique_stmt;
DEALLOCATE PREPARE add_tongluxing_id_unique_stmt;
