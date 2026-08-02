-- 车辆档案主表：保存用户当前可见车辆及认证汇总状态。
-- 敏感车牌拆分为稳定密文（服务端去重）和脱敏值（客户端展示）。
create table if not exists vehicle_profile (
   id bigint primary key comment '记录主键',
   user_id bigint not null comment '平台用户ID',
   plate_no_cipher varchar(256) null comment '车牌NO加密密文',
   plate_no_mask varchar(32) null comment '车牌NO脱敏展示值',
   brand varchar(64) not null default '' comment '品牌',
   model varchar(64) not null default '' comment '车型',
   vehicle_type varchar(32) not null default '' comment '车辆类型',
   color varchar(32) not null default '' comment '颜色',
   seat_count tinyint not null default 5 comment '座位数量',
   energy_type varchar(32) not null default '' comment '能源类型',
   vehicle_photo_image_key varchar(512) not null default '' comment '车辆照片图片标识或存储Key',
   certification_status varchar(32) not null default 'UNSUBMITTED' comment '认证状态',
   is_default tinyint not null default 0 comment '是否为默认记录：0否、1是',
   created_at datetime not null comment '记录创建时间',
   updated_at datetime not null comment '记录最后更新时间',
   deleted tinyint not null default 0 comment '逻辑删除标记：0未删除、1已删除',
   key idx_vehicle_profile_user (user_id, deleted),
   key idx_vehicle_profile_default (user_id, is_default, deleted),
   key idx_vehicle_profile_status (certification_status)
) comment='车辆资料表';

-- 车辆认证申请表：每次重新提交产生新记录，不覆盖历史。
-- vehicle_profile.certification_status 是当前状态快照，本表 status 是每次申请的审核证据。
create table if not exists vehicle_certification (
    id bigint primary key comment '记录主键',
    vehicle_id bigint not null comment '车辆ID',
    user_id bigint not null comment '平台用户ID',
    owner_name varchar(64) not null default '' comment '队长名称',
    plate_no_cipher varchar(256) null comment '车牌NO加密密文',
    plate_no_mask varchar(32) null comment '车牌NO脱敏展示值',
    vehicle_type varchar(32) not null default '' comment '车辆类型',
    vin_cipher varchar(256) null comment '车架号加密密文',
    vin_mask varchar(32) null comment '车架号脱敏展示值',
    engine_no_cipher varchar(256) null comment '发动机NO加密密文',
    engine_no_mask varchar(32) null comment '发动机NO脱敏展示值',
    register_date date null comment '注册日期',
    issue_date date null comment '签发日期',
    issuing_authority varchar(128) null comment '发证机关',
    license_front_image_key varchar(512) not null comment '驾驶证主页图片在对象存储中的文件Key',
    license_back_image_key varchar(512) null comment '驾驶证副页图片在对象存储中的文件Key',
    recognition_source varchar(32) not null default 'MINIPROGRAM_OCR' comment '识别来源',
    status varchar(32) not null default 'PENDING' comment '业务状态',
    reject_reason varchar(255) not null default '' comment '驳回原因',
    submitted_at datetime not null comment '提交时间',
    reviewed_at datetime null comment '审核时间',
    reviewer_id bigint null comment '审核人ID',
    key idx_vehicle_cert_vehicle (vehicle_id, submitted_at),
    key idx_vehicle_cert_user (user_id),
    key idx_vehicle_cert_status (status),
    key idx_vehicle_cert_plate_status (plate_no_cipher, status)
) comment='车辆认证表';

-- 认证附件明细：只保存对象存储 key/URL，不将图片二进制写入 MySQL。
create table if not exists vehicle_certification_image (
   id bigint primary key comment '记录主键',
   certification_id bigint not null comment '认证ID',
   vehicle_id bigint not null comment '车辆ID',
   image_type varchar(32) not null comment '图片类型',
   image_key varchar(512) not null comment '图片标识或存储Key',
   sort_no int not null default 0 comment '展示排序序号',
   created_at datetime not null comment '记录创建时间',
   deleted tinyint not null default 0 comment '逻辑删除标记：0未删除、1已删除',
   unique key uk_vehicle_cert_image (certification_id, image_type, sort_no, deleted),
   key idx_vehicle_cert_image_vehicle (vehicle_id),
   key idx_vehicle_cert_image_cert (certification_id)
) comment='车辆认证图片表';

-- 车辆操作审计表：保留创建、更新、删除、设默认和认证审核的前后 JSON 快照。
create table if not exists vehicle_audit_log (
                                                 id bigint primary key comment '记录主键',
                                                 vehicle_id bigint null comment '车辆ID',
                                                 user_id bigint not null comment '平台用户ID',
                                                 operation_type varchar(32) not null comment '操作类型',
                                                 before_snapshot text null comment '操作前数据快照',
                                                 after_snapshot text null comment '操作后数据快照',
                                                 remark varchar(255) null comment '业务备注',
                                                 created_at datetime not null comment '记录创建时间',
                                                 key idx_vehicle_audit_vehicle (vehicle_id),
                                                 key idx_vehicle_audit_user (user_id),
                                                 key idx_vehicle_audit_type (operation_type)
) comment='车辆审核日志表';
