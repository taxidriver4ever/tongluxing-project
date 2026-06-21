package com.tongdao.team.service.impl;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.tongdao.common.exception.BusinessException;
import com.tongdao.common.result.ResultCode;
import com.tongdao.common.utils.SnowflakeIdGenerator;
import com.tongdao.team.dto.CreateTeamRequest;
import com.tongdao.team.dto.JoinTeamApplicationRequest;
import com.tongdao.team.dto.ReviewTeamApplicationRequest;
import com.tongdao.team.entity.Team;
import com.tongdao.team.entity.TeamJoinApplication;
import com.tongdao.team.entity.TeamMember;
import com.tongdao.team.mapper.TeamAuditLogMapper;
import com.tongdao.team.mapper.TeamJoinApplicationMapper;
import com.tongdao.team.mapper.TeamMapper;
import com.tongdao.team.mapper.TeamMemberMapper;
import com.tongdao.team.service.TeamService;
import com.tongdao.team.vo.TeamApplicationResponse;
import com.tongdao.team.vo.TeamMemberListResponse;
import com.tongdao.team.vo.TeamMemberResponse;
import com.tongdao.team.vo.TeamResponse;
import com.tongdao.trip.entity.Trip;
import com.tongdao.trip.mapper.TripMapper;
import com.tongdao.user.support.CurrentUserContext;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TeamServiceImpl implements TeamService {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final TeamMapper teamMapper;
    private final TeamMemberMapper memberMapper;
    private final TeamJoinApplicationMapper applicationMapper;
    private final TeamAuditLogMapper auditLogMapper;
    private final TripMapper tripMapper;
    private final CurrentUserContext currentUserContext;

    @Override
    public TeamResponse createTeam(CreateTeamRequest request) {
        Long userId = currentUserContext.requireUserId();
        if (memberMapper.findActiveByUserId(userId) != null || teamMapper.findActiveOwnedByUser(userId) != null) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "当前用户已有活跃车队");
        }
        Trip trip = tripMapper.findById(request.tripId());
        if (trip == null || !userId.equals(trip.getUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只能基于自己的行程创建车队");
        }
        LocalDateTime now = LocalDateTime.now();
        Team team = new Team();
        team.setId(SnowflakeIdGenerator.nextId());
        team.setTripId(trip.getId());
        team.setOwnerUserId(userId);
        team.setOwnerVehicleId(request.ownerVehicleId());
        team.setTeamName(request.teamName());
        team.setTeamDesc(request.teamDesc());
        team.setStartName(trip.getStartName());
        team.setEndName(trip.getEndName());
        team.setDepartureTime(trip.getDepartureTime());
        team.setMaxMemberCount(request.maxMemberCount());
        team.setCurrentMemberCount(1);
        team.setJoinMode(StringUtils.hasText(request.joinMode()) ? request.joinMode() : "APPROVAL");
        team.setTeamStatus("ACTIVE");
        team.setPublicFlag(Boolean.TRUE.equals(request.publicFlag()) ? 1 : 0);
        team.setNotice(request.notice());
        team.setCreatedAt(now);
        team.setUpdatedAt(now);
        teamMapper.insert(team);
        addMember(team.getId(), userId, request.ownerVehicleId(), "OWNER");
        audit(team.getId(), userId, "CREATE_TEAM", "创建车队");
        return toTeamResponse(team);
    }

    @Override
    public TeamResponse getTeam(Long teamId) {
        return toTeamResponse(requireTeam(teamId));
    }

    @Override
    public TeamMemberListResponse getMembers(Long teamId) {
        requireTeam(teamId);
        return new TeamMemberListResponse(memberMapper.findActiveByTeamId(teamId).stream()
                .map(this::toMemberResponse)
                .toList());
    }

    @Override
    public TeamApplicationResponse apply(Long teamId, JoinTeamApplicationRequest request) {
        Long userId = currentUserContext.requireUserId();
        Team team = requireTeam(teamId);
        if (team.getOwnerUserId().equals(userId)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "队长无需申请入队");
        }
        if (memberMapper.findActiveByUserId(userId) != null) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "当前用户已有活跃车队");
        }
        if (applicationMapper.findPending(teamId, userId) != null) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "已存在待审批申请");
        }
        LocalDateTime now = LocalDateTime.now();
        TeamJoinApplication application = new TeamJoinApplication();
        application.setId(SnowflakeIdGenerator.nextId());
        application.setTeamId(teamId);
        application.setTripId(team.getTripId());
        application.setApplicantUserId(userId);
        application.setApplicantVehicleId(request.applicantVehicleId());
        application.setApplicationStatus("PENDING");
        application.setApplyMessage(request.applyMessage());
        application.setJoinQuestionJson(request.joinQuestionJson());
        application.setCreatedAt(now);
        application.setUpdatedAt(now);
        applicationMapper.insert(application);
        audit(teamId, userId, "APPLY_TEAM", "申请入队");
        return toApplicationResponse(application);
    }

    @Override
    public TeamApplicationResponse review(Long applicationId, ReviewTeamApplicationRequest request) {
        Long reviewerId = currentUserContext.requireUserId();
        TeamJoinApplication application = applicationMapper.findById(applicationId);
        if (application == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "入队申请不存在");
        }
        Team team = requireTeam(application.getTeamId());
        if (!team.getOwnerUserId().equals(reviewerId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "仅队长可审批");
        }
        String status = "APPROVE".equalsIgnoreCase(request.reviewAction()) || "APPROVED".equalsIgnoreCase(request.reviewAction())
                ? "APPROVED" : "REJECTED";
        LocalDateTime now = LocalDateTime.now();
        int changed = applicationMapper.review(applicationId, reviewerId, status, request.reviewMessage(), now);
        if (changed == 0) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "申请状态已变更");
        }
        application.setReviewerUserId(reviewerId);
        application.setApplicationStatus(status);
        application.setReviewMessage(request.reviewMessage());
        application.setReviewedAt(now);
        application.setUpdatedAt(now);
        if ("APPROVED".equals(status)) {
            if (memberMapper.findActiveByUserId(application.getApplicantUserId()) != null) {
                throw new BusinessException(ResultCode.BUSINESS_ERROR, "申请人已有活跃车队");
            }
            int incremented = teamMapper.incrementMemberCount(team.getId(), now);
            if (incremented == 0) {
                throw new BusinessException(ResultCode.BUSINESS_ERROR, "车队已满或不可加入");
            }
            addMember(team.getId(), application.getApplicantUserId(), application.getApplicantVehicleId(), "MEMBER");
        }
        audit(team.getId(), reviewerId, "REVIEW_TEAM_APPLICATION", status);
        return toApplicationResponse(application);
    }

    @Override
    public TeamResponse exit(Long teamId) {
        Long userId = currentUserContext.requireUserId();
        Team team = requireTeam(teamId);
        if (team.getOwnerUserId().equals(userId)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "队长不可直接退出车队");
        }
        int changed = memberMapper.exit(teamId, userId, LocalDateTime.now());
        if (changed == 0) {
            throw new BusinessException(ResultCode.FORBIDDEN, "不是活跃成员");
        }
        teamMapper.decrementMemberCount(teamId, LocalDateTime.now());
        audit(teamId, userId, "EXIT_TEAM", "退出车队");
        return toTeamResponse(teamMapper.findById(teamId));
    }

    private void addMember(Long teamId, Long userId, Long vehicleId, String role) {
        TeamMember existed = memberMapper.findByTeamAndUser(teamId, userId);
        if (existed != null) {
            memberMapper.reactivate(teamId, userId, vehicleId, role, LocalDateTime.now());
            return;
        }
        TeamMember member = new TeamMember();
        LocalDateTime now = LocalDateTime.now();
        member.setId(SnowflakeIdGenerator.nextId());
        member.setTeamId(teamId);
        member.setUserId(userId);
        member.setVehicleId(vehicleId);
        member.setMemberRole(role);
        member.setMemberStatus("ACTIVE");
        member.setJoinedAt(now);
        member.setNicknameSnapshot("用户" + userId);
        member.setVehicleSnapshot(vehicleId == null ? null : "车辆" + vehicleId);
        member.setCreatedAt(now);
        member.setUpdatedAt(now);
        memberMapper.insert(member);
    }

    private Team requireTeam(Long teamId) {
        Team team = teamMapper.findById(teamId);
        if (team == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "车队不存在");
        }
        return team;
    }

    private void audit(Long teamId, Long userId, String operationType, String remark) {
        auditLogMapper.insert(SnowflakeIdGenerator.nextId(), teamId, userId, operationType, null, null, remark);
    }

    private TeamResponse toTeamResponse(Team team) {
        return new TeamResponse(
                String.valueOf(team.getId()),
                String.valueOf(team.getTripId()),
                String.valueOf(team.getOwnerUserId()),
                String.valueOf(team.getOwnerVehicleId()),
                team.getTeamName(),
                team.getTeamDesc(),
                team.getStartName(),
                team.getEndName(),
                format(team.getDepartureTime()),
                team.getMaxMemberCount(),
                team.getCurrentMemberCount(),
                team.getJoinMode(),
                team.getTeamStatus(),
                team.getPublicFlag() != null && team.getPublicFlag() == 1,
                team.getChatConversationId() == null ? null : String.valueOf(team.getChatConversationId()),
                team.getNotice(),
                format(team.getCreatedAt())
        );
    }

    private TeamMemberResponse toMemberResponse(TeamMember member) {
        return new TeamMemberResponse(
                String.valueOf(member.getId()),
                String.valueOf(member.getTeamId()),
                String.valueOf(member.getUserId()),
                member.getVehicleId() == null ? null : String.valueOf(member.getVehicleId()),
                member.getMemberRole(),
                member.getMemberStatus(),
                member.getNicknameSnapshot(),
                member.getVehicleSnapshot(),
                format(member.getJoinedAt())
        );
    }

    private TeamApplicationResponse toApplicationResponse(TeamJoinApplication application) {
        return new TeamApplicationResponse(
                String.valueOf(application.getId()),
                String.valueOf(application.getTeamId()),
                application.getTripId() == null ? null : String.valueOf(application.getTripId()),
                String.valueOf(application.getApplicantUserId()),
                application.getApplicantVehicleId() == null ? null : String.valueOf(application.getApplicantVehicleId()),
                application.getApplicationStatus(),
                application.getApplyMessage(),
                application.getReviewMessage(),
                format(application.getReviewedAt()),
                format(application.getCreatedAt())
        );
    }

    private String format(LocalDateTime time) {
        return time == null ? null : FORMATTER.format(time);
    }
}
