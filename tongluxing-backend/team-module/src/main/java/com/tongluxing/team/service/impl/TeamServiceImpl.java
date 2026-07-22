package com.tongluxing.team.service.impl;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.tongluxing.common.exception.BusinessException;
import com.tongluxing.common.result.ResultCode;
import com.tongluxing.common.utils.SnowflakeIdGenerator;
import com.tongluxing.team.dto.CreateTeamRequest;
import com.tongluxing.team.dto.JoinTeamApplicationRequest;
import com.tongluxing.team.dto.ReviewTeamApplicationRequest;
import com.tongluxing.team.entity.Team;
import com.tongluxing.team.entity.TeamJoinApplication;
import com.tongluxing.team.entity.TeamMember;
import com.tongluxing.team.integration.TeamTripPort;
import com.tongluxing.team.integration.TeamTripPort.TeamTripDTO;
import com.tongluxing.team.mapper.TeamAuditLogMapper;
import com.tongluxing.team.mapper.TeamJoinApplicationMapper;
import com.tongluxing.team.mapper.TeamMapper;
import com.tongluxing.team.mapper.TeamMemberMapper;
import com.tongluxing.team.service.TeamService;
import com.tongluxing.team.service.TeamApplicationReviewedEvent;
import com.tongluxing.team.vo.TeamApplicationResponse;
import com.tongluxing.team.vo.TeamMemberListResponse;
import com.tongluxing.team.vo.TeamMemberResponse;
import com.tongluxing.team.vo.TeamResponse;
import com.tongluxing.user.support.CurrentUserContext;

import lombok.RequiredArgsConstructor;

/**
 * 车队模块业务服务实现，负责车队创建、入队审批、成员维护和审计日志。
 */
