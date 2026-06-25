package com.tongdao.verification.service.impl;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tongdao.common.exception.BusinessException;
import com.tongdao.common.result.ResultCode;
import com.tongdao.common.utils.SnowflakeIdGenerator;
import com.tongdao.verification.dto.ConfirmVerificationRequest;
import com.tongdao.verification.dto.CreateVerificationCodeRequest;
import com.tongdao.verification.dto.ParseVerificationRequest;
import com.tongdao.verification.dto.ReversalApplyRequest;
import com.tongdao.verification.dto.VerificationQueryRequest;
import com.tongdao.verification.entity.VerificationCode;
import com.tongdao.verification.entity.VerificationCompensationTask;
import com.tongdao.verification.entity.VerificationRecord;
import com.tongdao.verification.entity.VerificationReversalRequest;
import com.tongdao.verification.mapper.VerificationCodeMapper;
import com.tongdao.verification.mapper.VerificationCompensationTaskMapper;
import com.tongdao.verification.mapper.VerificationRecordMapper;
import com.tongdao.verification.mapper.VerificationReversalRequestMapper;
import com.tongdao.verification.service.VerificationService;
import com.tongdao.verification.vo.PageResult;
import com.tongdao.verification.vo.VerificationCodeVO;
import com.tongdao.verification.vo.VerificationParseVO;
import com.tongdao.verification.vo.VerificationRecordVO;
import com.tongdao.verification.vo.VerificationReversalVO;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class VerificationServiceImpl implements VerificationService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String ACTIVE = "ACTIVE";
    private static final String VERIFIED = "VERIFIED";
    private static final String EXPIRED = "EXPIRED";
    private static final String CANCELLED = "CANCELLED";
    private static final String SUCCESS = "SUCCESS";
    private static final String NONE = "NONE";
    private static final String APPLYING = "APPLYING";
    private static final String PENDING = "PENDING";
    private static final String ORDER = "ORDER";
    private static final String COUPON = "COUPON";

    private final VerificationCodeMapper codeMapper;
    private final VerificationRecordMapper recordMapper;
    private final VerificationReversalRequestMapper reversalMapper;
    private final VerificationCompensationTaskMapper compensationTaskMapper;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public VerificationCodeVO createCode(CreateVerificationCodeRequest request) {
        String bizType = normalizeBizType(request.bizType());
        validateBizRequest(bizType, request.orderId(), request.userCouponId());

        String idemKey = "verification:idem:create-code:%s".formatted(request.requestId());
        VerificationCodeVO cached = readJson(idemKey, VerificationCodeVO.class);
        if (cached != null) {
            return cached;
        }

        VerificationCode existed = codeMapper.findByBiz(bizType, request.bizId());
        if (existed != null) {
            VerificationCodeVO result = toCodeVO(existed);
            writeJson(idemKey, result, Duration.ofHours(24));
            return result;
        }

        LocalDateTime now = LocalDateTime.now();
        VerificationCode code = new VerificationCode();
        code.setId(SnowflakeIdGenerator.nextId());
        code.setVerificationCode(generateUniqueCode());
        code.setQrContent("TDVERIFY:" + code.getVerificationCode());
        code.setBizType(bizType);
        code.setBizId(request.bizId());
        code.setOrderId(request.orderId());
        code.setUserCouponId(request.userCouponId());
        code.setUserId(request.userId());
        code.setMerchantId(request.merchantId());
        code.setAmount(request.amount() == null ? BigDecimal.ZERO : request.amount());
        code.setCodeStatus(ACTIVE);
        code.setExpireAt(request.expireAt());
        code.setCreatedAt(now);
        code.setUpdatedAt(now);
        code.setDeleted(0);
        codeMapper.insert(code);

        VerificationCodeVO result = toCodeVO(code);
        writeJson("verification:code:%s".formatted(code.getVerificationCode()), result, ttlUntil(code.getExpireAt()));
        writeJson(idemKey, result, Duration.ofHours(24));
        return result;
    }

    @Override
    public VerificationParseVO parse(ParseVerificationRequest request) {
        String codeValue = normalizeCode(request.code());
        String key = "verification:parse:%s".formatted(codeValue);
        VerificationParseVO cached = readJson(key, VerificationParseVO.class);
        if (cached != null) {
            return cached;
        }

        VerificationCode code = codeMapper.findByCode(codeValue);
        VerificationParseVO result = toParseVO(code);
        writeJson(key, result, Duration.ofMinutes(2));
        return result;
    }

    @Override
    @Transactional
    public VerificationRecordVO confirm(ConfirmVerificationRequest request) {
        String codeValue = normalizeCode(request.verificationCode());
        String idemKey = "verification:idem:confirm:%s".formatted(request.requestId());
        VerificationRecordVO cached = readJson(idemKey, VerificationRecordVO.class);
        if (cached != null) {
            return cached;
        }

        String lockKey = "verification:lock:confirm:%s".formatted(codeValue);
        boolean locked = tryLock(lockKey, Duration.ofSeconds(30));
        if (!locked) {
            throw new BusinessException("核销处理中，请稍后重试");
        }

        try {
            VerificationCode code = requireCode(codeValue);
            VerificationRecord existed = recordMapper.findByCodeId(code.getId());
            if (existed != null) {
                VerificationRecordVO result = toRecordVO(existed);
                writeJson(idemKey, result, Duration.ofDays(7));
                return result;
            }

            validateCanConfirm(code, request.merchantId());
            LocalDateTime now = LocalDateTime.now();
            int updated = codeMapper.markVerified(code.getId(), request.merchantId(), now);
            if (updated == 0) {
                VerificationRecord concurrent = recordMapper.findByCodeId(code.getId());
                if (concurrent != null) {
                    VerificationRecordVO result = toRecordVO(concurrent);
                    writeJson(idemKey, result, Duration.ofDays(7));
                    return result;
                }
                throw new BusinessException("核销码状态已变化，请重新扫码");
            }

            VerificationRecord record = buildRecord(code, request, now);
            recordMapper.insert(record);
            addCrossModuleCompensation(record, request.requestId(), now);

            VerificationRecordVO result = toRecordVO(record);
            writeJson(idemKey, result, Duration.ofDays(7));
            deleteRedis("verification:code:%s".formatted(codeValue));
            deleteRedis("verification:parse:%s".formatted(codeValue));
            return result;
        } finally {
            deleteRedis(lockKey);
        }
    }

    @Override
    public PageResult<VerificationRecordVO> pageQuery(VerificationQueryRequest request) {
        int page = Math.max(request.page(), 1);
        int size = Math.min(Math.max(request.size(), 1), 100);
        int offset = (page - 1) * size;
        String bizType = StringUtils.hasText(request.bizType()) ? normalizeBizType(request.bizType()) : null;
        String status = StringUtils.hasText(request.verificationStatus())
                ? request.verificationStatus().trim().toUpperCase(Locale.ROOT)
                : null;
        var records = recordMapper.pageQuery(request.merchantId(), bizType, status, request.startTime(),
                        request.endTime(), offset, size)
                .stream()
                .map(this::toRecordVO)
                .toList();
        long total = recordMapper.countQuery(request.merchantId(), bizType, status, request.startTime(), request.endTime());
        return new PageResult<>(records, total, page, size);
    }

    @Override
    public VerificationRecordVO detail(Long verificationId, Long merchantId) {
        VerificationRecord record = requireRecord(verificationId);
        if (merchantId != null && !merchantId.equals(record.getMerchantId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权查看该核销记录");
        }
        return toRecordVO(record);
    }

    @Override
    @Transactional
    public VerificationReversalVO applyReversal(Long verificationId, ReversalApplyRequest request) {
        String idemKey = "verification:idem:reversal:%s".formatted(request.requestId());
        VerificationReversalVO cached = readJson(idemKey, VerificationReversalVO.class);
        if (cached != null) {
            return cached;
        }

        VerificationRecord record = requireRecord(verificationId);
        VerificationReversalRequest existed = reversalMapper.findLatestByVerificationId(verificationId);
        if (existed != null && PENDING.equals(existed.getAuditStatus())) {
            VerificationReversalVO result = toReversalVO(existed);
            writeJson(idemKey, result, Duration.ofHours(24));
            return result;
        }

        LocalDateTime now = LocalDateTime.now();
        VerificationReversalRequest reversal = new VerificationReversalRequest();
        reversal.setId(SnowflakeIdGenerator.nextId());
        reversal.setVerificationId(verificationId);
        reversal.setMerchantId(record.getMerchantId());
        reversal.setApplicantId(record.getOperatorId());
        reversal.setReason(request.reason().trim());
        reversal.setAuditStatus(PENDING);
        reversal.setCreatedAt(now);
        reversal.setUpdatedAt(now);
        reversal.setDeleted(0);
        reversalMapper.insert(reversal);
        recordMapper.updateReversalStatus(verificationId, APPLYING, now);

        VerificationReversalVO result = toReversalVO(reversal);
        writeJson(idemKey, result, Duration.ofHours(24));
        return result;
    }

    private VerificationRecord buildRecord(VerificationCode code, ConfirmVerificationRequest request, LocalDateTime now) {
        VerificationRecord record = new VerificationRecord();
        record.setId(SnowflakeIdGenerator.nextId());
        record.setVerificationCodeId(code.getId());
        record.setVerificationCode(code.getVerificationCode());
        record.setBizType(code.getBizType());
        record.setBizId(code.getBizId());
        record.setOrderId(code.getOrderId());
        record.setUserCouponId(code.getUserCouponId());
        record.setUserId(code.getUserId());
        record.setMerchantId(code.getMerchantId());
        record.setOperatorId(request.operatorId());
        record.setAmount(code.getAmount());
        record.setVerificationStatus(SUCCESS);
        record.setLocationName(trimToNull(request.locationName()));
        record.setLongitude(request.longitude());
        record.setLatitude(request.latitude());
        record.setVerifiedAt(now);
        record.setReversalStatus(NONE);
        record.setCreatedAt(now);
        record.setUpdatedAt(now);
        record.setDeleted(0);
        return record;
    }

    private void addCrossModuleCompensation(VerificationRecord record, String requestId, LocalDateTime now) {
        String target = ORDER.equals(record.getBizType()) ? "payment-module" : "coupon-module";
        String type = ORDER.equals(record.getBizType()) ? "PAYMENT_PROFIT_SHARING" : "COUPON_VERIFIED";
        String payload = """
                {"verificationId":%d,"bizType":"%s","bizId":%d,"orderId":%s,"userCouponId":%s}
                """.formatted(record.getId(), record.getBizType(), record.getBizId(),
                nullableNumber(record.getOrderId()), nullableNumber(record.getUserCouponId())).trim();

        VerificationCompensationTask task = new VerificationCompensationTask();
        task.setId(SnowflakeIdGenerator.nextId());
        task.setBizType(type);
        task.setBizId(String.valueOf(record.getBizId()));
        task.setIdempotentKey("verification:%s:%s".formatted(requestId, type));
        task.setTargetModule(target);
        task.setRequestPayload(payload);
        task.setTaskStatus(PENDING);
        task.setRetryCount(0);
        task.setNextRetryAt(now.plusMinutes(1));
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        compensationTaskMapper.insert(task);
    }

    private VerificationParseVO toParseVO(VerificationCode code) {
        if (code == null) {
            return new VerificationParseVO(null, null, null, null, null, null, null, null, false, "核销码不存在");
        }
        LocalDateTime now = LocalDateTime.now();
        if (ACTIVE.equals(code.getCodeStatus()) && !code.getExpireAt().isAfter(now)) {
            codeMapper.markExpired(code.getId(), now);
            code.setCodeStatus(EXPIRED);
        }
        String blockReason = blockReason(code, now);
        return new VerificationParseVO(code.getId(), code.getBizType(), code.getBizId(), code.getUserId(),
                code.getMerchantId(), code.getAmount(), code.getCodeStatus(), code.getExpireAt(),
                blockReason == null, blockReason);
    }

    private void validateCanConfirm(VerificationCode code, Long merchantId) {
        LocalDateTime now = LocalDateTime.now();
        if (!merchantId.equals(code.getMerchantId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "当前商家无权核销该码");
        }
        String reason = blockReason(code, now);
        if (reason != null) {
            throw new BusinessException(reason);
        }
    }

    private String blockReason(VerificationCode code, LocalDateTime now) {
        if (!ACTIVE.equals(code.getCodeStatus())) {
            if (VERIFIED.equals(code.getCodeStatus())) {
                return "核销码已核销";
            }
            if (EXPIRED.equals(code.getCodeStatus())) {
                return "核销码已过期";
            }
            if (CANCELLED.equals(code.getCodeStatus())) {
                return "核销码已取消";
            }
            return "核销码状态不可用";
        }
        if (code.getExpireAt() == null || !code.getExpireAt().isAfter(now)) {
            return "核销码已过期";
        }
        return null;
    }

    private VerificationCode requireCode(String verificationCode) {
        VerificationCode code = codeMapper.findByCode(verificationCode);
        if (code == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "核销码不存在");
        }
        return code;
    }

    private VerificationRecord requireRecord(Long verificationId) {
        VerificationRecord record = recordMapper.findById(verificationId);
        if (record == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "核销记录不存在");
        }
        return record;
    }

    private String generateUniqueCode() {
        for (int i = 0; i < 8; i++) {
            String code = "TDV" + randomToken(18);
            if (codeMapper.findByCode(code) == null) {
                return code;
            }
        }
        throw new BusinessException("核销码生成失败，请重试");
    }

    private String randomToken(int length) {
        String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            builder.append(alphabet.charAt(RANDOM.nextInt(alphabet.length())));
        }
        return builder.toString();
    }

    private String normalizeBizType(String bizType) {
        String value = bizType == null ? "" : bizType.trim().toUpperCase(Locale.ROOT);
        if (!ORDER.equals(value) && !COUPON.equals(value)) {
            throw new BusinessException("核销业务类型仅支持 ORDER 或 COUPON");
        }
        return value;
    }

    private void validateBizRequest(String bizType, Long orderId, Long userCouponId) {
        if (ORDER.equals(bizType) && orderId == null) {
            throw new BusinessException("订单核销必须传入 orderId");
        }
        if (COUPON.equals(bizType) && userCouponId == null) {
            throw new BusinessException("券核销必须传入 userCouponId");
        }
    }

    private String normalizeCode(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.startsWith("TDVERIFY:")) {
            return value.substring("TDVERIFY:".length());
        }
        return value;
    }

    private VerificationCodeVO toCodeVO(VerificationCode code) {
        return new VerificationCodeVO(code.getId(), code.getVerificationCode(), code.getQrContent(), code.getBizType(),
                code.getBizId(), code.getCodeStatus(), code.getExpireAt());
    }

    private VerificationRecordVO toRecordVO(VerificationRecord record) {
        return new VerificationRecordVO(record.getId(), record.getVerificationCodeId(), record.getVerificationCode(),
                record.getBizType(), record.getBizId(), record.getOrderId(), record.getUserCouponId(),
                record.getMerchantId(), record.getUserId(), record.getOperatorId(), record.getAmount(),
                record.getVerificationStatus(), record.getReversalStatus(), record.getLocationName(),
                record.getLongitude(), record.getLatitude(), record.getVerifiedAt());
    }

    private VerificationReversalVO toReversalVO(VerificationReversalRequest request) {
        return new VerificationReversalVO(request.getId(), request.getVerificationId(), request.getMerchantId(),
                request.getApplicantId(), request.getReason(), request.getAuditStatus(), request.getReviewerId(),
                request.getReviewedAt(), request.getRejectReason(), request.getCreatedAt());
    }

    private Duration ttlUntil(LocalDateTime expireAt) {
        Duration ttl = Duration.between(LocalDateTime.now(), expireAt);
        return ttl.isNegative() || ttl.isZero() ? Duration.ofSeconds(1) : ttl;
    }

    private boolean tryLock(String key, Duration ttl) {
        try {
            Boolean ok = redis.opsForValue().setIfAbsent(key, "1", ttl.toSeconds(), TimeUnit.SECONDS);
            return Boolean.TRUE.equals(ok);
        } catch (Exception ignored) {
            return true;
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
            // Redis 失败不影响 MySQL 事实写入。
        }
    }

    private void deleteRedis(String key) {
        try {
            redis.delete(key);
        } catch (Exception ignored) {
            // Redis 删除失败不影响主流程。
        }
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String nullableNumber(Long value) {
        return value == null ? "null" : String.valueOf(value);
    }
}
