package com.tongluxing.assessment.service.impl;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tongluxing.assessment.dto.AssessmentQueryDTO;
import com.tongluxing.assessment.dto.ManualAssessmentAdjustmentRequest;
import com.tongluxing.assessment.dto.MonthlyAssessmentRunRequest;
import com.tongluxing.assessment.dto.RecalculateMerchantAssessmentRequest;
import com.tongluxing.assessment.mapper.AssessmentMapper;
import com.tongluxing.assessment.service.AssessmentService;
import com.tongluxing.assessment.vo.MerchantAssessmentResultVO;
import com.tongluxing.assessment.vo.MerchantAssessmentSnapshotVO;
import com.tongluxing.assessment.vo.MonthlyRunResultVO;
import com.tongluxing.common.exception.BusinessException;
import com.tongluxing.common.result.ResultCode;
import com.tongluxing.common.utils.SnowflakeIdGenerator;
import com.tongluxing.user.support.CurrentUserContext;

import lombok.RequiredArgsConstructor;

/**
 * 商家考核服务实现。
 *
 * <p>MVP 阶段采用“指标聚合 + 等级映射”的本地规则：从订单、退款、核销记录中汇总月度指标，
 * 计算商家分数，再同步写入商家表中的等级、佣金率、排名权重和排他半径快照。
 * 这些快照会被支付分账、商家展示和推荐排序复用。</p>
 *
 * <p>当前实现以 MySQL 为事实源，Redis 仅承担幂等结果缓存、快照缓存和月度任务锁。
 * 因此 Redis 异常不会阻断考核结果落库，只会影响短期缓存命中率。</p>
 */
@Service
@RequiredArgsConstructor
public class AssessmentServiceImpl implements AssessmentService {

    private static final String CACHE_SNAPSHOT_KEY = "assessment:cache:snapshot:%d";
    private static final String CACHE_MERCHANT_KEY = "assessment:cache:merchant:%d";
    private static final String IDEM_RECALCULATE_KEY = "assessment:idem:recalculate:%s";
    private static final String MONTHLY_LOCK_KEY = "assessment:lock:monthly:%s";
    private static final BigDecimal DEFAULT_COMMISSION_RATE = new BigDecimal("0.0800");
    private static final BigDecimal DEFAULT_RANK_WEIGHT = new BigDecimal("1.0000");
    private static final BigDecimal DEFAULT_EXCLUSION_RADIUS = BigDecimal.ZERO;