@Service
@RequiredArgsConstructor
public class TeamServiceImpl implements TeamService {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final TeamMapper teamMapper;
    private final TeamMemberMapper memberMapper;
    private final TeamJoinApplicationMapper applicationMapper;
    private final TeamAuditLogMapper auditLogMapper;
    private final TeamTripPort tripPort;
    private final CurrentUserContext currentUserContext;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 创建车队：校验行程归属和重复车队后，写入车队及队长成员。
     */
    @Override
    @Transactional
    public TeamResponse createTeam(CreateTeamRequest request) {
        Long userId = currentUserContext.requireUserId();
        TeamTripDTO trip = tripPort.getTrip(request.tripId());
        if (trip == null || !userId.equals(trip.ownerUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只能基于自己的行程创建车队");
        }
        if (teamMapper.findAnyActiveByTripId(request.tripId()) != null) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "该行程已经创建车队");
        }
        LocalDateTime now = LocalDateTime.now();
        Team team = new Team();
        team.setId(SnowflakeIdGenerator.nextId());
        team.setTripId(trip.tripId());
        team.setOwnerUserId(userId);
        team.setOwnerVehicleId(request.ownerVehicleId());
        team.setTeamName(request.teamName());
        team.setTeamDesc(request.teamDesc());
        team.setStartName(trip.startName());
        team.setEndName(trip.endName());
        team.setDepartureTime(trip.departureTime());
        team.setMaxMemberCount(request.maxMemberCount());
        team.setCurrentMemberCount(1);
        team.setJoinMode(StringUtils.hasText(request.joinMode()) ? request.joinMode() : "APPROVAL");
        team.setTeamStatus("ACTIVE");
        team.setPublicFlag(Boolean.TRUE.equals(request.publicFlag()) ? 1 : 0);
        team.setNotice(request.notice());
        team.setCreatedAt(now);
        team.setUpdatedAt(now);
        teamMapper.insert(team);
        // 创建人自动成为队长，保证车队创建后至少有一名活跃成员。
        addMember(team.getId(), userId, request.ownerVehicleId(), "OWNER");
        audit(team.getId(), userId, "CREATE_TEAM", "创建车队");
        return toTeamResponse(team);
    }

    /**
     * 查询车队详情，不存在时统一抛出业务异常。
     */
    @Override
    public TeamResponse getTeam(Long teamId) {
        return toTeamResponse(requireTeam(teamId));
    }

    /**
     * 查询车队活跃成员列表。
     */
    @Override
    public TeamMemberListResponse getMembers(Long teamId) {
        requireTeam(teamId);
        return new TeamMemberListResponse(memberMapper.findActiveByTeamId(teamId).stream()
                .map(this::toMemberResponse)
                .toList());
    }

    /**
     * 提交入队申请：允许加入多个未来车队，但禁止与其他进行中行程冲突。
     */
    @Override
    @Transactional
    public TeamApplicationResponse apply(Long teamId, JoinTeamApplicationRequest request) {
        Long userId = currentUserContext.requireUserId();
        Team team = requireTeam(teamId);
        if (team.getOwnerUserId().equals(userId)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "队长无需申请入队");
        }
        TeamMember existedMember = memberMapper.findByTeamAndUser(teamId, userId);
        if (existedMember != null && "ACTIVE".equals(existedMember.getMemberStatus())) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "你已经是该车队成员");
        }
        ensureNoOtherRunningTrip(userId, team.getTripId());
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

    /**
     * 队长审批入队申请；审批通过时同步增加车队人数并写入成员记录。
     */
    @Override
    @Transactional
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
            // 仅当目标行程已经进行中时，校验申请人是否正在其他行程中。
            ensureNoOtherRunningTrip(application.getApplicantUserId(), team.getTripId());
            int incremented = teamMapper.incrementMemberCount(team.getId(), now);
            if (incremented == 0) {
                throw new BusinessException(ResultCode.BUSINESS_ERROR, "车队已满或不可加入");
            }
            addMember(team.getId(), application.getApplicantUserId(), application.getApplicantVehicleId(), "MEMBER");
        }
        audit(team.getId(), reviewerId, "REVIEW_TEAM_APPLICATION", status);
        eventPublisher.publishEvent(new TeamApplicationReviewedEvent(team.getId(), team.getTripId(),
                application.getApplicantUserId(), status));
        return toApplicationResponse(application);
    }

    /**
     * 普通成员退出车队，并同步扣减车队人数。
     */
    @Override
    @Transactional
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


    /**
     * 用户可以加入多个未来车队，但目标行程进行中时，不允许与其他进行中行程并行。
     */
    private void ensureNoOtherRunningTrip(Long userId, Long targetTripId) {
        TeamTripDTO target = tripPort.getTrip(targetTripId);
        if (target == null || !target.running()) {
            return;
        }
        Long ownedRunningTripId = tripPort.findRunningOwnedTripId(userId);
        if (ownedRunningTripId != null && !targetTripId.equals(ownedRunningTripId)) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "用户已有其他进行中的行程");
        }
        for (TeamMember membership : memberMapper.findActiveListByUserId(userId)) {
            Team membershipTeam = teamMapper.findById(membership.getTeamId());
            if (membershipTeam == null || targetTripId.equals(membershipTeam.getTripId())) {
                continue;
            }
            TeamTripDTO membershipTrip = tripPort.getTrip(membershipTeam.getTripId());
            if (membershipTrip != null && membershipTrip.running()) {
                throw new BusinessException(ResultCode.BUSINESS_ERROR, "用户正在参加其他进行中的车队");
            }
        }
    }

    /**
     * 新增或重新激活车队成员。
     */
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

    /**
     * 查询车队，不存在时统一抛出业务异常。
     */
    private Team requireTeam(Long teamId) {
        Team team = teamMapper.findById(teamId);
        if (team == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "车队不存在");
        }
        return team;
    }

    /**
     * 写入车队操作审计日志。
     */
    private void audit(Long teamId, Long userId, String operationType, String remark) {
        auditLogMapper.insert(SnowflakeIdGenerator.nextId(), teamId, userId, operationType, null, null, remark);
    }

    /**
     * 将车队实体转换为接口响应对象。
     */
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

    /**
     * 将成员实体转换为接口响应对象。
     */
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

    /**
     * 将入队申请实体转换为接口响应对象。
     */
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

    /**
     * 统一格式化时间字段。
     */
    private String format(LocalDateTime time) {
        return time == null ? null : FORMATTER.format(time);
    }
}
