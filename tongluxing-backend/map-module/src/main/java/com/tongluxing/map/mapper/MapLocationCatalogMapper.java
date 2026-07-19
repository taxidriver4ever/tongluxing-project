package com.tongluxing.map.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.tongluxing.map.entity.MapLocationCatalog;

/** 地点目录查询 Mapper。 */
@Mapper
public interface MapLocationCatalogMapper {

    @Select("""
            select id, name, address, city, keywords, latitude, longitude, sort_no
            from map_location_catalog
            where enabled = 1 and deleted = 0
              and (name like concat('%', #{keyword}, '%')
                   or address like concat('%', #{keyword}, '%')
                   or city like concat('%', #{keyword}, '%')
                   or keywords like concat('%', #{keyword}, '%'))
            order by case
                       when name = #{keyword} then 0
                       when name like concat(#{keyword}, '%') then 1
                       when name like concat('%', #{keyword}, '%') then 2
                       when city like concat(#{keyword}, '%') then 3
                       when keywords like concat('%', #{keyword}, '%') then 4
                       else 5
                     end,
                     sort_no desc, id asc
            limit #{limit}
            """)
    List<MapLocationCatalog> search(@Param("keyword") String keyword, @Param("limit") int limit);

    @Select("""
            select id, name, address, city, keywords, latitude, longitude, sort_no
            from map_location_catalog
            where enabled = 1 and deleted = 0
            order by sort_no desc, id asc
            limit #{limit}
            """)
    List<MapLocationCatalog> findPopular(@Param("limit") int limit);
}
