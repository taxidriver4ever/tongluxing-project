-- 聊天模块移除 MOCK 通道后的数据库调整。
--
-- 全新数据库：直接执行 01_reset_and_create_all_tables.sql 即可，其中默认值已经是 TENCENT_IM。
-- 已有数据库：执行一次本文件，把后续新建会话的 provider_type 默认值固定为 TENCENT_IM。
--
-- 注意：不要直接把历史 MOCK/LOCAL 行批量更新为 TENCENT_IM。
-- 旧群可能尚未在腾讯云创建，后端会在首次访问该会话时通过稳定 GroupId 幂等补建群组、
-- 同步有效成员，成功后才更新该行的 provider_type。

ALTER TABLE chat_conversation
    MODIFY COLUMN provider_type VARCHAR(32) NOT NULL DEFAULT 'TENCENT_IM' COMMENT '固定为腾讯云 IM';
