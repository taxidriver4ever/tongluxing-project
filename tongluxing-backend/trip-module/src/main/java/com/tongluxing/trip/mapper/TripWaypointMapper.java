package com.tongluxing.trip.mapper;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.tongluxing.trip.entity.TripWaypoint;

/**
 * 行程途经点 Mapper。
 */
@Mapper
public interface TripWaypointMapper {

    /**
     * 查询行程途经点列表。
     */
    @Select("""
            select id, trip_id, draft_id, seq_no, place_name, place_address, waypoint_type, lat, lng, stay_minutes, remark, created_at, updated_at, deleted
            from trip_waypoint
            where trip_id = #{tripId} and deleted = 0
            order by seq_no asc
            """)
    List<TripWaypoint> findByTripId(@Param("tripId") Long tripId);

    @Select("""
            select id, trip_id, draft_id, seq_no, place_name, place_address, waypoint_type, lat, lng, stay_minutes, remark, created_at, updated_at, deleted
            from trip_waypoint where draft_id=#{draftId} and deleted=0 order by seq_no asc
            """)
    List<TripWaypoint> findByDraftId(@Param("draftId") Long draftId);

    @Select("""
            select id, trip_id, draft_id, seq_no, place_name, place_address, waypoint_type, lat, lng, stay_minutes, remark, created_at, updated_at, deleted
            from trip_waypoint where id=#{id} and draft_id=#{draftId} and deleted=0 limit 1
            """)
    TripWaypoint findDraftWaypoint(@Param("draftId") Long draftId, @Param("id") Long id);

    /**
     * 新增行程途经点。
     */
    @Insert("""
            insert into trip_waypoint
                (id, trip_id, draft_id, seq_no, place_name, place_address, waypoint_type, lat, lng, stay_minutes, remark, created_at, updated_at, deleted)
            values
                (#{id}, #{tripId}, #{draftId}, #{seqNo}, #{placeName}, #{placeAddress}, #{waypointType}, #{lat}, #{lng}, #{stayMinutes}, #{remark}, #{createdAt}, #{updatedAt}, 0)
            """)
    void insert(TripWaypoint waypoint);

    @Update("""
            update trip_waypoint set seq_no=#{seqNo}, place_name=#{placeName}, place_address=#{placeAddress},
                waypoint_type=#{waypointType}, lat=#{lat}, lng=#{lng}, stay_minutes=#{stayMinutes}, remark=#{remark}, updated_at=#{updatedAt}
            where id=#{id} and draft_id=#{draftId} and deleted=0
            """)
    int updateDraftWaypoint(TripWaypoint waypoint);

    @Update("update trip_waypoint set seq_no=#{seqNo}, updated_at=#{now} where id=#{id} and draft_id=#{draftId} and deleted=0")
    int updateDraftSequence(@Param("draftId") Long draftId, @Param("id") Long id,
                            @Param("seqNo") Integer seqNo, @Param("now") LocalDateTime now);

    @Update("update trip_waypoint set deleted=1, updated_at=#{now} where id=#{id} and draft_id=#{draftId} and deleted=0")
    int deleteDraftWaypoint(@Param("draftId") Long draftId, @Param("id") Long id, @Param("now") LocalDateTime now);

    /**
     * 删除指定行程下的全部途经点。
     */
    @Delete("""
            delete from trip_waypoint
            where trip_id = #{tripId}
            """)
    void deleteByTripId(@Param("tripId") Long tripId);
}
