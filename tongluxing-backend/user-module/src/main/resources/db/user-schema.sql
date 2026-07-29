-- 用户域初始化表结构。
-- 约定：业务主表使用雪花 ID；deleted=0 表示有效数据；图片字段保存对象存储 Key，
-- 不保存带有效期的访问 URL。脚本使用 if not exists，便于开发环境重复执行。

-- 用户基础资料：一名平台用户只能有一条有效资料，tongluxing_id 是面向用户展示的
-- 稳定公开号码，与内部 user_id 分离，创建后不应再修改。
create table if not exists user_profile (
    id bigint not null,
    user_id bigint not null,
    tongluxing_id varchar(32) not null,
    nickname varchar(32) not null default '',
    avatar_image_key varchar(512) not null default '',
    gender tinyint not null default 0,
    birthday date null,
    city_code varchar(16) null,
    city_name varchar(64) null,
    bio varchar(200) null,
    profile_status varchar(16) not null default 'ACTIVE',
    created_at datetime not null default current_timestamp,
    updated_at datetime not null default current_timestamp on update current_timestamp,
    deleted tinyint not null default 0,
    primary key (id),
    -- 逻辑删除字段参与唯一约束，使同一用户在任一时刻只能有一条有效资料。
    unique key uk_user_profile_user (user_id, deleted),
    -- 同路行号需要全局唯一，公开搜索可据此精确定位用户。
    unique key uk_user_profile_tongluxing_id (tongluxing_id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

-- 驾驶证认证申请：每次提交保留独立历史记录。holder_name_cipher 与
-- license_no_cipher 保存 AES-GCM 密文，普通列表只使用 license_no_mask。
create table if not exists user_driving_license_certification (
    id bigint not null,
    user_id bigint not null,
    holder_name_cipher varchar(256) not null,
    license_no_cipher varchar(512) not null,
    license_no_mask varchar(32) not null,
    vehicle_class varchar(32) not null,
    first_issue_date date null,
    valid_from date null,
    valid_to date null,
    issuing_authority varchar(128) null,
    license_front_image_key varchar(512) not null,
    license_back_image_key varchar(512) null,
    recognition_source varchar(32) not null default 'MINIPROGRAM_OCR',
    -- 状态流转：PENDING -> APPROVED/REJECTED；终态不得被再次覆盖。
    certification_status varchar(20) not null default 'PENDING',
    reject_reason varchar(255) null,
    reviewer_id bigint null,
    submitted_at datetime not null,
    reviewed_at datetime null,
    created_at datetime not null default current_timestamp,
    updated_at datetime not null default current_timestamp on update current_timestamp,
    deleted tinyint not null default 0,
    primary key (id),
    -- 支持按用户快速查询最近一次提交。
    key idx_driver_cert_user_submit (user_id, submitted_at),
    -- 支持后台按状态、提交时间分页审核。
    key idx_driver_cert_status_submit (certification_status, submitted_at),
    -- 支持后台按审核员和审核时间审计。
    key idx_driver_cert_reviewer (reviewer_id, reviewed_at)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

-- 用户隐私设置：主页总可见性与城市、简介、统计等细粒度开关分开保存。
-- Service 使用增量更新语义，未传入的字段会先与本表旧值合并。
create table if not exists user_privacy_setting (
    id bigint not null,
    user_id bigint not null,
    profile_visibility varchar(16) not null default 'PUBLIC',
    vehicle_visibility varchar(16) not null default 'TEAM_ONLY',
    invite_enabled_flag tinyint not null default 1,
    city_visible_flag tinyint not null default 1,
    bio_visible_flag tinyint not null default 1,
    trip_stats_visible_flag tinyint not null default 1,
    level_visible_flag tinyint not null default 1,
    location_enabled_flag tinyint not null default 1,
    notification_enabled_flag tinyint not null default 1,
    created_at datetime not null default current_timestamp,
    updated_at datetime not null default current_timestamp on update current_timestamp,
    deleted tinyint not null default 0,
    primary key (id),
    -- 防止并发懒初始化为同一用户创建两条有效设置。
    unique key uk_user_privacy_user (user_id, deleted)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

-- 用户行程累计统计：以 user_id 为主键，一名用户至多一条聚合记录。
-- 资料查询采用 LEFT JOIN + coalesce，因此统计尚未生成时接口统一展示为 0。
create table if not exists user_statistics (
    user_id bigint not null,
    total_trip_count int not null default 0,
    total_distance_meters bigint not null default 0,
    total_duration_minutes bigint not null default 0,
    completed_waypoint_count int not null default 0,
    updated_at datetime not null default current_timestamp on update current_timestamp,
    primary key (user_id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

-- 有方向的关注关系：follower_user_id 主动关注 followed_user_id。
-- 正反两条关系同时存在时才构成互关，自关注由 Service 层禁止。
create table if not exists user_follow (
    id bigint not null,
    follower_user_id bigint not null,
    followed_user_id bigint not null,
    created_at datetime not null default current_timestamp,
    primary key (id),
    -- 唯一索引既防止重复关系，也为并发关注提供数据库级幂等保障。
    unique key uk_user_follow_relation (follower_user_id, followed_user_id),
    -- 分别优化“谁关注了我”和“我关注了谁”的倒序分页。
    key idx_user_follow_followed (followed_user_id, created_at),
    key idx_user_follow_follower (follower_user_id, created_at)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;
