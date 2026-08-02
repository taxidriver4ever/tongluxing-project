-- 地点搜索已切换为高德 POI 搜索 2.0，清理旧版静态地点目录。
DROP TABLE IF EXISTS map_location_catalog;

-- 路线规划持久化缓存：有序路线点 SHA-256 + 服务商/策略版本组成唯一键。
-- route_result_json 保存道路距离、时长和完整高德道路折线，供导航与顺路率复用。
CREATE TABLE IF NOT EXISTS map_route_plan (
  id BIGINT NOT NULL comment '记录主键',
  user_id BIGINT NOT NULL comment '平台用户ID',
  route_hash VARCHAR(64) NOT NULL comment '路线哈希值',
  route_points_json JSON NOT NULL comment '路线坐标点JSON数据',
  route_result_json JSON NULL comment '路线规划结果JSON数据',
  provider_type VARCHAR(32) NOT NULL comment '外部服务提供方类型',
  plan_status VARCHAR(20) NOT NULL DEFAULT 'SUCCESS' comment '规划状态',
  error_message VARCHAR(255) NULL comment '错误信息',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP comment '记录创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP comment '记录最后更新时间',
  deleted TINYINT(1) NOT NULL DEFAULT 0 comment '逻辑删除标记：0未删除、1已删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_map_route_hash (route_hash, provider_type, deleted),
  KEY idx_map_route_user_time (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 comment='地图路线规划表';
-- 兼容已有环境的较短 provider_type 列，确保新策略版本标识可完整保存。
ALTER TABLE map_route_plan MODIFY COLUMN provider_type VARCHAR(32) NOT NULL COMMENT '外部服务提供方类型';

-- 用户地点选择历史：只有 resolve 成功的地点才写入，搜索候选不落库。
CREATE TABLE IF NOT EXISTS map_location_search_log (
  id BIGINT NOT NULL comment '记录主键',
  user_id BIGINT NOT NULL comment '平台用户ID',
  keyword VARCHAR(128) NULL comment '搜索关键词',
  selected_name VARCHAR(128) NULL comment '选择名称',
  selected_address VARCHAR(255) NULL comment '选择地址',
  selected_latitude DECIMAL(10,6) NULL comment '选择纬度',
  selected_longitude DECIMAL(10,6) NULL comment '选择经度',
  scene VARCHAR(32) NOT NULL comment '业务场景',
  provider_type VARCHAR(32) NOT NULL comment '外部服务提供方类型',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP comment '记录创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP comment '记录最后更新时间',
  deleted TINYINT(1) NOT NULL DEFAULT 0 comment '逻辑删除标记：0未删除、1已删除',
  PRIMARY KEY (id),
  KEY idx_map_search_user_scene_time (user_id, scene, deleted, created_at),
  KEY idx_map_search_keyword (keyword)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 comment='地图位置搜索日志表';
-- 同样修正历史环境的服务商标识列长度。
ALTER TABLE map_location_search_log MODIFY COLUMN provider_type VARCHAR(32) NOT NULL COMMENT '外部服务提供方类型';

-- 预留地理编码缓存表：当前 POI 搜索不使用本地静态地点目录。
CREATE TABLE IF NOT EXISTS map_geocode_cache (
  id BIGINT NOT NULL comment '记录主键',
  location_hash VARCHAR(64) NOT NULL comment '位置哈希值',
  address VARCHAR(255) NULL comment '地址',
  latitude DECIMAL(10,6) NULL comment '纬度坐标',
  longitude DECIMAL(10,6) NULL comment '经度坐标',
  geocode_result_json JSON NULL comment '地理编码结果JSON数据',
  provider_type VARCHAR(32) NOT NULL comment '外部服务提供方类型',
  expire_at DATETIME NULL comment '过期时间',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP comment '记录创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP comment '记录最后更新时间',
  deleted TINYINT(1) NOT NULL DEFAULT 0 comment '逻辑删除标记：0未删除、1已删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_map_geocode_hash (location_hash, provider_type, deleted),
  KEY idx_map_geocode_expire (expire_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 comment='地图地理编码缓存表';
ALTER TABLE map_geocode_cache MODIFY COLUMN provider_type VARCHAR(32) NOT NULL COMMENT '外部服务提供方类型';
