package com.tongluxing;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.stereotype.Component;

import com.tongluxing.match.integration.MatchTeamPort;
import com.tongluxing.team.entity.Team;
import com.tongluxing.team.mapper.TeamMapper;
import com.tongluxing.team.mapper.TeamJoinApplicationMapper;
import com.tongluxing.team.mapper.TeamMemberMapper;
import com.tongluxing.user.mapper.UserDomainMapper;
import com.tongluxing.team.dto.JoinTeamApplicationRequest;
import com.tongluxing.team.service.TeamService;

import lombok.RequiredArgsConstructor;

/**
 * 匹配模块访问车队模块的适配器。
 *
 * <p>应用层负责把 team-module 的实体数据转换为 match-module 所需的端口 DTO。</p>
 */
@Component
@RequiredArgsConstructor
public class MatchTeamAdapter implements MatchTeamPort {
    private final TeamMapper teamMapper;
    private final TeamMemberMapper teamMemberMapper;
    private final TeamJoinApplicationMapper applicationMapper;
    private final TeamService teamService;
    private final UserDomainMapper userMapper;

    /**
     * 查询公开活跃车队，作为匹配推荐候选池。
     *
     * @param limit 最大查询数量
     * @return 匹配模块车队摘要列表
     */
    @Override
    public List<MatchTeamDTO> listPublicActiveTeams(int limit) {
        return teamMapper.findPublicActive(limit).stream()
                .map(this::toDTO)
                .toList();
    }

    @Override
    public MatchTeamDTO findActiveTeamByTripId(Long tripId) {
        Team team = teamMapper.findActiveByTripId(tripId);
        return team == null ? null : toDTO(team);
    }

    @Override
    public boolean hasActiveMembershipOrPending(Long teamId, Long userId) {
        var member = teamMemberMapper.findByTeamAndUser(teamId, userId);
        return (member != null && "ACTIVE".equals(member.getMemberStatus()))
                || applicationMapper.findPending(teamId, userId) != null;
    }

    @Override
    public String relationshipStatus(Long teamId, Long userId) {
        Team team = teamMapper.findById(teamId);
        if (team != null && userId.equals(team.getOwnerUserId())) {
            return "OWNER";
        }
        var member = teamMemberMapper.findByTeamAndUser(teamId, userId);
        if (member != null && "ACTIVE".equals(member.getMemberStatus())) {
            return "JOINED";
        }
        var application = applicationMapper.findLatest(teamId, userId);
        if (application != null && "PENDING".equals(application.getApplicationStatus())) {
            return "PENDING";
        }
        // 退出或被移除的历史成员需要显示“申请归队”，不能被旧 APPROVED 申请覆盖。
        if (member != null && List.of("EXITED", "REMOVED").contains(member.getMemberStatus())) {
            return member.getMemberStatus();
        }
        return application == null ? "NONE" : application.getApplicationStatus();
    }

    @Override
    public List<MatchMemberDTO> listPublicMembers(Long teamId, int limit) {
        return teamMemberMapper.findActiveByTeamId(teamId).stream().limit(Math.max(1, Math.min(limit, 50)))
                .map(member -> {
                    var profile = userMapper.findProfile(member.getUserId());
                    return new MatchMemberDTO(member.getUserId(),
                            profile == null || profile.getNickname() == null || profile.getNickname().isBlank()
                                    ? member.getNicknameSnapshot() : profile.getNickname(),
                            profile == null ? null : profile.getAvatarImageKey(), member.getMemberRole(),
                            profile == null ? "UNSUBMITTED" : profile.getCertificationStatus(),
                            profile == null ? 0 : profile.getTotalTripCount(),
                            profile == null ? 0L : profile.getTotalDistanceMeters(),
                            member.getVehicleId(), member.getVehicleSnapshot(), member.getPlateReference());
                }).toList();
    }

    @Override
    public Long apply(Long teamId, String message, Long applicantVehicleId, String joinQuestionJson,
            String joinRole, Long linkedOwnerUserId, Long linkedVehicleId, String plateNumber,
            String applicationType, BigDecimal currentLatitude, BigDecimal currentLongitude) {
        return Long.valueOf(teamService.apply(teamId,
                new JoinTeamApplicationRequest(applicantVehicleId, message, joinQuestionJson,
                        "RETURN".equalsIgnoreCase(applicationType) ? "RETURN" : "JOIN", joinRole,
                        linkedOwnerUserId, linkedVehicleId, plateNumber, currentLatitude, currentLongitude))
                .applicationId());
    }

    /**
     * 将车队实体转换为匹配模块所需的最小字段集合。
     *
     * @param team 车队实体
     * @return 匹配模块车队 DTO
     */
    private MatchTeamDTO toDTO(Team team) {
        int activeMemberCount = teamMemberMapper.countActiveByTeamId(team.getId());
        return new MatchTeamDTO(team.getId(), team.getTripId(), team.getOwnerUserId(), team.getTeamName(),
                team.getTeamDesc(), team.getNotice(),
                team.getStartName(), team.getEndName(), team.getDepartureTime(), activeMemberCount,
                team.getMaxMemberCount(), team.getRecruitmentStatus(), team.getAllowMidwayJoin() != null
                        && team.getAllowMidwayJoin() == 1);
    }
}
