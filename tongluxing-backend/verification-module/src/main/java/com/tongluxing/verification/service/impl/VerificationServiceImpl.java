package com.tongluxing.verification.service.impl;

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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tongluxing.common.exception.BusinessException;
import com.tongluxing.common.result.ResultCode;
import com.tongluxing.common.utils.SnowflakeIdGenerator;
import com.tongluxing.verification.dto.ConfirmVerificationRequest;
import com.tongluxing.verification.dto.CreateVerificationCodeRequest;
import com.tongluxing.verification.dto.ParseVerificationRequest;
import com.tongluxing.verification.dto.ReversalApplyRequest;
import com.tongluxing.verification.dto.VerificationQueryRequest;
import com.tongluxing.verification.entity.VerificationCode;
import com.tongluxing.verification.entity.VerificationCompensationTask;
import com.tongluxing.verification.entity.VerificationRecord;
import com.tongluxing.verification.entity.VerificationReversalRequest;
import com.tongluxing.verification.mapper.VerificationCodeMapper;
import com.tongluxing.verification.mapper.VerificationCompensationTaskMapper;
import com.tongluxing.verification.mapper.VerificationRecordMapper;
import com.tongluxing.verification.integration.VerificationPaymentPort;
import com.tongluxing.verification.mapper.VerificationReversalRequestMapper;
import com.tongluxing.verification.service.VerificationService;
import com.tongluxing.verification.vo.PageResult;
import com.tongluxing.verification.vo.VerificationCodeVO;
import com.tongluxing.verification.vo.VerificationParseVO;
import com.tongluxing.verification.vo.VerificationRecordVO;
import com.tongluxing.verification.vo.VerificationReversalVO;

import lombok.RequiredArgsConstructor;

