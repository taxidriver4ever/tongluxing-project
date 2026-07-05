package com.tongluxing.order.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tongluxing.common.result.Result;
import com.tongluxing.order.service.OrderService;
import com.tongluxing.order.vo.OrderVO;
import com.tongluxing.order.vo.PageResult;

import lombok.RequiredArgsConstructor;

/**
 * 商家侧订单查询接口。
 *
 * <p>MVP 阶段通过 merchantId 查询商家订单，后续可接入商家登录态后改为自动识别当前商家。</p>
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/merchants/orders")
public class MerchantOrderController {

    private final OrderService orderService;

    @GetMapping
    public Result<PageResult<OrderVO>> list(@RequestParam Long merchantId,
                                            @RequestParam(required = false) String status,
                                            @RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "20") int size) {
        return Result.success(orderService.merchantOrders(merchantId, status, page, size));
    }

    @GetMapping("/{orderId}")
    public Result<OrderVO> detail(@PathVariable Long orderId) {
        return Result.success(orderService.internalDetail(orderId));
    }
}
