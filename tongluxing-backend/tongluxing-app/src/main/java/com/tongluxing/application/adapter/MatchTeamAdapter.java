package com.tongluxing.application.adapter;

import java.math.BigDecimal;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

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
    public Map<Long, MatchTeamDTO> findActiveTeamsByTripIds(List<Long> tripIds) {
        if (tripIds == null || tripIds.isEmpty()) return Map.of();
        List<Long> ids = tripIds.stream().filter(java.util.Objects::nonNull).distinct().limit(1000).toList();
        if (ids.isEmpty()) return Map.of();
        Map<Long, MatchTeamDTO> result = new LinkedHashMap<>();
        teamMapper.findActiveByTripIds(ids).forEach(team -> result.put(team.getTripId(), toDTO(team)));
        return result;
    }

    @Override
    public Set<Long> findBlockedTeamIds(List<Long> teamIds, Long userId) {
        if (teamIds == null || teamIds.isEmpty() || userId == null) return Set.of();
        List<Long> ids = teamIds.stream().filter(java.util.Objects::nonNull).distinct().limit(1000).toList();
        if (ids.isEmpty()) return Set.of();
        Set<Long> blocked = new HashSet<>(teamMemberMapper.findActiveTeamIdsByUserAndTeams(userId, ids));
        blocked.addAll(applicationMapper.findPendingTeamIdsByUserAndTeams(userId, ids));
        return blocked;
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
        if (application == null || application.getApplicationStatus() == null) {
            return "NONE";
        }
        String applicationStatus = application.getApplicationStatus().toUpperCase(java.util.Locale.ROOT);
        // 主动取消代表当前已不存在申请关系，推荐和详情页应立即恢复“申请加入”。
        // 同时兼容历史数据中可能存在的美式拼写 CANCELED。
        if (List.of("CANCELLED", "CANCELED").contains(applicationStatus)) {
            return "NONE";
        }
        return applicationStatus;
    }

    @Override
    public Map<Long, String> relationshipStatuses(List<MatchTeamDTO> teams, Long userId) {
        if (teams == null || teams.isEmpty() || userId == null) return Map.of();
        Map<Long, String> result = new LinkedHashMap<>();
        List<Long> queryIds = teams.stream().filter(java.util.Objects::nonNull)
                .filter(team -> {
                    if (userId.equals(team.ownerUserId())) {
                        result.put(team.teamId(), "OWNER");
                        return false;
                    }
                    return true;
                }).map(MatchTeamDTO::teamId).filter(java.util.Objects::nonNull).distinct().toList();
        if (queryIds.isEmpty()) return result;

        Map<Long, String> memberStatuses = teamMemberMapper.findStatusesByUserAndTeams(userId, queryIds).stream()
                .collect(java.util.stream.Collectors.toMap(
                        com.tongluxing.team.entity.TeamMember::getTeamId,
                        com.tongluxing.team.entity.TeamMember::getMemberStatus,
                        (left, right) -> left));
        Map<Long, String> applicationStatuses = applicationMapper
                .findLatestStatusesByUserAndTeams(userId, queryIds).stream()
                .collect(java.util.stream.Collectors.toMap(
                        com.tongluxing.team.entity.TeamJoinApplication::getTeamId,
                        com.tongluxing.team.entity.TeamJoinApplication::getApplicationStatus,
                        (left, right) -> left));

        queryIds.forEach(teamId -> result.put(teamId,
                aggregateRelationship(memberStatuses.get(teamId), applicationStatuses.get(teamId))));
        return result;
    }

    private String aggregateRelationship(String memberStatus, String applicationStatus) {
        if ("ACTIVE".equals(memberStatus)) return "JOINED";
        if ("PENDING".equals(applicationStatus)) return "PENDING";
        if (List.of("EXITED", "REMOVED").contains(memberStatus)) return memberStatus;
        if (applicationStatus == null || applicationStatus.isBlank()) return "NONE";
        String normalized = applicationStatus.toUpperCase(java.util.Locale.ROOT);
        return List.of("CANCELLED", "CANCELED").contains(normalized) ? "NONE" : normalized;
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
        int activeMemberCount = team.getCurrentMemberCount() == null ? 0 : team.getCurrentMemberCount();
        return new MatchTeamDTO(team.getId(), team.getTripId(), team.getOwnerUserId(), team.getTeamName(),
                team.getTeamDesc(), team.getNotice(),
                team.getStartName(), team.getEndName(), team.getDepartureTime(), activeMemberCount,
                team.getMaxMemberCount(), team.getRecruitmentStatus(), team.getAllowMidwayJoin() != null
                        && team.getAllowMidwayJoin() == 1);
    }
}
