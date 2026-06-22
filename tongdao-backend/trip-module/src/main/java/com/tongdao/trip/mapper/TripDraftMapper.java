package com.tongdao.trip.mapper;

import java.time.LocalDateTime;
import java.util.List;

import com.tongdao.trip.dto.TripDraftQueryDTO;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface TripDraftMapper {
    @Insert("""
            insert into trip_draft(id,user_id,start_location_json,end_location_json,waypoint_json,departure_time,duration_days,
                people_count,remark,draft_status,published_trip_id,created_at,updated_at,deleted)
            values(#{id},#{userId},#{startJson},#{endJson},#{waypointJson},#{departureTime},#{durationDays},#{peopleCount},#{remark},'DRAFT',null,#{now},#{now},0)
            """)
    int insert(@Param("id") Long id, @Param("userId") Long userId, @Param("startJson") String startJson,
               @Param("endJson") String endJson, @Param("waypointJson") String waypointJson,
               @Param("departureTime") LocalDateTime departureTime, @Param("durationDays") Integer durationDays,
               @Param("peopleCount") Integer peopleCount, @Param("remark") String remark, @Param("now") LocalDateTime now);

    @Select("""
            select id draftId,user_id userId,start_location_json startJson,end_location_json endJson,waypoint_json waypointJson,
                   departure_time departureTime,duration_days durationDays,people_count peopleCount,remark,draft_status draftStatus,
                   published_trip_id publishedTripId,updated_at updatedAt from trip_draft
            where id=#{id} and user_id=#{userId} and deleted=0 limit 1
            """)
    TripDraftQueryDTO find(@Param("id") Long id, @Param("userId") Long userId);

    @Select("""
            select id draftId,user_id userId,start_location_json startJson,end_location_json endJson,waypoint_json waypointJson,
                   departure_time departureTime,duration_days durationDays,people_count peopleCount,remark,draft_status draftStatus,
                   published_trip_id publishedTripId,updated_at updatedAt from trip_draft
            where id=#{id} and user_id=#{userId} and deleted=0 limit 1 for update
            """)
    TripDraftQueryDTO findForUpdate(@Param("id") Long id, @Param("userId") Long userId);

    List<TripDraftQueryDTO> findAll(@Param("userId") Long userId, @Param("status") String status,
                                    @Param("offset") int offset, @Param("size") int size);
    long count(@Param("userId") Long userId, @Param("status") String status);

    @Update("""
            update trip_draft
            set start_location_json = #{startJson},
                end_location_json = #{endJson},
                waypoint_json = #{waypointJson},
                departure_time = #{departureTime},
                duration_days = #{durationDays},
                people_count = #{peopleCount},
                remark = #{remark},
                updated_at = #{now}
            where id = #{id}
              and user_id = #{userId}
              and draft_status = 'DRAFT'
              and deleted = 0
            """)
    int update(@Param("id") Long id, @Param("userId") Long userId, @Param("startJson") String startJson,
               @Param("endJson") String endJson, @Param("waypointJson") String waypointJson,
               @Param("departureTime") LocalDateTime departureTime, @Param("durationDays") Integer durationDays,
               @Param("peopleCount") Integer peopleCount, @Param("remark") String remark, @Param("now") LocalDateTime now);

    @Update("""
            update trip_draft
            set draft_status = 'DELETED',
                deleted = 1,
                updated_at = #{now}
            where id = #{id}
              and user_id = #{userId}
              and draft_status = 'DRAFT'
              and deleted = 0
            """)
    int delete(@Param("id") Long id, @Param("userId") Long userId, @Param("now") LocalDateTime now);

    @Update("""
            update trip_draft
            set draft_status = 'PUBLISHED',
                published_trip_id = #{tripId},
                publish_idempotency_key = #{bizId},
                updated_at = #{now}
            where id = #{id}
              and user_id = #{userId}
              and draft_status = 'DRAFT'
              and deleted = 0
            """)
    int markPublished(@Param("id") Long id, @Param("userId") Long userId, @Param("tripId") Long tripId,
                      @Param("bizId") String bizId, @Param("now") LocalDateTime now);
}
