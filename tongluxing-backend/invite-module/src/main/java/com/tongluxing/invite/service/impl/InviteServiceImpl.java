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

/**
 * 邀请业务服务实现。
 *
 * <p>负责邀请码生成、二维码签名校验、新用户七日内绑定、
 * 邀请关系幂等落库、首次组队/阶段奖励发放及失败补偿。</p>
 *
 * <p>关系建立与奖励发放故意解耦：邀请关系事务先提交，
 * 奖励再通过 AFTER_COMMIT 事件于新事务中执行，奖励系统故障不会回滚关系。</p>
 */
@Service
@RequiredArgsConstructor
public class InviteServiceImpl implements InviteService {

    /** 关系刚建立、尚未完成首次有效组队的状态。 */
    private static final String STATUS_REGISTERED = "REGISTERED";
    /** 注册时通过分享链接自动绑定的来源标识。 */
    private static final String SOURCE_AUTO_REGISTRATION = "SHARE_LINK";
    /** 用户注册后手动输入邀请码的来源标识。 */
    private static final String SOURCE_MANUAL_CODE = "MANUAL_CODE";
    /** 被邀请人首次组队完成奖励规则编码。 */
    private static final String RULE_FIRST_TEAM = "INVITEE_FIRST_TEAM_COMPLETED";
    /** 邀请关系成功建立奖励规则编码。 */
    private static final String RULE_REGISTER = "INVITE_REGISTER_SUCCESS";
    /** 注册绑定奖励默认同路值；数据库配置可覆盖。 */
    private static final int REGISTER_REWARD_POINTS = 50;
    /** 首次有效组队奖励默认同路值。 */
    private static final int FIRST_TEAM_REWARD_POINTS = 100;
    /** 新用户允许绑定邀请人的注册后窗口天数。 */
    private static final int BIND_WINDOW_DAYS = 7;
    /** 邀请人累计有效邀请人数对应的里程碑默认奖励。 */
    private static final Map<Integer, Integer> STAGE_REWARDS = Map.of(
            3, 200,
            10, 500,
            30, 2000,
            50, 5000
    );
    /** 邀请码发生唯一键碰撞时的最大换盐重试次数。 */
    private static final int MAX_CODE_GENERATE_ATTEMPTS = 5;
    /** 邀请二维码签名有效周期，当前为七天。 */
    private static final long QR_PERIOD_SECONDS = 7L * 24 * 60 * 60;

    /** 邀请码、关系、奖励和查询持久化 Mapper。 */
    private final InviteMapper mapper;
    /** 当前登录用户上下文。 */
    private final CurrentUserContext currentUser;
    /** 可选奖励端口；未注入时记录失败以便后续重试。 */
    private final ObjectProvider<InviteRewardPort> rewardPort;
    /** Redis 用于邀请码预览/绑定尝试限流。 */
    private final StringRedisTemplate redisTemplate;
    /** Spring 事件发布器，用于在关系提交后触发奖励。 */
    private final ApplicationEventPublisher eventPublisher;

    /** 复用 JWT 环境密钥对邀请二维码 payload 进行 HMAC 签名。 */
    @Value("${auth.jwt.secret}")
    private String qrSecret;

    @Override
    public InviteCodeVO currentCode() {
        // 当前用户接口不接收 userId，避免客户端越权获取他人邀请码。
        return getCode(currentUser.requireUserId());
    }

    /** 生成当前用户本周期的带签名邀请二维码。 */
    @Override
    public InviteQrVO currentQr() {
        long userId = currentUser.requireUserId();

        // 二维码始终携带数据库中当前有效邀请码。
        InviteCodeVO code = getCode(userId);
        long now = Instant.now().getEpochSecond();

        // 将当前时间对齐到七天周期起点，同一周期内反复查询得到相同 token。
        long issued = now - Math.floorMod(now, QR_PERIOD_SECONDS);
        long expires = issued + QR_PERIOD_SECONDS;
        String payload = userId + "|" + code.inviteCode() + "|" + issued + "|" + expires;

        // token 格式为 Base64URL(payload).HMAC，二维码内容使用 App deep link。
        String token = encode(payload.getBytes(StandardCharsets.UTF_8)) + "." + sign(payload);
        String content = "tongluxing://invite?code=" + code.inviteCode() + "&token=" + token;
        return new InviteQrVO(code.inviteCode(), token, content, qrPng(content),
                local(issued), local(expires), Math.max(0, expires - now));
    }

