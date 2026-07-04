package com.tongluxing.order.controller;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tongluxing.common.result.Result;
import com.tongluxing.order.dto.CreateOrderRequest;
import com.tongluxing.order.dto.OrderPreviewRequest;
import com.tongluxing.order.service.OrderService;
import com.tongluxing.order.vo.OrderPreviewVO;
import com.tongluxing.order.vo.OrderVO;
import com.tongluxing.order.vo.PageResult;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 订单模块接口控制器，提供订单试算、创建、查询和取消能力。
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/orders")
public class OrderController {
    private final OrderService orderService;

    /**
     * 订单金额试算，用于下单前展示原价、拼团优惠、券抵扣和应付金额。
     */
    @PostMapping("/preview")
    public Result<OrderPreviewVO> preview(@Valid @RequestBody OrderPreviewRequest request) {
        return Result.success(orderService.preview(request));
    }

    /**
     * 创建订单，服务层会基于 requestId 做幂等保护。
     */
    @PostMapping
    public Result<OrderVO> create(@Valid @RequestBody CreateOrderRequest request) {
        return Result.success(orderService.create(request));
    }

    /**
     * 查询当前登录用户的订单列表。
     */
    @GetMapping
    public Result<PageResult<OrderVO>> myOrders(@RequestParam(required = false) String status,
                                                @RequestParam(defaultValue = "1") int page,
                                                @RequestParam(defaultValue = "20") int size) {
        return Result.success(orderService.myOrders(status, page, size));
    }

    /**
     * 查询当前登录用户可见的订单详情。
     */
    @GetMapping("/{orderId}")
    public Result<OrderVO> detail(@PathVariable Long orderId) {
        return Result.success(orderService.detail(orderId));
    }

    /**
     * 取消当前登录用户自己的待支付订单。
     */
    @PostMapping("/{orderId}/cancel")
    public Result<Void> cancel(@PathVariable Long orderId) {
        orderService.cancel(orderId);
        return Result.success();
    }
}
