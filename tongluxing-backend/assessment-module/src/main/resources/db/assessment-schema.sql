create table if not exists assessment_level_mapping (
    id bigint not null,
    level_code varchar(16) not null,
    min_score decimal(8,2) not null,
    max_score decimal(8,2) not null,
    commission_rate decimal(8,4) not null,
    rank_weight decimal(8,4) not null,
    exclusion_radius_km decimal(8,2) not null default 0.00,
    mapping_status varchar(32) not null default 'ACTIVE',
    created_at datetime not null,
    updated_at datetime not null,
    deleted tinyint not null default 0,
    primary key (id),
    unique key uk_assessment_level (level_code, deleted),
    key idx_assessment_score_range (min_score, max_score, mapping_status)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

create table if not exists assessment_merchant_score (
    id bigint not null,
    merchant_id bigint not null,
    period varchar(16) not null,
    total_score decimal(8,2) not null,
    merchant_level varchar(16) not null,
    commission_rate decimal(8,4) not null,
    rank_weight decimal(8,4) not null,
    exclusion_radius_km decimal(8,2) not null default 0.00,
    calculate_status varchar(32) not null,
    calculated_at datetime not null,
    request_id varchar(128) not null,
    created_at datetime not null,
    updated_at datetime not null,
    deleted tinyint not null default 0,
    primary key (id),
    unique key uk_assessment_merchant_period (merchant_id, period, deleted),
    unique key uk_assessment_request (request_id, deleted),
    key idx_assessment_merchant_latest (merchant_id, calculated_at)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

create table if not exists assessment_merchant_score_item (
    id bigint not null,
    score_id bigint not null,
    metric_key varchar(64) not null,
    metric_value decimal(12,2) not null,
    score_delta decimal(8,2) not null,
    created_at datetime not null,
    deleted tinyint not null default 0,
    primary key (id),
    key idx_assessment_item_score (score_id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;


create table if not exists assessment_manual_adjustment (
    id bigint not null,
    merchant_id bigint not null,
    score_delta decimal(8,2) not null,
    reason varchar(255) not null,
    operator_id bigint not null,
    request_id varchar(128) not null,
    created_at datetime not null,
    updated_at datetime not null,
    deleted tinyint not null default 0,
    primary key (id),
    unique key uk_assessment_manual_adjustment_request (request_id, deleted),
    key idx_assessment_manual_adjustment_merchant (merchant_id, created_at)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;
