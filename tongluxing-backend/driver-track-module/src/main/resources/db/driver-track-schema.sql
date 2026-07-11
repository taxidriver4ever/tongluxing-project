create table if not exists driver_track_record (
    id bigint primary key,
    trip_id bigint not null,
    driver_id bigint not null,
    longitude decimal(10,6) not null,
    latitude decimal(10,6) not null,
    speed decimal(10,2) null,
    direction decimal(10,2) null,
    accuracy decimal(10,2) null,
    distance_from_prev int not null default 0,
    record_time datetime not null,
    created_at datetime not null,
    deleted tinyint not null default 0,
    key idx_driver_track_trip_time (trip_id, record_time),
    key idx_driver_track_driver_time (driver_id, record_time)
);

create table if not exists driver_track_distance_record (
    id bigint primary key,
    trip_id bigint not null,
    driver_id bigint not null,
    total_distance int not null default 0,
    last_settle_distance int not null default 0,
    settle_type varchar(32) not null,
    settle_key varchar(128) not null,
    settle_time datetime not null,
    event_published tinyint not null default 0,
    created_at datetime not null,
    updated_at datetime not null,
    deleted tinyint not null default 0,
    unique key uk_driver_track_distance_settle_key (settle_key, deleted),
    key idx_driver_track_distance_trip_driver (trip_id, driver_id, updated_at)
);

create table if not exists driver_track_deviation_record (
    id bigint primary key,
    trip_id bigint not null,
    driver_id bigint not null,
    longitude decimal(10,6) not null,
    latitude decimal(10,6) not null,
    deviation_distance int not null default 0,
    deviation_status tinyint not null default 0,
    record_time datetime not null,
    created_at datetime not null,
    deleted tinyint not null default 0,
    key idx_driver_track_deviation_trip_driver_time (trip_id, driver_id, record_time)
);
