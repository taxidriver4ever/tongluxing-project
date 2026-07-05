package com.tongluxing.payment.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tongluxing.common.exception.BusinessException;
import com.tongluxing.common.result.ResultCode;
import com.tongluxing.common.utils.SnowflakeIdGenerator;
import com.tongluxing.payment.dto.JsapiPaymentRequest;
import com.tongluxing.payment.dto.PaymentCallbackRequest;
import com.tongluxing.payment.dto.ProfitSharingRequest;
import com.tongluxing.payment.dto.RefundApplyRequest;
import com.tongluxing.payment.dto.RefundCallbackRequest;
import com.tongluxing.payment.entity.PaymentProfitSharingRecord;
import com.tongluxing.payment.entity.PaymentRecord;
import com.tongluxing.payment.entity.PaymentRefundRecord;
import com.tongluxing.payment.gateway.PaymentGateway;
import com.tongluxing.payment.integration.PaymentGroupbuyPort;
import com.tongluxing.payment.integration.PaymentOrderPort;
import com.tongluxing.payment.integration.PaymentOrderPort.PaymentOrderDTO;
import com.tongluxing.payment.mapper.PaymentProfitSharingRecordMapper;
import com.tongluxing.payment.mapper.PaymentRecordMapper;
import com.tongluxing.payment.mapper.PaymentRefundRecordMapper;
import com.tongluxing.payment.service.PaymentService;
import com.tongluxing.payment.vo.JsapiPayParamsVO;
import com.tongluxing.payment.vo.ProfitSharingVO;
import com.tongluxing.payment.vo.RefundVO;
import com.tongluxing.user.support.CurrentUserContext;

import lombok.RequiredArgsConstructor;

