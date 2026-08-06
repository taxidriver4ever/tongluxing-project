-- 修复历史版本把公开同路行号写入 nickname 导致“我的”页面把编号当昵称展示的问题。
-- 仅清理由系统写入且与 tongluxing_id 完全相同的值，用户自行设置的真实昵称保持不变。
UPDATE user_profile
SET nickname = '', updated_at = NOW()
WHERE deleted = 0
  AND tongluxing_id IS NOT NULL
  AND TRIM(tongluxing_id) <> ''
  AND UPPER(TRIM(nickname)) = UPPER(TRIM(tongluxing_id));
