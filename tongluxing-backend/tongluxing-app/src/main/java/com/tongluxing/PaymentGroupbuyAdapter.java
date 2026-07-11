package com.tongluxing;

import java.time.LocalDateTime;

import org.springframework.stereotype.Component;

import com.tongluxing.groupbuy.dto.PaidParticipantRequest;
import com.tongluxing.groupbuy.service.GroupbuyService;
import com.tongluxing.payment.integration.PaymentGroupbuyPort;

import lombok.RequiredArgsConstructor;

/**
 * 支付模块访问拼团模块的适配器。
 *
 * <p>支付成功后通过该适配器同步拼团参与人的支付状态。</p>
 */
@Component
@RequiredArgsConstructor
public class PaymentGroupbuyAdapter implements PaymentGroupbuyPort {
    private final GroupbuyService groupbuyService;

    /**
     * 将支付成功事件同步给拼团模块。
     *
     * @param activityId 拼团活动 ID
     * @param orderId 订单 ID
     * @param userId 支付用户 ID
     * @param paidAt 支付成功时间
     * @param requestId 幂等请求号
     */
    @Override
    public void addPaidParticipant(Long activityId, Long orderId, Long userId, LocalDateTime paidAt, String requestId) {
        groupbuyService.addPaidParticipant(activityId, new PaidParticipantRequest(orderId, userId, paidAt, requestId));
    }
}