    @Override
    public InviteQrValidationVO validateQr(String token) {
        try {
            // 先限制空值与最大长度，避免对异常大输入执行解码和签名计算。
            if (!StringUtils.hasText(token) || token.length() > 1024) {
                return invalid("INVALID");
            }
            String[] segments = token.split("\\.", -1);
            if (segments.length != 2) {
                return invalid("INVALID");
            }
            String payload = new String(Base64.getUrlDecoder().decode(segments[0]), StandardCharsets.UTF_8);

            // MessageDigest.isEqual 使用常量时间比较，降低签名时序攻击风险。
            if (!MessageDigest.isEqual(sign(payload).getBytes(StandardCharsets.US_ASCII),
                    segments[1].getBytes(StandardCharsets.US_ASCII))) {
                return invalid("INVALID");
            }
            String[] fields = payload.split("\\|", -1);
            if (fields.length != 4) {
                return invalid("INVALID");
            }

            // payload 字段顺序：邀请人 ID、邀请码、签发时间、过期时间。
            long userId = Long.parseLong(fields[0]);
            String inviteCode = normalizeCode(fields[1]);
            long expires = Long.parseLong(fields[3]);
            if (Instant.now().getEpochSecond() >= expires) {
                return new InviteQrValidationVO(false, "EXPIRED", inviteCode, local(expires));
            }

            // 签名正确仍需回查数据库，确认邀请码未更换/停用且仍属于 token 中的用户。
            InviteQueryDTO owner = mapper.findCode(inviteCode);
            if (owner == null || !Long.valueOf(userId).equals(owner.getUserId())) {
                return new InviteQrValidationVO(false, "DISABLED", inviteCode, local(expires));
            }
            return new InviteQrValidationVO(true, "VALID", inviteCode, local(expires));
        } catch (RuntimeException exception) {
            // Base64 解码、数字转换等任何格式错误统一映射为 INVALID，不暴露内部异常。
            return invalid("INVALID");
        }
    }

    /** 查询当前用户是否已绑定，以及七天绑定窗口剩余时间。 */
    @Override
    public InviteBindStatusVO currentBindStatus() {
        Long userId = currentUser.requireUserId();

        // 关系与注册时间分别查询：前者决定是否绑定，后者决定是否过期。
        InviteQueryDTO relation = mapper.findRelationByInvitee(userId);
        InviteQueryDTO registration = requireRegistration(userId);
        LocalDateTime expireAt = registration.getRegisteredAt().plusDays(BIND_WINDOW_DAYS);
        if (relation != null) {
            // 已绑定是终态，即使七天窗口已过也仍返回邀请人摘要。
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

        // 只在仍可绑定时返回正数剩余秒，过期后固定为 0。
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

        // 预览也必须校验已绑定、绑定窗口和尝试频率，不能被用作查询他人资料的接口。
        if (mapper.findRelationByInvitee(invitee) != null) {
            throw new BusinessException(409, "您已经完成邀请绑定了");
        }
        InviteQueryDTO registration = requireRegistration(invitee);
        ensureWithinBindWindow(registration.getRegisteredAt());
        checkAttemptLimit(invitee);
        String normalizedCode = normalizeAndValidateCode(inviteCode);

        // 确认邀请码存在且启用，再校验不能邀请自己或形成关系环。
        InviteQueryDTO owner = requireActiveCode(normalizedCode);
        validateRelation(invitee, owner.getUserId());
        // 预览只返回邀请人昵称和头像，不暴露内部 userId。
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

        // 绑定关系对被邀请人是唯一的。重复请求直接返回已有关系，并尝试补发失败奖励。
        InviteQueryDTO existing = mapper.findRelationByInvitee(invitee);
        if (existing != null) {
            issueRegistrationReward(existing.getInviterUserId(), existing.getRelationId(), LocalDateTime.now());
            return relationResult(existing, false);
        }
        // 注册时间用于校验七天窗口；限流在查询邀请码前执行。
        InviteQueryDTO registration = requireRegistration(invitee);
        checkAttemptLimit(invitee);
        String sourceType = normalizeSourceType(request.sourceType());

        // 客户端传 requestId 时可跨重试幂等；旧客户端未传时由服务端生成。
        String requestId = StringUtils.hasText(request.requestId())
                ? request.requestId().trim()
                : UUID.randomUUID().toString();
        return bind(invitee, request.inviteCode(), registration.getRegisteredAt(), sourceType, requestId, true);
    }

    @Override
    public InviteRewardProgressVO currentProgress() {
        // 当前用户是邀请人，查询其累计邀请进度。
        return getProgress(currentUser.requireUserId());
    }

    @Override
    public PageResult<InvitationVO> currentRecords(String status, int page, int size) {
        // 强制使用当前用户作为 inviter，避免查看他人邀请明细。
        return getRecords(currentUser.requireUserId(), status, page, size);
    }

    /** 查询或为指定用户幂等创建唯一邀请码。 */
    @Override
    public InviteCodeVO getCode(Long userId) {
        if (userId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "请先登录");
        }
        // 先按用户查询，已有邀请码时绝不重新生成。
        InviteQueryDTO row = mapper.findCodeByUser(userId);
        for (int i = 0; i < MAX_CODE_GENERATE_ATTEMPTS && row == null; i++) {
            try {
                // 邀请码唯一键保护全平台唯一性，salt 用于碰撞后换一个候选值。
                mapper.insertCode(SnowflakeIdGenerator.nextId(), userId, code(userId, i), LocalDateTime.now());
            } catch (DuplicateKeyException ignored) {
                // 唯一键碰撞后换 salt 重试。
            }
            // 不依赖 insert 返回值，每轮回查也能兼容并发请求已经为用户创建的记录。
            row = mapper.findCodeByUser(userId);
        }
        if (row == null) {
            throw new BusinessException("邀请码生成失败，请稍后重试");
        }
        // shareQuery 可直接拼接到分享链接，enabled 供前端判断是否允许展示。
        return new InviteCodeVO(row.getInviteCode(), "inviteCode=" + row.getInviteCode(), row.getEnabledFlag());
    }

