package com.tongluxing.trip.mapper;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.tongluxing.trip.entity.TripCreationDraft;

/** 创建行程专用草稿 Mapper，支持空草稿和增量保存。 */
@Mapper
public interface TripCreationDraftMapper {

    @Insert("""
            insert into trip_draft(id,user_id,title,description,start_location_json,end_location_json,waypoint_json,
                departure_time,duration_days,people_count,vehicle_requirements,budget_description,notes,remark,draft_status,created_at,updated_at,deleted)
            values(#{id},#{userId},'', '', null, null, json_array(), null, null, null, '不限', null, null, '', 'DRAFT',#{now},#{now},0)
            """)
    int insertEmpty(@Param("id") Long id, @Param("userId") Long userId, @Param("now") LocalDateTime now);

    @Select("""
            select id,user_id,title,description,start_location_json,end_location_json,departure_time,duration_days,
                   people_count,vehicle_requirements,budget_description,notes,remark,draft_status,published_trip_id,created_at,updated_at
            from trip_draft where id=#{id} and user_id=#{userId} and deleted=0 limit 1
            """)
    TripCreationDraft find(@Param("id") Long id, @Param("userId") Long userId);

    @Select("""
            select id,user_id,title,description,start_location_json,end_location_json,departure_time,duration_days,
                   people_count,vehicle_requirements,budget_description,notes,remark,draft_status,published_trip_id,created_at,updated_at
            from trip_draft where id=#{id} and user_id=#{userId} and deleted=0 limit 1 for update
            """)
    TripCreationDraft findForUpdate(@Param("id") Long id, @Param("userId") Long userId);

    @Select("""
            select id,user_id,title,description,start_location_json,end_location_json,departure_time,duration_days,
                   people_count,vehicle_requirements,budget_description,notes,remark,draft_status,published_trip_id,created_at,updated_at
            from trip_draft where user_id=#{userId} and draft_status=#{status} and deleted=0 order by updated_at desc
            """)
    List<TripCreationDraft> findByStatus(@Param("userId") Long userId, @Param("status") String status);

    @Update("""
            update trip_draft set title=#{title},description=#{description},start_location_json=#{startLocationJson},
                end_location_json=#{endLocationJson},departure_time=#{departureTime},duration_days=#{durationDays},
                people_count=#{peopleCount},vehicle_requirements=#{vehicleRequirements},
                budget_description=#{budgetDescription},notes=#{notes},remark=#{notes},updated_at=#{updatedAt}
            where id=#{id} and user_id=#{userId} and draft_status='DRAFT' and deleted=0
            """)
    int update(TripCreationDraft draft);

    @Update("""
            update trip_draft set deleted=1,updated_at=#{now}
            where id=#{id} and user_id=#{userId} and draft_status='DRAFT' and deleted=0
            """)
    int softDelete(@Param("id") Long id, @Param("userId") Long userId,
                   @Param("now") LocalDateTime now);

    @Update("""
            update trip_draft set deleted=1
            where draft_status='DRAFT' and deleted=0 and updated_at < #{expiresBefore}
            """)
    int softDeleteExpired(@Param("expiresBefore") LocalDateTime expiresBefore);
}
