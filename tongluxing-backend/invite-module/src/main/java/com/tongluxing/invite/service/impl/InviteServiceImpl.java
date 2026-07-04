package com.tongluxing.invite.service.impl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tongluxing.common.exception.BusinessException;
import com.tongluxing.common.result.ResultCode;
import com.tongluxing.common.utils.SnowflakeIdGenerator;
import com.tongluxing.invite.integration.InviteFacade.InviteRewardResult;
import com.tongluxing.invite.integration.InviteAuthPort;
import com.tongluxing.invite.integration.InviteRewardPort;
import com.tongluxing.invite.mapper.InviteMapper;
import com.tongluxing.invite.dto.InviteQueryDTO;
import com.tongluxing.invite.model.InviteModels.InvitationVO;
import com.tongluxing.invite.model.InviteModels.InviteBindVO;
import com.tongluxing.invite.model.InviteModels.InviteCodeVO;
import com.tongluxing.invite.model.InviteModels.InviteRewardProgressVO;
import com.tongluxing.invite.model.InviteModels.PageResult;
import com.tongluxing.invite.service.InviteService;
import com.tongluxing.user.support.CurrentUserContext;

import lombok.RequiredArgsConstructor;

/**
 * 邀请业务服务实现。
 *
 * <p>包含邀请码自动生成、邀请码绑定、邀请记录查询、有效邀请统计，以及“首次组队完成”后的奖励发放流程。</p>
 */
@Service
@RequiredArgsConstructor
public class InviteServiceImpl implements InviteService {

    /** 邀请关系初始状态：已绑定，但尚未完成有效行为。 */
    private static final String STATUS_BOUND = "BOUND";

    /** 分享链接自动绑定来源。 */
    private static final String SOURCE_LINK = "LINK";

    /** 邀请二维码自动绑定来源。 */
    private static final String SOURCE_QR_CODE = "QR_CODE";

    /** 手机号弱兜底绑定来源。 */
    private static final String SOURCE_PHONE_FALLBACK = "PHONE_FALLBACK";

    /** 注册后允许绑定邀请关系的窗口期，超过后视为自然用户。 */
    private static final int BIND_WINDOW_DAYS = 7;

    /** 邀请奖励规则：被邀请人首次完成组队。 */
    private static final String RULE_FIRST_TEAM = "FIRST_TEAM";

    /** 邀请奖励业务幂等号前缀。 */
    private static final String REWARD_BIZ_PREFIX = "INVITE_FIRST_TEAM";

    /** V1 首次组队奖励快照；实际发放由 InviteRewardPort 适配。 */
    private static final String FIRST_TEAM_REWARD_SNAPSHOT = "{\"growthPoints\":100}";

    /** 邀请码最多生成尝试次数，用于处理极小概率的唯一键碰撞。 */
    private static final int MAX_CODE_GENERATE_ATTEMPTS = 5;

    /** 邀请模块数据访问对象。 */
    private final InviteMapper mapper;

    /** 当前登录用户上下文，用于 current* 系列接口获取 userId。 */
    private final CurrentUserContext currentUser;

    /** 奖励发放端口；使用 ObjectProvider 允许没有实现时模块仍可启动。 */
    private final ObjectProvider<InviteRewardPort> rewardPort;

    /** 认证账号查询端口；由 auth-module 侧提供适配器实现。 */
    private final ObjectProvider<InviteAuthPort> inviteAuthPort;

    /** 获取当前登录用户的邀请码。 */
    @Override
    public InviteCodeVO currentCode() {
        return getCode(currentUser.requireUserId());
    }

    /** 当前登录用户根据分享链接/二维码系统参数自动绑定邀请关系。 */
    @Override
    public InviteBindVO autoBindCurrent(String inviteCode, String sourceType, String sourceScene) {
        return autoBind(currentUser.requireUserId(), inviteCode, sourceType, sourceScene);
    }

