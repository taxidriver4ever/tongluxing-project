package com.tongluxing.team.service;

import java.util.List;

import com.tongluxing.team.dto.CreateTeamRequest;
import com.tongluxing.team.dto.JoinTeamApplicationRequest;
import com.tongluxing.team.dto.ReviewTeamApplicationRequest;
import com.tongluxing.team.vo.TeamApplicationResponse;
import com.tongluxing.team.vo.TeamMemberListResponse;
import com.tongluxing.team.vo.TeamResponse;

/**
 * 车队模块业务服务接口。
 */
public interface TeamService {

    /**
     * 创建车队并自动将创建人加入为队长。
     */
    TeamResponse createTeam(CreateTeamRequest request);

    /**
     * 为公开发布行程确保存在可申请加入的车队。
     *
     * <p>仅供后端行程生命周期联调使用，不暴露为控制器接口。</p>
     */
    TeamResponse ensurePublishedTripTeam(
            Long tripId,
            Long ownerUserId,
            Long ownerVehicleId,
            String teamName,
            Integer maxMemberCount
    );

    /**
     * 查询车队详情。
     */
    TeamResponse getTeam(Long teamId);

    /**
     * 查询车队成员列表。
     */
    TeamMemberListResponse getMembers(Long teamId);

    /**
     * 提交入队申请。
     */
    TeamApplicationResponse apply(Long teamId, JoinTeamApplicationRequest request);

    /**
     * 审批入队申请。
     */
    TeamApplicationResponse review(Long applicationId, ReviewTeamApplicationRequest request);

    /** 查询当前用户提交的入队申请。 */
    List<TeamApplicationResponse> getMyApplications();

    /** 队长查询自己指定行程收到的申请。 */
    List<TeamApplicationResponse> getTripApplications(Long tripId);

    /**
     * 成员退出车队。
     */
    TeamResponse exit(Long teamId);
}
