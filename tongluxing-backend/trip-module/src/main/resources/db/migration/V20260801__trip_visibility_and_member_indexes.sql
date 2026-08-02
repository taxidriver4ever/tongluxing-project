-- V1.7：我的行程退出列表按 user_id / join_status 做 EXISTS 查询，需要反向成员索引。
-- 动态判断使脚本可以安全用于已经手工补过索引的内测库。
SET @has_member_user_status_trip = (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'trip_member_snapshot'
      AND index_name = 'idx_member_user_status_trip'
);
SET @add_member_user_status_trip_sql = IF(
    @has_member_user_status_trip = 0,
    'ALTER TABLE trip_member_snapshot ADD KEY idx_member_user_status_trip (user_id, join_status, trip_id)',
    'SELECT 1'
);
PREPARE add_member_user_status_trip_stmt FROM @add_member_user_status_trip_sql;
EXECUTE add_member_user_status_trip_stmt;
DEALLOCATE PREPARE add_member_user_status_trip_stmt;