    /** 当前登录用户通过邀请人手机号进行 7 天内弱兜底绑定。 */
    @Override
    public InviteBindVO bindCurrentByPhone(String inviterPhone) {
        return bindByPhone(currentUser.requireUserId(), inviterPhone);
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

    /**
     * 按默认 LINK 来源自动绑定系统邀请参数。
     *
     * <p>该方法保留给内部 Facade 使用；HTTP 主入口会显式传入 LINK 或 QR_CODE。</p>
     */
    @Override
    @Transactional
    public InviteBindVO bind(Long invitee, String code) {
        return autoBind(invitee, code, SOURCE_LINK, null);
    }

    /**
     * 分享链接/二维码注册后的自动绑定。
     *
     * <p>inviteCode 在这里是系统分享参数，不是用户手动填写的邀请码。若用户已经存在邀请关系，
     * 直接返回第一条关系，不覆盖、不改绑。</p>
     */
    @Transactional
    public InviteBindVO autoBind(Long invitee, String code, String sourceType, String sourceScene) {
        if (invitee == null || code == null || code.isBlank()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "邀请码不能为空");
        }
        String normalizedSource = normalizeAutoSource(sourceType);
        String normalizedCode = code.trim();
        InviteQueryDTO owner = mapper.findCode(normalizedCode);
        if (owner == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "邀请码不存在");
        }
        long inviter = owner.getUserId();
        if (inviter == invitee) {
            throw new BusinessException(409, "不能绑定自己的邀请码");
        }
        InviteQueryDTO existing = mapper.findRelationByInvitee(invitee);
        if (existing != null) {
            return relationResult(existing);
        }
        LocalDateTime registeredAt = requireBindableRegisteredAt(invitee);
        if (mapper.createsCycle(inviter, invitee) > 0) {
            throw new BusinessException(409, "邀请关系形成循环");
        }
        LocalDateTime now = LocalDateTime.now();
        try {
            mapper.insertRelation(SnowflakeIdGenerator.nextId(), inviter, invitee, normalizedCode,
                    normalizedSource, sourceValue(normalizedCode, sourceScene), registeredAt, now);
        } catch (DuplicateKeyException e) {
            InviteQueryDTO duplicate = mapper.findRelationByInvitee(invitee);
            if (duplicate != null) {
                return relationResult(duplicate);
            }
            throw new BusinessException(409, "邀请关系已绑定");
        }
        return new InviteBindVO(inviter, invitee, STATUS_BOUND, now);
    }

    /**
     * 注册后 7 天内通过邀请人手机号进行弱兜底绑定。
     *
     * <p>该入口填写的是邀请人手机号，不是邀请码；如果当前用户已经绑定邀请关系，则拒绝覆盖。</p>
     */
    @Transactional
    public InviteBindVO bindByPhone(Long invitee, String inviterPhone) {
        if (invitee == null || inviterPhone == null || inviterPhone.isBlank()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "邀请人手机号不能为空");
        }
        String normalizedPhone = inviterPhone.trim();
        LocalDateTime registeredAt = requireBindableRegisteredAt(invitee);
        if (mapper.findRelationIdByInvitee(invitee) != null) {
            throw new BusinessException(409, "邀请关系已绑定");
        }

        Long inviter = authPort().findAvailableUserIdByPhone(normalizedPhone)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "邀请人不存在或账号不可用"));
        if (inviter.equals(invitee)) {
            throw new BusinessException(409, "不能绑定自己的手机号");
        }
        if (mapper.createsCycle(inviter, invitee) > 0) {
            throw new BusinessException(409, "邀请关系形成循环");
        }

        InviteCodeVO inviterCode = getCode(inviter);
        LocalDateTime now = LocalDateTime.now();
        try {
            mapper.insertRelation(SnowflakeIdGenerator.nextId(), inviter, invitee, inviterCode.inviteCode(),
                    SOURCE_PHONE_FALLBACK, maskPhone(normalizedPhone), registeredAt, now);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(409, "邀请关系已绑定");
        }
        return new InviteBindVO(inviter, invitee, STATUS_BOUND, now);
    }

    /** 查询指定用户的邀请奖励进度。 */
    @Override
    public InviteRewardProgressVO getProgress(Long userId) {
        int count = mapper.countValid(userId);
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
        String rewardBizNo = REWARD_BIZ_PREFIX + ":" + relationId + ":" + bizId;
        try {
            mapper.insertReward(SnowflakeIdGenerator.nextId(), relationId, inviter, RULE_FIRST_TEAM,
                    rewardBizNo, FIRST_TEAM_REWARD_SNAPSHOT, now);
        } catch (DuplicateKeyException e) {
            // reward_biz_no 有唯一约束，重复事件直接返回 DUPLICATE，保证幂等。
            return new InviteRewardResult(relationId, inviter, rewardBizNo, "DUPLICATE");
        }
        try {
            InviteRewardPort port = rewardPort.getIfAvailable();
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

    /** 校验自动绑定来源类型。 */
    private String normalizeAutoSource(String sourceType) {
        if (SOURCE_LINK.equals(sourceType) || SOURCE_QR_CODE.equals(sourceType)) {
            return sourceType;
        }
        throw new BusinessException(ResultCode.BAD_REQUEST, "来源类型只能是 LINK 或 QR_CODE");
    }

    /** 生成来源值，保留系统参数，同时拼接可选场景用于排查。 */
    private String sourceValue(String inviteCode, String sourceScene) {
        if (sourceScene == null || sourceScene.isBlank()) {
            return inviteCode;
        }
        String value = inviteCode + ":" + sourceScene.trim();
        return value.length() <= 64 ? value : value.substring(0, 64);
    }

    /** 获取并校验用户注册时间，超过 7 天视为自然用户，不再允许绑定。 */
    private LocalDateTime requireBindableRegisteredAt(Long userId) {
        LocalDateTime registeredAt = authPort().findRegisteredAt(userId)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "用户注册信息不存在"));
        if (registeredAt.plusDays(BIND_WINDOW_DAYS).isBefore(LocalDateTime.now())) {
            throw new BusinessException(409, "注册已超过7天，无法绑定邀请关系");
        }
        return registeredAt;
    }

    /** 获取认证账号查询端口，缺失实现时返回清晰错误。 */
    private InviteAuthPort authPort() {
        InviteAuthPort port = inviteAuthPort.getIfAvailable();
        if (port == null) {
            throw new BusinessException("邀请绑定依赖的认证账号查询能力未配置");
        }
        return port;
    }

    /** 手机号脱敏后用于 bind_source_value 审计。 */
    private String maskPhone(String phone) {
        if (phone.length() < 11) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(7);
    }

    /** 根据用户 ID 和 salt 生成短邀请码。 */
    private String code(long id, int salt) {
        String value = Long.toUnsignedString(id + salt * 97L, 36).toUpperCase(Locale.ROOT);
        return value.substring(0, Math.min(10, value.length()));
    }

    /** 计算距离下一档奖励还差多少有效邀请数。 */
    private int next(int count) {
        for (int level : new int[]{1, 3, 5, 10}) {
            if (count < level) {
                return level - count;
            }
        }
        return 0;
    }
}
