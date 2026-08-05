package com.tongluxing.match.mapper;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 行程搜索历史数据访问。 */
@Mapper
public interface TripSearchHistoryMapper {

    @Insert("""
            INSERT INTO trip_search_history(id, user_id, keyword, search_type, created_at, updated_at)
            VALUES(#{id}, #{userId}, #{keyword}, #{searchType}, #{now}, #{now})
            ON DUPLICATE KEY UPDATE updated_at = VALUES(updated_at)
            """)
    int upsert(@Param("id") Long id,
               @Param("userId") Long userId,
               @Param("keyword") String keyword,
               @Param("searchType") String searchType,
               @Param("now") LocalDateTime now);

    @Select("""
            SELECT id, keyword, search_type AS searchType, updated_at AS updatedAt
            FROM trip_search_history
            WHERE user_id = #{userId}
            ORDER BY updated_at DESC
            LIMIT #{limit}
            """)
    List<HistoryRow> findRecent(@Param("userId") Long userId, @Param("limit") int limit);

    @Delete("DELETE FROM trip_search_history WHERE id = #{historyId} AND user_id = #{userId}")
    int deleteOne(@Param("userId") Long userId, @Param("historyId") Long historyId);

    @Delete("DELETE FROM trip_search_history WHERE user_id = #{userId}")
    int deleteAll(@Param("userId") Long userId);

    record HistoryRow(Long id, String keyword, String searchType, LocalDateTime updatedAt) {
    }
}
