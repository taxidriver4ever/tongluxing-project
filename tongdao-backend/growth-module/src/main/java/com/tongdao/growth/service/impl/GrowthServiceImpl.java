package com.tongdao.growth.service.impl;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tongdao.common.exception.BusinessException;
import com.tongdao.common.result.ResultCode;
import com.tongdao.common.utils.SnowflakeIdGenerator;
import com.tongdao.growth.integration.GrowthFacade.GrowthGrantResult;
import com.tongdao.growth.mapper.GrowthMapper;
import com.tongdao.growth.dto.GrowthQueryDTO;
import com.tongdao.growth.service.GrowthService;
import com.tongdao.user.model.UserModels.BadgeVO;
import com.tongdao.user.model.UserModels.BadgeWallVO;
import com.tongdao.user.model.UserModels.GrowthLogVO;
import com.tongdao.user.model.UserModels.GrowthSummaryVO;
import com.tongdao.user.model.UserModels.PageResult;
import com.tongdao.user.support.CurrentUserContext;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GrowthServiceImpl implements GrowthService {
    private final GrowthMapper mapper;
    private final CurrentUserContext currentUser;

    @Override
    public GrowthSummaryVO getCurrentSummary() {
        return getSummary(currentUser.requireUserId());
    }

    @Override
    public BadgeWallVO getCurrentBadges() {
        return getBadgeWall(currentUser.requireUserId());
    }

    @Override
    public PageResult<GrowthLogVO> getCurrentLogs(int page, int size) {
        long userId = currentUser.requireUserId();
        int normalizedPage = Math.max(1, page);
        int normalizedSize = Math.min(100, Math.max(1, size));
        List<GrowthLogVO> records = mapper.findLogs(userId, (normalizedPage - 1) * normalizedSize, normalizedSize)
                .stream().map(this::log).toList();
        return new PageResult<>(records, mapper.countLogs(userId), normalizedPage, normalizedSize);
    }

    @Override
    public GrowthSummaryVO getSummary(Long userId) {
        GrowthQueryDTO account = ensureAccount(userId);
        int points = account.getTotalPoints();
        Integer next = mapper.findNextLevelPoints(points);
        return new GrowthSummaryVO(points, account.getLevelCode(), next == null ? 0 : Math.max(0, next - points));
    }

    @Override
    public BadgeWallVO getBadgeWall(Long userId) {
        return new BadgeWallVO(
                mapper.findEarnedBadges(userId).stream().map(this::badge).toList(),
                mapper.findLockedBadges(userId).stream().map(this::badge).toList());
    }

    @Override
    @Transactional
    public GrowthGrantResult grant(Long userId, String bizType, String bizId, int points, String remark) {
        LocalDateTime now = LocalDateTime.now();
        GrowthQueryDTO account = ensureAccountForUpdate(userId);
        int oldPoints = account.getTotalPoints();
        int balance = oldPoints + points;
        if (balance < 0) throw new BusinessException(ResultCode.BAD_REQUEST, "成长值余额不足");
        try {
            mapper.insertLog(SnowflakeIdGenerator.nextId(), userId, bizType, bizId, points, balance, remark, now);
        } catch (DuplicateKeyException e) {
            return new GrowthGrantResult(userId, points, oldPoints, account.getLevelCode(), true);
        }
        String level = mapper.findLevelCode(balance);
        if (level == null) level = "LV1";
        if (mapper.updateAccount(account.getId(), balance, level, account.getVersion(), now) == 0) {
            throw new BusinessException(ResultCode.INTERNAL_SERVER_ERROR, "成长账户并发更新失败");
        }
        int count = mapper.countEvents(userId, bizType);
        for (Long badgeId : mapper.findEligibleBadges(bizType, count)) {
            mapper.insertUserBadge(SnowflakeIdGenerator.nextId(), userId, badgeId, bizId, now);
        }
        return new GrowthGrantResult(userId, points, balance, level, false);
    }

    private GrowthQueryDTO ensureAccount(Long userId) {
        GrowthQueryDTO account = mapper.findAccount(userId);
        if (account != null) return account;
        try { mapper.insertAccount(SnowflakeIdGenerator.nextId(), userId, LocalDateTime.now()); }
        catch (DuplicateKeyException ignored) { }
        return mapper.findAccount(userId);
    }

    private GrowthQueryDTO ensureAccountForUpdate(Long userId) {
        GrowthQueryDTO account = mapper.findAccountForUpdate(userId);
        if (account != null) return account;
        try { mapper.insertAccount(SnowflakeIdGenerator.nextId(), userId, LocalDateTime.now()); }
        catch (DuplicateKeyException ignored) { }
        return mapper.findAccountForUpdate(userId);
    }

    private GrowthLogVO log(GrowthQueryDTO row) {
        return new GrowthLogVO(row.getId(), row.getBizType(), row.getBizId(), row.getPointDelta(),
                row.getBalanceAfter(), row.getRemark(), row.getCreatedAt());
    }

    private BadgeVO badge(GrowthQueryDTO row) {
        return new BadgeVO(row.getBadgeId(), row.getBadgeCode(), row.getBadgeName(),
                row.getBadgeImageKey(), row.getAwardedAt());
    }
}