/**
 * 支付模块业务服务实现，负责支付会话、回调幂等、退款和核销后分账。
 */
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {
    private static final BigDecimal DEFAULT_COMMISSION_RATE = new BigDecimal("0.0800");

    private final CurrentUserContext currentUserContext;
    private final PaymentOrderPort orderPort;
    private final PaymentGroupbuyPort groupbuyPort;
    private final PaymentGateway paymentGateway;
    private final PaymentRecordMapper paymentRecordMapper;
    private final PaymentRefundRecordMapper refundRecordMapper;
    private final PaymentProfitSharingRecordMapper sharingRecordMapper;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    /**
     * 创建支付记录并返回小程序调起支付所需参数。
     */
    @Override
    @Transactional
    public JsapiPayParamsVO createJsapiPayment(JsapiPaymentRequest request) {
        PaymentOrderDTO order = orderPort.getOrder(request.orderId());
        if (!"WAIT_PAY".equals(order.paymentStatus())) {
            throw new BusinessException("订单不是待支付状态");
        }
        LocalDateTime now = LocalDateTime.now();
        PaymentGateway.PrepayResult prepay = paymentGateway.createPrepay(
                new PaymentGateway.PrepayCommand(order.orderId(), order.orderNo(), order.payableAmount()));
        PaymentRecord record = new PaymentRecord();
        record.setId(SnowflakeIdGenerator.nextId());
        record.setOrderId(order.orderId());
        record.setOrderNo(order.orderNo());
        record.setPaymentNo("PY" + SnowflakeIdGenerator.nextIdString());
        record.setWxPrepayId(prepay.prepayId());
        record.setPayChannel("WECHAT_JSAPI");
        record.setPayAmount(order.payableAmount());
        record.setPaymentStatus("PAYING");
        record.setCreatedAt(now);
        record.setUpdatedAt(now);
        record.setDeleted(0);
        paymentRecordMapper.insert(record);

        JsapiPayParamsVO params = new JsapiPayParamsVO(prepay.appId(), prepay.timeStamp(),
                prepay.nonceStr(), prepay.packageValue(), prepay.signType(), prepay.paySign());
        writeJson("payment:session:%d".formatted(order.orderId()), params, Duration.ofMinutes(30));
        return params;
    }

    /**
     * 处理支付成功回调；通过 transactionId 做幂等，避免重复推进订单和拼团状态。
     */
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
        // 支付成功后，如果订单来自拼团活动，则同步拼团参与人支付状态。
        if (order.activityId() != null) {
            groupbuyPort.addPaidParticipant(order.activityId(), order.orderId(), order.userId(), paidAt,
                    "payment-callback:" + request.transactionId());
        }
        writeJson(idemKey, "SUCCESS", Duration.ofDays(7));
    }

    /**
     * 用户申请退款；通过 requestId 做幂等，防止重复创建退款单。
     */
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
        // 已核销或已分账订单不能走普通退款，避免资金和履约状态不一致。
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
        PaymentGateway.RefundResult refundResult = paymentGateway.requestRefund(
                new PaymentGateway.RefundCommand(record.getId(), record.getRefundNo(),
                        record.getOrderId(), record.getRefundAmount(), record.getRefundReason()));
        record.setWxRefundId(refundResult.externalRefundId());
        record.setCallbackPayload(refundResult.rawPayload());
        record.setCreatedAt(now);
        record.setUpdatedAt(now);
        record.setDeleted(0);
        refundRecordMapper.insert(record);
        orderPort.markRefunding(order.orderId());
        RefundVO result = toRefundVO(record);
        writeJson(idemKey, result, Duration.ofHours(24));
        return result;
    }

    /**
     * 查询退款记录，不存在时返回统一业务异常。
     */
    @Override
    public RefundVO refundDetail(Long refundId) {
        PaymentRefundRecord record = refundRecordMapper.findById(refundId);
        if (record == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "退款记录不存在");
        }
        return toRefundVO(record);
    }

    /**
     * 处理退款成功回调；通过微信退款单号做幂等，并同步订单退款状态。
     */
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

    /**
     * 核销完成后执行分账，并将订单推进到已完成状态。
     */
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
        // 以实付金额为分账基数；若支付记录缺失，则回退到应付金额。
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
        PaymentGateway.ProfitSharingResult sharingResult = paymentGateway.createProfitSharing(
                new PaymentGateway.ProfitSharingCommand(request.orderId(), request.merchantId(),
                        request.verificationId(), total, merchantAmount));
        record.setWxSharingId(sharingResult.externalSharingId());
        record.setTotalAmount(total);
        record.setPlatformCommissionAmount(commission);
        record.setMerchantAmount(merchantAmount);
        record.setCommissionRate(DEFAULT_COMMISSION_RATE);
        record.setSharingStatus("SUCCESS");
        record.setCallbackPayload(sharingResult.rawPayload());
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

    /**
     * 将退款实体转换为接口返回对象。
     */
    private RefundVO toRefundVO(PaymentRefundRecord record) {
        return new RefundVO(record.getId(), record.getOrderId(), record.getRefundNo(), record.getRefundAmount(),
                record.getRefundStatus(), record.getAuditStatus(), record.getRequestedAt(), record.getRefundedAt());
    }

    /**
     * 将分账实体转换为接口返回对象。
     */
    private ProfitSharingVO toSharingVO(PaymentProfitSharingRecord record) {
        return new ProfitSharingVO(record.getId(), record.getOrderId(), record.getMerchantId(), record.getSharingNo(),
                record.getTotalAmount(), record.getPlatformCommissionAmount(), record.getMerchantAmount(),
                record.getCommissionRate(), record.getSharingStatus(), record.getSharedAt());
    }

    /**
     * 从 Redis 读取幂等或会话缓存；读取失败时降级为空，避免缓存影响主流程。
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
     * 写入 Redis 缓存；写入失败不影响 MySQL 事实数据。
     */
    private void writeJson(String key, Object value, Duration ttl) {
        try {
            redis.opsForValue().set(key, value instanceof String text ? text : objectMapper.writeValueAsString(value), ttl);
        } catch (Exception ignored) {
            // Redis 失败不影响 MySQL 事实数据。
        }
    }
}