    /**
     * 注册主事务提交后根据注册来源尝试自动绑定。
     * REQUIRES_NEW 确保邀请库操作有独立事务边界。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleUserRegistered(UserRegisteredEvent event) {
        if (event == null || !event.hasSource("INVITE")) {
            // 非邀请渠道注册不建立任何邀请关系。
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
        // count 表示已有邀请关系数，grantedRules 用于前端标记已发放里程碑。
        int count = mapper.countInvitees(userId);
        return new InviteRewardProgressVO(count, next(count), mapper.findGrantedRules(userId));
    }

    @Override
    public PageResult<InvitationVO> getRecords(Long userId, String status, int page, int size) {
        // 页码至少为 1，每页最多 100 条，防止误传大 size 形成重查询。
        int normalizedPage = Math.max(1, page);
        int normalizedSize = Math.min(100, Math.max(1, size));
        // 列表与 count 使用相同的 userId/status 条件，保证分页总数一致。
        List<InvitationVO> records = mapper.findRecords(userId, status,
                (normalizedPage - 1) * normalizedSize, normalizedSize).stream().map(this::record).toList();
        return new PageResult<>(records, mapper.countRecords(userId, status), normalizedPage, normalizedSize);
    }

    /** 邀请关系提交后，在独立事务中发放注册奖励和可达里程碑。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleInviteRelationBound(InviteRelationBoundEvent event) {
        if (event == null || event.relationId() == null || event.inviterUserId() == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        // 两类奖励均有独立 bizNo 唯一键，重复事件不会重复发放。
        issueRegistrationReward(event.inviterUserId(), event.relationId(), now);
        issueStageRewards(event.inviterUserId(), event.relationId(), now);
    }

    @Override
    @Transactional
    public InviteRewardResult completeFirstTeam(Long userId, Long teamId, String bizId) {
        // teamId 由上游传入用于业务语义；幂等判定使用稳定 bizId 和关系派生 bizNo。
        if (userId == null || !StringUtils.hasText(bizId)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "首次组队完成事件参数不完整");
        }
        // for update 锁定被邀请人关系，防止两个组队完成事件并发标记首次。
        InviteQueryDTO relation = mapper.findRelationForUpdate(userId);
        if (relation == null) {
            // 用户没有邀请关系时，组队业务仍正常完成，只是无奖励。
            return new InviteRewardResult(null, null, bizId, "NO_RELATION");
        }
        long relationId = relation.getRelationId();
        long inviter = relation.getInviterUserId();
        String rewardBizNo = "INVITEE_FIRST_TEAM_REWARD:" + relationId;
        if (relation.getFirstTeamCompletedAt() != null) {
            // 首次组队已标记时不再改关系，仅补偿上次 FAILED 的奖励。
            return retryFirstTeamReward(relationId, inviter, rewardBizNo, LocalDateTime.now());
        }
        LocalDateTime now = LocalDateTime.now();
        if (mapper.markValid(relationId, now) == 0) {
            // 条件更新失败说明已被其他事务标记，转入幂等补偿逻辑。
            return retryFirstTeamReward(relationId, inviter, rewardBizNo, now);
        }
        // 奖励值优先从数据库规则读取，未配置时才使用代码默认值。
        InviteRewardPort port = rewardPort.getIfAvailable();
        int configuredPoints = rewardPoints(RULE_FIRST_TEAM, FIRST_TEAM_REWARD_POINTS);
        String rewardSnapshot = "{\"growthPoints\":" + configuredPoints + "}";
        try {
            // 先插入 PENDING 奖励台账，唯一 bizNo 用于防止重复发放。
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
            // 外部奖励成功后才把本地台账标记为 ISSUED。
            mapper.updateReward(rewardBizNo, "ISSUED", null, now, now);
            return new InviteRewardResult(relationId, inviter, rewardBizNo, "ISSUED");
        } catch (RuntimeException e) {
            // 失败信息截断保存，后续相同事件可根据 FAILED 状态重试。
            mapper.updateReward(rewardBizNo, "FAILED", safeMessage(e), null, now);
            return new InviteRewardResult(relationId, inviter, rewardBizNo, "FAILED");
        }
    }

    /**
     * 绑定邀请关系的核心方法，同时供注册自动绑定和用户手动绑定复用。
     */
    private InviteBindVO bind(Long invitee, String inviteCode, LocalDateTime registeredAt,
                              String sourceType, String requestId, boolean enforceWindow) {
        // 第一层幂等：被邀请人已经有关系时直接返回，并补偿注册奖励。
        InviteQueryDTO existing = mapper.findRelationByInvitee(invitee);
        if (existing != null) {
            issueRegistrationReward(existing.getInviterUserId(), existing.getRelationId(), LocalDateTime.now());
            return relationResult(existing, false);
        }
        if (enforceWindow) {
            // 手动绑定及注册事件均可按参数决定是否强制七天窗口。
            ensureWithinBindWindow(registeredAt);
        }
        // 邀请码先规范格式，再检查存在/启用状态和关系合法性。
        String normalizedCode = normalizeAndValidateCode(inviteCode);
        InviteQueryDTO owner = requireActiveCode(normalizedCode);
        long inviter = owner.getUserId();
        validateRelation(invitee, inviter);
        LocalDateTime now = LocalDateTime.now();
        try {
            // 数据库唯一键同时保护 invitee 唯一绑定和 requestId 请求幂等。
            mapper.insertRelation(SnowflakeIdGenerator.nextId(), inviter, invitee, normalizedCode,
                    sourceType, requestId, registeredAt, now);
        } catch (DuplicateKeyException e) {
            // 并发重复时优先按 invitee 回查；再按 requestId 回查同一用户请求。
            InviteQueryDTO duplicate = mapper.findRelationByInvitee(invitee);
            if (duplicate == null && StringUtils.hasText(requestId)) {
                InviteQueryDTO requestDuplicate = mapper.findRelationByRequestId(requestId);
                if (requestDuplicate != null && invitee.equals(requestDuplicate.getInviteeUserId())) {
                    duplicate = requestDuplicate;
                }
            }
            if (duplicate != null) {
                // 确认是同一关系后按成功幂等返回，不再抛出冲突。
                issueRegistrationReward(duplicate.getInviterUserId(), duplicate.getRelationId(), LocalDateTime.now());
                return relationResult(duplicate, false);
            }
            throw new BusinessException(409, "您已经完成邀请绑定了");
        }
        // 插入后回查完整关系，用于构建响应和事件。
        InviteQueryDTO relation = mapper.findRelationByInvitee(invitee);
        if (relation == null) {
            throw new BusinessException(ResultCode.INTERNAL_SERVER_ERROR, "邀请关系创建失败");
        }
        // 事件监听器会等待当前关系事务提交后再发奖，不污染主事务。
        eventPublisher.publishEvent(new InviteRelationBoundEvent(
                relation.getRelationId(), inviter, invitee, sourceType, now));
        return relationResult(relation, true);
    }

