-- 存量数据库一次性迁移：商家优惠券增加拼单规则。
-- 新建数据库无需执行，merchant-schema.sql 已包含这些字段。
ALTER TABLE merchant_coupon_offer
  ADD COLUMN group_enabled TINYINT NOT NULL DEFAULT 0 COMMENT '是否启用拼单：0否、1是' AFTER limit_count,
  ADD COLUMN group_people INT NULL COMMENT '拼单成团所需人数' AFTER group_enabled,
  ADD COLUMN group_timeout_hours INT NULL COMMENT '拼单超时时长，单位为小时' AFTER group_people;
