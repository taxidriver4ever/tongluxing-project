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
 * <p>MVP 阶段先提供本地可运行的考核快照，后续可替换为更精细的规则引擎。</p>
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
        String idemKey = IDEM_RECALCULATE_KEY.formatted(request.requestId().trim());
        MerchantAssessmentResultVO cached = readJson(idemKey, MerchantAssessmentResultVO.class);
        if (cached != null) {
            return cached;
        }
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
        String lockKey = MONTHLY_LOCK_KEY.formatted(period);
        Boolean locked = redis.opsForValue().setIfAbsent(lockKey, request.requestId(), Duration.ofHours(2));
        if (!Boolean.TRUE.equals(locked)) {
            throw new BusinessException(409, "该周期考核任务正在执行");
        }
        int success = 0;
        var merchantIds = assessmentMapper.listActiveMerchantIds();
        for (Long merchantId : merchantIds) {
            calculateAndSave(merchantId, period, "monthly:%s:%d".formatted(period, merchantId));
            success++;
        }
        return new MonthlyRunResultVO(period, merchantIds.size(), success);
    }

    @Override
    public MerchantAssessmentSnapshotVO snapshot(Long merchantId) {
        String key = CACHE_SNAPSHOT_KEY.formatted(merchantId);
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
        LocalDateTime now = LocalDateTime.now();
        AssessmentQueryDTO latest = assessmentMapper.findLatestScore(merchantId);
        BigDecimal baseScore = latest == null ? BigDecimal.ZERO : latest.getTotalScore();
        BigDecimal adjustedScore = baseScore.add(request.scoreDelta()).max(BigDecimal.ZERO);
        AssessmentQueryDTO mapping = levelMapping(adjustedScore);
        String period = latest == null ? YearMonth.now().toString() : latest.getPeriod();

        // 人工调整必须单独落明细，方便运营后台审计和后续追责。
        assessmentMapper.insertManualAdjustment(SnowflakeIdGenerator.nextId(), merchantId, request.scoreDelta(),
                request.reason().trim(), request.operatorId(), request.requestId().trim(), now);
        assessmentMapper.upsertScore(SnowflakeIdGenerator.nextId(), merchantId, period, adjustedScore,
                mapping.getMerchantLevel(), mapping.getCommissionRate(), mapping.getRankWeight(),
                mapping.getExclusionRadiusKm(), request.requestId().trim(), now);
        assessmentMapper.updateMerchantAssessment(merchantId, adjustedScore, mapping.getMerchantLevel(),
                mapping.getCommissionRate(), mapping.getRankWeight(), mapping.getExclusionRadiusKm(), now);
        deleteCache(merchantId);
        return toResult(merchantId, assessmentMapper.findLatestScore(merchantId), requireMerchant(merchantId));
    }

    private MerchantAssessmentResultVO calculateAndSave(Long merchantId, String period, String requestId) {
        AssessmentQueryDTO merchant = requireMerchant(merchantId);
        DateRange range = toRange(period);
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

    private AssessmentQueryDTO requireMerchant(Long merchantId) {
        AssessmentQueryDTO merchant = assessmentMapper.findMerchantById(merchantId);
        if (merchant == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "商家不存在");
        }
        return merchant;
    }

    private BigDecimal calculateScore(int paid, int completed, int refunded, int verified) {
        int rawScore = 50 + paid * 2 + completed * 5 + verified * 3 - refunded * 8;
        int limited = Math.max(0, Math.min(100, rawScore));
        return new BigDecimal(limited).setScale(2);
    }

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
                score == null ? null : score.getPeriod(),
                currentScore,
                level,
                commissionRate,
                rankWeight,
                exclusionRadius,
                next == null ? null : next.getMerchantLevel(),
                needScore.max(BigDecimal.ZERO),
                score == null ? null : score.getCalculatedAt());
    }

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
