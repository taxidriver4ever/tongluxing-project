create table if not exists customer_service_ticket (
    id bigint not null,
    creator_type varchar(32) not null,
    creator_id bigint not null,
    scene varchar(64) not null,
    target_type varchar(64) not null default '',
    target_id varchar(64) not null default '',
    title varchar(128) not null,
    content varchar(2048) not null,
    ticket_status varchar(32) not null default 'OPEN',
    priority varchar(32) not null default 'NORMAL',
    assigned_admin_id bigint null,
    request_id varchar(128) not null,
    created_at datetime not null,
    updated_at datetime not null,
    closed_at datetime null,
    deleted tinyint not null default 0,
    primary key (id),
    unique key uk_customer_service_ticket_request (request_id, deleted),
    key idx_customer_service_creator (creator_type, creator_id, created_at),
    key idx_customer_service_status (ticket_status, priority, created_at)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

create table if not exists customer_service_ticket_message (
    id bigint not null,
    ticket_id bigint not null,
    sender_type varchar(32) not null,
    sender_id bigint not null,
    message_type varchar(32) not null,
    content varchar(2048) not null,
    image_keys_json text null,
    request_id varchar(128) null,
    created_at datetime not null,
    deleted tinyint not null default 0,
    primary key (id),
    key idx_customer_service_message_ticket (ticket_id, created_at),
    unique key uk_customer_service_message_request (request_id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;
