package com.tongluxing.drivertrack.mapper;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.tongluxing.drivertrack.entity.TripMemberLatestLocation;

/** 普通成员最新位置快照 Mapper。 */
@Mapper
public interface TripMemberLatestLocationMapper {

    String COLUMNS = """
            id, trip_id, captain_user_id, member_user_id, longitude, latitude,
            speed, accuracy, sequence_no, mock_location, record_time,
            server_receive_time, created_at, updated_at, deleted
            """;

    /**
     * 只保留成员最新位置。重传的旧点会被服务端确认，但不会覆盖更新的位置。
     */
    @Insert("""
            insert into trip_member_latest_location
              (id, trip_id, captain_user_id, member_user_id, longitude, latitude,
               speed, accuracy, sequence_no, mock_location, record_time,
               server_receive_time, created_at, updated_at, deleted)
            values
              (#{id}, #{tripId}, #{captainUserId}, #{memberUserId}, #{longitude}, #{latitude},
               #{speed}, #{accuracy}, #{sequenceNo}, #{mockLocation}, #{recordTime},
               #{serverReceiveTime}, #{now}, #{now}, 0)
            on duplicate key update
              captain_user_id = values(captain_user_id),
              longitude = if(values(record_time) >= record_time, values(longitude), longitude),
              latitude = if(values(record_time) >= record_time, values(latitude), latitude),
              speed = if(values(record_time) >= record_time, values(speed), speed),
              accuracy = if(values(record_time) >= record_time, values(accuracy), accuracy),
              sequence_no = if(values(record_time) >= record_time, values(sequence_no), sequence_no),
              mock_location = if(values(record_time) >= record_time, values(mock_location), mock_location),
              server_receive_time = if(values(record_time) >= record_time,
                  values(server_receive_time), server_receive_time),
              record_time = greatest(record_time, values(record_time)),
              updated_at = values(updated_at),
              deleted = 0
            """)
    int upsert(@Param("id") Long id,
               @Param("tripId") Long tripId,
               @Param("captainUserId") Long captainUserId,
               @Param("memberUserId") Long memberUserId,
               @Param("longitude") java.math.BigDecimal longitude,
               @Param("latitude") java.math.BigDecimal latitude,
               @Param("speed") java.math.BigDecimal speed,
               @Param("accuracy") java.math.BigDecimal accuracy,
               @Param("sequenceNo") Long sequenceNo,
               @Param("mockLocation") int mockLocation,
               @Param("recordTime") LocalDateTime recordTime,
               @Param("serverReceiveTime") LocalDateTime serverReceiveTime,
               @Param("now") LocalDateTime now);

    @Select("""
            select
            """ + COLUMNS + """
            from trip_member_latest_location
            where trip_id = #{tripId} and member_user_id = #{memberUserId} and deleted = 0
            limit 1
            """)
    TripMemberLatestLocation findOne(@Param("tripId") Long tripId,
                                     @Param("memberUserId") Long memberUserId);

    @Select("""
            select
            """ + COLUMNS + """
            from trip_member_latest_location
            where trip_id = #{tripId} and deleted = 0
            order by member_user_id asc
            """)
    List<TripMemberLatestLocation> findByTripId(@Param("tripId") Long tripId);
}
