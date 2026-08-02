create table if not exists customer_service_ticket (
    id bigint not null comment '记录主键',
    creator_type varchar(32) not null comment '创建人类型',
    creator_id bigint not null comment '创建人ID',
    scene varchar(64) not null comment '业务场景',
    target_type varchar(64) not null default '' comment '目标类型',
    target_id varchar(64) not null default '' comment '目标ID',
    title varchar(128) not null comment '展示标题',
    content varchar(2048) not null comment '正文内容',
    ticket_status varchar(32) not null default 'OPEN' comment '工单状态',
    priority varchar(32) not null default 'NORMAL' comment '优先级',
    assigned_admin_id bigint null comment '分配后台ID',
    request_id varchar(128) not null comment '请求唯一标识，用于链路追踪或幂等控制',
    created_at datetime not null comment '记录创建时间',
    updated_at datetime not null comment '记录最后更新时间',
    closed_at datetime null comment '关闭时间',
    deleted tinyint not null default 0 comment '逻辑删除标记：0未删除、1已删除',
    primary key (id),
    unique key uk_customer_service_ticket_request (request_id, deleted),
    key idx_customer_service_creator (creator_type, creator_id, created_at),
    key idx_customer_service_status (ticket_status, priority, created_at)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='客服工单表';

create table if not exists customer_service_ticket_message (
    id bigint not null comment '记录主键',
    ticket_id bigint not null comment '工单ID',
    sender_type varchar(32) not null comment '发送人类型',
    sender_id bigint not null comment '发送人ID',
    message_type varchar(32) not null comment '消息类型',
    content varchar(2048) not null comment '正文内容',
    image_keys_json text null comment '图片Key列表JSON数据',
    request_id varchar(128) null comment '请求唯一标识，用于链路追踪或幂等控制',
    created_at datetime not null comment '记录创建时间',
    deleted tinyint not null default 0 comment '逻辑删除标记：0未删除、1已删除',
    primary key (id),
    key idx_customer_service_message_ticket (ticket_id, created_at),
    unique key uk_customer_service_message_request (request_id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='客服工单消息表';
