-- 腾讯 IM 发送后回调幂等索引。
--
-- 使用方法：已有数据库只需要执行一次本文件；全新环境执行
-- 01_reset_and_create_all_tables.sql 时已经包含同一个唯一索引。
-- 本脚本通过 information_schema 判断索引是否存在，因此可重复执行。

SET @chat_provider_index_exists := (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'chat_message'
      AND index_name = 'uk_chat_msg_provider'
);

SET @chat_provider_index_sql := IF(
    @chat_provider_index_exists = 0,
    'ALTER TABLE chat_message ADD UNIQUE KEY uk_chat_msg_provider (provider_message_key, deleted)',
    'SELECT ''uk_chat_msg_provider already exists'' AS migration_result'
);

PREPARE chat_provider_index_statement FROM @chat_provider_index_sql;
EXECUTE chat_provider_index_statement;
DEALLOCATE PREPARE chat_provider_index_statement;
