package com.tongluxing.invite.service.impl;

import java.time.LocalDateTime;
import java.time.Instant;
import java.time.ZoneId;
import java.nio.charset.StandardCharsets;
import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Base64;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.tongluxing.invite.dto.InviteRewardResult;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.tongluxing.common.exception.BusinessException;
import com.tongluxing.common.event.UserRegisteredEvent;
import com.tongluxing.common.result.ResultCode;
import com.tongluxing.common.utils.SnowflakeIdGenerator;
import com.tongluxing.invite.integration.InviteRewardPort;
import com.tongluxing.invite.mapper.InviteMapper;
import com.tongluxing.invite.dto.InviteQueryDTO;
import com.tongluxing.invite.model.InviteModels.InvitationVO;
import com.tongluxing.invite.model.InviteModels.InviteBindVO;
import com.tongluxing.invite.model.InviteModels.InviteCodeVO;
import com.tongluxing.invite.model.InviteModels.InviteRewardProgressVO;
import com.tongluxing.invite.model.InviteModels.InviteQrVO;
import com.tongluxing.invite.model.InviteModels.InviteQrValidationVO;
import com.tongluxing.invite.model.InviteModels.PageResult;
import com.tongluxing.invite.service.InviteService;
import com.tongluxing.user.support.CurrentUserContext;

import lombok.RequiredArgsConstructor;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.client.j2se.MatrixToImageWriter;

/**
 * 邀请业务服务实现。
 *
 * <p>包含邀请码自动生成、注册来源事件绑定、邀请记录查询、邀请人数统计和阶梯奖励发放流程。</p>
 */
@Service
@RequiredArgsConstructor
public class InviteServiceImpl implements InviteService {

    /** 邀请关系初始状态：被邀请人已完成注册并绑定来源。 */
    private static final String STATUS_REGISTERED = "REGISTERED";

    /** 注册来源类型：用户邀请。 */
    private static final String SOURCE_INVITE = "INVITE";

    /** 邀请奖励规则：被邀请人首次完成组队。 */
    private static final String RULE_FIRST_TEAM = "FIRST_TEAM";

    /** 邀请奖励业务幂等号前缀。 */
    private static final String REWARD_BIZ_PREFIX = "IFT";

    /** 邀请新用户注册奖励，每条邀请关系只发一次。 */
    private static final String RULE_REGISTER = "INVITE_USER_REGISTER";

    /** MVP 邀请注册人数阶梯奖励，value 为同路值。 */
    private static final Map<Integer, Integer> STAGE_REWARDS = Map.of(
            3, 200,
            10, 500,
            30, 2000,
            50, 5000
    );

    /** 邀请码最多生成尝试次数，用于处理极小概率的唯一键碰撞。 */
    private static final int MAX_CODE_GENERATE_ATTEMPTS = 5;

    /** 二维码签名周期：七天。 */
    private static final long QR_PERIOD_SECONDS = 7L * 24 * 60 * 60;

    /** 邀请模块数据访问对象。 */
    private final InviteMapper mapper;

    /** 当前登录用户上下文，用于 current* 系列接口获取 userId。 */
    private final CurrentUserContext currentUser;

    /** 奖励发放端口；使用 ObjectProvider 允许没有实现时模块仍可启动。 */
    private final ObjectProvider<InviteRewardPort> rewardPort;

    /** 复用部署环境中的高强度 JWT 密钥进行 HMAC；生产环境不会落库二维码明文令牌。 */
    @Value("${auth.jwt.secret}")
    private String qrSecret;

    /** 获取当前登录用户的邀请码。 */
    @Override
    public InviteCodeVO currentCode() {
        return getCode(currentUser.requireUserId());
    }

