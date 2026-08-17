package com.tongluxing.match.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 推荐曝光与行为漏斗日志 Mapper。
 *
 * <p>记录 IMPRESSION、CLICK、APPLY、ACCEPT、REJECT、START、FINISH 等事件，支持
 * 推荐效果分析。日志只追加不更新，requestId 用来关联一次推荐或场景来源。</p>
 */
@Mapper
public interface MatchRecommendLogMapper {

    /** 一批曝光使用一条 INSERT 语句，避免推荐页逐卡同步写库。 */
    @Insert("""
            <script>
            insert into match_recommend_log
                (id, user_id, trip_id, target_trip_id, target_team_id, scene, action_type,
                 request_id, extra_json, created_at, updated_at, deleted)
            values
            <foreach collection='rows' item='row' separator=','>
                (#{row.id}, #{row.userId}, #{row.tripId}, #{row.targetTripId}, #{row.targetTeamId},
                 #{row.scene}, #{row.actionType}, #{row.requestId}, #{row.extraJson}, now(), now(), 0)
            </foreach>
            </script>
            """)
    void batchInsert(@Param("rows") List<RecommendLogRow> rows);

    /**
     * 写入一次推荐场景下的用户行为日志。
     *
     * @param id 日志雪花主键
     * @param userId 行为用户 ID
     * @param tripId 用户的源行程；无源行程场景使用目标行程满足非空约束
     * @param targetTripId 被推荐或操作的目标行程
     * @param targetTeamId 关联车队，可为空
     * @param scene 稳定推荐场景编码
     * @param actionType 漏斗动作类型
     * @param requestId 推荐结果 ID 或带场景前缀的追踪标识
     * @param extraJson 可选扩展 JSON
     */
    @Insert("""
            insert into match_recommend_log
                (id, user_id, trip_id, target_trip_id, target_team_id, scene, action_type,
                 request_id, extra_json, created_at, updated_at, deleted)
            values
                (#{id}, #{userId}, #{tripId}, #{targetTripId}, #{targetTeamId}, #{scene}, #{actionType},
                 #{requestId}, #{extraJson}, now(), now(), 0)
            """)
    void insert(@Param("id") Long id,
                @Param("userId") Long userId,
                @Param("tripId") Long tripId,
                @Param("targetTripId") Long targetTripId,
                @Param("targetTeamId") Long targetTeamId,
                @Param("scene") String scene,
                @Param("actionType") String actionType,
                @Param("requestId") String requestId,
                @Param("extraJson") String extraJson);

    record RecommendLogRow(Long id, Long userId, Long tripId, Long targetTripId, Long targetTeamId,
                           String scene, String actionType, String requestId, String extraJson) { }
}