    /** 读取账号注册时间；缺失时视为数据一致性异常。 */
    private InviteQueryDTO requireRegistration(Long userId) {
        InviteQueryDTO registration = mapper.findRegistration(userId);
        if (registration == null || registration.getRegisteredAt() == null) {
            throw new BusinessException(ResultCode.INTERNAL_SERVER_ERROR, "无法读取账号注册时间");
        }
        return registration;
    }

    /** 校验当前时间仍处于注册后七天的邀请绑定窗口。 */
    private void ensureWithinBindWindow(LocalDateTime registeredAt) {
        if (!InviteBindingRules.isWithinWindow(
                registeredAt, LocalDateTime.now(), BIND_WINDOW_DAYS)) {
            throw new BusinessException(409, "邀请码仅限注册后 7 天内绑定");
        }
    }

    /** 查询邀请码并区分“不存在”和“已停用”两种业务错误。 */
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

    /** 防止自邀请以及 A→B、B→A 等形成邀请环的关系。 */
    private void validateRelation(Long invitee, Long inviter) {
        if (inviter.equals(invitee)) {
            throw new BusinessException(409, "不能绑定自己的邀请码");
        }
        if (mapper.createsCycle(inviter, invitee) > 0) {
            throw new BusinessException(409, "无法绑定该邀请关系");
        }
    }

