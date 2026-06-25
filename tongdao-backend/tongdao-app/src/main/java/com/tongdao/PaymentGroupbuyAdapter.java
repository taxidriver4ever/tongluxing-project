package com.tongdao;

import java.time.LocalDateTime;

import org.springframework.stereotype.Component;

import com.tongdao.groupbuy.dto.PaidParticipantRequest;
import com.tongdao.groupbuy.service.GroupbuyService;
import com.tongdao.payment.integration.PaymentGroupbuyPort;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class PaymentGroupbuyAdapter implements PaymentGroupbuyPort {
    private final GroupbuyService groupbuyService;

    @Override
    public void addPaidParticipant(Long activityId, Long orderId, Long userId, LocalDateTime paidAt, String requestId) {
        groupbuyService.addPaidParticipant(activityId, new PaidParticipantRequest(orderId, userId, paidAt, requestId));
    }
}
