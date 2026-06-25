package com.tongdao;

import java.time.LocalDateTime;

import org.springframework.stereotype.Component;

import com.tongdao.order.service.OrderService;
import com.tongdao.order.vo.OrderVO;
import com.tongdao.payment.integration.PaymentOrderPort;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class PaymentOrderAdapter implements PaymentOrderPort {
    private final OrderService orderService;

    @Override
    public PaymentOrderDTO getOrder(Long orderId) {
        return toDTO(orderService.detail(orderId));
    }

    @Override
    public PaymentOrderDTO markPaid(Long orderId, LocalDateTime paidAt) {
        return toDTO(orderService.markPaid(orderId, paidAt));
    }

    @Override
    public PaymentOrderDTO markRefunding(Long orderId) {
        return toDTO(orderService.markRefunding(orderId));
    }

    @Override
    public PaymentOrderDTO markRefunded(Long orderId) {
        return toDTO(orderService.markRefunded(orderId));
    }

    @Override
    public PaymentOrderDTO markVerified(Long orderId) {
        return toDTO(orderService.markVerified(orderId));
    }

    @Override
    public PaymentOrderDTO markCompleted(Long orderId) {
        return toDTO(orderService.markCompleted(orderId));
    }

    private PaymentOrderDTO toDTO(OrderVO order) {
        return new PaymentOrderDTO(order.orderId(), order.orderNo(), order.userId(), order.merchantId(),
                order.activityId(), order.paymentStatus(), order.verificationStatus(), order.profitSharingStatus(),
                order.payableAmount(), order.paidAmount());
    }
}
