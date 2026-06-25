package com.tongdao.groupbuy.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.tongdao.groupbuy.entity.GroupbuyParticipant;

@Mapper
public interface GroupbuyParticipantMapper {
    @Insert("""
            insert into groupbuy_participant
                (id, activity_id, order_id, user_id, participant_status, joined_at,
                 paid_at, refunded_at, created_at, updated_at, deleted)
            values
                (#{id}, #{activityId}, #{orderId}, #{userId}, #{participantStatus}, #{joinedAt},
                 #{paidAt}, #{refundedAt}, #{createdAt}, #{updatedAt}, 0)
            """)
    void insert(GroupbuyParticipant participant);

    @Select("""
            select id, activity_id, order_id, user_id, participant_status, joined_at,
                   paid_at, refunded_at, created_at, updated_at, deleted
            from groupbuy_participant
            where activity_id = #{activityId}
              and deleted = 0
            order by joined_at asc
            """)
    List<GroupbuyParticipant> findByActivityId(@Param("activityId") Long activityId);

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

