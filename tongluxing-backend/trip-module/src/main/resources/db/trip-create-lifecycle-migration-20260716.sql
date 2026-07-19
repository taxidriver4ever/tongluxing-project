-- 创建行程闭环增量迁移（兼容已有 trip 数据，不删除历史字段和接口）。
alter table trip
    add column title varchar(128) not null default '' after vehicle_id,
    add column description varchar(1000) not null default '' after title,
    add column expected_people int null after description,
    add column actual_start_time datetime null after remark,
    add column actual_end_time datetime null after actual_start_time;

alter table trip_draft
    add column title varchar(128) not null default '' after user_id,
    add column description varchar(1000) not null default '' after title,
    modify column start_location_json json null,
    modify column end_location_json json null,
    modify column departure_time datetime null,
    modify column duration_days int null,
    modify column people_count int null;

alter table trip_route
    modify column trip_id bigint null,
    add column draft_id bigint null after trip_id,
    add column route_plan_id bigint null after draft_id,
    add column provider_type varchar(32) not null default 'MOCK' after plan_duration,
    add column route_status varchar(16) not null default 'VALID' after provider_type,
    add unique key uk_trip_route_draft (draft_id, deleted);

alter table trip_waypoint
    modify column trip_id bigint null,
    add column draft_id bigint null after trip_id,
    add column place_address varchar(255) not null default '' after place_name,
    add column waypoint_type varchar(16) not null default 'REST' after place_address,
    add key idx_waypoint_draft_seq (draft_id, seq_no);
