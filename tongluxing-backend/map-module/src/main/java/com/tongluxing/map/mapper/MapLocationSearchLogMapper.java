package com.tongluxing.map.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.tongluxing.map.entity.MapLocationSearchLog;

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

    /** 按时间倒序返回当前用户的地点选择历史。 */
    @Select("""
            select id, user_id, keyword, selected_name, selected_address,
                   selected_latitude, selected_longitude, scene, provider_type,
                   created_at, updated_at, deleted
            from map_location_search_log
            where user_id = #{userId} and scene = 'RESOLVE' and deleted = 0
            order by created_at desc
            limit #{limit}
            """)
    List<MapLocationSearchLog> findRecent(@Param("userId") Long userId, @Param("limit") int limit);
}
