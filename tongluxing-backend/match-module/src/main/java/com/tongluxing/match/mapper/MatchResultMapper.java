package com.tongluxing.match.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;

import com.tongluxing.match.entity.MatchResult;

/**
 * 有方向的行程匹配结果 Mapper。
 *
 * <p>source_trip_id 是用户基准行程，target_trip_id 是推荐候选；唯一索引包含两个
 * 方向和逻辑删除位，同一方向重复计算会更新分数而不是产生重复推荐。</p>
 */
@Mapper
public interface MatchResultMapper {

    /**
     * 新增或更新两条行程之间的匹配结果。
     *
     * <p>重算时保留原主键和创建时间，只刷新评分、差异、状态及计算/更新时间。</p>
     *
     * @param result 包含源/目标方向、分数明细和计算时间的实体
     */
    @Insert("""
            insert into match_result
                (id, source_trip_id, target_trip_id, source_user_id, target_user_id,
                 match_score, overlap_rate, distance_gap_meters, departure_gap_minutes,
                 score_detail_json, result_status, calculated_at, created_at, updated_at, deleted)
            values
                (#{id}, #{sourceTripId}, #{targetTripId}, #{sourceUserId}, #{targetUserId},
                 #{matchScore}, #{overlapRate}, #{distanceGapMeters}, #{departureGapMinutes},
                 #{scoreDetailJson}, #{resultStatus}, #{calculatedAt}, #{createdAt}, #{updatedAt}, 0)
            on duplicate key update
                 match_score = values(match_score),
                 overlap_rate = values(overlap_rate),
                 distance_gap_meters = values(distance_gap_meters),
                 departure_gap_minutes = values(departure_gap_minutes),
                 score_detail_json = values(score_detail_json),
                 result_status = values(result_status),
                 calculated_at = values(calculated_at),
                 updated_at = values(updated_at)
            """)
    void upsert(MatchResult result);

    /**
     * 查询源行程的有效推荐，按综合分降序、时间差升序。
     *
     * @param tripId 源行程 ID
     * @param limit 最大返回数
     * @return 有效推荐结果
     */
    @Select("""
            select id, source_trip_id, target_trip_id, source_user_id, target_user_id,
                   match_score, overlap_rate, distance_gap_meters, departure_gap_minutes,
                   score_detail_json, result_status, calculated_at, created_at, updated_at, deleted
            from match_result
            where source_trip_id = #{tripId} and result_status = 'VALID' and deleted = 0
            order by match_score desc, departure_gap_minutes asc
            limit #{limit}
            """)
    List<MatchResult> findBySourceTrip(@Param("tripId") Long tripId, @Param("limit") Integer limit);

    /**
     * 按推荐主键查询一条未删除结果；状态有效性由 Service 再校验。
     *
     * @param id 推荐结果主键
     * @return 未删除结果，不存在时为 null
     */
    @Select("""
            select id, source_trip_id, target_trip_id, source_user_id, target_user_id,
                   match_score, overlap_rate, distance_gap_meters, departure_gap_minutes,
                   score_detail_json, result_status, calculated_at, created_at, updated_at, deleted
            from match_result where id = #{id} and deleted = 0 limit 1
            """)
    MatchResult findById(@Param("id") Long id);

    /**
     * 查询申请人针对目标行程最近一次匹配结果，用于关联审核 ACCEPT/REJECT 事件。
     *
     * @param userId 申请人用户 ID
     * @param targetTripId 目标行程 ID
     * @return 最近结果；非推荐入口申请时为 null
     */
    @Select("""
            select id, source_trip_id, target_trip_id, source_user_id, target_user_id,
                   match_score, overlap_rate, distance_gap_meters, departure_gap_minutes,
                   score_detail_json, result_status, calculated_at, created_at, updated_at, deleted
            from match_result
            where source_user_id=#{userId} and target_trip_id=#{targetTripId} and deleted=0
            order by calculated_at desc limit 1
            """)
    MatchResult findLatestByApplicant(@Param("userId") Long userId, @Param("targetTripId") Long targetTripId);
}