    /** 生成当前七天时间窗的确定性签名令牌及可直接展示的 PNG 二维码。 */
    @Override
    public InviteQrVO currentQr() {
        long userId = currentUser.requireUserId();
        InviteCodeVO code = getCode(userId);
        long now = Instant.now().getEpochSecond();
        long issued = now - Math.floorMod(now, QR_PERIOD_SECONDS);
        long expires = issued + QR_PERIOD_SECONDS;
        String payload = userId + "|" + code.inviteCode() + "|" + issued + "|" + expires;
        String token = encode(payload.getBytes(StandardCharsets.UTF_8)) + "." + sign(payload);
        String content = "tongluxing://invite?token=" + token;
        return new InviteQrVO(code.inviteCode(), token, content, qrPng(content),
                local(issued), local(expires), Math.max(0, expires - now));
    }

    /** 先做常量时间验签，再校验时间窗和邀请码状态，明确区分过期与伪造。 */
    @Override
    public InviteQrValidationVO validateQr(String token) {
        try {
            if (token == null || token.isBlank() || token.length() > 1024) {
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
            String inviteCode = fields[1];
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

    /** 手动扫码/输入邀请码绑定；与注册来源自动绑定共用同一幂等实现。 */
    @Override
    @Transactional
    public InviteBindVO bindCurrent(String inviteCode) {
        return bindFromRegistration(currentUser.requireUserId(), inviteCode, LocalDateTime.now());
    }

    /** 查询当前登录用户的邀请奖励进度。 */
    @Override
    public InviteRewardProgressVO currentProgress() {
        return getProgress(currentUser.requireUserId());
    }

    /** 分页查询当前登录用户的邀请记录。 */
    @Override
    public PageResult<InvitationVO> currentRecords(String status, int page, int size) {
        return getRecords(currentUser.requireUserId(), status, page, size);
    }

    /** 获取指定用户的邀请码；如果还没有邀请码，则最多尝试生成 5 次。 */
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
                // 邀请码存在唯一约束，碰撞时换一个 salt 再试。
            }
            row = mapper.findCodeByUser(userId);
        }
        if (row == null) {
            throw new BusinessException("邀请码生成失败，请稍后重试");
        }
        return new InviteCodeVO(row.getInviteCode(), "inviteCode=" + row.getInviteCode(), row.getEnabledFlag());
    }

