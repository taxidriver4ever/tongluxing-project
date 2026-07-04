package com.tongluxing.order.service.impl;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tongluxing.common.exception.BusinessException;
import com.tongluxing.common.result.ResultCode;
import com.tongluxing.common.utils.SnowflakeIdGenerator;
import com.tongluxing.order.dto.CreateOrderRequest;
import com.tongluxing.order.dto.OrderPreviewRequest;
import com.tongluxing.order.entity.OrderCompensationTask;
import com.tongluxing.order.entity.OrderItem;
import com.tongluxing.order.entity.OrderTrade;
import com.tongluxing.order.mapper.OrderCompensationTaskMapper;
import com.tongluxing.order.mapper.OrderItemMapper;
import com.tongluxing.order.mapper.OrderTradeMapper;
import com.tongluxing.order.service.OrderService;
import com.tongluxing.order.vo.OrderItemVO;
import com.tongluxing.order.vo.OrderPreviewVO;
import com.tongluxing.order.vo.OrderVO;
import com.tongluxing.order.vo.PageResult;
import com.tongluxing.user.support.CurrentUserContext;

import lombok.RequiredArgsConstructor;

/**
 * 订单模块业务服务实现，负责订单金额试算、创建、查询和状态流转。
 */
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

    /**
     * 读取短期试算缓存；缓存不存在时重新计算金额并写回 Redis。
     */
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

    /**
     * 创建订单主表和订单明细，并通过 requestId 防止重复提交。
     */
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

        // 当前商品信息为本地快照，后续接入商品模块后可替换为真实商品快照。
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

        // 订单创建后通过补偿任务异步锁定优惠券，避免订单与优惠券模块强耦合。
        if (preview.selectedCouponId() != null) {
            addCompensation("COUPON_LOCK", String.valueOf(orderId), request.requestId(), "coupon-module",
                    "{\"orderId\":%d,\"userCouponId\":%d}".formatted(orderId, preview.selectedCouponId()), now);
        }

        OrderVO result = toVO(orderMapper.findById(orderId));
        writeJson(idemKey, result, Duration.ofHours(24));
        return result;
    }

    /**
     * 按用户和状态分页查询订单，并统一规范分页参数范围。
     */
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

    /**
     * 查询订单详情，并校验当前用户只能查看自己的订单。
     */
    @Override
    public OrderVO detail(Long orderId) {
        Long userId = currentUserContext.requireUserId();
        OrderTrade order = requireOrder(orderId);
        if (!userId.equals(order.getUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权查看该订单");
        }
        return toVO(order);
    }

    /**
     * 取消待支付订单；如订单绑定了优惠券，则写入释放优惠券的补偿任务。
     */
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

    /**
     * 支付成功后更新订单、支付和核销状态。
     */
    @Override
    @Transactional
    public OrderVO markPaid(Long orderId, LocalDateTime paidAt) {
        requireOrder(orderId);
        orderMapper.markPaid(orderId, paidAt == null ? LocalDateTime.now() : paidAt);
        return toVO(requireOrder(orderId));
    }

    /**
     * 退款申请通过后，将订单置为退款中。
     */
    @Override
    @Transactional
    public OrderVO markRefunding(Long orderId) {
        requireOrder(orderId);
        orderMapper.updateRefundStatus(orderId, "REFUNDING", "PAID", LocalDateTime.now());
        return toVO(requireOrder(orderId));
    }

    /**
     * 退款成功后，同步订单退款状态和订单状态。
     */
    @Override
    @Transactional
    public OrderVO markRefunded(Long orderId) {
        requireOrder(orderId);
        orderMapper.updateRefundStatus(orderId, "SUCCESS", "REFUNDED", LocalDateTime.now());
        return toVO(requireOrder(orderId));
    }

    /**
     * 券码核销成功后，订单进入已核销并等待分账状态。
     */
    @Override
    @Transactional
    public OrderVO markVerified(Long orderId) {
        requireOrder(orderId);
        orderMapper.markVerified(orderId, LocalDateTime.now());
        return toVO(requireOrder(orderId));
    }

    /**
     * 订单完成履约后标记完成时间。
     */
    @Override
    @Transactional
    public OrderVO markCompleted(Long orderId) {
        requireOrder(orderId);
        orderMapper.markCompleted(orderId, LocalDateTime.now());
        return toVO(requireOrder(orderId));
    }

    /**
     * 本地金额试算逻辑：根据数量、拼团活动和用户券计算应付金额。
     */
    private OrderPreviewVO calculatePreview(Long productId, Long activityId, Integer quantity, Long userCouponId) {
        BigDecimal original = DEFAULT_UNIT_PRICE.multiply(BigDecimal.valueOf(quantity == null ? 1 : quantity));
        BigDecimal groupDiscount = activityId == null ? BigDecimal.ZERO : original.multiply(new BigDecimal("0.10"));
        BigDecimal coupon = userCouponId == null ? BigDecimal.ZERO : new BigDecimal("10.00").min(original.subtract(groupDiscount));
        BigDecimal payable = original.subtract(groupDiscount).subtract(coupon).max(BigDecimal.ZERO);
        return new OrderPreviewVO(productId, activityId, original, groupDiscount, coupon, payable, userCouponId,
                "当前为本地试算；创建订单时会重新计算并通过补偿任务对接优惠券锁定");
    }

    /**
     * 查询订单，不存在时统一抛出业务异常。
     */
    private OrderTrade requireOrder(Long orderId) {
        OrderTrade order = orderMapper.findById(orderId);
        if (order == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "订单不存在");
        }
        return order;
    }

    /**
     * 将订单主表和明细表数据组装成前端返回对象。
     */
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

    /**
     * 创建跨模块补偿任务，用于后续异步处理优惠券锁定/释放等操作。
     */
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

    /**
     * 根据试算参数生成缓存键后缀。
     */
    private String previewHash(OrderPreviewRequest request) {
        return "%s:%s:%s:%s:%s".formatted(request.productId(), request.activityId(), request.quantity(),
                request.userCouponId(), request.useBestCoupon()).replace(" ", "");
    }

    /**
     * 清理用户输入的备注内容。
     */
    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    /**
     * 从 Redis 读取 JSON 缓存；读取失败时降级为空，避免缓存影响主流程。
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
     * 写入 Redis JSON 缓存；写入失败不影响 MySQL 事实数据。
     */
    private void writeJson(String key, Object value, Duration ttl) {
        try {
            redis.opsForValue().set(key, objectMapper.writeValueAsString(value), ttl);
        } catch (Exception ignored) {
            // Redis 失败不影响 MySQL 事实写入。
        }
    }
}
