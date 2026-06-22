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
import com.tongdao.invite.service.InviteService;
import com.tongdao.user.model.UserModels.InvitationVO;
import com.tongdao.user.model.UserModels.InviteBindVO;
import com.tongdao.user.model.UserModels.InviteCodeVO;
import com.tongdao.user.model.UserModels.InviteRewardProgressVO;
import com.tongdao.user.model.UserModels.PageResult;
import com.tongdao.user.support.CurrentUserContext;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class InviteServiceImpl implements InviteService {
    private final InviteMapper mapper;
    private final CurrentUserContext currentUser;
    private final ObjectProvider<InviteRewardPort> rewardPort;

    @Override
    public InviteCodeVO currentCode() { return getCode(currentUser.requireUserId()); }

    @Override
    public InviteBindVO bindCurrent(String code) { return bind(currentUser.requireUserId(), code); }

    @Override
    public InviteRewardProgressVO currentProgress() { return getProgress(currentUser.requireUserId()); }

    @Override
    public PageResult<InvitationVO> currentRecords(String status, int page, int size) {
        return getRecords(currentUser.requireUserId(), status, page, size);
    }

    @Override
    public InviteCodeVO getCode(Long userId) {
        InviteQueryDTO row = mapper.findCodeByUser(userId);
        for (int i = 0; i < 5 && row == null; i++) {
            try { mapper.insertCode(SnowflakeIdGenerator.nextId(), userId, code(userId, i), LocalDateTime.now()); }
            catch (DuplicateKeyException ignored) { }
            row = mapper.findCodeByUser(userId);
        }
        return new InviteCodeVO(row.getInviteCode(), "inviteCode=" + row.getInviteCode(), row.getEnabledFlag());
    }

    @Override
    @Transactional
    public InviteBindVO bind(Long invitee, String code) {
        if (invitee == null || code == null || code.isBlank()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "邀请码不能为空");
        }
        InviteQueryDTO owner = mapper.findCode(code.trim());
        if (owner == null) throw new BusinessException(ResultCode.NOT_FOUND, "邀请码不存在");
        long inviter = owner.getUserId();
        if (inviter == invitee) throw new BusinessException(409, "不能绑定自己的邀请码");
        if (mapper.findRelationIdByInvitee(invitee) != null) throw new BusinessException(409, "邀请关系已绑定");
        if (mapper.createsCycle(inviter, invitee) > 0) throw new BusinessException(409, "邀请关系形成循环");
        LocalDateTime now = LocalDateTime.now();
        try {
            mapper.insertRelation(SnowflakeIdGenerator.nextId(), inviter, invitee, code.trim(), now);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(409, "邀请关系已绑定");
        }
        return new InviteBindVO(inviter, invitee, "BOUND", now);
    }

    @Override
    public InviteRewardProgressVO getProgress(Long userId) {
        int count = mapper.countValid(userId);
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

    @Override
    @Transactional
    public InviteRewardResult completeFirstTeam(Long userId, Long teamId, String bizId) {
        InviteQueryDTO relation = mapper.findRelationForUpdate(userId);
        if (relation == null) return new InviteRewardResult(null, null, bizId, "NO_RELATION");
        long relationId = relation.getRelationId();
        long inviter = relation.getInviterUserId();
        LocalDateTime now = LocalDateTime.now();
        mapper.markValid(relationId, now);
        String rewardBizNo = "INVITE_FIRST_TEAM:" + relationId + ":" + bizId;
        try {
            mapper.insertReward(SnowflakeIdGenerator.nextId(), relationId, inviter, "FIRST_TEAM",
                    rewardBizNo, "{\"growthPoints\":100}", now);
        } catch (DuplicateKeyException e) {
            return new InviteRewardResult(relationId, inviter, rewardBizNo, "DUPLICATE");
        }
        try {
            InviteRewardPort port = rewardPort.getIfAvailable();
            if (port == null) throw new IllegalStateException("reward port unavailable");
            port.grantInviteReward(inviter, rewardBizNo, "FIRST_TEAM");
            mapper.updateReward(rewardBizNo, "GRANTED", null, now, now);
            return new InviteRewardResult(relationId, inviter, rewardBizNo, "GRANTED");
        } catch (RuntimeException e) {
            mapper.updateReward(rewardBizNo, "FAILED", e.getMessage(), null, now);
            return new InviteRewardResult(relationId, inviter, rewardBizNo, "FAILED");
        }
    }

    private InvitationVO record(InviteQueryDTO row) {
        return new InvitationVO(row.getRelationId(), row.getInviteeUserId(), row.getInviteCode(),
                row.getStatus(), row.getBoundAt(), row.getFirstTeamCompletedAt());
    }

    private String code(long id, int salt) {
        String value = Long.toUnsignedString(id + salt * 97L, 36).toUpperCase(Locale.ROOT);
        return value.substring(0, Math.min(10, value.length()));
    }

    private int next(int count) {
        for (int level : new int[]{1, 3, 5, 10}) if (count < level) return level - count;
        return 0;
    }
}
