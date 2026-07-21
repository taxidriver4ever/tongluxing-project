package com.tongluxing.map.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

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

    /**
     * 将当前用户已选择过的相同地点更新时间推进到最新。
     *
     * <p>返回 0 时表示没有历史记录，调用方再插入新记录；返回大于 0 时不重复插入，
     * 避免同一地点连续选择挤占最近搜索列表。</p>
     */
    @org.apache.ibatis.annotations.Update("""
            update map_location_search_log
            set keyword = #{keyword}, updated_at = #{updatedAt}, created_at = #{createdAt}
            where user_id = #{userId} and scene = 'RESOLVE' and deleted = 0
              and coalesce(selected_name, '') = coalesce(#{selectedName}, '')
              and coalesce(selected_address, '') = coalesce(#{selectedAddress}, '')
              and selected_latitude = #{selectedLatitude}
              and selected_longitude = #{selectedLongitude}
            """)
    int touchExisting(MapLocationSearchLog log);

    /** 按时间倒序返回当前用户的地点选择历史。 */
    @Select("""
            select id, user_id, keyword, selected_name, selected_address,
                   selected_latitude, selected_longitude, scene, provider_type,
                   created_at, updated_at, deleted
            from map_location_search_log current_log
            where user_id = #{userId} and scene = 'RESOLVE' and deleted = 0
              and not exists (
                select 1
                from map_location_search_log newer
                where newer.user_id = current_log.user_id
                  and newer.scene = 'RESOLVE' and newer.deleted = 0
                  and coalesce(newer.selected_name, '') = coalesce(current_log.selected_name, '')
                  and coalesce(newer.selected_address, '') = coalesce(current_log.selected_address, '')
                  and newer.selected_latitude = current_log.selected_latitude
                  and newer.selected_longitude = current_log.selected_longitude
                  and (newer.created_at > current_log.created_at
                       or (newer.created_at = current_log.created_at and newer.id > current_log.id))
              )
            order by created_at desc, id desc
            limit #{limit}
            """)
    List<MapLocationSearchLog> findRecent(@Param("userId") Long userId, @Param("limit") int limit);

    /** 逻辑删除当前用户的一条地点历史，不能删除其他用户的数据。 */
    @Update("""
            update map_location_search_log
            set deleted = 1, updated_at = current_timestamp
            where id = #{historyId} and user_id = #{userId}
              and scene = 'RESOLVE' and deleted = 0
            """)
    int softDeleteByIdAndUser(@Param("historyId") Long historyId, @Param("userId") Long userId);

    /** 逻辑清空当前用户的全部地点历史。 */
    @Update("""
            update map_location_search_log
            set deleted = 1, updated_at = current_timestamp
            where user_id = #{userId} and scene = 'RESOLVE' and deleted = 0
            """)
    int softDeleteAllByUser(@Param("userId") Long userId);
}
