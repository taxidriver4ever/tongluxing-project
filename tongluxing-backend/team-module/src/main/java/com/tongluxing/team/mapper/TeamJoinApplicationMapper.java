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
            select id, team_id, trip_id, applicant_user_id, applicant_vehicle_id, reviewer_user_id,
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
            select id, team_id, trip_id, applicant_user_id, applicant_vehicle_id, reviewer_user_id,
                   application_status, apply_message, join_question_json, review_message, reviewed_at,
                   created_at, updated_at, deleted
            from team_join_application
            where team_id = #{teamId} and applicant_user_id = #{userId}
              and application_status = 'PENDING' and deleted = 0
            limit 1
            """)
    TeamJoinApplication findPending(@Param("teamId") Long teamId, @Param("userId") Long userId);

    @Select("""
            select id, team_id, trip_id, applicant_user_id, applicant_vehicle_id, reviewer_user_id,
                   application_status, apply_message, join_question_json, review_message, reviewed_at,
                   created_at, updated_at, deleted
            from team_join_application
            where team_id=#{teamId} and applicant_user_id=#{userId} and deleted=0
            order by created_at desc limit 1
            """)
    TeamJoinApplication findLatest(@Param("teamId") Long teamId, @Param("userId") Long userId);

    /** 查询当前用户提交的申请，最新优先。 */
    @Select("""
            select id, team_id, trip_id, applicant_user_id, applicant_vehicle_id, reviewer_user_id,
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
            select id, team_id, trip_id, applicant_user_id, applicant_vehicle_id, reviewer_user_id,
                   application_status, apply_message, join_question_json, review_message, reviewed_at,
                   created_at, updated_at, deleted
            from team_join_application
            where trip_id = #{tripId} and deleted = 0
            order by case application_status when 'PENDING' then 0 else 1 end, created_at desc
            limit 200
            """)
    List<TeamJoinApplication> findByTripId(@Param("tripId") Long tripId);

    /**
     * 新增入队申请。
     */
    @Insert("""
            insert into team_join_application
                (id, team_id, trip_id, applicant_user_id, applicant_vehicle_id, reviewer_user_id,
                 application_status, apply_message, join_question_json, review_message, reviewed_at,
                 created_at, updated_at, deleted)
            values
                (#{id}, #{teamId}, #{tripId}, #{applicantUserId}, #{applicantVehicleId}, #{reviewerUserId},
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
}
