package com.tongluxing.team.mapper;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.tongluxing.team.entity.TeamJoinApplication;

/**
 * 入队申请 Mapper。
 */
@Mapper
public interface TeamJoinApplicationMapper {

    /**
     * 根据申请 ID 查询入队申请。
     */
    @Select("""
            select id, team_id, trip_id, applicant_user_id, applicant_vehicle_id, application_type, join_role, linked_owner_user_id, linked_vehicle_id, plate_reference, current_latitude, current_longitude, owner_confirm_status, reviewer_user_id,
                   application_status, apply_message, join_question_json, review_message, reviewed_at,
                   created_at, updated_at, deleted
            from team_join_application
            where id = #{applicationId} and deleted = 0
            limit 1
            """)
    TeamJoinApplication findById(@Param("applicationId") Long applicationId);

    /**
     * 查询指定用户在指定车队下是否已有待审批申请。
     */
    @Select("""
            select id, team_id, trip_id, applicant_user_id, applicant_vehicle_id, application_type, join_role, linked_owner_user_id, linked_vehicle_id, plate_reference, current_latitude, current_longitude, owner_confirm_status, reviewer_user_id,
                   application_status, apply_message, join_question_json, review_message, reviewed_at,
                   created_at, updated_at, deleted
            from team_join_application
            where team_id = #{teamId} and applicant_user_id = #{userId}
              and application_status = 'PENDING' and deleted = 0
            limit 1
            """)
    TeamJoinApplication findPending(@Param("teamId") Long teamId, @Param("userId") Long userId);

    @Select("""
            select id, team_id, trip_id, applicant_user_id, applicant_vehicle_id, application_type, join_role, linked_owner_user_id, linked_vehicle_id, plate_reference, current_latitude, current_longitude, owner_confirm_status, reviewer_user_id,
                   application_status, apply_message, join_question_json, review_message, reviewed_at,
                   created_at, updated_at, deleted
            from team_join_application
            where team_id=#{teamId} and applicant_user_id=#{userId} and deleted=0
            order by created_at desc limit 1
            """)
    TeamJoinApplication findLatest(@Param("teamId") Long teamId, @Param("userId") Long userId);

    /** 查询当前用户提交的申请，最新优先。 */
    @Select("""
            select id, team_id, trip_id, applicant_user_id, applicant_vehicle_id, application_type, join_role, linked_owner_user_id, linked_vehicle_id, plate_reference, current_latitude, current_longitude, owner_confirm_status, reviewer_user_id,
                   application_status, apply_message, join_question_json, review_message, reviewed_at,
                   created_at, updated_at, deleted
            from team_join_application
            where applicant_user_id = #{userId} and deleted = 0
            order by created_at desc
            limit 100
            """)
    List<TeamJoinApplication> findByApplicantUserId(@Param("userId") Long userId);

    /** 查询某行程全部申请，由服务层校验队长权限。 */
    @Select("""
            select id, team_id, trip_id, applicant_user_id, applicant_vehicle_id, application_type, join_role, linked_owner_user_id, linked_vehicle_id, plate_reference, current_latitude, current_longitude, owner_confirm_status, reviewer_user_id,
                   application_status, apply_message, join_question_json, review_message, reviewed_at,
                   created_at, updated_at, deleted
            from team_join_application
            where trip_id = #{tripId} and deleted = 0
            order by case application_status when 'PENDING' then 0 else 1 end, created_at desc
            limit 200
            """)
    List<TeamJoinApplication> findByTripId(@Param("tripId") Long tripId);

    /** 查询当前队长收到的全部车队申请，供互动消息和队长申请列表使用。 */
    @Select("""
            <script>
            select a.id, a.team_id, a.trip_id, a.applicant_user_id, a.applicant_vehicle_id, a.application_type, a.join_role,
                   a.linked_owner_user_id, a.linked_vehicle_id, a.plate_reference, a.current_latitude,
                   a.current_longitude, a.owner_confirm_status, a.reviewer_user_id, a.application_status, a.apply_message, a.join_question_json,
                   a.review_message, a.reviewed_at, a.created_at, a.updated_at, a.deleted
            from team_join_application a
            join team t on t.id = a.team_id and t.deleted = 0
            where t.owner_user_id = #{ownerUserId} and a.deleted = 0
            <if test='status != null and status != ""'>
              and a.application_status = #{status}
            </if>
            order by case a.application_status when 'PENDING' then 0 else 1 end, a.created_at desc
            limit 200
            </script>
            """)
    List<TeamJoinApplication> findReceivedByOwner(@Param("ownerUserId") Long ownerUserId,
                                                  @Param("status") String status);

    /**
     * 新增入队申请。
     */
    @Insert("""
            insert into team_join_application
                (id, team_id, trip_id, applicant_user_id, applicant_vehicle_id, application_type, join_role, linked_owner_user_id, linked_vehicle_id, plate_reference, current_latitude, current_longitude, owner_confirm_status, reviewer_user_id,
                 application_status, apply_message, join_question_json, review_message, reviewed_at,
                 created_at, updated_at, deleted)
            values
                (#{id}, #{teamId}, #{tripId}, #{applicantUserId}, #{applicantVehicleId}, #{applicationType}, #{joinRole}, #{linkedOwnerUserId}, #{linkedVehicleId}, #{plateReference}, #{currentLatitude}, #{currentLongitude}, #{ownerConfirmStatus}, #{reviewerUserId},
                 #{applicationStatus}, #{applyMessage}, #{joinQuestionJson}, #{reviewMessage}, #{reviewedAt},
                 #{createdAt}, #{updatedAt}, 0)
            """)
    void insert(TeamJoinApplication application);

    /**
     * 审批待处理入队申请，避免重复审批。
     */
    @Update("""
            update team_join_application
            set application_status = #{status},
                reviewer_user_id = #{reviewerUserId},
                review_message = #{reviewMessage},
                reviewed_at = #{now},
                updated_at = #{now}
            where id = #{applicationId} and application_status = 'PENDING' and deleted = 0
            """)
    int review(@Param("applicationId") Long applicationId,
               @Param("reviewerUserId") Long reviewerUserId,
               @Param("status") String status,
               @Param("reviewMessage") String reviewMessage,
               @Param("now") LocalDateTime now);

    /**
     * 申请人主动取消仍处于待审批状态的申请。
     */
    @Update("""
            update team_join_application
            set application_status = 'CANCELLED',
                review_message = '申请人主动取消',
                reviewed_at = #{now},
                updated_at = #{now}
            where id = #{applicationId}
              and applicant_user_id = #{applicantUserId}
              and application_status = 'PENDING'
              and deleted = 0
            """)
    int cancelByApplicant(@Param("applicationId") Long applicationId,
                          @Param("applicantUserId") Long applicantUserId,
                          @Param("now") LocalDateTime now);
}

