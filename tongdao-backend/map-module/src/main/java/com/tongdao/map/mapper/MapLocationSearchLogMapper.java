package com.tongdao.map.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

import com.tongdao.map.entity.MapLocationSearchLog;

/**
 * 地点搜索日志 Mapper。
 */
@Mapper
public interface MapLocationSearchLogMapper {

    /** 写入地点搜索/选择日志。 */
    @Insert("""
            insert into map_location_search_log
                (id, user_id, keyword, selected_name, selected_address, selected_latitude,
                 selected_longitude, scene, provider_type, created_at, updated_at, deleted)
            values
                (#{id}, #{userId}, #{keyword}, #{selectedName}, #{selectedAddress}, #{selectedLatitude},
                 #{selectedLongitude}, #{scene}, #{providerType}, #{createdAt}, #{updatedAt}, 0)
            """)
    void insert(MapLocationSearchLog log);
}
