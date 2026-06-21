package com.tongdao.map.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.tongdao.map.entity.MapRoutePlan;

@Mapper
public interface MapRoutePlanMapper {

    @Select("""
            select id, user_id, route_hash, route_points_json, route_result_json, provider_type,
                   plan_status, error_message, created_at, updated_at, deleted
            from map_route_plan
            where route_hash = #{routeHash} and provider_type = #{providerType} and deleted = 0
            limit 1
            """)
    MapRoutePlan findByHash(@Param("routeHash") String routeHash, @Param("providerType") String providerType);

    @Insert("""
            insert into map_route_plan
                (id, user_id, route_hash, route_points_json, route_result_json, provider_type,
                 plan_status, error_message, created_at, updated_at, deleted)
            values
                (#{id}, #{userId}, #{routeHash}, #{routePointsJson}, #{routeResultJson}, #{providerType},
                 #{planStatus}, #{errorMessage}, #{createdAt}, #{updatedAt}, 0)
            """)
    void insert(MapRoutePlan plan);
}
