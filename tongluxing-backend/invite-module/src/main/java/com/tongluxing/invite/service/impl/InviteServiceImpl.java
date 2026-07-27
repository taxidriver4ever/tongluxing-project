package com.tongluxing.invite.service.impl;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.util.StringUtils;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.tongluxing.common.event.UserRegisteredEvent;
import com.tongluxing.common.exception.BusinessException;
import com.tongluxing.common.result.ResultCode;
import com.tongluxing.common.utils.SnowflakeIdGenerator;
import com.tongluxing.invite.dto.InviteBindRequest;
import com.tongluxing.invite.dto.InviteQueryDTO;
import com.tongluxing.invite.dto.InviteRewardResult;
import com.tongluxing.invite.event.InviteRelationBoundEvent;
import com.tongluxing.invite.integration.InviteRewardPort;
import com.tongluxing.invite.mapper.InviteMapper;
import com.tongluxing.invite.model.InviteModels.InvitationVO;
import com.tongluxing.invite.model.InviteModels.InviteBindRewardVO;
import com.tongluxing.invite.model.InviteModels.InviteBindStatusVO;
import com.tongluxing.invite.model.InviteModels.InviteBindVO;
import com.tongluxing.invite.model.InviteModels.InviteCodeVO;
import com.tongluxing.invite.model.InviteModels.InvitePreviewVO;
import com.tongluxing.invite.model.InviteModels.InviteQrVO;
import com.tongluxing.invite.model.InviteModels.InviteQrValidationVO;
import com.tongluxing.invite.model.InviteModels.InviteRewardProgressVO;
import com.tongluxing.invite.model.InviteModels.InviterBriefVO;
import com.tongluxing.invite.model.InviteModels.PageResult;
import com.tongluxing.invite.service.InviteService;
import com.tongluxing.invite.support.InviteBindingRules;
import com.tongluxing.user.support.CurrentUserContext;

import lombok.RequiredArgsConstructor;

/** 邀请业务服务实现。 */
@Service
@RequiredArgsConstructor
public class InviteServiceImpl implements InviteService {

    private static final String STATUS_REGISTERED = "REGISTERED";
    private static final String SOURCE_AUTO_REGISTRATION = "SHARE_LINK";
    private static final String SOURCE_MANUAL_CODE = "MANUAL_CODE";
    private static final String RULE_FIRST_TEAM = "INVITEE_FIRST_TEAM_COMPLETED";
    private static final String RULE_REGISTER = "INVITE_REGISTER_SUCCESS";
    private static final int REGISTER_REWARD_POINTS = 50;
    private static final int FIRST_TEAM_REWARD_POINTS = 100;
    private static final int BIND_WINDOW_DAYS = 7;
    private static final Map<Integer, Integer> STAGE_REWARDS = Map.of(
            3, 200,
            10, 500,
            30, 2000,
            50, 5000
    );
    private static final int MAX_CODE_GENERATE_ATTEMPTS = 5;
    private static final long QR_PERIOD_SECONDS = 7L * 24 * 60 * 60;

    private final InviteMapper mapper;
    private final CurrentUserContext currentUser;
    private final ObjectProvider<InviteRewardPort> rewardPort;
    private final StringRedisTemplate redisTemplate;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${auth.jwt.secret}")
    private String qrSecret;

    @Override
    public InviteCodeVO currentCode() {
        return getCode(currentUser.requireUserId());
    }

    @Override
    public InviteQrVO currentQr() {
        long userId = currentUser.requireUserId();
        InviteCodeVO code = getCode(userId);
        long now = Instant.now().getEpochSecond();
        long issued = now - Math.floorMod(now, QR_PERIOD_SECONDS);
        long expires = issued + QR_PERIOD_SECONDS;
        String payload = userId + "|" + code.inviteCode() + "|" + issued + "|" + expires;
        String token = encode(payload.getBytes(StandardCharsets.UTF_8)) + "." + sign(payload);
        String content = "tongluxing://invite?code=" + code.inviteCode() + "&token=" + token;
        return new InviteQrVO(code.inviteCode(), token, content, qrPng(content),
                local(issued), local(expires), Math.max(0, expires - now));
    }

