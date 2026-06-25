create table if not exists groupbuy_activity (
    id bigint not null,
    merchant_id bigint not null default 0,
    product_id bigint not null,
    initiator_user_id bigint not null,
    target_people int not null,
    current_people int not null default 0,
    group_price decimal(12,2) not null default 0.00,
    ladder_price_json text null,
    activity_status varchar(32) not null,
    start_at datetime not null,
    expire_at datetime not null,
    success_at datetime null,
    failed_at datetime null,
    created_at datetime not null,
    updated_at datetime not null,
    deleted tinyint not null default 0,
    primary key (id),
    key idx_groupbuy_activity_product (product_id, activity_status),
    key idx_groupbuy_activity_merchant (merchant_id, activity_status),
    key idx_groupbuy_activity_expire (activity_status, expire_at)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

create table if not exists groupbuy_participant (
    id bigint not null,
    activity_id bigint not null,
    order_id bigint not null,
    user_id bigint not null,
    participant_status varchar(32) not null,
    joined_at datetime not null,
    paid_at datetime null,
    refunded_at datetime null,
    created_at datetime not null,
    updated_at datetime not null,
    deleted tinyint not null default 0,
    primary key (id),
    unique key uk_groupbuy_participant_user (activity_id, user_id, deleted),
    key idx_groupbuy_participant_order (order_id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

