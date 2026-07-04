create table if not exists notify_message (
    id bigint not null,
    receiver_type varchar(32) not null,
    receiver_id bigint not null,
    scene varchar(64) not null default 'SYSTEM',
    event_type varchar(64) not null,
    title varchar(128) not null,
    content varchar(1024) not null,
    target_type varchar(64) not null default '',
    target_id varchar(64) not null default '',
    read_status varchar(32) not null default 'UNREAD',
    read_at datetime null,
    request_id varchar(128) not null,
    created_at datetime not null,
    updated_at datetime not null,
    deleted tinyint not null default 0,
    primary key (id),
    unique key uk_notify_message_request (request_id, deleted),
    key idx_notify_receiver (receiver_type, receiver_id, read_status, created_at),
    key idx_notify_target (target_type, target_id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

create table if not exists notify_template (
    id bigint not null,
    template_code varchar(64) not null,
    channel varchar(32) not null,
    title_template varchar(128) not null,
    content_template varchar(1024) not null,
    template_status varchar(32) not null default 'ACTIVE',
    created_at datetime not null,
    updated_at datetime not null,
    deleted tinyint not null default 0,
    primary key (id),
    unique key uk_notify_template_code (template_code, channel, deleted)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

create table if not exists notify_delivery_log (
    id bigint not null,
    message_id bigint not null,
    channel varchar(32) not null,
    delivery_status varchar(32) not null,
    third_request_id varchar(128) not null default '',
    error_message varchar(512) not null default '',
    retry_count int not null default 0,
    next_retry_at datetime null,
    created_at datetime not null,
    updated_at datetime not null,
    deleted tinyint not null default 0,
    primary key (id),
    key idx_notify_delivery_message (message_id, channel),
    key idx_notify_delivery_retry (delivery_status, next_retry_at)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;
