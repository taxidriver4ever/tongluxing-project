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
import com.tongluxing.order.service.OrderCompensationTaskService;
import com.tongluxing.order.service.OrderService;
import com.tongluxing.order.vo.OrderPreviewVO;
import com.tongluxing.order.vo.OrderVO;
import com.tongluxing.order.vo.PageResult;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 用户侧订单 REST 接口。
 *
 * <p>提供订单试算、创建、当前用户订单查询、取消，以及少量内部运维触发入口。</p>
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/orders")
public class OrderController {
    private final OrderService orderService;
    private final OrderCompensationTaskService compensationTaskService;

    /**
     * 订单金额试算，用于下单前展示原价、拼团优惠、券抵扣和应付金额。
     *
     * @param request 试算参数
     * @return 试算后的金额拆分
     */
    @PostMapping("/preview")
    public Result<OrderPreviewVO> preview(@Valid @RequestBody OrderPreviewRequest request) {
        return Result.success(orderService.preview(request));
    }

    /**
     * 创建订单，服务层会基于 requestId 做幂等保护。
     *
     * @param request 创建订单参数
     * @return 创建后的订单详情
     */
    @PostMapping
    public Result<OrderVO> create(@Valid @RequestBody CreateOrderRequest request) {
        return Result.success(orderService.create(request));
    }

    /**
     * 查询当前登录用户的订单列表。
     *
     * @param status 可选订单状态过滤条件
     * @param page 页码，从 1 开始
     * @param size 每页条数
     * @return 当前用户订单分页数据
     */
    @GetMapping
    public Result<PageResult<OrderVO>> myOrders(@RequestParam(required = false) String status,
                                                @RequestParam(defaultValue = "1") int page,
                                                @RequestParam(defaultValue = "20") int size) {
        return Result.success(orderService.myOrders(status, page, size));
    }

    /**
     * 查询当前登录用户可见的订单详情。
     *
     * @param orderId 订单 ID
     * @return 订单详情
     */
    @GetMapping("/{orderId}")
    public Result<OrderVO> detail(@PathVariable Long orderId) {
        return Result.success(orderService.detail(orderId));
    }

    /**
     * 取消当前登录用户自己的待支付订单。
     *
     * @param orderId 订单 ID
     * @return 空响应
     */
    @PostMapping("/{orderId}/cancel")
    public Result<Void> cancel(@PathVariable Long orderId) {
        orderService.cancel(orderId);
        return Result.success();
    }

    /**
     * 内部关闭超时未支付订单，便于本地任务或运营补偿触发。
     *
     * @param limit 单次最多关闭订单数
     * @return 成功关闭的订单数量
     */
    @PostMapping("/internal/expired/close")
    public Result<Integer> closeExpired(@RequestParam(defaultValue = "100") int limit) {
        return Result.success(orderService.closeExpiredWaitPay(limit));
    }

    /**
     * 内部接口：手动消费订单补偿任务，便于内测和运营排障。
     *
     * @param limit 单次最多处理任务数
     * @return 成功处理的补偿任务数量
     */
    @PostMapping("/internal/compensation-tasks/process")
    public Result<Integer> processCompensationTasks(@RequestParam(defaultValue = "50") int limit) {
        return Result.success(compensationTaskService.processDueTasks(limit));
    }
}
