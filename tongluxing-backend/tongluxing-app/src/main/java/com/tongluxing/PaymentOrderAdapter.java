package com.tongluxing;

import java.time.LocalDateTime;

import org.springframework.stereotype.Component;

import com.tongluxing.order.service.OrderService;
import com.tongluxing.order.vo.OrderVO;
import com.tongluxing.payment.integration.PaymentOrderPort;

import lombok.RequiredArgsConstructor;

/**
 * 支付模块访问订单模块的适配器。
 *
 * <p>应用层通过订单服务推进支付、退款、核销和完成状态，避免 payment-module 直接依赖订单实现细节。</p>
 */
@Component
@RequiredArgsConstructor
public class PaymentOrderAdapter implements PaymentOrderPort {
    private final OrderService orderService;

    /**
     * 查询支付所需的订单摘要。
     *
     * @param orderId 订单 ID
     * @return 支付模块订单摘要
     */
    @Override
    public PaymentOrderDTO getOrder(Long orderId) {
        return toDTO(orderService.internalDetail(orderId));
    }

    /**
     * 支付回调成功后标记订单已支付。
     *
     * @param orderId 订单 ID
     * @param paidAt 支付成功时间
     * @return 更新后的支付模块订单摘要
     */
    @Override
    public PaymentOrderDTO markPaid(Long orderId, LocalDateTime paidAt) {
        return toDTO(orderService.markPaid(orderId, paidAt));
    }

    /**
     * 退款申请创建后标记订单退款中。
     *
     * @param orderId 订单 ID
     * @return 更新后的支付模块订单摘要
     */
    @Override
    public PaymentOrderDTO markRefunding(Long orderId) {
        return toDTO(orderService.markRefunding(orderId));
    }

    /**
     * 退款回调成功后标记订单已退款。
     *
     * @param orderId 订单 ID
     * @return 更新后的支付模块订单摘要
     */
    @Override
    public PaymentOrderDTO markRefunded(Long orderId) {
        return toDTO(orderService.markRefunded(orderId));
    }

    /**
     * 券码核销成功后标记订单已核销。
     *
     * @param orderId 订单 ID
     * @return 更新后的支付模块订单摘要
     */
    @Override
    public PaymentOrderDTO markVerified(Long orderId) {
        return toDTO(orderService.markVerified(orderId));
    }

    /**
     * 分账成功后标记订单完成。
     *
     * @param orderId 订单 ID
     * @return 更新后的支付模块订单摘要
     */
    @Override
    public PaymentOrderDTO markCompleted(Long orderId) {
        return toDTO(orderService.markCompleted(orderId));
    }

    /**
     * 将订单响应转换为支付模块端口 DTO。
     *
     * @param order 订单模块响应对象
     * @return 支付模块端口 DTO
     */
    private PaymentOrderDTO toDTO(OrderVO order) {
        return new PaymentOrderDTO(order.orderId(), order.orderNo(), order.userId(), order.merchantId(),
                order.activityId(), order.paymentStatus(), order.verificationStatus(), order.profitSharingStatus(),
                order.payableAmount(), order.paidAmount());
    }
}