    @Override
    public InviteQrValidationVO validateQr(String token) {
        try {
            if (!StringUtils.hasText(token) || token.length() > 1024) {
                return invalid("INVALID");
            }
            String[] segments = token.split("\\.", -1);
            if (segments.length != 2) {
                return invalid("INVALID");
            }
            String payload = new String(Base64.getUrlDecoder().decode(segments[0]), StandardCharsets.UTF_8);
            if (!MessageDigest.isEqual(sign(payload).getBytes(StandardCharsets.US_ASCII),
                    segments[1].getBytes(StandardCharsets.US_ASCII))) {
                return invalid("INVALID");
            }
            String[] fields = payload.split("\\|", -1);
            if (fields.length != 4) {
                return invalid("INVALID");
            }
            long userId = Long.parseLong(fields[0]);
            String inviteCode = normalizeCode(fields[1]);
            long expires = Long.parseLong(fields[3]);
            if (Instant.now().getEpochSecond() >= expires) {
                return new InviteQrValidationVO(false, "EXPIRED", inviteCode, local(expires));
            }
            InviteQueryDTO owner = mapper.findCode(inviteCode);
            if (owner == null || !Long.valueOf(userId).equals(owner.getUserId())) {
                return new InviteQrValidationVO(false, "DISABLED", inviteCode, local(expires));
            }
            return new InviteQrValidationVO(true, "VALID", inviteCode, local(expires));
        } catch (RuntimeException exception) {
            return invalid("INVALID");
        }
    }

    @Override
    public InviteBindStatusVO currentBindStatus() {
        Long userId = currentUser.requireUserId();
        InviteQueryDTO relation = mapper.findRelationByInvitee(userId);
        InviteQueryDTO registration = requireRegistration(userId);
        LocalDateTime expireAt = registration.getRegisteredAt().plusDays(BIND_WINDOW_DAYS);
        if (relation != null) {
            return new InviteBindStatusVO(
                    true,
                    false,
                    false,
                    registration.getRegisteredAt(),
                    expireAt,
                    0L,
                    relation.getBoundAt(),
                    new InviterBriefVO(
                            fallbackNickname(relation.getInviterNickname()),
                            relation.getInviterAvatarUrl())
            );
        }
        LocalDateTime now = LocalDateTime.now();
        boolean eligible = now.isBefore(expireAt);
        long remaining = eligible ? Math.max(0, Duration.between(now, expireAt).getSeconds()) : 0;
        return new InviteBindStatusVO(
                false,
                eligible,
                !eligible,
                registration.getRegisteredAt(),
                expireAt,
                remaining,
                null,
                null
        );
    }

    @Override
    public InvitePreviewVO previewCurrent(String inviteCode) {
        Long invitee = currentUser.requireUserId();
        if (mapper.findRelationByInvitee(invitee) != null) {
            throw new BusinessException(409, "您已经完成邀请绑定了");
        }
        InviteQueryDTO registration = requireRegistration(invitee);
        ensureWithinBindWindow(registration.getRegisteredAt());
        checkAttemptLimit(invitee);
        String normalizedCode = normalizeAndValidateCode(inviteCode);
        InviteQueryDTO owner = requireActiveCode(normalizedCode);
        validateRelation(invitee, owner.getUserId());
        InviteQueryDTO profile = mapper.findInviterProfile(owner.getUserId());
        return new InvitePreviewVO(
                true,
                fallbackNickname(profile == null ? null : profile.getInviterNickname()),
                profile == null ? null : profile.getInviterAvatarUrl()
        );
    }

    @Override
    @Transactional
    public InviteBindVO bindCurrent(InviteBindRequest request) {
        Long invitee = currentUser.requireUserId();
        InviteQueryDTO existing = mapper.findRelationByInvitee(invitee);
        if (existing != null) {
            issueRegistrationReward(existing.getInviterUserId(), existing.getRelationId(), LocalDateTime.now());
            return relationResult(existing, false);
        }
        InviteQueryDTO registration = requireRegistration(invitee);
        checkAttemptLimit(invitee);
        String sourceType = normalizeSourceType(request.sourceType());
        String requestId = StringUtils.hasText(request.requestId())
                ? request.requestId().trim()
                : UUID.randomUUID().toString();
        return bind(invitee, request.inviteCode(), registration.getRegisteredAt(), sourceType, requestId, true);
    }

