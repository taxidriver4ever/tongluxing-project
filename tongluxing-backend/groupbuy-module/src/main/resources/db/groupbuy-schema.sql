create table if not exists groupbuy_activity (
    id bigint not null comment '记录主键',
    merchant_id bigint not null default 0 comment '商家ID',
    product_id bigint not null comment '商品ID',
    initiator_user_id bigint not null comment '发起人用户ID',
    target_people int not null comment '目标人数',
    current_people int not null default 0 comment '当前人数',
    group_price decimal(12,2) not null default 0.00 comment '拼团价格',
    ladder_price_json text null comment '阶梯价格JSON数据',
    activity_status varchar(32) not null comment '活动状态',
    start_at datetime not null comment '起点时间',
    expire_at datetime not null comment '过期时间',
    success_at datetime null comment '成功时间',
    failed_at datetime null comment '失败时间',
    created_at datetime not null comment '记录创建时间',
    updated_at datetime not null comment '记录最后更新时间',
    deleted tinyint not null default 0 comment '逻辑删除标记：0未删除、1已删除',
    primary key (id),
    key idx_groupbuy_activity_product (product_id, activity_status),
    key idx_groupbuy_activity_merchant (merchant_id, activity_status),
    key idx_groupbuy_activity_expire (activity_status, expire_at)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='拼团活动表';

create table if not exists groupbuy_participant (
    id bigint not null comment '记录主键',
    activity_id bigint not null comment '活动ID',
    order_id bigint not null comment '订单ID',
    user_id bigint not null comment '平台用户ID',
    participant_status varchar(32) not null comment '参与人状态',
    joined_at datetime not null comment '加入时间',
    paid_at datetime null comment '支付时间',
    refunded_at datetime null comment '退款时间',
    created_at datetime not null comment '记录创建时间',
    updated_at datetime not null comment '记录最后更新时间',
    deleted tinyint not null default 0 comment '逻辑删除标记：0未删除、1已删除',
    primary key (id),
    unique key uk_groupbuy_participant_user (activity_id, user_id, deleted),
    key idx_groupbuy_participant_order (order_id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='拼团参与人表';
