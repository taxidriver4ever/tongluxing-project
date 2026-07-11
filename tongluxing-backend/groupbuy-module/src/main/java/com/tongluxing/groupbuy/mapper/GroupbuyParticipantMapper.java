package com.tongluxing.groupbuy.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.tongluxing.groupbuy.entity.GroupbuyParticipant;

/**
 * 拼团参与人表 MyBatis Mapper。
 *
 * <p>负责 groupbuy_participant 表的写入与按活动、用户维度查询。</p>
 */
@Mapper
public interface GroupbuyParticipantMapper {
    /**
     * 插入一条参团记录。
     *
     * @param participant 参团实体
     */
    @Insert("""
            insert into groupbuy_participant
                (id, activity_id, order_id, user_id, participant_status, joined_at,
                 paid_at, refunded_at, created_at, updated_at, deleted)
            values
                (#{id}, #{activityId}, #{orderId}, #{userId}, #{participantStatus}, #{joinedAt},
                 #{paidAt}, #{refundedAt}, #{createdAt}, #{updatedAt}, 0)
            """)
    void insert(GroupbuyParticipant participant);

    /**
     * 查询指定活动下的全部未删除参与人。
     *
     * @param activityId 活动 ID
     * @return 按加入时间升序排列的参与人列表
     */
    @Select("""
            select id, activity_id, order_id, user_id, participant_status, joined_at,
                   paid_at, refunded_at, created_at, updated_at, deleted
            from groupbuy_participant
            where activity_id = #{activityId}
              and deleted = 0
            order by joined_at asc
            """)
    List<GroupbuyParticipant> findByActivityId(@Param("activityId") Long activityId);

    /**
     * 查询用户是否已加入指定拼团活动。
     *
     * @param activityId 活动 ID
     * @param userId 用户 ID
     * @return 已存在的参与记录；不存在时返回 null
     */
    @Select("""
            select id, activity_id, order_id, user_id, participant_status, joined_at,
                   paid_at, refunded_at, created_at, updated_at, deleted
            from groupbuy_participant
            where activity_id = #{activityId}
              and user_id = #{userId}
              and deleted = 0
            limit 1
            """)
    GroupbuyParticipant findByActivityAndUser(@Param("activityId") Long activityId, @Param("userId") Long userId);
}