    @Override
    public InviteRewardProgressVO currentProgress() {
        return getProgress(currentUser.requireUserId());
    }

    @Override
    public PageResult<InvitationVO> currentRecords(String status, int page, int size) {
        return getRecords(currentUser.requireUserId(), status, page, size);
    }

    @Override
    public InviteCodeVO getCode(Long userId) {
        if (userId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "请先登录");
        }
        InviteQueryDTO row = mapper.findCodeByUser(userId);
        for (int i = 0; i < MAX_CODE_GENERATE_ATTEMPTS && row == null; i++) {
            try {
                mapper.insertCode(SnowflakeIdGenerator.nextId(), userId, code(userId, i), LocalDateTime.now());
            } catch (DuplicateKeyException ignored) {
                // 唯一键碰撞后换 salt 重试。
            }
            row = mapper.findCodeByUser(userId);
        }
        if (row == null) {
            throw new BusinessException("邀请码生成失败，请稍后重试");
        }
        return new InviteCodeVO(row.getInviteCode(), "inviteCode=" + row.getInviteCode(), row.getEnabledFlag());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleUserRegistered(UserRegisteredEvent event) {
        if (event == null || !event.hasSource("INVITE")) {
            return;
        }
        try {
            bind(event.userId(), event.normalizedSourceCode(), event.registerTime(),
                    SOURCE_AUTO_REGISTRATION, "REGISTER:" + event.userId(), true);
        } catch (RuntimeException ignored) {
            // 注册主链路已经完成，邀请绑定失败只留待用户在七天内手动重试。
        }
    }

    @Override
    public InviteRewardProgressVO getProgress(Long userId) {
        int count = mapper.countInvitees(userId);
        return new InviteRewardProgressVO(count, next(count), mapper.findGrantedRules(userId));
    }

