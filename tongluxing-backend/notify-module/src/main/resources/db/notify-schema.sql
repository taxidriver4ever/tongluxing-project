create table if not exists notify_message (
    id bigint not null comment '记录主键',
    receiver_type varchar(32) not null comment '接收方类型',
    receiver_id bigint not null comment '接收方ID',
    scene varchar(64) not null default 'SYSTEM' comment '业务场景',
    event_type varchar(64) not null comment '事件类型',
    title varchar(128) not null comment '展示标题',
    content varchar(1024) not null comment '正文内容',
    target_type varchar(64) not null default '' comment '目标类型',
    target_id varchar(64) not null default '' comment '目标ID',
    read_status varchar(32) not null default 'UNREAD' comment '已读状态',
    read_at datetime null comment '已读时间',
    request_id varchar(128) not null comment '请求唯一标识，用于链路追踪或幂等控制',
    created_at datetime not null comment '记录创建时间',
    updated_at datetime not null comment '记录最后更新时间',
    deleted tinyint not null default 0 comment '逻辑删除标记：0未删除、1已删除',
    primary key (id),
    unique key uk_notify_message_request (request_id, deleted),
    key idx_notify_receiver (receiver_type, receiver_id, read_status, created_at),
    key idx_notify_target (target_type, target_id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='通知消息表';

create table if not exists notify_template (
    id bigint not null comment '记录主键',
    template_code varchar(64) not null comment '模板编码',
    channel varchar(32) not null comment '通知或投递渠道',
    title_template varchar(128) not null comment '标题模板',
    content_template varchar(1024) not null comment '内容模板',
    template_status varchar(32) not null default 'ACTIVE' comment '模板状态',
    created_at datetime not null comment '记录创建时间',
    updated_at datetime not null comment '记录最后更新时间',
    deleted tinyint not null default 0 comment '逻辑删除标记：0未删除、1已删除',
    primary key (id),
    unique key uk_notify_template_code (template_code, channel, deleted)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='通知模板表';

create table if not exists notify_delivery_log (
    id bigint not null comment '记录主键',
    message_id bigint not null comment '消息ID',
    channel varchar(32) not null comment '通知或投递渠道',
    delivery_status varchar(32) not null comment '投递状态',
    third_request_id varchar(128) not null default '' comment '第三方请求ID',
    error_message varchar(512) not null default '' comment '错误信息',
    retry_count int not null default 0 comment '任务已重试次数',
    next_retry_at datetime null comment '下一次计划重试时间',
    created_at datetime not null comment '记录创建时间',
    updated_at datetime not null comment '记录最后更新时间',
    deleted tinyint not null default 0 comment '逻辑删除标记：0未删除、1已删除',
    primary key (id),
    key idx_notify_delivery_message (message_id, channel),
    key idx_notify_delivery_retry (delivery_status, next_retry_at)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='通知投递日志表';
