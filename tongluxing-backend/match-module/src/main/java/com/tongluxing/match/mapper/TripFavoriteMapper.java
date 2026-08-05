package com.tongluxing.match.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 行程收藏 MyBatis 数据访问接口。
 * 方法直接对应数据库读写语句；事务边界由调用它的服务层统一管理。
 */
@Mapper
public interface TripFavoriteMapper {

    /**
     * @param userId 当前用户 ID
     * @param tripId 行程 ID
     * @return 收藏关系数量；唯一索引保证只会是 0 或 1
     */
    @Select("SELECT COUNT(1) FROM trip_favorite WHERE user_id=#{userId} AND trip_id=#{tripId}")
    int exists(@Param("userId") Long userId, @Param("tripId") Long tripId);

    /**
     * 幂等创建收藏关系。
     *
     * <p>INSERT IGNORE 把并发或重复收藏转换为影响 0 行，Service 仍返回已收藏。</p>
     */
    @Insert("""
            INSERT IGNORE INTO trip_favorite(id,user_id,trip_id,created_at)
            VALUES(#{id},#{userId},#{tripId},NOW())
            """)
    int insert(@Param("id") Long id, @Param("userId") Long userId, @Param("tripId") Long tripId);


    /** 按最近收藏时间返回当前用户的行程 ID。 */
    @Select("""
            SELECT trip_id
            FROM trip_favorite
            WHERE user_id = #{userId}
            ORDER BY created_at DESC
            LIMIT #{offset}, #{size}
            """)
    List<Long> findTripIds(@Param("userId") Long userId,
                           @Param("offset") int offset,
                           @Param("size") int size);

    @Select("SELECT COUNT(1) FROM trip_favorite WHERE user_id = #{userId}")
    long countByUserId(@Param("userId") Long userId);

    /** 删除收藏关系；关系不存在时影响 0 行并保持未收藏状态。 */
    @Delete("DELETE FROM trip_favorite WHERE user_id=#{userId} AND trip_id=#{tripId}")
    int delete(@Param("userId") Long userId, @Param("tripId") Long tripId);
}
