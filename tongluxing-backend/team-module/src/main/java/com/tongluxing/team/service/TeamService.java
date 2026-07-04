package com.tongluxing.team.service;

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

    /**
     * 成员退出车队。
     */
    TeamResponse exit(Long teamId);
}
