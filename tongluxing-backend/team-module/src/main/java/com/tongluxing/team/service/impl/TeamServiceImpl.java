package com.tongluxing.team.service.impl;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tongluxing.common.exception.BusinessException;
import com.tongluxing.common.result.ResultCode;
import com.tongluxing.common.utils.SnowflakeIdGenerator;
import com.tongluxing.team.dto.CreateTeamRequest;
import com.tongluxing.team.dto.JoinTeamApplicationRequest;
import com.tongluxing.team.dto.ReviewTeamApplicationRequest;
import com.tongluxing.team.dto.UpdateTeamSettingsRequest;
import com.tongluxing.team.dto.RemoveTeamMemberRequest;
import com.tongluxing.team.dto.ConfirmPassengerVehicleRequest;
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
import com.tongluxing.team.service.TeamMemberRemovedEvent;
import com.tongluxing.team.vo.TeamApplicationResponse;
import com.tongluxing.team.vo.TeamMemberListResponse;
import com.tongluxing.team.vo.TeamMemberResponse;
import com.tongluxing.team.vo.TeamResponse;
import com.tongluxing.notify.dto.CreateNotificationEventRequest;
import com.tongluxing.notify.service.NotificationService;
import com.tongluxing.user.service.UserService;
import com.tongluxing.user.support.CurrentUserContext;
import com.tongluxing.vehicle.service.VehicleService;
import com.tongluxing.vehicle.vo.PublicVehicleCardResponse;
import com.tongluxing.vehicle.vo.VehicleResponse;

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
    private final UserService userService;
    private final VehicleService vehicleService;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    /**
     * 创建车队：校验行程归属和重复车队后，写入车队及队长成员。
     */
    @Override
    @Transactional
    public TeamResponse createTeam(CreateTeamRequest request) {
        Long userId = currentUserContext.requireUserId();
        TeamTripDTO trip = tripPort.getTrip(request.tripId());
        if (trip == null || !userId.equals(trip.publisherUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只能基于自己发布的行程创建车队");
        }
        if (!trip.driverLedBy(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN,
                    "乘客发布的是出行需求，不能创建车队或成为队长");
        }
        if (!trip.vehicleId().equals(request.ownerVehicleId())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "车队车辆必须与行程认证车辆一致");
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
        team.setRecruitmentStatus("OPEN");
        team.setAllowMidwayJoin(0);
        team.setDeviationWarningDistanceM(50_000);
        team.setDeviationWarningMinutes(30);
        team.setSevereDeviationDistanceM(100_000);
        team.setSevereDeviationMinutes(60);
        team.setMissingLocationMinutes(720);
        team.setJoinRadiusM(100_000);
        team.setPrivacyLevel("STANDARD");
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

    @Override
    @Transactional
    public TeamResponse ensurePublishedTripTeam(
            Long tripId,
            Long ownerUserId,
            Long ownerVehicleId,
            String teamName,
            Integer maxMemberCount) {
        Team existing = teamMapper.findAnyActiveByTripId(tripId);
        if (existing != null) {
            return toTeamResponse(existing);
        }
        TeamTripDTO trip = tripPort.getTrip(tripId);
        if (trip == null || !ownerUserId.equals(trip.publisherUserId())
                || !trip.driverLedBy(ownerUserId)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "可创建车队的车主行程不存在");
        }
        LocalDateTime now = LocalDateTime.now();
        Team team = new Team();
        team.setId(SnowflakeIdGenerator.nextId());
        team.setTripId(trip.tripId());
        team.setOwnerUserId(ownerUserId);
        team.setOwnerVehicleId(ownerVehicleId);
        team.setTeamName(StringUtils.hasText(teamName) ? teamName : trip.startName() + "同行车队");
        team.setTeamDesc("由公开行程自动创建，可在发现行程中申请加入");
        team.setStartName(trip.startName());
        team.setEndName(trip.endName());
        team.setDepartureTime(trip.departureTime());
        team.setMaxMemberCount(Math.max(2, Math.min(20, maxMemberCount == null ? 4 : maxMemberCount)));
        team.setCurrentMemberCount(1);
        team.setJoinMode("APPLICATION");
        team.setRecruitmentStatus("OPEN");
        team.setAllowMidwayJoin(0);
        team.setDeviationWarningDistanceM(50_000);
        team.setDeviationWarningMinutes(30);
        team.setSevereDeviationDistanceM(100_000);
        team.setSevereDeviationMinutes(60);
        team.setMissingLocationMinutes(720);
        team.setJoinRadiusM(100_000);
        team.setPrivacyLevel("STANDARD");
        team.setTeamStatus("ACTIVE");
        team.setPublicFlag(1);
        team.setNotice("");
        team.setCreatedAt(now);
        team.setUpdatedAt(now);
        teamMapper.insert(team);
        addMember(team.getId(), ownerUserId, ownerVehicleId, "OWNER");
        audit(team.getId(), ownerUserId, "CREATE_TEAM", "公开行程自动创建车队");
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
        Team team = requireTeam(teamId);
        Long requesterId = currentUserContext.requireUserId();
        requireActiveMemberOrOwner(team, requesterId);
        return new TeamMemberListResponse(memberMapper.findActiveByTeamId(teamId).stream()
                .map(member -> toMemberResponse(team, member, requesterId))
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
        TeamTripDTO trip = tripPort.getTrip(team.getTripId());
        if (!acceptingApplications(team, trip)) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR,
                    "车队已暂停招募，或行程进行中且未开启途中加入");
        }
        if (team.getCurrentMemberCount() + 1 > team.getMaxMemberCount()) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "剩余名额不足");
        }
        TeamMember existedMember = memberMapper.findByTeamAndUser(teamId, userId);
        if (existedMember != null && "ACTIVE".equals(existedMember.getMemberStatus())) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "你已经是该车队成员");
        }
        // P0：用户可以保留自己发布的行程，但同一时间只能作为普通成员加入一个其他队伍。
        ensureNoOtherActiveExternalTeam(userId, teamId);
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
        String applicationType = "RETURN".equalsIgnoreCase(request.applicationType()) ? "RETURN" : "JOIN";
        String joinRole = resolveJoinRole(request);
        if ("RETURN".equals(applicationType)) {
            validateReturnApplication(existedMember, request);
        }
        boolean wantsToDrive = "DRIVER".equals(joinRole);
        Long applicantVehicleId = wantsToDrive
                ? requireEligiblePrimaryVehicle(request.applicantVehicleId()).vehicleId()
                : null;
        application.setApplicantVehicleId(applicantVehicleId);
        application.setApplicationType(applicationType);
        application.setJoinRole(joinRole);
        application.setLinkedOwnerUserId(request.linkedOwnerUserId());
        application.setLinkedVehicleId(request.linkedVehicleId());
        application.setPlateReference(maskPlate(request.plateNumber()));
        application.setCurrentLatitude(request.currentLatitude());
        application.setCurrentLongitude(request.currentLongitude());
        boolean requiresOwnerConfirmation = !wantsToDrive
                && (request.linkedOwnerUserId() != null || request.linkedVehicleId() != null
                || StringUtils.hasText(request.plateNumber()));
        application.setOwnerConfirmStatus(requiresOwnerConfirmation ? "PENDING" : "NOT_REQUIRED");
        application.setApplicationStatus("PENDING");
        application.setApplyMessage(request.applyMessage());
        application.setJoinQuestionJson(request.joinQuestionJson());
        application.setCreatedAt(now);
        application.setUpdatedAt(now);
        applicationMapper.insert(application);
        audit(teamId, userId, "APPLY_TEAM", "申请入队");
        var applicant = userService.getChatMemberProfile(userId);
        notificationService.createEvent(new CreateNotificationEventRequest(
                "TEAM_JOIN_APPLICATION",
                "USER",
                team.getOwnerUserId(),
                "INTERACTION",
                "TEAM_APPLICATION",
                String.valueOf(application.getId()),
                "新的入队申请",
                displayName(applicant.nickname()) + "申请加入“" + team.getTeamName() + "”"
                        + (wantsToDrive ? "，并表示要开车" : "，作为乘客加入"),
                "team-join-application:" + application.getId()
        ));
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
            TeamTripDTO targetTrip = tripPort.getTrip(team.getTripId());
            if (!acceptingApplications(team, targetTrip)) {
                throw new BusinessException(ResultCode.BUSINESS_ERROR, "车队已停止招募，不能通过申请");
            }
            ensureNoOtherActiveExternalTeam(application.getApplicantUserId(), team.getId());
            // 仅当目标行程已经进行中时，校验申请人是否正在其他行程中。
            ensureNoOtherRunningTrip(application.getApplicantUserId(), team.getTripId());
            int incremented = teamMapper.incrementMemberCount(team.getId(), 1, now);
            if (incremented == 0) {
                throw new BusinessException(ResultCode.BUSINESS_ERROR, "车队已满或不可加入");
            }
            TeamMember approvedMember = addMember(
                    team.getId(), application.getApplicantUserId(),
                    application.getApplicantVehicleId(),
                    "DRIVER".equals(application.getJoinRole()) ? "DRIVER" : "MEMBER");
            memberMapper.updateVehicleLink(
                    team.getId(), application.getApplicantUserId(),
                    application.getLinkedOwnerUserId(), application.getLinkedVehicleId(),
                    application.getPlateReference(), application.getOwnerConfirmStatus(), now);
            tripPort.addApprovedMember(
                    team.getTripId(), approvedMember.getUserId(), approvedMember.getVehicleId(),
                    approvedMember.getNicknameSnapshot(), approvedMember.getVehicleSnapshot(), now);
        }
        audit(team.getId(), reviewerId, "REVIEW_TEAM_APPLICATION", status);
        eventPublisher.publishEvent(new TeamApplicationReviewedEvent(team.getId(), team.getTripId(),
                application.getApplicantUserId(), status));
        return toApplicationResponse(application);
    }

    @Override
    public List<TeamApplicationResponse> getMyApplications() {
        return applicationMapper.findByApplicantUserId(currentUserContext.requireUserId()).stream()
                .map(this::toApplicationResponse)
                .toList();
    }

    @Override
    public List<TeamApplicationResponse> getTripApplications(Long tripId) {
        Team team = teamMapper.findAnyActiveByTripId(tripId);
        if (team == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "行程车队不存在");
        }
        if (!team.getOwnerUserId().equals(currentUserContext.requireUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "仅队长可查看申请列表");
        }
        return applicationMapper.findByTripId(tripId).stream()
                .map(this::toApplicationResponse)
                .toList();
    }

    @Override
    public List<TeamApplicationResponse> getReceivedApplications(String status) {
        String normalized = StringUtils.hasText(status) ? status.trim().toUpperCase() : null;
        if (normalized != null && !List.of("PENDING", "APPROVED", "REJECTED").contains(normalized)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "申请状态不正确");
        }
        return applicationMapper.findReceivedByOwner(currentUserContext.requireUserId(), normalized).stream()
                .map(this::toApplicationResponse)
                .toList();
    }

    /** 队长更新队伍管理参数。 */
    @Override
    @Transactional
    public TeamResponse updateSettings(Long teamId, UpdateTeamSettingsRequest request) {
        Long ownerUserId = currentUserContext.requireUserId();
        Team team = requireOwnedTeam(teamId, ownerUserId);
        validateThresholds(request);
        int changed = teamMapper.updateSettings(
                teamId, ownerUserId,
                normalizeUpper(request.recruitmentStatus()),
                request.allowMidwayJoin() == null ? null : (Boolean.TRUE.equals(request.allowMidwayJoin()) ? 1 : 0),
                request.deviationWarningDistanceM(), request.deviationWarningMinutes(),
                request.severeDeviationDistanceM(), request.severeDeviationMinutes(),
                request.missingLocationMinutes(), request.joinRadiusM(),
                normalizeUpper(request.privacyLevel()), LocalDateTime.now());
        if (changed == 0) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "队伍状态已变化，设置保存失败");
        }
        audit(teamId, ownerUserId, "UPDATE_TEAM_SETTINGS", "更新招募、途中加入、脱队阈值和隐私设置");
        return toTeamResponse(teamMapper.findById(teamId));
    }

    /** 队长移除成员，同时释放名额并发布聊天同步事件。 */
    @Override
    @Transactional
    public TeamResponse removeMember(Long teamId, Long memberUserId, RemoveTeamMemberRequest request) {
        Long ownerUserId = currentUserContext.requireUserId();
        Team team = requireOwnedTeam(teamId, ownerUserId);
        if (ownerUserId.equals(memberUserId)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "队长不能移除自己，请使用解散车队");
        }
        String reason = request == null || !StringUtils.hasText(request.reason())
                ? "队长移除成员" : request.reason().trim();
        LocalDateTime now = LocalDateTime.now();
        if (memberMapper.removeByOwner(teamId, memberUserId, ownerUserId, reason, now) == 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "成员不存在或已经退出");
        }
        teamMapper.decrementMemberCount(teamId, now);
        tripPort.markMemberExited(team.getTripId(), memberUserId, "REMOVED", now);
        audit(teamId, ownerUserId, "REMOVE_MEMBER", reason);
        eventPublisher.publishEvent(new TeamMemberRemovedEvent(
                teamId, team.getTripId(), memberUserId, ownerUserId, reason));
        return toTeamResponse(teamMapper.findById(teamId));
    }

    /** 被关联车主确认乘客所选车辆，确认结果直接反映到成员列表。 */
    @Override
    @Transactional
    public TeamMemberListResponse confirmPassengerVehicle(
            Long teamId, Long passengerUserId, ConfirmPassengerVehicleRequest request) {
        Long ownerUserId = currentUserContext.requireUserId();
        Team team = requireTeam(teamId);
        requireActiveMemberOrOwner(team, ownerUserId);
        TeamMember passenger = memberMapper.findByTeamAndUser(teamId, passengerUserId);
        if (passenger == null || !"ACTIVE".equals(passenger.getMemberStatus())
                || !ownerUserId.equals(passenger.getLinkedOwnerUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "该乘客没有关联你的车辆，或已不在队伍中");
        }
        if (Boolean.TRUE.equals(request.approved()) && passenger.getLinkedVehicleId() != null) {
            // 车主确认前，以车辆座位数校验“司机 + 已关联乘客”的总人数。
            VehicleResponse linkedVehicle = vehicleService.getMyVehicles().vehicles().stream()
                    .filter(vehicle -> passenger.getLinkedVehicleId().equals(vehicle.vehicleId()))
                    .filter(vehicle -> "APPROVED".equals(vehicle.certificationStatus()))
                    .findFirst()
                    .orElseThrow(() -> new BusinessException(ResultCode.BAD_REQUEST,
                            "关联车辆不存在、未通过行驶证认证或不属于当前车主"));
            int occupiedSeats = memberMapper.countActiveByVehicle(teamId, linkedVehicle.vehicleId());
            if (linkedVehicle.seatCount() != null && occupiedSeats > linkedVehicle.seatCount()) {
                throw new BusinessException(ResultCode.BAD_REQUEST,
                        "该车辆座位已满，无法继续确认同车乘客");
            }
        }
        String status = Boolean.TRUE.equals(request.approved()) ? "CONFIRMED" : "REJECTED";
        if (memberMapper.confirmVehicleLink(teamId, passengerUserId, ownerUserId, status, LocalDateTime.now()) == 0) {
            throw new BusinessException(ResultCode.FORBIDDEN, "乘客车辆确认失败，请刷新后重试");
        }
        audit(teamId, ownerUserId, "CONFIRM_PASSENGER_VEHICLE", status);
        return getMembers(teamId);
    }

    /** 当前车队优先返回本人拥有的车队，否则返回唯一的外部成员车队。 */
    @Override
    public TeamResponse getMyCurrentTeam() {
        Long userId = currentUserContext.requireUserId();
        Team owned = teamMapper.findActiveOwnedByUser(userId);
        if (owned != null) {
            return toTeamResponse(owned);
        }
        return memberMapper.findActiveListByUserId(userId).stream()
                .filter(member -> !"OWNER".equals(member.getMemberRole()))
                .map(member -> teamMapper.findById(member.getTeamId()))
                .filter(team -> team != null && "ACTIVE".equals(team.getTeamStatus()))
                .findFirst()
                .map(this::toTeamResponse)
                .orElse(null);
    }

    /**
     * 普通成员退出车队，并同步扣减车队人数。
     */
    @Override
    @Transactional
    public TeamResponse exit(Long teamId) {
        Team team = requireTeam(teamId);
        return exitTeam(team, currentUserContext.requireUserId());
    }

    @Override
    @Transactional
    public TeamResponse exitTrip(Long tripId) {
        Team team = teamMapper.findAnyActiveByTripId(tripId);
        if (team == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "行程车队不存在");
        }
        return exitTeam(team, currentUserContext.requireUserId());
    }

    @Override
    @Transactional
    public void dissolveTrip(Long tripId) {
        Team team = teamMapper.findAnyActiveByTripId(tripId);
        if (team == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "行程车队不存在");
        }
        Long ownerUserId = currentUserContext.requireUserId();
        if (!team.getOwnerUserId().equals(ownerUserId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只有队长可以解散车队");
        }
        LocalDateTime now = LocalDateTime.now();
        List<TeamMember> activeMembers = memberMapper.findActiveByTeamId(team.getId());
        for (TeamMember member : activeMembers) {
            tripPort.markMemberExited(tripId, member.getUserId(), "EXITED", now);
        }
        memberMapper.exitAll(team.getId(), now);
        teamMapper.dissolve(team.getId(), now);
        audit(team.getId(), ownerUserId, "DISSOLVE_TEAM", "群主解散群聊并取消关联行程");
    }

    private TeamResponse exitTeam(Team team, Long userId) {
        if (team.getOwnerUserId().equals(userId)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "队长不可直接退出车队");
        }
        LocalDateTime now = LocalDateTime.now();
        int changed = memberMapper.exit(team.getId(), userId, now);
        if (changed == 0) {
            throw new BusinessException(ResultCode.FORBIDDEN, "不是活跃成员");
        }
        teamMapper.decrementMemberCount(team.getId(), now);
        TeamTripDTO trip = tripPort.getTrip(team.getTripId());
        String exitStatus = trip != null && trip.running()
                ? "EXITED_DURING_TRIP"
                : trip != null && List.of("FINISHED", "ENDED", "SETTLED", "ARCHIVED").contains(trip.status())
                ? "EXITED_AFTER_TRIP"
                : "EXITED";
        tripPort.markMemberExited(team.getTripId(), userId, exitStatus, now);
        audit(team.getId(), userId, "EXIT_TEAM", "退出车队并退出关联行程");
        return toTeamResponse(teamMapper.findById(team.getId()));
    }


    /**
     * P0 限制：用户可以保留自己发布并担任队长的车队，但最多只能作为普通成员加入一个其他车队。
     */
    private void ensureNoOtherActiveExternalTeam(Long userId, Long targetTeamId) {
        for (TeamMember membership : memberMapper.findActiveListByUserId(userId)) {
            if (targetTeamId.equals(membership.getTeamId()) || "OWNER".equals(membership.getMemberRole())) {
                continue;
            }
            Team other = teamMapper.findById(membership.getTeamId());
            if (other != null && "ACTIVE".equals(other.getTeamStatus())) {
                throw new BusinessException(ResultCode.BUSINESS_ERROR,
                        "同一时间最多只能作为队员加入一个其他队伍");
            }
        }
    }

    /** 判断当前车队是否接受首次加入或途中加入申请。 */
    private boolean acceptingApplications(Team team, TeamTripDTO trip) {
        if (team == null || trip == null || !"ACTIVE".equals(team.getTeamStatus())
                || !Integer.valueOf(1).equals(team.getPublicFlag())
                || !"OPEN".equals(team.getRecruitmentStatus())) {
            return false;
        }
        if ("PUBLISHED".equals(trip.status()) || "READY".equals(trip.status())
                || "CONFIRMING".equals(trip.status())) {
            return true;
        }
        return trip.running() && Integer.valueOf(1).equals(team.getAllowMidwayJoin());
    }

    /**
     * 目标行程进行中时，不允许与其他进行中行程并行。
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
    private TeamMember addMember(Long teamId, Long userId, Long vehicleId, String role) {
        TeamMember existed = memberMapper.findByTeamAndUser(teamId, userId);
        if (existed != null) {
            LocalDateTime now = LocalDateTime.now();
            memberMapper.reactivate(teamId, userId, vehicleId, role, now);
            existed.setVehicleId(vehicleId);
            existed.setMemberRole(role);
            existed.setMemberStatus("ACTIVE");
            existed.setJoinedAt(now);
            existed.setExitedAt(null);
            existed.setUpdatedAt(now);
            return existed;
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
        return member;
    }

    /** 解析申请人是否选择“我会开车”；非法历史 JSON 按不驾车处理。 */
    private boolean wantsToDrive(String joinQuestionJson) {
        if (!StringUtils.hasText(joinQuestionJson)) {
            return false;
        }
        try {
            return objectMapper.readTree(joinQuestionJson).path("selfDrive").asBoolean(false);
        } catch (Exception ignored) {
            return false;
        }
    }

    /** P0 只要求行驶证认证；不再把驾驶证作为车主身份的前置条件。 */
    private VehicleResponse requireEligiblePrimaryVehicle(Long requestedVehicleId) {
        var approvedVehicles = vehicleService.getMyVehicles().vehicles().stream()
                .filter(vehicle -> "APPROVED".equals(vehicle.certificationStatus()))
                .toList();
        if (approvedVehicles.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                    "需要一辆已通过行驶证认证的车辆才能选择“我会开车”");
        }
        if (requestedVehicleId != null) {
            // 允许申请人选择任意一辆属于自己的已认证车辆，而不是强制默认车辆。
            return approvedVehicles.stream()
                    .filter(vehicle -> requestedVehicleId.equals(vehicle.vehicleId()))
                    .findFirst()
                    .orElseThrow(() -> new BusinessException(ResultCode.BAD_REQUEST,
                            "所选车辆不存在、未通过认证或不属于当前用户"));
        }
        return approvedVehicles.stream()
                .filter(vehicle -> Boolean.TRUE.equals(vehicle.isDefault()))
                .findFirst()
                .orElse(approvedVehicles.get(0));
    }

    private String resolveJoinRole(JoinTeamApplicationRequest request) {
        if ("DRIVER".equalsIgnoreCase(request.joinRole()) || wantsToDrive(request.joinQuestionJson())) {
            return "DRIVER";
        }
        return "PASSENGER";
    }

    private void validateReturnApplication(TeamMember existedMember, JoinTeamApplicationRequest request) {
        if (existedMember == null || (!"EXITED".equals(existedMember.getMemberStatus())
                && !"REMOVED".equals(existedMember.getMemberStatus()))) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "只有退出或被移除的成员可以申请归队");
        }
        if (request.currentLatitude() == null || request.currentLongitude() == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "申请归队时必须提交当前位置");
        }
    }

    private String maskPlate(String plateNumber) {
        if (!StringUtils.hasText(plateNumber)) {
            return null;
        }
        String normalized = plateNumber.replaceAll("\\s+", "").toUpperCase();
        if (normalized.length() <= 3) {
            return "***";
        }
        return normalized.substring(0, 2) + "***" + normalized.substring(normalized.length() - 1);
    }

    private String normalizeUpper(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase() : null;
    }

    private void validateThresholds(UpdateTeamSettingsRequest request) {
        Integer warningDistance = request.deviationWarningDistanceM();
        Integer severeDistance = request.severeDeviationDistanceM();
        if (warningDistance != null && severeDistance != null && severeDistance <= warningDistance) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "严重脱队距离必须大于一级提醒距离");
        }
        Integer warningMinutes = request.deviationWarningMinutes();
        Integer severeMinutes = request.severeDeviationMinutes();
        if (warningMinutes != null && severeMinutes != null && severeMinutes <= warningMinutes) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "严重脱队持续时间必须大于一级提醒持续时间");
        }
    }

    private Team requireOwnedTeam(Long teamId, Long ownerUserId) {
        Team team = requireTeam(teamId);
        if (!ownerUserId.equals(team.getOwnerUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只有队长可以执行此操作");
        }
        return team;
    }

    private void requireActiveMemberOrOwner(Team team, Long userId) {
        if (team.getOwnerUserId().equals(userId)) {
            return;
        }
        TeamMember member = memberMapper.findByTeamAndUser(team.getId(), userId);
        if (member == null || !"ACTIVE".equals(member.getMemberStatus())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只有车队成员可以查看该信息");
        }
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
        Long currentUserId = currentUserContext.requireUserId();
        TeamMember currentMembership = memberMapper.findByTeamAndUser(team.getId(), currentUserId);
        boolean activeMember = currentMembership != null
                && "ACTIVE".equals(currentMembership.getMemberStatus());
        int activeMemberCount = memberMapper.countActiveByTeamId(team.getId());
        return new TeamResponse(
                String.valueOf(team.getId()),
                String.valueOf(team.getTripId()),
                String.valueOf(team.getOwnerUserId()),
                team.getOwnerVehicleId() == null ? null : String.valueOf(team.getOwnerVehicleId()),
                team.getTeamName(),
                team.getTeamDesc(),
                team.getStartName(),
                team.getEndName(),
                format(team.getDepartureTime()),
                team.getMaxMemberCount(),
                activeMemberCount,
                team.getJoinMode(),
                team.getTeamStatus(),
                team.getPublicFlag() != null && team.getPublicFlag() == 1,
                team.getChatConversationId() == null ? null : String.valueOf(team.getChatConversationId()),
                team.getNotice(),
                format(team.getCreatedAt()),
                team.getRecruitmentStatus(),
                Integer.valueOf(1).equals(team.getAllowMidwayJoin()),
                team.getDeviationWarningDistanceM(),
                team.getDeviationWarningMinutes(),
                team.getSevereDeviationDistanceM(),
                team.getSevereDeviationMinutes(),
                team.getMissingLocationMinutes(),
                team.getJoinRadiusM(),
                team.getPrivacyLevel(),
                team.getOwnerUserId().equals(currentUserId),
                activeMember
        );
    }

    /**
     * 将成员实体转换为接口响应对象。
     */
    private TeamMemberResponse toMemberResponse(Team team, TeamMember member, Long requesterId) {
        boolean ownerView = team.getOwnerUserId().equals(requesterId);
        boolean self = member.getUserId().equals(requesterId);
        boolean privateView = "PRIVATE".equals(team.getPrivacyLevel()) && !ownerView && !self;
        boolean standardView = "STANDARD".equals(team.getPrivacyLevel()) && !ownerView && !self;
        String nickname = privateView ? "车队成员" : member.getNicknameSnapshot();
        String vehicle = privateView ? null : member.getVehicleSnapshot();
        String plate = privateView ? null : member.getPlateReference();
        if (standardView && StringUtils.hasText(plate)) {
            plate = maskPlate(plate);
        }
        return new TeamMemberResponse(
                String.valueOf(member.getId()),
                String.valueOf(member.getTeamId()),
                privateView ? null : String.valueOf(member.getUserId()),
                privateView || member.getVehicleId() == null ? null : String.valueOf(member.getVehicleId()),
                member.getMemberRole(),
                member.getMemberStatus(),
                nickname,
                vehicle,
                format(member.getJoinedAt()),
                privateView || member.getLinkedOwnerUserId() == null ? null : String.valueOf(member.getLinkedOwnerUserId()),
                privateView || member.getLinkedVehicleId() == null ? null : String.valueOf(member.getLinkedVehicleId()),
                plate,
                member.getOwnerConfirmStatus(),
                ownerView && !team.getOwnerUserId().equals(member.getUserId()),
                self
        );
    }

    /**
     * 将入队申请实体转换为接口响应对象。
     */
    private TeamApplicationResponse toApplicationResponse(TeamJoinApplication application) {
        var applicant = userService.getChatMemberProfile(application.getApplicantUserId());
        var relation = userService.getFollowStatus(application.getApplicantUserId());
        Team team = teamMapper.findById(application.getTeamId());
        boolean wantsToDrive = wantsToDrive(application.getJoinQuestionJson());
        PublicVehicleCardResponse vehicle = wantsToDrive && application.getApplicantVehicleId() != null
                ? vehicleService.getPublicCard(application.getApplicantVehicleId()) : null;
        String vehicleSummary = vehicle == null ? "" : List.of(
                        vehicle.brand() == null ? "" : vehicle.brand(),
                        vehicle.model() == null ? "" : vehicle.model(),
                        vehicle.plateNoMask() == null ? "" : vehicle.plateNoMask())
                .stream().filter(StringUtils::hasText).reduce((left, right) -> left + " · " + right).orElse("");
        return new TeamApplicationResponse(
                String.valueOf(application.getId()),
                String.valueOf(application.getTeamId()),
                application.getTripId() == null ? null : String.valueOf(application.getTripId()),
                String.valueOf(application.getApplicantUserId()),
                displayName(applicant.nickname()),
                applicant.avatarImageKey(),
                application.getApplicantVehicleId() == null ? null : String.valueOf(application.getApplicantVehicleId()),
                wantsToDrive,
                vehicleSummary,
                team == null ? "行程车队" : team.getTeamName(),
                application.getApplicationStatus(),
                application.getApplyMessage(),
                application.getReviewMessage(),
                format(application.getReviewedAt()),
                format(application.getCreatedAt()),
                relation.following(),
                relation.followedByTarget(),
                relation.mutual(),
                application.getApplicationType(),
                application.getJoinRole(),
                application.getLinkedOwnerUserId() == null ? null : String.valueOf(application.getLinkedOwnerUserId()),
                application.getLinkedVehicleId() == null ? null : String.valueOf(application.getLinkedVehicleId()),
                application.getPlateReference(),
                application.getOwnerConfirmStatus(),
                application.getCurrentLatitude() == null ? null : application.getCurrentLatitude().toPlainString(),
                application.getCurrentLongitude() == null ? null : application.getCurrentLongitude().toPlainString()
        );
    }

    private String displayName(String nickname) {
        return StringUtils.hasText(nickname) ? nickname.trim() : "同路行用户";
    }

    /**
     * 统一格式化时间字段。
     */
    private String format(LocalDateTime time) {
        return time == null ? null : FORMATTER.format(time);
    }
}
