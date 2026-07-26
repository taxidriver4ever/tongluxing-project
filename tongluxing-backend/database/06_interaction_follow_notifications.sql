-- “谁关注了我”真实消息与未读角标修复。
-- 新关注会由 user-module 实时写入 notify_message；这里为升级前已经存在的
-- 关注关系补齐消息，确保列表数量和互动消息角标都有真实数据库来源。

insert ignore into notify_message
    (id, receiver_type, receiver_id, scene, event_type, title, content,
     target_type, target_id, read_status, request_id, created_at, updated_at, deleted)
select
    f.id,
    'USER',
    f.followed_user_id,
    'INTERACTION',
    'USER_FOLLOW',
    '新的关注',
    concat(coalesce(nullif(p.nickname, ''), '同路行用户'), '关注了你'),
    'USER',
    cast(f.follower_user_id as char),
    'UNREAD',
    concat('USER_FOLLOW:', f.id),
    f.created_at,
    f.created_at,
    0
from user_follow f
left join user_profile p
       on p.user_id = f.follower_user_id and p.deleted = 0;

set @interaction_index_exists = (
    select count(*)
    from information_schema.statistics
    where table_schema = database()
      and table_name = 'notify_message'
      and index_name = 'idx_notify_interaction_unread'
);
set @interaction_index_ddl = if(
    @interaction_index_exists = 0,
    'create index idx_notify_interaction_unread on notify_message(receiver_id, scene, event_type, read_status, deleted)',
    'select 1'
);
prepare interaction_index_stmt from @interaction_index_ddl;
execute interaction_index_stmt;
deallocate prepare interaction_index_stmt;
