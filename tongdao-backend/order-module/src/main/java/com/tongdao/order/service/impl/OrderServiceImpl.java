package com.tongdao.order.service.impl;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tongdao.common.exception.BusinessException;
import com.tongdao.common.result.ResultCode;
import com.tongdao.common.utils.SnowflakeIdGenerator;
import com.tongdao.order.dto.CreateOrderRequest;
import com.tongdao.order.dto.OrderPreviewRequest;
import com.tongdao.order.entity.OrderCompensationTask;
import com.tongdao.order.entity.OrderItem;
import com.tongdao.order.entity.OrderTrade;
import com.tongdao.order.mapper.OrderCompensationTaskMapper;
import com.tongdao.order.mapper.OrderItemMapper;
import com.tongdao.order.mapper.OrderTradeMapper;
import com.tongdao.order.service.OrderService;
import com.tongdao.order.vo.OrderItemVO;
import com.tongdao.order.vo.OrderPreviewVO;
import com.tongdao.order.vo.OrderVO;
import com.tongdao.order.vo.PageResult;
import com.tongdao.user.support.CurrentUserContext;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {
    private static final BigDecimal DEFAULT_UNIT_PRICE = new BigDecimal("99.00");
    private static final String WAIT_PAY = "WAIT_PAY";

    private final CurrentUserContext currentUserContext;
    private final OrderTradeMapper orderMapper;
    private final OrderItemMapper itemMapper;
    private final OrderCompensationTaskMapper compensationTaskMapper;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    @Override
    public OrderPreviewVO preview(OrderPreviewRequest request) {
        Long userId = currentUserContext.requireUserId();
        String key = "order:preview:%d:%s".formatted(userId, previewHash(request));
        OrderPreviewVO cached = readJson(key, OrderPreviewVO.class);
        if (cached != null) {
            return cached;
        }
        OrderPreviewVO result = calculatePreview(request.productId(), request.activityId(), request.quantity(),
                request.userCouponId());
        writeJson(key, result, Duration.ofMinutes(5));
        return result;
    }

    @Override
    @Transactional
    public OrderVO create(CreateOrderRequest request) {
        Long userId = currentUserContext.requireUserId();
        String idemKey = "order:idem:create:%s".formatted(request.requestId());
        OrderVO cached = readJson(idemKey, OrderVO.class);
        if (cached != null) {
            return cached;
        }

        OrderPreviewVO preview = calculatePreview(request.productId(), request.activityId(), request.quantity(),
                request.userCouponId());
        LocalDateTime now = LocalDateTime.now();
        Long orderId = SnowflakeIdGenerator.nextId();
        OrderTrade order = new OrderTrade();
        order.setId(orderId);
        order.setOrderNo("OD" + SnowflakeIdGenerator.nextIdString());
        order.setUserId(userId);
        order.setMerchantId(0L);
        order.setProductId(request.productId());
        order.setActivityId(request.activityId());
        order.setOriginalAmount(preview.originalAmount());
        order.setGroupbuyDiscountAmount(preview.groupbuyDiscountAmount());
        order.setCouponDeductionAmount(preview.couponDeductionAmount());
        order.setPayableAmount(preview.payableAmount());
        order.setPaidAmount(BigDecimal.ZERO);
        order.setUserCouponId(preview.selectedCouponId());
        order.setOrderStatus(WAIT_PAY);
        order.setPaymentStatus(WAIT_PAY);
        order.setVerificationStatus("UNGENERATED");
        order.setRefundStatus("NONE");
        order.setProfitSharingStatus("NONE");
        order.setExpireAt(now.plusMinutes(30));
        order.setRemark(trim(request.remark()));
        order.setCreatedAt(now);
        order.setUpdatedAt(now);
        order.setDeleted(0);
        orderMapper.insert(order);

        OrderItem item = new OrderItem();
        item.setId(SnowflakeIdGenerator.nextId());
        item.setOrderId(orderId);
        item.setProductId(request.productId());
        item.setProductName("拼团商品-" + request.productId());
        item.setProductType("GROUPBUY");
        item.setUnitPrice(DEFAULT_UNIT_PRICE);
        item.setQuantity(request.quantity());
        item.setTotalAmount(preview.originalAmount());
        item.setSnapshotJson("{}");
        item.setCreatedAt(now);
        itemMapper.insert(item);

        if (preview.selectedCouponId() != null) {
            addCompensation("COUPON_LOCK", String.valueOf(orderId), request.requestId(), "coupon-module",
                    "{\"orderId\":%d,\"userCouponId\":%d}".formatted(orderId, preview.selectedCouponId()), now);
        }

        OrderVO result = toVO(orderMapper.findById(orderId));
        writeJson(idemKey, result, Duration.ofHours(24));
        return result;
    }

    @Override
    public PageResult<OrderVO> myOrders(String status, int page, int size) {
        Long userId = currentUserContext.requireUserId();
        int normalizedPage = Math.max(page, 1);
        int normalizedSize = Math.min(Math.max(size, 1), 100);
        int offset = (normalizedPage - 1) * normalizedSize;
        String normalizedStatus = StringUtils.hasText(status) ? status.trim() : null;
        List<OrderVO> records = orderMapper.findByUser(userId, normalizedStatus, offset, normalizedSize)
                .stream()
                .map(this::toVO)
                .toList();
        return new PageResult<>(records, orderMapper.countByUser(userId, normalizedStatus), normalizedPage, normalizedSize);
    }

    @Override
    public OrderVO detail(Long orderId) {
        Long userId = currentUserContext.requireUserId();
        OrderTrade order = requireOrder(orderId);
        if (!userId.equals(order.getUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权查看该订单");
        }
        return toVO(order);
    }

    @Override
    @Transactional
    public void cancel(Long orderId) {
        Long userId = currentUserContext.requireUserId();
        OrderTrade order = requireOrder(orderId);
        if (!userId.equals(order.getUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权取消该订单");
        }
        if (!WAIT_PAY.equals(order.getOrderStatus())) {
            if ("CANCELLED".equals(order.getOrderStatus())) {
                return;
            }
            throw new BusinessException("只有待支付订单可以取消");
        }
        if (orderMapper.cancelWaitPay(orderId, userId, LocalDateTime.now()) == 0) {
            throw new BusinessException("订单状态已变化，请刷新后重试");
        }
        if (order.getUserCouponId() != null) {
            addCompensation("COUPON_RELEASE", String.valueOf(orderId), "order:cancel:" + orderId, "coupon-module",
                    "{\"orderId\":%d}".formatted(orderId), LocalDateTime.now());
        }
    }

    @Override
    @Transactional
    public OrderVO markPaid(Long orderId, LocalDateTime paidAt) {
        requireOrder(orderId);
        orderMapper.markPaid(orderId, paidAt == null ? LocalDateTime.now() : paidAt);
        return toVO(requireOrder(orderId));
    }

    @Override
    @Transactional
    public OrderVO markRefunding(Long orderId) {
        requireOrder(orderId);
        orderMapper.updateRefundStatus(orderId, "REFUNDING", "PAID", LocalDateTime.now());
        return toVO(requireOrder(orderId));
    }

    @Override
    @Transactional
    public OrderVO markRefunded(Long orderId) {
        requireOrder(orderId);
        orderMapper.updateRefundStatus(orderId, "SUCCESS", "REFUNDED", LocalDateTime.now());
        return toVO(requireOrder(orderId));
    }

    @Override
    @Transactional
    public OrderVO markVerified(Long orderId) {
        requireOrder(orderId);
        orderMapper.markVerified(orderId, LocalDateTime.now());
        return toVO(requireOrder(orderId));
    }

    @Override
    @Transactional
    public OrderVO markCompleted(Long orderId) {
        requireOrder(orderId);
        orderMapper.markCompleted(orderId, LocalDateTime.now());
        return toVO(requireOrder(orderId));
    }

    private OrderPreviewVO calculatePreview(Long productId, Long activityId, Integer quantity, Long userCouponId) {
        BigDecimal original = DEFAULT_UNIT_PRICE.multiply(BigDecimal.valueOf(quantity == null ? 1 : quantity));
        BigDecimal groupDiscount = activityId == null ? BigDecimal.ZERO : original.multiply(new BigDecimal("0.10"));
        BigDecimal coupon = userCouponId == null ? BigDecimal.ZERO : new BigDecimal("10.00").min(original.subtract(groupDiscount));
        BigDecimal payable = original.subtract(groupDiscount).subtract(coupon).max(BigDecimal.ZERO);
        return new OrderPreviewVO(productId, activityId, original, groupDiscount, coupon, payable, userCouponId,
                "当前为本地试算；创建订单时会重新计算并通过补偿任务对接优惠券锁定");
    }

    private OrderTrade requireOrder(Long orderId) {
        OrderTrade order = orderMapper.findById(orderId);
        if (order == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "订单不存在");
        }
        return order;
    }

    private OrderVO toVO(OrderTrade order) {
        List<OrderItemVO> items = itemMapper.findByOrderId(order.getId())
                .stream()
                .map(item -> new OrderItemVO(item.getId(), item.getProductId(), item.getProductName(),
                        item.getProductType(), item.getUnitPrice(), item.getQuantity(), item.getTotalAmount()))
                .toList();
        return new OrderVO(order.getId(), order.getOrderNo(), order.getUserId(), order.getMerchantId(),
                order.getProductId(), order.getActivityId(), order.getOrderStatus(), order.getPaymentStatus(),
                order.getVerificationStatus(), order.getRefundStatus(), order.getProfitSharingStatus(),
                order.getOriginalAmount(), order.getGroupbuyDiscountAmount(), order.getCouponDeductionAmount(),
                order.getPayableAmount(), order.getPaidAmount(), order.getUserCouponId(), order.getExpireAt(),
                order.getPaidAt(), order.getCompletedAt(), items);
    }

    private void addCompensation(String type, String bizId, String idem, String target, String payload, LocalDateTime now) {
        OrderCompensationTask task = new OrderCompensationTask();
        task.setId(SnowflakeIdGenerator.nextId());
        task.setBizType(type);
        task.setBizId(bizId);
        task.setIdempotentKey(idem);
        task.setTargetModule(target);
        task.setRequestPayload(payload);
        task.setTaskStatus("PENDING");
        task.setRetryCount(0);
        task.setNextRetryAt(now.plusMinutes(1));
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        compensationTaskMapper.insert(task);
    }

    private String previewHash(OrderPreviewRequest request) {
        return "%s:%s:%s:%s:%s".formatted(request.productId(), request.activityId(), request.quantity(),
                request.userCouponId(), request.useBestCoupon()).replace(" ", "");
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
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
}

