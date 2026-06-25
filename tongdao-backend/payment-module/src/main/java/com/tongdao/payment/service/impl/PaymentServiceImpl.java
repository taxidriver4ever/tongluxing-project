package com.tongdao.payment.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tongdao.common.exception.BusinessException;
import com.tongdao.common.result.ResultCode;
import com.tongdao.common.utils.SnowflakeIdGenerator;
import com.tongdao.payment.dto.JsapiPaymentRequest;
import com.tongdao.payment.dto.PaymentCallbackRequest;
import com.tongdao.payment.dto.ProfitSharingRequest;
import com.tongdao.payment.dto.RefundApplyRequest;
import com.tongdao.payment.dto.RefundCallbackRequest;
import com.tongdao.payment.entity.PaymentProfitSharingRecord;
import com.tongdao.payment.entity.PaymentRecord;
import com.tongdao.payment.entity.PaymentRefundRecord;
import com.tongdao.payment.integration.PaymentGroupbuyPort;
import com.tongdao.payment.integration.PaymentOrderPort;
import com.tongdao.payment.integration.PaymentOrderPort.PaymentOrderDTO;
import com.tongdao.payment.mapper.PaymentProfitSharingRecordMapper;
import com.tongdao.payment.mapper.PaymentRecordMapper;
import com.tongdao.payment.mapper.PaymentRefundRecordMapper;
import com.tongdao.payment.service.PaymentService;
import com.tongdao.payment.vo.JsapiPayParamsVO;
import com.tongdao.payment.vo.ProfitSharingVO;
import com.tongdao.payment.vo.RefundVO;
import com.tongdao.user.support.CurrentUserContext;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {
    private static final BigDecimal DEFAULT_COMMISSION_RATE = new BigDecimal("0.0800");

    private final CurrentUserContext currentUserContext;
    private final PaymentOrderPort orderPort;
    private final PaymentGroupbuyPort groupbuyPort;
    private final PaymentRecordMapper paymentRecordMapper;
    private final PaymentRefundRecordMapper refundRecordMapper;
    private final PaymentProfitSharingRecordMapper sharingRecordMapper;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public JsapiPayParamsVO createJsapiPayment(JsapiPaymentRequest request) {
        PaymentOrderDTO order = orderPort.getOrder(request.orderId());
        if (!"WAIT_PAY".equals(order.paymentStatus())) {
            throw new BusinessException("订单不是待支付状态");
        }
        LocalDateTime now = LocalDateTime.now();
        String prepayId = "mock_prepay_" + SnowflakeIdGenerator.nextIdString();
        PaymentRecord record = new PaymentRecord();
        record.setId(SnowflakeIdGenerator.nextId());
        record.setOrderId(order.orderId());
        record.setOrderNo(order.orderNo());
        record.setPaymentNo("PY" + SnowflakeIdGenerator.nextIdString());
        record.setWxPrepayId(prepayId);
        record.setPayChannel("WECHAT_JSAPI");
        record.setPayAmount(order.payableAmount());
        record.setPaymentStatus("PAYING");
        record.setCreatedAt(now);
        record.setUpdatedAt(now);
        record.setDeleted(0);
        paymentRecordMapper.insert(record);

        JsapiPayParamsVO params = new JsapiPayParamsVO("mock-app-id", String.valueOf(System.currentTimeMillis() / 1000),
                SnowflakeIdGenerator.nextIdString(), "prepay_id=" + prepayId, "RSA", "mock-pay-sign");
        writeJson("payment:session:%d".formatted(order.orderId()), params, Duration.ofMinutes(30));
        return params;
    }

    @Override
    @Transactional
    public void handlePaymentCallback(PaymentCallbackRequest request) {
        String idemKey = "payment:idem:callback:%s".formatted(request.transactionId());
        if (Boolean.TRUE.equals(redis.hasKey(idemKey))) {
            return;
        }
        LocalDateTime paidAt = request.paidAt() == null ? LocalDateTime.now() : request.paidAt();
        PaymentRecord latest = paymentRecordMapper.findLatestByOrderId(request.orderId());
        if (latest == null) {
            latest = new PaymentRecord();
            latest.setId(SnowflakeIdGenerator.nextId());
            latest.setOrderId(request.orderId());
            latest.setOrderNo("");
            latest.setPaymentNo("PY" + SnowflakeIdGenerator.nextIdString());
            latest.setPayChannel("WECHAT_JSAPI");
            latest.setPayAmount(request.payAmount());
            latest.setPaymentStatus("PAYING");
            latest.setCreatedAt(paidAt);
            latest.setUpdatedAt(paidAt);
            latest.setDeleted(0);
            paymentRecordMapper.insert(latest);
        }
        paymentRecordMapper.markSuccess(request.orderId(), request.transactionId(), request.rawPayload(), paidAt);
        PaymentOrderDTO order = orderPort.markPaid(request.orderId(), paidAt);
        if (order.activityId() != null) {
            groupbuyPort.addPaidParticipant(order.activityId(), order.orderId(), order.userId(), paidAt,
                    "payment-callback:" + request.transactionId());
        }
        writeJson(idemKey, "SUCCESS", Duration.ofDays(7));
    }

    @Override
    @Transactional
    public RefundVO applyRefund(RefundApplyRequest request) {
        String idemKey = "payment:idem:refund:%s".formatted(request.requestId());
        RefundVO cached = readJson(idemKey, RefundVO.class);
        if (cached != null) {
            return cached;
        }
        Long userId = currentUserContext.requireUserId();
        PaymentOrderDTO order = orderPort.getOrder(request.orderId());
        if (!"SUCCESS".equals(order.paymentStatus())) {
            throw new BusinessException("订单未支付，不能退款");
        }
        if ("VERIFIED".equals(order.verificationStatus()) || "SUCCESS".equals(order.profitSharingStatus())) {
            throw new BusinessException("已核销或已分账订单不能普通退款");
        }
        LocalDateTime now = LocalDateTime.now();
        PaymentRefundRecord record = new PaymentRefundRecord();
        record.setId(SnowflakeIdGenerator.nextId());
        record.setOrderId(order.orderId());
        record.setRefundNo("RF" + SnowflakeIdGenerator.nextIdString());
        record.setUserId(userId);
        record.setRefundAmount(order.paidAmount());
        record.setRefundReason(request.reason().trim());
        record.setRefundType("USER_APPLY");
        record.setRefundStatus("REFUNDING");
        record.setAuditStatus("APPROVED");
        record.setRequestedAt(now);
        record.setCreatedAt(now);
        record.setUpdatedAt(now);
        record.setDeleted(0);
        refundRecordMapper.insert(record);
        orderPort.markRefunding(order.orderId());
        RefundVO result = toRefundVO(record);
        writeJson(idemKey, result, Duration.ofHours(24));
        return result;
    }

    @Override
    public RefundVO refundDetail(Long refundId) {
        PaymentRefundRecord record = refundRecordMapper.findById(refundId);
        if (record == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "退款记录不存在");
        }
        return toRefundVO(record);
    }

    @Override
    @Transactional
    public void handleRefundCallback(RefundCallbackRequest request) {
        String idemKey = "payment:idem:refund-callback:%s".formatted(request.wxRefundId());
        if (Boolean.TRUE.equals(redis.hasKey(idemKey))) {
            return;
        }
        PaymentRefundRecord record = refundRecordMapper.findById(request.refundId());
        if (record == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "退款记录不存在");
        }
        LocalDateTime refundedAt = request.refundedAt() == null ? LocalDateTime.now() : request.refundedAt();
        refundRecordMapper.markSuccess(request.refundId(), request.wxRefundId(), request.rawPayload(), refundedAt);
        orderPort.markRefunded(record.getOrderId());
        writeJson(idemKey, "SUCCESS", Duration.ofDays(7));
    }

    @Override
    @Transactional
    public ProfitSharingVO shareAfterVerification(ProfitSharingRequest request) {
        String idemKey = "payment:idem:profit-sharing:%s".formatted(request.requestId());
        ProfitSharingVO cached = readJson(idemKey, ProfitSharingVO.class);
        if (cached != null) {
            return cached;
        }
        PaymentOrderDTO verified = orderPort.markVerified(request.orderId());
        LocalDateTime now = LocalDateTime.now();
        BigDecimal total = verified.paidAmount() == null || BigDecimal.ZERO.compareTo(verified.paidAmount()) == 0
                ? verified.payableAmount() : verified.paidAmount();
        BigDecimal commission = total.multiply(DEFAULT_COMMISSION_RATE).setScale(2, RoundingMode.HALF_UP);
        BigDecimal merchantAmount = total.subtract(commission).max(BigDecimal.ZERO);
        PaymentProfitSharingRecord record = new PaymentProfitSharingRecord();
        record.setId(SnowflakeIdGenerator.nextId());
        record.setOrderId(request.orderId());
        record.setMerchantId(request.merchantId());
        record.setVerificationId(request.verificationId());
        record.setSharingNo("PS" + SnowflakeIdGenerator.nextIdString());
        record.setWxSharingId("mock_sharing_" + SnowflakeIdGenerator.nextIdString());
        record.setTotalAmount(total);
        record.setPlatformCommissionAmount(commission);
        record.setMerchantAmount(merchantAmount);
        record.setCommissionRate(DEFAULT_COMMISSION_RATE);
        record.setSharingStatus("SUCCESS");
        record.setCallbackPayload("{}");
        record.setSharedAt(now);
        record.setCreatedAt(now);
        record.setUpdatedAt(now);
        record.setDeleted(0);
        sharingRecordMapper.insert(record);
        orderPort.markCompleted(request.orderId());
        ProfitSharingVO result = toSharingVO(record);
        writeJson(idemKey, result, Duration.ofDays(7));
        return result;
    }

    private RefundVO toRefundVO(PaymentRefundRecord record) {
        return new RefundVO(record.getId(), record.getOrderId(), record.getRefundNo(), record.getRefundAmount(),
                record.getRefundStatus(), record.getAuditStatus(), record.getRequestedAt(), record.getRefundedAt());
    }

    private ProfitSharingVO toSharingVO(PaymentProfitSharingRecord record) {
        return new ProfitSharingVO(record.getId(), record.getOrderId(), record.getMerchantId(), record.getSharingNo(),
                record.getTotalAmount(), record.getPlatformCommissionAmount(), record.getMerchantAmount(),
                record.getCommissionRate(), record.getSharingStatus(), record.getSharedAt());
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
            redis.opsForValue().set(key, value instanceof String text ? text : objectMapper.writeValueAsString(value), ttl);
        } catch (Exception ignored) {
            // Redis 失败不影响 MySQL 事实数据。
        }
    }
}