    @Override
    public PageResult<InvitationVO> getRecords(Long userId, String status, int page, int size) {
        int normalizedPage = Math.max(1, page);
        int normalizedSize = Math.min(100, Math.max(1, size));
        List<InvitationVO> records = mapper.findRecords(userId, status,
                (normalizedPage - 1) * normalizedSize, normalizedSize).stream().map(this::record).toList();
        return new PageResult<>(records, mapper.countRecords(userId, status), normalizedPage, normalizedSize);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleInviteRelationBound(InviteRelationBoundEvent event) {
        if (event == null || event.relationId() == null || event.inviterUserId() == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        issueRegistrationReward(event.inviterUserId(), event.relationId(), now);
        issueStageRewards(event.inviterUserId(), event.relationId(), now);
    }

    @Override
    @Transactional
    public InviteRewardResult completeFirstTeam(Long userId, Long teamId, String bizId) {
        if (userId == null || !StringUtils.hasText(bizId)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "首次组队完成事件参数不完整");
        }
        InviteQueryDTO relation = mapper.findRelationForUpdate(userId);
        if (relation == null) {
            return new InviteRewardResult(null, null, bizId, "NO_RELATION");
        }
        long relationId = relation.getRelationId();
        long inviter = relation.getInviterUserId();
        String rewardBizNo = "INVITEE_FIRST_TEAM_REWARD:" + relationId;
        if (relation.getFirstTeamCompletedAt() != null) {
            return retryFirstTeamReward(relationId, inviter, rewardBizNo, LocalDateTime.now());
        }
        LocalDateTime now = LocalDateTime.now();
        if (mapper.markValid(relationId, now) == 0) {
            return retryFirstTeamReward(relationId, inviter, rewardBizNo, now);
        }
        InviteRewardPort port = rewardPort.getIfAvailable();
        int configuredPoints = rewardPoints(RULE_FIRST_TEAM, FIRST_TEAM_REWARD_POINTS);
        String rewardSnapshot = "{\"growthPoints\":" + configuredPoints + "}";
        try {
            mapper.insertReward(SnowflakeIdGenerator.nextId(), relationId, inviter, RULE_FIRST_TEAM,
                    rewardBizNo, rewardSnapshot, now);
        } catch (DuplicateKeyException e) {
            return retryFirstTeamReward(relationId, inviter, rewardBizNo, now);
        }
        try {
            if (port == null) {
                throw new IllegalStateException("reward port unavailable");
            }
            port.grantInviteReward(inviter, rewardBizNo, RULE_FIRST_TEAM, configuredPoints);
            mapper.updateReward(rewardBizNo, "ISSUED", null, now, now);
            return new InviteRewardResult(relationId, inviter, rewardBizNo, "ISSUED");
        } catch (RuntimeException e) {
            mapper.updateReward(rewardBizNo, "FAILED", safeMessage(e), null, now);
            return new InviteRewardResult(relationId, inviter, rewardBizNo, "FAILED");
        }
    }

    private InviteBindVO bind(Long invitee, String inviteCode, LocalDateTime registeredAt,
                              String sourceType, String requestId, boolean enforceWindow) {
        InviteQueryDTO existing = mapper.findRelationByInvitee(invitee);
        if (existing != null) {
            issueRegistrationReward(existing.getInviterUserId(), existing.getRelationId(), LocalDateTime.now());
            return relationResult(existing, false);
        }
        if (enforceWindow) {
            ensureWithinBindWindow(registeredAt);
        }
        String normalizedCode = normalizeAndValidateCode(inviteCode);
        InviteQueryDTO owner = requireActiveCode(normalizedCode);
        long inviter = owner.getUserId();
        validateRelation(invitee, inviter);
        LocalDateTime now = LocalDateTime.now();
        try {
            mapper.insertRelation(SnowflakeIdGenerator.nextId(), inviter, invitee, normalizedCode,
                    sourceType, requestId, registeredAt, now);
        } catch (DuplicateKeyException e) {
            InviteQueryDTO duplicate = mapper.findRelationByInvitee(invitee);
            if (duplicate == null && StringUtils.hasText(requestId)) {
                InviteQueryDTO requestDuplicate = mapper.findRelationByRequestId(requestId);
                if (requestDuplicate != null && invitee.equals(requestDuplicate.getInviteeUserId())) {
                    duplicate = requestDuplicate;
                }
            }
            if (duplicate != null) {
                issueRegistrationReward(duplicate.getInviterUserId(), duplicate.getRelationId(), LocalDateTime.now());
                return relationResult(duplicate, false);
            }
            throw new BusinessException(409, "您已经完成邀请绑定了");
        }
        InviteQueryDTO relation = mapper.findRelationByInvitee(invitee);
        if (relation == null) {
            throw new BusinessException(ResultCode.INTERNAL_SERVER_ERROR, "邀请关系创建失败");
        }
        eventPublisher.publishEvent(new InviteRelationBoundEvent(
                relation.getRelationId(), inviter, invitee, sourceType, now));
        return relationResult(relation, true);
    }

    private InviteQueryDTO requireRegistration(Long userId) {
        InviteQueryDTO registration = mapper.findRegistration(userId);
        if (registration == null || registration.getRegisteredAt() == null) {
            throw new BusinessException(ResultCode.INTERNAL_SERVER_ERROR, "无法读取账号注册时间");
        }
        return registration;
    }

    private void ensureWithinBindWindow(LocalDateTime registeredAt) {
        if (!InviteBindingRules.isWithinWindow(
                registeredAt, LocalDateTime.now(), BIND_WINDOW_DAYS)) {
            throw new BusinessException(409, "邀请码仅限注册后 7 天内绑定");
        }
    }

    private InviteQueryDTO requireActiveCode(String normalizedCode) {
        InviteQueryDTO any = mapper.findAnyCode(normalizedCode);
        if (any == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "邀请码不存在，请检查后重试");
        }
        if (!Boolean.TRUE.equals(any.getEnabledFlag())) {
            throw new BusinessException(409, "该邀请码暂不可用");
        }
        return any;
    }

    private void validateRelation(Long invitee, Long inviter) {
        if (inviter.equals(invitee)) {
            throw new BusinessException(409, "不能绑定自己的邀请码");
        }
        if (mapper.createsCycle(inviter, invitee) > 0) {
            throw new BusinessException(409, "无法绑定该邀请关系");
        }
    }