    /** 基于 Redis 分钟桶限制邀请码预览和绑定尝试频率。 */
    private void checkAttemptLimit(Long userId) {
        // key 中包含当前分钟序号，不同时间桶之间不会累计。
        long minute = Instant.now().getEpochSecond() / 60;
        String key = "invite:bind-attempt:" + userId + ":" + minute;
        Long attempts = redisTemplate.opsForValue().increment(key);
        if (attempts != null && attempts == 1) {
            // 首次计数时设置两分钟 TTL，覆盖当前分钟并容纳边界时间。
            redisTemplate.expire(key, Duration.ofMinutes(2));
        }
        if (attempts != null && attempts > 10) {
            throw new BusinessException(429, "邀请码尝试过于频繁，请稍后重试");
        }
    }

    /** 邀请码统一大写并校验 6~16 位数字/英文组合。 */
    private String normalizeAndValidateCode(String inviteCode) {
        String code = normalizeCode(inviteCode);
        if (code.length() < 6 || code.length() > 16 || !code.matches("[A-Z0-9]+")) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "邀请码格式不正确");
        }
        return code;
    }

    /** 委托无状态规则类执行邀请码基础规范化。 */
    private String normalizeCode(String inviteCode) {
        return InviteBindingRules.normalizeCode(inviteCode);
    }

    /** 统一来源类型；旧客户端未传时默认按手动输入。 */
    private String normalizeSourceType(String sourceType) {
        if (!StringUtils.hasText(sourceType)) {
            return SOURCE_MANUAL_CODE;
        }
        return sourceType.trim().toUpperCase(Locale.ROOT);
    }

    /** 将 Mapper 聚合查询行裁剪为邀请记录列表项。 */
    private InvitationVO record(InviteQueryDTO row) {
        return new InvitationVO(row.getRelationId(), row.getInviteeUserId(), row.getInviteCode(),
                row.getStatus(), row.getBoundAt(), row.getFirstTeamCompletedAt());
    }

    /**
     * 构建绑定响应。新绑定展示预期注册奖励，幂等重复请求不重复展示奖励。
     */
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

    /** 根据邀请人当前邀请数，补发所有已达到但可能未成功的里程碑奖励。 */
    private void issueStageRewards(Long inviter, Long relationId, LocalDateTime now) {
        int inviteCount = mapper.countInvitees(inviter);
        // 阶段按阈值升序处理，方便审计和测试输出保持稳定。
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
        // 关系 ID 全局唯一，因此可作为注册奖励业务号的稳定组成部分。
        String rewardBizNo = "INVITE_REGISTER_REWARD:" + relationId;
        InviteRewardPort port = rewardPort.getIfAvailable();
        int configuredPoints = rewardPoints(RULE_REGISTER, REGISTER_REWARD_POINTS);
        String snapshot = "{\"rewardType\":\"GROWTH_VALUE\",\"rewardValue\":" + configuredPoints + "}";
        try {
            // 先写本地奖励台账；唯一键冲突时只尝试重试 FAILED 记录。
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
            // 奖励失败只更新台账，不抛出以避免影响已建立的邀请关系。
            mapper.updateReward(rewardBizNo, "FAILED", safeMessage(e), null, now);
        }
    }

    /** 重复首次组队事件仅允许重试 FAILED 奖励，其他状态返回 DUPLICATE。 */
    private InviteRewardResult retryFirstTeamReward(
            Long relationId, Long inviter, String rewardBizNo, LocalDateTime now) {
        String status = mapper.findRewardStatus(rewardBizNo);
        if (!"FAILED".equals(status)) {
            return new InviteRewardResult(relationId, inviter, rewardBizNo, "DUPLICATE");
        }
        String retried = retryExistingReward(inviter, rewardBizNo, RULE_FIRST_TEAM, now);
        return new InviteRewardResult(relationId, inviter, rewardBizNo, retried);
    }

    /** 补发一条已存在且状态为 FAILED 的奖励台账。 */
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

    /** 创建并发放单个邀请里程碑奖励。 */
    private void issueStageReward(Long inviter, Long relationId, int stage, int points, LocalDateTime now) {
        // 里程碑 bizNo 与邀请人+阶段绑定，不会因触发该阶段的 relationId 变化而重复发放。
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

    /** 读取可运营配置的奖励值；无配置或非正数时使用代码默认值。 */
    private int rewardPoints(String ruleCode, int fallback) {
        Integer configured = mapper.findRewardRuleValue(ruleCode);
        return configured != null && configured > 0 ? configured : fallback;
    }

    /** 根据规则编码恢复补偿重试所需的默认奖励值。 */
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

    /**
     * 从用户 ID 和碰撞重试 salt 生成 6~10 位大写 36 进制邀请码候选值。
     * 最终唯一性仍以数据库唯一索引为准。
     */
    private String code(long id, int salt) {
        String value = Long.toUnsignedString(id + salt * 97L, 36).toUpperCase(Locale.ROOT);
        return value.length() >= 6 ? value.substring(0, Math.min(10, value.length()))
                : "0".repeat(6 - value.length()) + value;
    }

    /** 计算距离下一个邀请人数展示节点还差多少人。 */
    private int next(int count) {
        for (int level : new int[]{1, 3, 10, 30, 50}) {
            if (count < level) {
                return level - count;
            }
        }
        return 0;
    }

    /** 构建不携带邀请码和过期时间的二维码校验失败响应。 */
    private InviteQrValidationVO invalid(String status) {
        return new InviteQrValidationVO(false, status, null, null);
    }

    /** 邀请人昵称缺失时使用通用文案，避免预览页显示空值。 */
    private String fallbackNickname(String nickname) {
        return StringUtils.hasText(nickname) ? nickname : "同路行用户";
    }

    /** 把外部奖励异常压缩为最多 255 字符的可持久化错误摘要。 */
    private String safeMessage(RuntimeException e) {
        String message = e.getMessage();
        return message == null ? e.getClass().getSimpleName() : message.substring(0, Math.min(255, message.length()));
    }

    /** 使用 HMAC-SHA256 签名二维码 payload，输出无补位 Base64URL 字符串。 */
    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(qrSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return encode(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("邀请二维码签名失败", exception);
        }
    }

    /** 使用 URL 安全且不带等号补位的 Base64 编码。 */
    private String encode(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    /** 将 deep link 内容生成 720×720 PNG 二维码，再转为 Base64 供客户端显示。 */
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

    /** 把 Unix 秒转换为服务器默认时区的本地日期时间。 */
    private LocalDateTime local(long epochSecond) {
        return LocalDateTime.ofInstant(Instant.ofEpochSecond(epochSecond), ZoneId.systemDefault());
    }
}
