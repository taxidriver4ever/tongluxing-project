create table if not exists verification_code
(
    id                bigint         not null comment '核销码ID',
    verification_code varchar(128)   not null comment '核销码',
    qr_content        varchar(512)   not null comment '二维码内容',
    biz_type          varchar(32)    not null comment '业务类型：ORDER、COUPON',
    biz_id            bigint         not null comment '业务ID',
    order_id          bigint         null comment '订单ID',
    user_coupon_id    bigint         null comment '用户券ID',
    user_id           bigint         not null comment '用户ID',
    merchant_id       bigint         not null comment '商家ID',
    amount            decimal(12, 2) not null default 0.00 comment '核销金额',
    code_status       varchar(32)    not null comment '核销码状态',
    expire_at         datetime       not null comment '过期时间',
    verified_at       datetime       null comment '核销时间',
    created_at        datetime       not null comment '创建时间',
    updated_at        datetime       not null comment '更新时间',
    deleted           tinyint        not null default 0 comment '逻辑删除',
    primary key (id),
    unique key uk_verification_code (verification_code),
    unique key uk_verification_biz (biz_type, biz_id, deleted),
    key idx_verification_code_merchant (merchant_id, code_status),
    key idx_verification_code_expire (code_status, expire_at)
) engine = InnoDB
  default charset = utf8mb4
  collate = utf8mb4_unicode_ci
  comment = '核销码表';

create table if not exists verification_record
(
    id                    bigint         not null comment '核销记录ID',
    verification_code_id  bigint         not null comment '核销码ID',
    verification_code     varchar(128)   not null comment '核销码快照',
    biz_type              varchar(32)    not null comment '业务类型',
    biz_id                bigint         not null comment '业务ID',
    order_id              bigint         null comment '订单ID',
    user_coupon_id        bigint         null comment '用户券ID',
    user_id               bigint         not null comment '用户ID',
    merchant_id           bigint         not null comment '商家ID',
    operator_id           bigint         not null comment '操作员ID',
    amount                decimal(12, 2) not null default 0.00 comment '核销金额',
    verification_status   varchar(32)    not null comment '核销状态',
    location_name         varchar(128)   null comment '核销地点',
    longitude             decimal(10, 6) null comment '经度',
    latitude              decimal(10, 6) null comment '纬度',
    verified_at           datetime       not null comment '核销时间',
    reversal_status       varchar(32)    not null default 'NONE' comment '撤销状态',
    created_at            datetime       not null comment '创建时间',
    updated_at            datetime       not null comment '更新时间',
    deleted               tinyint        not null default 0 comment '逻辑删除',
    primary key (id),
    unique key uk_verification_record_code (verification_code_id),
    key idx_verification_record_merchant (merchant_id, verified_at),
    key idx_verification_record_user (user_id, verified_at),
    key idx_verification_record_biz (biz_type, biz_id)
) engine = InnoDB
  default charset = utf8mb4
  collate = utf8mb4_unicode_ci
  comment = '核销记录表';

create table if not exists verification_reversal_request
(
    id              bigint       not null comment '申请ID',
    verification_id bigint       not null comment '核销记录ID',
    merchant_id     bigint       not null comment '商家ID',
    applicant_id    bigint       not null comment '申请人ID',
    reason          varchar(255) not null comment '撤销原因',
    audit_status    varchar(32)  not null comment '审核状态',
    reviewer_id     bigint       null comment '审核人ID',
    reviewed_at     datetime     null comment '审核时间',
    reject_reason   varchar(255) null comment '驳回原因',
    created_at      datetime     not null comment '创建时间',
    updated_at      datetime     not null comment '更新时间',
    deleted         tinyint      not null default 0 comment '逻辑删除',
    primary key (id),
    key idx_verification_reversal_record (verification_id),
    key idx_verification_reversal_status (audit_status, created_at)
) engine = InnoDB
  default charset = utf8mb4
  collate = utf8mb4_unicode_ci
  comment = '核销撤销申请表';

create table if not exists verification_compensation_task
(
    id              bigint        not null comment '任务ID',
    biz_type        varchar(64)   not null comment '业务类型',
    biz_id          varchar(64)   not null comment '业务ID',
    idempotent_key  varchar(128)  not null comment '幂等键',
    target_module   varchar(64)   not null comment '目标模块',
    request_payload text          not null comment '请求报文',
    task_status     varchar(32)   not null comment '任务状态',
    retry_count     int           not null default 0 comment '重试次数',
    next_retry_at   datetime      null comment '下次重试时间',
    last_error      varchar(1000) null comment '最近错误',
    created_at      datetime      not null comment '创建时间',
    updated_at      datetime      not null comment '更新时间',
    primary key (id),
    unique key uk_verification_compensation_idem (idempotent_key),
    key idx_verification_compensation_retry (task_status, next_retry_at),
    key idx_verification_compensation_biz (biz_type, biz_id)
) engine = InnoDB
  default charset = utf8mb4
  collate = utf8mb4_unicode_ci
  comment = '核销跨模块补偿任务表';