    private void checkAttemptLimit(Long userId) {
        long minute = Instant.now().getEpochSecond() / 60;
        String key = "invite:bind-attempt:" + userId + ":" + minute;
        Long attempts = redisTemplate.opsForValue().increment(key);
        if (attempts != null && attempts == 1) {
            redisTemplate.expire(key, Duration.ofMinutes(2));
        }
        if (attempts != null && attempts > 10) {
            throw new BusinessException(429, "邀请码尝试过于频繁，请稍后重试");
        }
    }

    private String normalizeAndValidateCode(String inviteCode) {
        String code = normalizeCode(inviteCode);
        if (code.length() < 6 || code.length() > 16 || !code.matches("[A-Z0-9]+")) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "邀请码格式不正确");
        }
        return code;
    }

    private String normalizeCode(String inviteCode) {
        return InviteBindingRules.normalizeCode(inviteCode);
    }

    private String normalizeSourceType(String sourceType) {
        if (!StringUtils.hasText(sourceType)) {
            return SOURCE_MANUAL_CODE;
        }
        return sourceType.trim().toUpperCase(Locale.ROOT);
    }

    private InvitationVO record(InviteQueryDTO row) {
        return new InvitationVO(row.getRelationId(), row.getInviteeUserId(), row.getInviteCode(),
                row.getStatus(), row.getBoundAt(), row.getFirstTeamCompletedAt());
    }

    private InviteBindVO relationResult(InviteQueryDTO row, boolean newlyBound) {
        return new InviteBindVO(
                true,
                row.getRelationId(),
                row.getInviterUserId(),
                row.getInviteeUserId(),
                row.getStatus(),
                row.getBoundAt(),
                new InviteBindRewardVO(newlyBound ? rewardPoints(RULE_REGISTER, REGISTER_REWARD_POINTS) : 0, 0, false)
        );
    }

    private void issueStageRewards(Long inviter, Long relationId, LocalDateTime now) {
        int inviteCount = mapper.countInvitees(inviter);
        for (Integer stage : STAGE_REWARDS.keySet().stream().sorted().toList()) {
            if (inviteCount >= stage) {
                issueStageReward(inviter, relationId, stage, STAGE_REWARDS.get(stage), now);
            }
        }
    }

    /** 成功绑定后邀请人获得 50 同路值；失败只记录，不能回滚邀请关系。 */
    private void issueRegistrationReward(Long inviter, Long relationId, LocalDateTime now) {
        if (relationId == null) {
            return;
        }
        String rewardBizNo = "INVITE_REGISTER_REWARD:" + relationId;
        InviteRewardPort port = rewardPort.getIfAvailable();
        int configuredPoints = rewardPoints(RULE_REGISTER, REGISTER_REWARD_POINTS);
        String snapshot = "{\"rewardType\":\"GROWTH_VALUE\",\"rewardValue\":" + configuredPoints + "}";
        try {
            mapper.insertReward(SnowflakeIdGenerator.nextId(), relationId, inviter, RULE_REGISTER,
                    rewardBizNo, snapshot, now);
        } catch (DuplicateKeyException e) {
            retryExistingReward(inviter, rewardBizNo, RULE_REGISTER, now);
            return;
        }
        try {
            if (port == null) {
                throw new IllegalStateException("reward port unavailable");
            }
            port.grantInviteReward(inviter, rewardBizNo, RULE_REGISTER, configuredPoints);
            mapper.updateReward(rewardBizNo, "ISSUED", null, now, now);
        } catch (RuntimeException e) {
            mapper.updateReward(rewardBizNo, "FAILED", safeMessage(e), null, now);
        }
    }

    private InviteRewardResult retryFirstTeamReward(
            Long relationId, Long inviter, String rewardBizNo, LocalDateTime now) {
        String status = mapper.findRewardStatus(rewardBizNo);
        if (!"FAILED".equals(status)) {
            return new InviteRewardResult(relationId, inviter, rewardBizNo, "DUPLICATE");
        }
        String retried = retryExistingReward(inviter, rewardBizNo, RULE_FIRST_TEAM, now);
        return new InviteRewardResult(relationId, inviter, rewardBizNo, retried);
    }

    private String retryExistingReward(
            Long inviter, String rewardBizNo, String ruleCode, LocalDateTime now) {
        if (!"FAILED".equals(mapper.findRewardStatus(rewardBizNo))) {
            return "DUPLICATE";
        }
        InviteRewardPort port = rewardPort.getIfAvailable();
        try {
            if (port == null) {
                throw new IllegalStateException("reward port unavailable");
            }
            int configuredPoints = rewardPoints(ruleCode, defaultRewardPoints(ruleCode));
            port.grantInviteReward(inviter, rewardBizNo, ruleCode, configuredPoints);
            mapper.updateReward(rewardBizNo, "ISSUED", null, now, now);
            return "ISSUED";
        } catch (RuntimeException exception) {
            mapper.updateReward(rewardBizNo, "FAILED", safeMessage(exception), null, now);
            return "FAILED";
        }
    }

    private void issueStageReward(Long inviter, Long relationId, int stage, int points, LocalDateTime now) {
        String ruleCode = "INVITE_STAGE_" + stage;
        String rewardBizNo = "INVITE_MILESTONE:" + inviter + ":" + stage;
        InviteRewardPort port = rewardPort.getIfAvailable();
        int configuredPoints = rewardPoints(ruleCode, points);
        String snapshot = "{\"rewardType\":\"GROWTH_VALUE\",\"rewardStage\":" + stage
                + ",\"rewardValue\":" + configuredPoints + "}";
        try {
            mapper.insertReward(SnowflakeIdGenerator.nextId(), relationId, inviter,
                    ruleCode, rewardBizNo, snapshot, now);
        } catch (DuplicateKeyException e) {
            retryExistingReward(inviter, rewardBizNo, ruleCode, now);
            return;
        }
        try {
            if (port == null) {
                throw new IllegalStateException("reward port unavailable");
            }
            port.grantInviteReward(inviter, rewardBizNo, ruleCode, configuredPoints);
            mapper.updateReward(rewardBizNo, "ISSUED", null, now, now);
        } catch (RuntimeException e) {
            mapper.updateReward(rewardBizNo, "FAILED", safeMessage(e), null, now);
        }
    }

    private int rewardPoints(String ruleCode, int fallback) {
        Integer configured = mapper.findRewardRuleValue(ruleCode);
        return configured != null && configured > 0 ? configured : fallback;
    }

    private int defaultRewardPoints(String ruleCode) {
        if (RULE_REGISTER.equals(ruleCode)) return REGISTER_REWARD_POINTS;
        if (RULE_FIRST_TEAM.equals(ruleCode)) return FIRST_TEAM_REWARD_POINTS;
        if (ruleCode != null && ruleCode.startsWith("INVITE_STAGE_")) {
            try {
                return STAGE_REWARDS.getOrDefault(
                        Integer.parseInt(ruleCode.substring("INVITE_STAGE_".length())), 0);
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    private String code(long id, int salt) {
        String value = Long.toUnsignedString(id + salt * 97L, 36).toUpperCase(Locale.ROOT);
        return value.length() >= 6 ? value.substring(0, Math.min(10, value.length()))
                : "0".repeat(6 - value.length()) + value;
    }

    private int next(int count) {
        for (int level : new int[]{1, 3, 10, 30, 50}) {
            if (count < level) {
                return level - count;
            }
        }
        return 0;
    }

    private InviteQrValidationVO invalid(String status) {
        return new InviteQrValidationVO(false, status, null, null);
    }

    private String fallbackNickname(String nickname) {
        return StringUtils.hasText(nickname) ? nickname : "同路行用户";
    }

    private String safeMessage(RuntimeException e) {
        String message = e.getMessage();
        return message == null ? e.getClass().getSimpleName() : message.substring(0, Math.min(255, message.length()));
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(qrSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return encode(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("邀请二维码签名失败", exception);
        }
    }

    private String encode(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private String qrPng(String content) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            MatrixToImageWriter.writeToStream(
                    new MultiFormatWriter().encode(content, BarcodeFormat.QR_CODE, 720, 720),
                    "PNG", output);
            return Base64.getEncoder().encodeToString(output.toByteArray());
        } catch (Exception exception) {
            throw new IllegalStateException("邀请二维码生成失败", exception);
        }
    }

    private LocalDateTime local(long epochSecond) {
        return LocalDateTime.ofInstant(Instant.ofEpochSecond(epochSecond), ZoneId.systemDefault());
    }
}
