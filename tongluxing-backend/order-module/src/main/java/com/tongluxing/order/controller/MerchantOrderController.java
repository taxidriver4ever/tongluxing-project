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

    /**
     * 分页查询商家订单。
     *
     * @param merchantId 商家 ID
     * @param status 可选订单状态过滤条件
     * @param page 页码，从 1 开始
     * @param size 每页条数
     * @return 商家订单分页数据
     */
    @GetMapping
    public Result<PageResult<OrderVO>> list(@RequestParam Long merchantId,
                                            @RequestParam(required = false) String status,
                                            @RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "20") int size) {
        return Result.success(orderService.merchantOrders(merchantId, status, page, size));
    }

    /**
     * 查询商家侧订单详情。
     *
     * @param orderId 订单 ID
     * @return 订单详情
     */
    @GetMapping("/{orderId}")
    public Result<OrderVO> detail(@PathVariable Long orderId) {
        return Result.success(orderService.internalDetail(orderId));
    }
}
