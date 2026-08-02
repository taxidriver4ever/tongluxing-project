create table if not exists assessment_level_mapping (
    id bigint not null comment '记录主键',
    level_code varchar(16) not null comment '等级编码',
    min_score decimal(8,2) not null comment '最低评分',
    max_score decimal(8,2) not null comment '最高评分',
    commission_rate decimal(8,4) not null comment '佣金比例',
    rank_weight decimal(8,4) not null comment '排序权重',
    exclusion_radius_km decimal(8,2) not null default 0.00 comment '排除半径，单位为公里',
    mapping_status varchar(32) not null default 'ACTIVE' comment '映射状态',
    created_at datetime not null comment '记录创建时间',
    updated_at datetime not null comment '记录最后更新时间',
    deleted tinyint not null default 0 comment '逻辑删除标记：0未删除、1已删除',
    primary key (id),
    unique key uk_assessment_level (level_code, deleted),
    key idx_assessment_score_range (min_score, max_score, mapping_status)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='考核等级映射表';

create table if not exists assessment_merchant_score (
    id bigint not null comment '记录主键',
    merchant_id bigint not null comment '商家ID',
    assessment_period varchar(16) not null comment '考核周期',
    total_score decimal(8,2) not null comment '总计评分',
    merchant_level varchar(16) not null comment '商家等级',
    commission_rate decimal(8,4) not null comment '佣金比例',
    rank_weight decimal(8,4) not null comment '排序权重',
    exclusion_radius_km decimal(8,2) not null default 0.00 comment '排除半径，单位为公里',
    calculate_status varchar(32) not null comment '计算状态',
    calculated_at datetime not null comment '计算完成时间',
    request_id varchar(128) not null comment '请求唯一标识，用于链路追踪或幂等控制',
    created_at datetime not null comment '记录创建时间',
    updated_at datetime not null comment '记录最后更新时间',
    deleted tinyint not null default 0 comment '逻辑删除标记：0未删除、1已删除',
    primary key (id),
    unique key uk_assessment_merchant_assessment_period (merchant_id, assessment_period, deleted),
    unique key uk_assessment_request (request_id, deleted),
    key idx_assessment_merchant_latest (merchant_id, calculated_at)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='考核商家评分表';

create table if not exists assessment_merchant_score_item (
    id bigint not null comment '记录主键',
    score_id bigint not null comment '评分ID',
    metric_key varchar(64) not null comment '指标标识或存储Key',
    metric_value decimal(12,2) not null comment '指标值',
    score_delta decimal(8,2) not null comment '评分调整值',
    created_at datetime not null comment '记录创建时间',
    deleted tinyint not null default 0 comment '逻辑删除标记：0未删除、1已删除',
    primary key (id),
    key idx_assessment_item_score (score_id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='考核商家评分事项表';


create table if not exists assessment_manual_adjustment (
    id bigint not null comment '记录主键',
    merchant_id bigint not null comment '商家ID',
    score_delta decimal(8,2) not null comment '评分调整值',
    reason varchar(255) not null comment '原因说明',
    operator_id bigint not null comment '管理员ID',
    request_id varchar(128) not null comment '请求唯一标识，用于链路追踪或幂等控制',
    created_at datetime not null comment '记录创建时间',
    updated_at datetime not null comment '记录最后更新时间',
    deleted tinyint not null default 0 comment '逻辑删除标记：0未删除、1已删除',
    primary key (id),
    unique key uk_assessment_manual_adjustment_request (request_id, deleted),
    key idx_assessment_manual_adjustment_merchant (merchant_id, created_at)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='考核人工调整表';
