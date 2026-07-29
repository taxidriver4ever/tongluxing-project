-- 为现有同路行数据库增加唯一、可分享的行程号。
-- 可重复执行；已有行程使用 TLX + 主键的 36 进制大写值回填。
USE tongluxing;

DROP PROCEDURE IF EXISTS migrate_trip_number;
DELIMITER $$
CREATE PROCEDURE migrate_trip_number()
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'trip'
          AND column_name = 'trip_number'
    ) THEN
        ALTER TABLE trip
            ADD COLUMN trip_number varchar(20) NULL AFTER id;
    END IF;

    UPDATE trip
    SET trip_number = CONCAT('TLX', UPPER(CONV(id, 10, 36)))
    WHERE trip_number IS NULL OR TRIM(trip_number) = '';

    ALTER TABLE trip
        MODIFY COLUMN trip_number varchar(20) NOT NULL;

    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'trip'
          AND index_name = 'uk_trip_number'
    ) THEN
        ALTER TABLE trip
            ADD UNIQUE KEY uk_trip_number (trip_number);
    END IF;
END$$
DELIMITER ;

CALL migrate_trip_number();
DROP PROCEDURE migrate_trip_number;

-- 校验：下面两项应相等，重复数量应为 0。
SELECT COUNT(*) AS total_trip_count,
       COUNT(trip_number) AS numbered_trip_count
FROM trip;

SELECT trip_number, COUNT(*) AS duplicate_count
FROM trip
GROUP BY trip_number
HAVING COUNT(*) > 1;