    private final CurrentUserContext currentUserContext;
    private final AssessmentMapper assessmentMapper;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    @Override
    public MerchantAssessmentResultVO currentMerchantAssessment() {
        // 商家端查看自己的考核结果时，以当前登录用户反查商家，避免前端传入越权 merchantId。
        Long userId = currentUserContext.requireUserId();
        AssessmentQueryDTO merchant = assessmentMapper.findMerchantByUserId(userId);
        if (merchant == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "当前账号尚未入驻商家");
        }
        return toResult(merchant.getMerchantId(), assessmentMapper.findLatestScore(merchant.getMerchantId()), merchant);
    }

    @Override
    @Transactional
    public MerchantAssessmentResultVO recalculate(Long merchantId, RecalculateMerchantAssessmentRequest request) {
        // requestId 同时用于 Redis 短缓存和 MySQL 历史查询，保证重复重算请求返回同一份结果。
        String idemKey = IDEM_RECALCULATE_KEY.formatted(request.requestId().trim());
        MerchantAssessmentResultVO cached = readJson(idemKey, MerchantAssessmentResultVO.class);
        if (cached != null) {
            return cached;
        }
        // Redis 过期后仍通过 request_id 回源 MySQL，避免运营重复点击导致重复计算。
        AssessmentQueryDTO existed = assessmentMapper.findScoreByRequestId(request.requestId().trim());
        if (existed != null) {
            MerchantAssessmentResultVO result = toResult(merchantId, existed, requireMerchant(merchantId));
            writeJson(idemKey, result, Duration.ofHours(24));
            return result;
        }

        MerchantAssessmentResultVO result = calculateAndSave(merchantId, request.period(), request.requestId().trim());
        writeJson(idemKey, result, Duration.ofHours(24));
        return result;
    }

    @Override
    public MonthlyRunResultVO runMonthly(MonthlyAssessmentRunRequest request) {
        String period = request.period().trim();
        // 月度批处理同一周期只允许一个执行者进入，避免多实例重复扫描商家。
        String lockKey = MONTHLY_LOCK_KEY.formatted(period);
        Boolean locked = redis.opsForValue().setIfAbsent(lockKey, request.requestId(), Duration.ofHours(2));
        if (!Boolean.TRUE.equals(locked)) {
            throw new BusinessException(409, "该周期考核任务正在执行");
        }
        int success = 0;
        var merchantIds = assessmentMapper.listActiveMerchantIds();
        for (Long merchantId : merchantIds) {
            // 每个商家的 requestId 独立，便于某个商家失败后单独补偿。
            calculateAndSave(merchantId, period, "monthly:%s:%d".formatted(period, merchantId));
            success++;
        }
        return new MonthlyRunResultVO(period, merchantIds.size(), success);
    }

    @Override
    public MerchantAssessmentSnapshotVO snapshot(Long merchantId) {
        String key = CACHE_SNAPSHOT_KEY.formatted(merchantId);
        // 快照接口会被交易链路频繁调用，先读 Redis 可以减少商家表热点读取。
        MerchantAssessmentSnapshotVO cached = readJson(key, MerchantAssessmentSnapshotVO.class);
        if (cached != null) {
            return cached;
        }
        AssessmentQueryDTO merchant = requireMerchant(merchantId);
        MerchantAssessmentSnapshotVO result = new MerchantAssessmentSnapshotVO(
                merchantId,
                merchant.getMerchantLevel(),
                merchant.getScore(),
                merchant.getCommissionRate(),
                merchant.getRankWeight(),
                merchant.getExclusionRadiusKm());
        writeJson(key, result, Duration.ofMinutes(30));
        return result;
    }

    /**
     * 人工调整商家考核分，调整后立即刷新商家等级快照。
     */
    @Override
    @Transactional
    public MerchantAssessmentResultVO manualAdjust(Long merchantId, ManualAssessmentAdjustmentRequest request) {
        if (assessmentMapper.countManualAdjustmentByRequestId(request.requestId().trim()) > 0) {
            return toResult(merchantId, assessmentMapper.findLatestScore(merchantId), requireMerchant(merchantId));
        }
        LocalDateTime now = LocalDateTime.now();
        AssessmentQueryDTO latest = assessmentMapper.findLatestScore(merchantId);
        BigDecimal baseScore = latest == null ? BigDecimal.ZERO : latest.getTotalScore();
        BigDecimal adjustedScore = baseScore.add(request.scoreDelta())
                .max(BigDecimal.ZERO).min(new BigDecimal("100.00"));
        AssessmentQueryDTO mapping = levelMapping(adjustedScore);
        String period = latest == null ? YearMonth.now().toString() : latest.getAssessmentPeriod();

        // 人工调整必须单独落明细，方便运营后台审计和后续追责。
        assessmentMapper.insertManualAdjustment(SnowflakeIdGenerator.nextId(), merchantId, request.scoreDelta(),
                request.reason().trim(), currentUserContext.requireUserId(), request.requestId().trim(), now);
        assessmentMapper.upsertScore(SnowflakeIdGenerator.nextId(), merchantId, period, adjustedScore,
                mapping.getMerchantLevel(), mapping.getCommissionRate(), mapping.getRankWeight(),
                mapping.getExclusionRadiusKm(), request.requestId().trim(), now);
        assessmentMapper.updateMerchantAssessment(merchantId, adjustedScore, mapping.getMerchantLevel(),
                mapping.getCommissionRate(), mapping.getRankWeight(), mapping.getExclusionRadiusKm(), now);
        deleteCache(merchantId);
        return toResult(merchantId, assessmentMapper.findLatestScore(merchantId), requireMerchant(merchantId));
    }

    /**
     * 汇总指定商家的月度指标并保存结果。
     *
     * <p>保存时会同时更新考核分表和商家资料快照，确保支付分账、推荐排序读取到的是最新等级配置。</p>
     */
    private MerchantAssessmentResultVO calculateAndSave(Long merchantId, String period, String requestId) {
        AssessmentQueryDTO merchant = requireMerchant(merchantId);
        DateRange range = toRange(period);
        // 当前评分模型只使用可以从本地交易闭环稳定获得的指标，避免依赖第三方实时数据。
        int paid = assessmentMapper.countPaidOrders(merchantId, range.startAt(), range.endAt());
        int completed = assessmentMapper.countCompletedOrders(merchantId, range.startAt(), range.endAt());
        int refunded = assessmentMapper.countRefundedOrders(merchantId, range.startAt(), range.endAt());
        int verified = assessmentMapper.countVerifiedRecords(merchantId, range.startAt(), range.endAt());
        BigDecimal score = calculateScore(paid, completed, refunded, verified);
        AssessmentQueryDTO level = levelMapping(score);
        LocalDateTime now = LocalDateTime.now();
        assessmentMapper.upsertScore(
                SnowflakeIdGenerator.nextId(),
                merchantId,
                period,
                score,
                level.getMerchantLevel(),
                level.getCommissionRate(),
                level.getRankWeight(),
                level.getExclusionRadiusKm(),
                requestId,
                now);
        assessmentMapper.updateMerchantAssessment(
                merchantId,
                score,
                level.getMerchantLevel(),
                level.getCommissionRate(),
                level.getRankWeight(),
                level.getExclusionRadiusKm(),
                now);
        deleteCache(merchantId);
        AssessmentQueryDTO saved = assessmentMapper.findLatestScore(merchantId);
        return toResult(merchantId, saved, merchant);
    }

    /**
     * 查询有效商家，不存在时抛出统一业务异常。
     */
    private AssessmentQueryDTO requireMerchant(Long merchantId) {
        AssessmentQueryDTO merchant = assessmentMapper.findMerchantById(merchantId);
        if (merchant == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "商家不存在");
        }
        return merchant;
    }

    /**
     * MVP 评分模型：基础分 50，正向指标加分，退款扣分，并限制在 0 到 100 分之间。
     */
    private BigDecimal calculateScore(int paid, int completed, int refunded, int verified) {
        int rawScore = 50 + paid * 2 + completed * 5 + verified * 3 - refunded * 8;
        int limited = Math.max(0, Math.min(100, rawScore));
        return new BigDecimal(limited).setScale(2);
    }

    /**
     * 根据分数匹配等级配置；配置表缺失时使用保守默认值兜底，避免交易链路读取空佣金率。
     */
    private AssessmentQueryDTO levelMapping(BigDecimal score) {
        AssessmentQueryDTO mapping = assessmentMapper.findLevelMapping(score);
        if (mapping != null) {
            return mapping;
        }
        AssessmentQueryDTO fallback = new AssessmentQueryDTO();
        fallback.setMerchantLevel(defaultLevel(score));
        fallback.setCommissionRate(DEFAULT_COMMISSION_RATE);
        fallback.setRankWeight(DEFAULT_RANK_WEIGHT);
        fallback.setExclusionRadiusKm(DEFAULT_EXCLUSION_RADIUS);
        return fallback;
    }

    private String defaultLevel(BigDecimal score) {
        if (score.compareTo(new BigDecimal("95")) >= 0) {
            return "L5";
        }
        if (score.compareTo(new BigDecimal("85")) >= 0) {
            return "L4";
        }
        if (score.compareTo(new BigDecimal("75")) >= 0) {
            return "L3";
        }
        if (score.compareTo(new BigDecimal("60")) >= 0) {
            return "L2";
        }
        return "L1";
    }

    /**
     * 将分数记录和商家快照合并为前端展示模型。
     */
    private MerchantAssessmentResultVO toResult(Long merchantId, AssessmentQueryDTO score, AssessmentQueryDTO merchant) {
        BigDecimal currentScore = score == null ? merchant.getScore() : score.getTotalScore();
        String level = score == null ? merchant.getMerchantLevel() : score.getMerchantLevel();
        BigDecimal commissionRate = score == null ? merchant.getCommissionRate() : score.getCommissionRate();
        BigDecimal rankWeight = score == null ? merchant.getRankWeight() : score.getRankWeight();
        BigDecimal exclusionRadius = score == null ? merchant.getExclusionRadiusKm() : score.getExclusionRadiusKm();
        AssessmentQueryDTO next = assessmentMapper.findNextLevel(currentScore);
        BigDecimal needScore = next == null ? BigDecimal.ZERO : next.getMinScore().subtract(currentScore);
        return new MerchantAssessmentResultVO(
                merchantId,
                score == null ? null : score.getAssessmentPeriod(),
                currentScore,
                level,
                commissionRate,
                rankWeight,
                exclusionRadius,
                next == null ? null : next.getMerchantLevel(),
                needScore.max(BigDecimal.ZERO),
                score == null ? null : score.getCalculatedAt());
    }

    /**
     * 将 yyyy-MM 周期转换为左闭右开的自然月时间区间，避免跨月边界重复统计。
     */
    private DateRange toRange(String period) {
        YearMonth yearMonth = YearMonth.parse(period);
        LocalDate start = yearMonth.atDay(1);
        return new DateRange(start.atStartOfDay(), yearMonth.plusMonths(1).atDay(1).atStartOfDay());
    }

    private void deleteCache(Long merchantId) {
        try {
            redis.delete(CACHE_MERCHANT_KEY.formatted(merchantId));
            redis.delete(CACHE_SNAPSHOT_KEY.formatted(merchantId));
        } catch (Exception ignored) {
            // 缓存失败不影响考核结果落库。
        }
    }

    private <T> T readJson(String key, Class<T> type) {
        try {
            String value = redis.opsForValue().get(key);
            return value == null ? null : objectMapper.readValue(value, type);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void writeJson(String key, Object value, Duration ttl) {
        try {
            redis.opsForValue().set(key, objectMapper.writeValueAsString(value), ttl);
        } catch (Exception ignored) {
            // Redis 缓存失败时 MySQL 仍是事实源。
        }
    }

    private record DateRange(LocalDateTime startAt, LocalDateTime endAt) {
    }
}
