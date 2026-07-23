package com.tongluxing.trip.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.tongluxing.trip.entity.TripMemberSnapshot;

/**
 * 行程成员快照 Mapper。
 */
@Mapper
public interface TripMemberSnapshotMapper {

    /**
     * 查询行程成员快照，包含车主、已通过和待确认成员。
     */
    @Select("""
            select id, trip_id, user_id, vehicle_id, member_role, join_status, nickname_snapshot, vehicle_snapshot,
                   joined_at, created_at, updated_at
            from trip_member_snapshot
            where trip_id = #{tripId} and join_status in ('OWNER', 'APPROVED', 'PENDING')
            order by created_at asc
            """)
    List<TripMemberSnapshot> findByTripId(@Param("tripId") Long tripId);

    /**
     * 新增行程成员快照。
     */
    @Insert("""
            insert into trip_member_snapshot
                (id, trip_id, user_id, vehicle_id, member_role, join_status, nickname_snapshot, vehicle_snapshot,
                 joined_at, created_at, updated_at)
            values
                (#{id}, #{tripId}, #{userId}, #{vehicleId}, #{memberRole}, #{joinStatus}, #{nicknameSnapshot}, #{vehicleSnapshot},
                 #{joinedAt}, #{createdAt}, #{updatedAt})
            """)
    void insert(TripMemberSnapshot member);

    /** 开始行程前把确认名单外的非队长成员标记为不参加。 */
    @Update("""
            <script>
            update trip_member_snapshot
            set join_status = 'DECLINED', updated_at = #{now}
            where trip_id = #{tripId}
              and user_id != #{ownerUserId}
              and join_status in ('APPROVED', 'PENDING')
              and user_id not in
              <foreach collection="confirmedUserIds" item="userId" open="(" separator="," close=")">
                #{userId}
              </foreach>
            </script>
            """)
    int declineUnconfirmedMembers(@Param("tripId") Long tripId,
                                   @Param("ownerUserId") Long ownerUserId,
                                   @Param("confirmedUserIds") List<Long> confirmedUserIds,
                                   @Param("now") java.time.LocalDateTime now);
}