/**
 * 券核销模块业务服务实现，负责核销码生命周期、核销幂等、冲正申请和跨模块补偿任务。
 */
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
    private final VerificationPaymentPort paymentPort;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    /**
     * 创建核销码；通过 requestId 做幂等，业务对象已有核销码时直接复用。
     */
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

    /**
     * 解析核销码并短期缓存解析结果，降低频繁扫码对数据库的压力。
     */
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

    /**
     * 确认核销；通过 requestId 幂等和 Redis 短锁避免同一码并发重复核销。
     */
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
            // 核销成功后写入补偿任务，由后续任务处理分账或券状态同步。
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

    /**
     * 分页查询核销记录，统一规范分页参数和查询状态格式。
     */
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

    /**
     * 查询核销详情，并可按商家 ID 做访问控制。
     */
    @Override
    public VerificationRecordVO detail(Long verificationId, Long merchantId) {
        VerificationRecord record = requireRecord(verificationId);
        if (merchantId != null && !merchantId.equals(record.getMerchantId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权查看该核销记录");
        }
        return toRecordVO(record);
    }

    /**
     * 提交冲正申请；同一核销记录已有待审核冲正时直接返回已有申请。
     */
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

    /**
     * 消费核销补偿任务。未接入第三方时仍会推进本地分账/券同步状态。
     */
    @Override
    @Transactional
    public int processCompensationTasks(int limit) {
        LocalDateTime now = LocalDateTime.now();
        int processed = 0;
        for (VerificationCompensationTask task : compensationTaskMapper.listDueTasks(now, Math.min(Math.max(limit, 1), 100))) {
            try {
                if ("PAYMENT_PROFIT_SHARING".equals(task.getBizType())) {
                    JsonNode payload = objectMapper.readTree(task.getRequestPayload());
                    Long orderId = payload.path("orderId").isNull() ? null : payload.path("orderId").asLong();
                    if (orderId == null || orderId == 0L) {
                        throw new BusinessException("核销补偿任务缺少订单 ID");
                    }
                    Long verificationId = payload.path("verificationId").asLong(task.getId());
                    Long merchantId = Long.valueOf(task.getBizId());
                    paymentPort.shareAfterVerification(orderId, merchantId, verificationId,
                            "verification-task:" + task.getId());
                }
                compensationTaskMapper.markSuccess(task.getId(), now);
                processed++;
            } catch (Exception ex) {
                compensationTaskMapper.markFailed(task.getId(), ex.getMessage(), now.plusMinutes(5), now);
            }
        }
        return processed;
    }

    /**
     * 基于核销码和确认请求构造核销记录实体。
     */
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

    /**
     * 创建跨模块补偿任务：订单核销走支付分账，券核销走优惠券核销同步。
     */
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

    /**
     * 将核销码转换为扫码解析结果，并在过期时同步标记为 EXPIRED。
     */
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

    /**
     * 校验商家权限和核销码状态是否允许确认核销。
     */
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

    /**
     * 返回阻断核销的业务原因；为空表示当前可核销。
     */
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

    /**
     * 查询核销码，不存在时抛出统一业务异常。
     */
    private VerificationCode requireCode(String verificationCode) {
        VerificationCode code = codeMapper.findByCode(verificationCode);
        if (code == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "核销码不存在");
        }
        return code;
    }

    /**
     * 查询核销记录，不存在时抛出统一业务异常。
     */
    private VerificationRecord requireRecord(Long verificationId) {
        VerificationRecord record = recordMapper.findById(verificationId);
        if (record == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "核销记录不存在");
        }
        return record;
    }

    /**
     * 生成不重复的核销码，最多尝试 8 次。
     */
    private String generateUniqueCode() {
        for (int i = 0; i < 8; i++) {
            String code = "TDV" + randomToken(18);
            if (codeMapper.findByCode(code) == null) {
                return code;
            }
        }
        throw new BusinessException("核销码生成失败，请重试");
    }

    /**
     * 生成排除易混淆字符的随机码片段。
     */
    private String randomToken(int length) {
        String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            builder.append(alphabet.charAt(RANDOM.nextInt(alphabet.length())));
        }
        return builder.toString();
    }

    /**
     * 规范化并校验核销业务类型。
     */
    private String normalizeBizType(String bizType) {
        String value = bizType == null ? "" : bizType.trim().toUpperCase(Locale.ROOT);
        if (!ORDER.equals(value) && !COUPON.equals(value)) {
            throw new BusinessException("核销业务类型仅支持 ORDER 或 COUPON");
        }
        return value;
    }

    /**
     * 校验不同业务类型所需的业务字段。
     */
    private void validateBizRequest(String bizType, Long orderId, Long userCouponId) {
        if (ORDER.equals(bizType) && orderId == null) {
            throw new BusinessException("订单核销必须传入 orderId");
        }
        if (COUPON.equals(bizType) && userCouponId == null) {
            throw new BusinessException("券核销必须传入 userCouponId");
        }
    }

    /**
     * 兼容二维码内容前缀和纯核销码输入。
     */
    private String normalizeCode(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.startsWith("TDVERIFY:")) {
            return value.substring("TDVERIFY:".length());
        }
        return value;
    }

    /**
     * 将核销码实体转换为接口返回对象。
     */
    private VerificationCodeVO toCodeVO(VerificationCode code) {
        return new VerificationCodeVO(code.getId(), code.getVerificationCode(), code.getQrContent(), code.getBizType(),
                code.getBizId(), code.getCodeStatus(), code.getExpireAt());
    }

    /**
     * 将核销记录实体转换为接口返回对象。
     */
    private VerificationRecordVO toRecordVO(VerificationRecord record) {
        return new VerificationRecordVO(record.getId(), record.getVerificationCodeId(), record.getVerificationCode(),
                record.getBizType(), record.getBizId(), record.getOrderId(), record.getUserCouponId(),
                record.getMerchantId(), record.getUserId(), record.getOperatorId(), record.getAmount(),
                record.getVerificationStatus(), record.getReversalStatus(), record.getLocationName(),
                record.getLongitude(), record.getLatitude(), record.getVerifiedAt());
    }

    /**
     * 将冲正申请实体转换为接口返回对象。
     */
    private VerificationReversalVO toReversalVO(VerificationReversalRequest request) {
        return new VerificationReversalVO(request.getId(), request.getVerificationId(), request.getMerchantId(),
                request.getApplicantId(), request.getReason(), request.getAuditStatus(), request.getReviewerId(),
                request.getReviewedAt(), request.getRejectReason(), request.getCreatedAt());
    }

    /**
     * 根据过期时间计算 Redis 缓存 TTL。
     */
    private Duration ttlUntil(LocalDateTime expireAt) {
        Duration ttl = Duration.between(LocalDateTime.now(), expireAt);
        return ttl.isNegative() || ttl.isZero() ? Duration.ofSeconds(1) : ttl;
    }

    /**
     * 尝试获取 Redis 短锁；Redis 异常时降级放行，避免缓存故障阻塞主流程。
     */
    private boolean tryLock(String key, Duration ttl) {
        try {
            Boolean ok = redis.opsForValue().setIfAbsent(key, "1", ttl.toSeconds(), TimeUnit.SECONDS);
            return Boolean.TRUE.equals(ok);
        } catch (Exception ignored) {
            return true;
        }
    }

    /**
     * 从 Redis 读取 JSON 缓存；读取失败时降级为空。
     */
    private <T> T readJson(String key, Class<T> type) {
        try {
            String value = redis.opsForValue().get(key);
            return value == null ? null : objectMapper.readValue(value, type);
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * 写入 Redis JSON 缓存；写入失败不影响 MySQL 事实写入。
     */
    private void writeJson(String key, Object value, Duration ttl) {
        try {
            redis.opsForValue().set(key, objectMapper.writeValueAsString(value), ttl);
        } catch (Exception ignored) {
            // Redis 失败不影响 MySQL 事实写入。
        }
    }

    /**
     * 删除 Redis 缓存或锁；删除失败不影响主流程。
     */
    private void deleteRedis(String key) {
        try {
            redis.delete(key);
        } catch (Exception ignored) {
            // Redis 删除失败不影响主流程。
        }
    }

    /**
     * 清理文本字段，空字符串转为 null。
     */
    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    /**
     * 将可空 Long 转换为 JSON 数字或 null 字面量。
     */
    private String nullableNumber(Long value) {
        return value == null ? "null" : String.valueOf(value);
    }
}
