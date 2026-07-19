-- 存量数据库一次性迁移：商家优惠券增加拼单规则。
-- 新建数据库无需执行，merchant-schema.sql 已包含这些字段。
ALTER TABLE merchant_coupon_offer
  ADD COLUMN group_enabled TINYINT NOT NULL DEFAULT 0 AFTER limit_count,
  ADD COLUMN group_people INT NULL AFTER group_enabled,
  ADD COLUMN group_timeout_hours INT NULL AFTER group_people;
