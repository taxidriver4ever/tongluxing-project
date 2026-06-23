package com.tongdao.invite.service.impl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tongdao.common.exception.BusinessException;
import com.tongdao.common.result.ResultCode;
import com.tongdao.common.utils.SnowflakeIdGenerator;
import com.tongdao.invite.integration.InviteFacade.InviteRewardResult;
import com.tongdao.invite.integration.InviteRewardPort;
import com.tongdao.invite.mapper.InviteMapper;
import com.tongdao.invite.dto.InviteQueryDTO;
import com.tongdao.invite.model.InviteModels.InvitationVO;
import com.tongdao.invite.model.InviteModels.InviteBindVO;
import com.tongdao.invite.model.InviteModels.InviteCodeVO;
import com.tongdao.invite.model.InviteModels.InviteRewardProgressVO;
import com.tongdao.invite.model.InviteModels.PageResult;
import com.tongdao.invite.service.InviteService;
import com.tongdao.user.support.CurrentUserContext;

import lombok.RequiredArgsConstructor;

/**
 * 邀请业务服务实现。
 *
 * <p>包含邀请码自动生成、邀请码绑定、邀请记录查询、有效邀请统计，以及“首次组队完成”后的奖励发放流程。</p>
 */
@Service
@RequiredArgsConstructor
public class InviteServiceImpl implements InviteService {

    /** 邀请模块数据访问对象。 */
    private final InviteMapper mapper;

    /** 当前登录用户上下文，用于 current* 系列接口获取 userId。 */
    private final CurrentUserContext currentUser;

    /** 奖励发放端口；使用 ObjectProvider 允许没有实现时模块仍可启动。 */
    private final ObjectProvider<InviteRewardPort> rewardPort;

    /** 获取当前登录用户的邀请码。 */
    @Override
    public InviteCodeVO currentCode() {
        return getCode(currentUser.requireUserId());
    }

    /** 当前登录用户绑定邀请码。 */
    @Override
    public InviteBindVO bindCurrent(String code) {
        return bind(currentUser.requireUserId(), code);
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
        InviteQueryDTO row = mapper.findCodeByUser(userId);
        for (int i = 0; i < 5 && row == null; i++) {
            try {
                mapper.insertCode(SnowflakeIdGenerator.nextId(), userId, code(userId, i), LocalDateTime.now());
            } catch (DuplicateKeyException ignored) {
                // 邀请码存在唯一约束，碰撞时换一个 salt 再试。
            }
            row = mapper.findCodeByUser(userId);
        }
        return new InviteCodeVO(row.getInviteCode(), "inviteCode=" + row.getInviteCode(), row.getEnabledFlag());
    }

    /**
     * 绑定邀请码。
     *
     * <p>绑定前会校验邀请码存在、不能绑定自己、不能重复绑定、不能形成邀请关系环。</p>
     */
    @Override
    @Transactional
    public InviteBindVO bind(Long invitee, String code) {
        if (invitee == null || code == null || code.isBlank()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "邀请码不能为空");
        }
        InviteQueryDTO owner = mapper.findCode(code.trim());
        if (owner == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "邀请码不存在");
        }
        long inviter = owner.getUserId();
        if (inviter == invitee) {
            throw new BusinessException(409, "不能绑定自己的邀请码");
        }
        if (mapper.findRelationIdByInvitee(invitee) != null) {
            throw new BusinessException(409, "邀请关系已绑定");
        }
        if (mapper.createsCycle(inviter, invitee) > 0) {
            throw new BusinessException(409, "邀请关系形成循环");
        }
        LocalDateTime now = LocalDateTime.now();
        try {
            mapper.insertRelation(SnowflakeIdGenerator.nextId(), inviter, invitee, code.trim(), now);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(409, "邀请关系已绑定");
        }
        return new InviteBindVO(inviter, invitee, "BOUND", now);
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
        InviteQueryDTO relation = mapper.findRelationForUpdate(userId);
        if (relation == null) {
            return new InviteRewardResult(null, null, bizId, "NO_RELATION");
        }
        long relationId = relation.getRelationId();
        long inviter = relation.getInviterUserId();
        LocalDateTime now = LocalDateTime.now();
        mapper.markValid(relationId, now);
        String rewardBizNo = "INVITE_FIRST_TEAM:" + relationId + ":" + bizId;
        try {
            mapper.insertReward(SnowflakeIdGenerator.nextId(), relationId, inviter, "FIRST_TEAM",
                    rewardBizNo, "{\"growthPoints\":100}", now);
        } catch (DuplicateKeyException e) {
            // reward_biz_no 有唯一约束，重复事件直接返回 DUPLICATE，保证幂等。
            return new InviteRewardResult(relationId, inviter, rewardBizNo, "DUPLICATE");
        }
        try {
            InviteRewardPort port = rewardPort.getIfAvailable();
            if (port == null) {
                throw new IllegalStateException("reward port unavailable");
            }
            port.grantInviteReward(inviter, rewardBizNo, "FIRST_TEAM");
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
