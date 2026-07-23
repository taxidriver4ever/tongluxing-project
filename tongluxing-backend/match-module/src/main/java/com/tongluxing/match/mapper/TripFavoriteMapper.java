package com.tongluxing.match.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface TripFavoriteMapper {

    @Select("SELECT COUNT(1) FROM trip_favorite WHERE user_id=#{userId} AND trip_id=#{tripId}")
    int exists(@Param("userId") Long userId, @Param("tripId") Long tripId);

    @Insert("""
            INSERT IGNORE INTO trip_favorite(id,user_id,trip_id,created_at)
            VALUES(#{id},#{userId},#{tripId},NOW())
            """)
    int insert(@Param("id") Long id, @Param("userId") Long userId, @Param("tripId") Long tripId);

    @Delete("DELETE FROM trip_favorite WHERE user_id=#{userId} AND trip_id=#{tripId}")
    int delete(@Param("userId") Long userId, @Param("tripId") Long tripId);
}
