package com.tongluxing.map.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.tongluxing.map.entity.MapRoutePlan;

/**
 * 路线规划 Mapper。
 */
@Mapper
public interface MapRoutePlanMapper {

    /** 根据路线哈希和服务商查询历史规划结果。 */
    @Select("""
            select id, user_id, route_hash, route_points_json, route_result_json, provider_type,
                   plan_status, error_message, created_at, updated_at, deleted
            from map_route_plan
            where route_hash = #{routeHash} and provider_type = #{providerType} and deleted = 0
            limit 1
            """)
    MapRoutePlan findByHash(@Param("routeHash") String routeHash, @Param("providerType") String providerType);

    /** 新增路线规划记录。 */
    @Insert("""
            insert into map_route_plan
                (id, user_id, route_hash, route_points_json, route_result_json, provider_type,
                 plan_status, error_message, created_at, updated_at, deleted)
            values
                (#{id}, #{userId}, #{routeHash}, #{routePointsJson}, #{routeResultJson}, #{providerType},
                 #{planStatus}, #{errorMessage}, #{createdAt}, #{updatedAt}, 0)
            """)
    void insert(MapRoutePlan plan);

    /** 缓存损坏时用最新高德结果覆盖原记录。 */
    @Update("""
            update map_route_plan
            set route_result_json = #{routeResultJson},
                plan_status = #{planStatus},
                error_message = #{errorMessage},
                updated_at = #{updatedAt}
            where id = #{id} and deleted = 0
            """)
    int updateSuccess(MapRoutePlan plan);
}