    /** 监听首次注册成功事件，只处理 INVITE 来源。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleUserRegistered(UserRegisteredEvent event) {
        if (event == null || !event.hasSource(SOURCE_INVITE)) {
            return;
        }
        try {
            bindFromRegistration(event.userId(), event.normalizedSourceCode(), event.registerTime());
        } catch (RuntimeException ignored) {
            // 注册来源绑定不能反向影响已完成的注册主链路。
        }
    }

    /**
     * 根据注册事件绑定邀请关系。
     *
     * <p>该方法只服务 UserRegisteredEvent，不作为登录主流程或前端手填入口。</p>
     */
    @Transactional
    public InviteBindVO bindFromRegistration(Long invitee, String sourceCode, LocalDateTime registeredAt) {
        if (invitee == null || sourceCode == null || sourceCode.isBlank()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "邀请来源不能为空");
        }
        String normalizedCode = sourceCode.trim();
        InviteQueryDTO owner = mapper.findCode(normalizedCode);
        if (owner == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "邀请来源不存在");
        }
        long inviter = owner.getUserId();
        if (inviter == invitee) {
            throw new BusinessException(409, "不能绑定自己的邀请来源");
        }
        InviteQueryDTO existing = mapper.findRelationByInvitee(invitee);
        if (existing != null) {
            issueRegistrationReward(existing.getInviterUserId(), existing.getRelationId(), LocalDateTime.now());
            return relationResult(existing);
        }
        if (mapper.createsCycle(inviter, invitee) > 0) {
            throw new BusinessException(409, "邀请关系形成循环");
        }
        LocalDateTime now = LocalDateTime.now();
        try {
            mapper.insertRelation(SnowflakeIdGenerator.nextId(), inviter, invitee, normalizedCode,
                    SOURCE_INVITE, normalizedCode, registeredAt, now);
        } catch (DuplicateKeyException e) {
            InviteQueryDTO duplicate = mapper.findRelationByInvitee(invitee);
            if (duplicate != null) {
                issueRegistrationReward(duplicate.getInviterUserId(), duplicate.getRelationId(), LocalDateTime.now());
                return relationResult(duplicate);
            }
            throw new BusinessException(409, "邀请关系已绑定");
        }
        InviteQueryDTO relation = mapper.findRelationByInvitee(invitee);
        Long relationId = relation == null ? null : relation.getRelationId();
        issueRegistrationReward(inviter, relationId, now);
        issueStageRewards(inviter, relationId, now);
        return new InviteBindVO(inviter, invitee, STATUS_REGISTERED, now);
    }

    /** 查询指定用户的邀请奖励进度。 */
    @Override
    public InviteRewardProgressVO getProgress(Long userId) {
        int count = mapper.countInvitees(userId);
        return new InviteRewardProgressVO(count, next(count), mapper.findGrantedRules(userId));
    }

    /** 分页查询指定用户的邀请记录，并规范化 page/size，避免异常分页参数拖垮查询。 */
    @Override
    public PageResult<InvitationVO> getRecords(Long userId, String status, int page, int size) {
        int normalizedPage = Math.max(1, page);
        int normalizedSize = Math.min(100, Math.max(1, size));
        List<InvitationVO> records = mapper.findRecords(userId, status,
                (normalizedPage - 1) * normalizedSize, normalizedSize).stream().map(this::record).toList();
        return new PageResult<>(records, mapper.countRecords(userId, status), normalizedPage, normalizedSize);
    }

    /**
     * 处理被邀请人首次完成组队事件。
     *
     * <p>流程：锁定邀请关系 -> 标记有效 -> 写入奖励记录 -> 调用奖励端口 -> 回写奖励状态。</p>
     */
    @Override
    @Transactional
    public InviteRewardResult completeFirstTeam(Long userId, Long teamId, String bizId) {
        if (userId == null || bizId == null || bizId.isBlank()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "首次组队完成事件参数不完整");
        }
        InviteQueryDTO relation = mapper.findRelationForUpdate(userId);
        if (relation == null) {
            return new InviteRewardResult(null, null, bizId, "NO_RELATION");
        }
        long relationId = relation.getRelationId();
        long inviter = relation.getInviterUserId();
        LocalDateTime now = LocalDateTime.now();
        mapper.markValid(relationId, now);
        // 外部 bizId 可能很长；将其稳定散列后再拼接，避免超过 reward_biz_no 的 64 字符限制。
        String eventDigest = UUID.nameUUIDFromBytes(bizId.getBytes(StandardCharsets.UTF_8))
                .toString().replace("-", "");
        String rewardBizNo = REWARD_BIZ_PREFIX + ":" + relationId + ":" + eventDigest;
        InviteRewardPort port = rewardPort.getIfAvailable();
        int configuredPoints = port == null ? 100 : port.rewardPoints(RULE_FIRST_TEAM);
        String rewardSnapshot = "{\"growthPoints\":" + configuredPoints + "}";
        try {
            mapper.insertReward(SnowflakeIdGenerator.nextId(), relationId, inviter, RULE_FIRST_TEAM,
                    rewardBizNo, rewardSnapshot, now);
        } catch (DuplicateKeyException e) {
            // reward_biz_no 有唯一约束，重复事件直接返回 DUPLICATE，保证幂等。
            return new InviteRewardResult(relationId, inviter, rewardBizNo, "DUPLICATE");
        }
        try {
            if (port == null) {
                throw new IllegalStateException("reward port unavailable");
            }
            port.grantInviteReward(inviter, rewardBizNo, RULE_FIRST_TEAM);
            mapper.updateReward(rewardBizNo, "GRANTED", null, now, now);
            return new InviteRewardResult(relationId, inviter, rewardBizNo, "GRANTED");
        } catch (RuntimeException e) {
            mapper.updateReward(rewardBizNo, "FAILED", e.getMessage(), null, now);
            return new InviteRewardResult(relationId, inviter, rewardBizNo, "FAILED");
        }
    }

    /** 将 Mapper 查询 DTO 转成前端需要的邀请记录 VO。 */
    private InvitationVO record(InviteQueryDTO row) {
        return new InvitationVO(row.getRelationId(), row.getInviteeUserId(), row.getInviteCode(),
                row.getStatus(), row.getBoundAt(), row.getFirstTeamCompletedAt());
    }

    /** 将已有邀请关系转为绑定结果，体现“第一条关系为准”。 */
    private InviteBindVO relationResult(InviteQueryDTO row) {
        return new InviteBindVO(row.getInviterUserId(), row.getInviteeUserId(), row.getStatus(), row.getBoundAt());
    }

    /** 检查并发放邀请注册人数阶梯奖励。 */
    private void issueStageRewards(Long inviter, Long relationId, LocalDateTime now) {
        int inviteCount = mapper.countInvitees(inviter);
        for (Integer stage : STAGE_REWARDS.keySet().stream().sorted().toList()) {
            if (inviteCount >= stage) {
                issueStageReward(inviter, relationId, stage, STAGE_REWARDS.get(stage), now);
            }
        }
    }

    /** 发放“邀请新用户注册”100 成长值，奖励业务号和成长流水均具备幂等性。 */
    private void issueRegistrationReward(Long inviter, Long relationId, LocalDateTime now) {
        if (relationId == null) {
            throw new BusinessException(ResultCode.INTERNAL_SERVER_ERROR, "邀请关系创建失败");
        }
        String ruleCode = "INV_REG:" + Long.toUnsignedString(relationId, 36).toUpperCase(Locale.ROOT);
        String rewardBizNo = RULE_REGISTER + ":" + relationId;
        InviteRewardPort port = rewardPort.getIfAvailable();
        int configuredPoints = port == null ? 100 : port.rewardPoints(ruleCode);
        String snapshot = "{\"rewardType\":\"GROWTH\",\"rewardValue\":" + configuredPoints + "}";
        try {
            mapper.insertReward(SnowflakeIdGenerator.nextId(), relationId, inviter, ruleCode,
                    rewardBizNo, snapshot, now);
        } catch (DuplicateKeyException e) {
            return;
        }
        try {
            if (port == null) {
                throw new IllegalStateException("reward port unavailable");
            }
            port.grantInviteReward(inviter, rewardBizNo, ruleCode);
            mapper.updateReward(rewardBizNo, "GRANTED", null, now, now);
        } catch (RuntimeException e) {
            mapper.updateReward(rewardBizNo, "FAILED", e.getMessage(), null, now);
            throw e;
        }
    }

    /** 发放单个阶梯奖励，使用 reward_biz_no 保证幂等。 */
    private void issueStageReward(Long inviter, Long relationId, int stage, int points, LocalDateTime now) {
        String ruleCode = "INVITE_STAGE_" + stage;
        String rewardBizNo = "INVITE_STAGE:" + inviter + ":" + stage;
        InviteRewardPort port = rewardPort.getIfAvailable();
        int configuredPoints = port == null ? points : port.rewardPoints(ruleCode);
        String snapshot = "{\"rewardType\":\"GROWTH\",\"rewardStage\":" + stage + ",\"rewardValue\":" + configuredPoints + "}";
        try {
            mapper.insertReward(SnowflakeIdGenerator.nextId(), relationId, inviter, ruleCode, rewardBizNo, snapshot, now);
        } catch (DuplicateKeyException e) {
            return;
        }
        try {
            if (port == null) {
                throw new IllegalStateException("reward port unavailable");
            }
            port.grantInviteReward(inviter, rewardBizNo, ruleCode);
            mapper.updateReward(rewardBizNo, "GRANTED", null, now, now);
        } catch (RuntimeException e) {
            mapper.updateReward(rewardBizNo, "FAILED", e.getMessage(), null, now);
        }
    }

    /** 根据用户 ID 和 salt 生成短邀请码。 */
    private String code(long id, int salt) {
        String value = Long.toUnsignedString(id + salt * 97L, 36).toUpperCase(Locale.ROOT);
        return value.substring(0, Math.min(10, value.length()));
    }

    /** 计算距离下一档奖励还差多少有效邀请数。 */
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
