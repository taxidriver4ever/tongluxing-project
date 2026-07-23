-- 发现行程公开主页关注闭环迁移。
-- 项目本地开发仍会通过 user-schema.sql 幂等建表；生产环境可单独执行本文件。
create table if not exists user_follow (
    id bigint not null,
    follower_user_id bigint not null,
    followed_user_id bigint not null,
    created_at datetime not null default current_timestamp,
    primary key (id),
    unique key uk_user_follow_relation (follower_user_id, followed_user_id),
    key idx_user_follow_followed (followed_user_id, created_at),
    key idx_user_follow_follower (follower_user_id, created_at)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;
