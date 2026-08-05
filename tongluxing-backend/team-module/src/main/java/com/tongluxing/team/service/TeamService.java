package com.tongluxing.team.service;

import java.util.List;

import com.tongluxing.team.dto.CreateTeamRequest;
import com.tongluxing.team.dto.JoinTeamApplicationRequest;
import com.tongluxing.team.dto.ReviewTeamApplicationRequest;
import com.tongluxing.team.dto.UpdateTeamSettingsRequest;
import com.tongluxing.team.dto.RemoveTeamMemberRequest;
import com.tongluxing.team.dto.ConfirmPassengerVehicleRequest;
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

    /** 队长查询自己收到的全部入队申请。 */
    List<TeamApplicationResponse> getReceivedApplications(String status);

    /** 队长更新招募、途中加入、脱队阈值和隐私设置。 */
    TeamResponse updateSettings(Long teamId, UpdateTeamSettingsRequest request);

    /** 队长移除指定成员，并同步行程和群聊。 */
    TeamResponse removeMember(Long teamId, Long memberUserId, RemoveTeamMemberRequest request);

    /** 被关联车主确认或拒绝乘客同车关系。 */
    TeamMemberListResponse confirmPassengerVehicle(Long teamId, Long passengerUserId,
                                                    ConfirmPassengerVehicleRequest request);

    /** 查询当前用户作为队长或队员所在的当前车队。 */
    TeamResponse getMyCurrentTeam();

    /**
     * 成员退出车队。
     */
    TeamResponse exit(Long teamId);

    /** 当前成员按行程 ID 退出对应车队，供行程群聊退群流程调用。 */
    TeamResponse exitTrip(Long tripId);

    /** 队长解散行程群时解散关联车队，并将全部成员记录为已退出。 */
    void dissolveTrip(Long tripId);
}
