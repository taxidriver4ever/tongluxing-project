-- 用户域初始化表结构。
-- 约定：业务主表使用雪花 ID；deleted=0 表示有效数据；图片字段保存对象存储 Key，
-- 不保存带有效期的访问 URL。脚本使用 if not exists，便于开发环境重复执行。

-- 用户基础资料：一名平台用户只能有一条有效资料，tongluxing_id 是面向用户展示的
-- 稳定公开号码，与内部 user_id 分离，创建后不应再修改。
create table if not exists user_profile (
    id bigint not null comment '用户资料记录主键，使用雪花算法生成',
    user_id bigint not null comment '平台内部用户ID，用于关联认证、隐私设置及其他业务数据',
    tongluxing_id varchar(32) not null comment '面向用户公开展示且创建后不可修改的同路行号',
    nickname varchar(32) not null default '' comment '用户昵称',
    avatar_image_key varchar(512) not null default '' comment '用户头像在对象存储中的文件Key，不保存临时访问URL',
    gender tinyint not null default 0 comment '用户性别编码：0未知、1男、2女',
    birthday date null comment '用户生日',
    city_code varchar(16) null comment '用户所在城市的标准行政区划编码',
    city_name varchar(64) null comment '用户所在城市的展示名称',
    bio varchar(200) null comment '用户个人简介',
    profile_status varchar(16) not null default 'ACTIVE' comment '用户资料状态，例如ACTIVE表示正常可用',
    created_at datetime not null default current_timestamp comment '用户资料创建时间',
    updated_at datetime not null default current_timestamp on update current_timestamp comment '用户资料最后更新时间',
    deleted tinyint not null default 0 comment '逻辑删除标记：0未删除、1已删除',
    primary key (id),
    -- 逻辑删除字段参与唯一约束，使同一用户在任一时刻只能有一条有效资料。
    unique key uk_user_profile_user (user_id, deleted),
    -- 同路行号需要全局唯一，公开搜索可据此精确定位用户。
    unique key uk_user_profile_tongluxing_id (tongluxing_id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='用户基础资料表';

-- 驾驶证认证申请：每次提交保留独立历史记录。holder_name_cipher 与
-- license_no_cipher 保存 AES-GCM 密文，普通列表只使用 license_no_mask。
create table if not exists user_driving_license_certification (
    id bigint not null comment '驾驶证认证申请主键，使用雪花算法生成',
    user_id bigint not null comment '提交驾驶证认证的用户ID',
    holder_name_cipher varchar(256) not null comment '驾驶证持证人姓名的AES-GCM加密密文',
    license_no_cipher varchar(512) not null comment '完整驾驶证号码的AES-GCM加密密文',
    license_no_mask varchar(32) not null comment '用于列表安全展示的脱敏驾驶证号码',
    vehicle_class varchar(32) not null comment '驾驶证准驾车型',
    first_issue_date date null comment '驾驶证初次领证日期',
    valid_from date null comment '驾驶证当前有效期开始日期',
    valid_to date null comment '驾驶证当前有效期截止日期',
    issuing_authority varchar(128) null comment '驾驶证发证机关名称',
    license_front_image_key varchar(512) not null comment '驾驶证主页图片在对象存储中的文件Key',
    license_back_image_key varchar(512) null comment '驾驶证副页图片在对象存储中的文件Key',
    recognition_source varchar(32) not null default 'MINIPROGRAM_OCR' comment '证件信息识别来源：MINIPROGRAM_OCR小程序识别、MANUAL_UPLOAD手动上传',
    -- 业务服务在材料齐全时显式写入 APPROVED；数据库默认 PENDING 用于拦截绕过服务层的异常写入。
    certification_status varchar(20) not null default 'PENDING' comment '认证状态：APPROVED系统自动通过、PENDING历史待处理、REJECTED异常驳回',
    reject_reason varchar(255) null comment '认证审核未通过时的驳回原因',
    reviewer_id bigint null comment '执行认证审核的后台管理员用户ID',
    submitted_at datetime not null comment '用户提交本次认证申请的时间',
    reviewed_at datetime null comment '系统自动通过或后台完成异常复核的时间',
    created_at datetime not null default current_timestamp comment '认证申请记录创建时间',
    updated_at datetime not null default current_timestamp on update current_timestamp comment '认证申请记录最后更新时间',
    deleted tinyint not null default 0 comment '逻辑删除标记：0未删除、1已删除',
    primary key (id),
    -- 支持按用户快速查询最近一次提交。
    key idx_driver_cert_user_submit (user_id, submitted_at),
    -- 支持后台按状态、提交时间分页审核。
    key idx_driver_cert_status_submit (certification_status, submitted_at),
    -- 支持后台按审核员和审核时间审计。
    key idx_driver_cert_reviewer (reviewer_id, reviewed_at)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='用户驾驶证认证申请及审核记录表';

-- 用户隐私设置：主页总可见性与城市、简介、统计等细粒度开关分开保存。
-- Service 使用增量更新语义，未传入的字段会先与本表旧值合并。
create table if not exists user_privacy_setting (
    id bigint not null comment '用户隐私设置记录主键，使用雪花算法生成',
    user_id bigint not null comment '隐私设置所属的平台用户ID',
    profile_visibility varchar(16) not null default 'PUBLIC' comment '用户公开主页可见范围：PUBLIC公开、PRIVATE私密',
    vehicle_visibility varchar(16) not null default 'TEAM_ONLY' comment '用户车辆信息可见范围：PUBLIC公开、TEAM_ONLY仅队友、PRIVATE私密',
    invite_enabled_flag tinyint not null default 1 comment '是否启用邀请功能：0关闭、1启用',
    city_visible_flag tinyint not null default 1 comment '是否在公开主页展示所在城市：0隐藏、1展示',
    bio_visible_flag tinyint not null default 1 comment '是否在公开主页展示个人简介：0隐藏、1展示',
    trip_stats_visible_flag tinyint not null default 1 comment '是否在公开主页展示行程统计：0隐藏、1展示',
    level_visible_flag tinyint not null default 1 comment '是否在公开主页展示用户等级：0隐藏、1展示',
    location_enabled_flag tinyint not null default 1 comment '是否允许使用定位相关功能：0关闭、1启用',
    notification_enabled_flag tinyint not null default 1 comment '是否允许接收业务通知：0关闭、1启用',
    created_at datetime not null default current_timestamp comment '隐私设置记录创建时间',
    updated_at datetime not null default current_timestamp on update current_timestamp comment '隐私设置最后更新时间',
    deleted tinyint not null default 0 comment '逻辑删除标记：0未删除、1已删除',
    primary key (id),
    -- 防止并发懒初始化为同一用户创建两条有效设置。
    unique key uk_user_privacy_user (user_id, deleted)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='用户隐私及功能授权设置表';

-- 用户行程累计统计：以 user_id 为主键，一名用户至多一条聚合记录。
-- 资料查询采用 LEFT JOIN + coalesce，因此统计尚未生成时接口统一展示为 0。
create table if not exists user_statistics (
    user_id bigint not null comment '平台用户ID，同时作为本表主键',
    total_trip_count int not null default 0 comment '用户累计完成或参与的行程数量',
    total_distance_meters bigint not null default 0 comment '用户累计行程距离，单位为米',
    total_duration_minutes bigint not null default 0 comment '用户累计行程时长，单位为分钟',
    completed_waypoint_count int not null default 0 comment '用户累计完成的行程途经点数量',
    updated_at datetime not null default current_timestamp on update current_timestamp comment '用户行程统计最后更新时间',
    primary key (user_id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='用户行程累计统计表';

-- 有方向的关注关系：follower_user_id 主动关注 followed_user_id。
-- 正反两条关系同时存在时才构成互关，自关注由 Service 层禁止。
create table if not exists user_follow (
    id bigint not null comment '用户关注关系主键，使用雪花算法生成',
    follower_user_id bigint not null comment '发起关注行为的用户ID',
    followed_user_id bigint not null comment '被关注的目标用户ID',
    created_at datetime not null default current_timestamp comment '关注关系建立时间',
    primary key (id),
    -- 唯一索引既防止重复关系，也为并发关注提供数据库级幂等保障。
    unique key uk_user_follow_relation (follower_user_id, followed_user_id),
    -- 分别优化“谁关注了我”和“我关注了谁”的倒序分页。
    key idx_user_follow_followed (followed_user_id, created_at),
    key idx_user_follow_follower (follower_user_id, created_at)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='用户关注关系表';
