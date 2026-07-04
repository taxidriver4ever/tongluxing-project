package com.tongluxing.growth.service.impl;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tongluxing.common.exception.BusinessException;
import com.tongluxing.common.result.ResultCode;
import com.tongluxing.common.utils.SnowflakeIdGenerator;
import com.tongluxing.growth.integration.GrowthFacade.GrowthGrantResult;
import com.tongluxing.growth.mapper.GrowthMapper;
import com.tongluxing.growth.dto.GrowthQueryDTO;
import com.tongluxing.growth.service.GrowthService;
import com.tongluxing.user.model.UserModels.BadgeVO;
import com.tongluxing.user.model.UserModels.BadgeWallVO;
import com.tongluxing.user.model.UserModels.GrowthLogVO;
import com.tongluxing.user.model.UserModels.GrowthSummaryVO;
import com.tongluxing.user.model.UserModels.PageResult;
import com.tongluxing.user.support.CurrentUserContext;

import lombok.RequiredArgsConstructor;

/**
 * 成长模块业务实现。
 *
 * <p>该类负责成长账户初始化、成长值发放幂等、等级重算、徽章授予等核心流程。
 * 对外返回统一的 VO，避免 Controller 直接处理数据库查询对象。</p>
 */
@Service
@RequiredArgsConstructor
public class GrowthServiceImpl implements GrowthService {
    private final GrowthMapper mapper;
    private final CurrentUserContext currentUser;

    /**
     * 查询当前登录用户成长值概览。
     */
    @Override
    public GrowthSummaryVO getCurrentSummary() {
        return getSummary(currentUser.requireUserId());
    }

    /**
     * 查询当前登录用户徽章墙。
     */
    @Override
    public BadgeWallVO getCurrentBadges() {
        return getBadgeWall(currentUser.requireUserId());
    }

    /**
     * 分页查询当前登录用户成长值流水。
     *
     * <p>页码和分页大小在这里做兜底限制，避免前端传入异常参数导致大范围查询。</p>
     */
    @Override
    public PageResult<GrowthLogVO> getCurrentLogs(int page, int size) {
        long userId = currentUser.requireUserId();
        int normalizedPage = Math.max(1, page);
        int normalizedSize = Math.min(100, Math.max(1, size));
        List<GrowthLogVO> records = mapper.findLogs(userId, (normalizedPage - 1) * normalizedSize, normalizedSize)
                .stream().map(this::log).toList();
        return new PageResult<>(records, mapper.countLogs(userId), normalizedPage, normalizedSize);
    }

    /**
     * 查询指定用户成长值概览。
     *
     * <p>如果用户还没有成长账户，会先懒加载创建一个默认账户。</p>
     */
    @Override
    public GrowthSummaryVO getSummary(Long userId) {
        GrowthQueryDTO account = ensureAccount(userId);
        int points = account.getTotalPoints();
        Integer next = mapper.findNextLevelPoints(points);
        return new GrowthSummaryVO(points, account.getLevelCode(), next == null ? 0 : Math.max(0, next - points));
    }

    /**
     * 查询指定用户的徽章墙。
     */
    @Override
    public BadgeWallVO getBadgeWall(Long userId) {
        return new BadgeWallVO(
                mapper.findEarnedBadges(userId).stream().map(this::badge).toList(),
                mapper.findLockedBadges(userId).stream().map(this::badge).toList());
    }

    /**
     * 发放或扣减成长值。
     *
     * <p>完整流程：锁定账户 → 校验余额 → 写入幂等流水 → 重算等级 → 更新账户 → 尝试授予徽章。
     * 如果同一业务事件重复调用，直接返回 duplicate=true，避免重复发放。</p>
     */
    @Override
    @Transactional
    public GrowthGrantResult grant(Long userId, String bizType, String bizId, int points, String remark) {
        LocalDateTime now = LocalDateTime.now();
        GrowthQueryDTO account = ensureAccountForUpdate(userId);
        int oldPoints = account.getTotalPoints();
        int balance = oldPoints + points;
        if (balance < 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "成长值余额不足");
        }
        try {
            mapper.insertLog(SnowflakeIdGenerator.nextId(), userId, bizType, bizId, points, balance, remark, now);
        } catch (DuplicateKeyException e) {
            return new GrowthGrantResult(userId, points, oldPoints, account.getLevelCode(), true);
        }
        String level = mapper.findLevelCode(balance);
        if (level == null) {
            level = "LV1";
        }
        if (mapper.updateAccount(account.getId(), balance, level, account.getVersion(), now) == 0) {
            throw new BusinessException(ResultCode.INTERNAL_SERVER_ERROR, "成长账户并发更新失败");
        }
        int count = mapper.countEvents(userId, bizType);
        for (Long badgeId : mapper.findEligibleBadges(bizType, count)) {
            mapper.insertUserBadge(SnowflakeIdGenerator.nextId(), userId, badgeId, bizId, now);
        }
        return new GrowthGrantResult(userId, points, balance, level, false);
    }

    /**
     * 确保用户成长账户存在。
     *
     * <p>并发首次访问时，可能多个请求同时尝试创建账户；这里忽略唯一键冲突后重新查询即可。</p>
     */
    private GrowthQueryDTO ensureAccount(Long userId) {
        GrowthQueryDTO account = mapper.findAccount(userId);
        if (account != null) {
            return account;
        }
        try {
            mapper.insertAccount(SnowflakeIdGenerator.nextId(), userId, LocalDateTime.now());
        } catch (DuplicateKeyException ignored) {
            // 其他并发请求已经创建账户，重新查询即可。
        }
        return mapper.findAccount(userId);
    }

    /**
     * 确保用户成长账户存在，并返回带数据库行锁的账户记录。
     */
    private GrowthQueryDTO ensureAccountForUpdate(Long userId) {
        GrowthQueryDTO account = mapper.findAccountForUpdate(userId);
        if (account != null) {
            return account;
        }
        try {
            mapper.insertAccount(SnowflakeIdGenerator.nextId(), userId, LocalDateTime.now());
        } catch (DuplicateKeyException ignored) {
            // 账户已被并发请求创建，下面重新加锁查询。
        }
        return mapper.findAccountForUpdate(userId);
    }

    /**
     * 将数据库查询结果转换为成长值流水 VO。
     */
    private GrowthLogVO log(GrowthQueryDTO row) {
        return new GrowthLogVO(row.getId(), row.getBizType(), row.getBizId(), row.getPointDelta(),
                row.getBalanceAfter(), row.getRemark(), row.getCreatedAt());
    }

    /**
     * 将数据库查询结果转换为徽章展示 VO。
     */
    private BadgeVO badge(GrowthQueryDTO row) {
        return new BadgeVO(row.getBadgeId(), row.getBadgeCode(), row.getBadgeName(),
                row.getBadgeImageKey(), row.getAwardedAt());
    }
}
