package com.tongluxing.interfaces.web;

import java.math.BigDecimal;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tongluxing.common.result.Result;
import com.tongluxing.merchant.service.MerchantService;
import com.tongluxing.merchant.vo.MerchantProfileVO;
import com.tongluxing.notify.service.NotificationService;
import com.tongluxing.order.service.OrderService;
import com.tongluxing.verification.dto.VerificationQueryRequest;
import com.tongluxing.verification.service.VerificationService;

import lombok.RequiredArgsConstructor;

/**
 * 商家经营工作台聚合接口。
 *
 * <p>该 Controller 放在应用装配层，负责聚合订单、核销和通知等跨模块只读数据；
 * 不直接修改任何业务模块事实表。</p>
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/merchants/workbench")
public class MerchantWorkbenchController {
    private final MerchantService merchantService;
    private final OrderService orderService;
    private final VerificationService verificationService;
    private final NotificationService notificationService;

    /**
     * 查询当前商家的经营工作台汇总。
     *
     * @return 商家基础资料、订单数、核销数、结算占位金额和未读通知数
     */
    @GetMapping("/summary")
    public Result<MerchantWorkbenchSummaryVO> summary() {
        MerchantProfileVO profile = merchantService.currentProfile();
        Long merchantId = profile.merchantId();
        long orderCount = orderService.merchantOrders(merchantId, null, 1, 1).total();
        long verificationCount = verificationService.pageQuery(
                new VerificationQueryRequest(merchantId, null, null, null, null, 1, 1)).total();
        long unreadCount = notificationService.countCurrentUserUnread().unreadCount();
        return Result.success(new MerchantWorkbenchSummaryVO(
                merchantId,
                profile.merchantName(),
                profile.auditStatus(),
                profile.merchantLevel(),
                profile.commissionRate(),
                orderCount,
                verificationCount,
                BigDecimal.ZERO,
                unreadCount));
    }

    /**
     * 商家工作台汇总展示对象。
     *
     * @param merchantId 商家 ID
     * @param merchantName 商家名称
     * @param auditStatus 入驻审核状态
     * @param merchantLevel 商家等级
     * @param commissionRate 平台佣金比例
     * @param orderCount 订单总数
     * @param verificationCount 核销总数
     * @param localSettlementAmount 本地结算金额，当前版本预留
     * @param unreadNotificationCount 当前商家未读通知数
     */
    public record MerchantWorkbenchSummaryVO(
            Long merchantId,
            String merchantName,
            String auditStatus,
            String merchantLevel,
            BigDecimal commissionRate,
            long orderCount,
            long verificationCount,
            BigDecimal localSettlementAmount,
            long unreadNotificationCount) {
    }
}
