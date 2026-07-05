package com.tongluxing.order.service;

import java.time.LocalDateTime;

import com.tongluxing.order.dto.CreateOrderRequest;
import com.tongluxing.order.dto.OrderPreviewRequest;
import com.tongluxing.order.vo.OrderPreviewVO;
import com.tongluxing.order.vo.OrderVO;
import com.tongluxing.order.vo.PageResult;

/**
 * 订单模块业务服务接口。
 */
public interface OrderService {

    /**
     * 下单前金额试算。
     */
    OrderPreviewVO preview(OrderPreviewRequest request);

    /**
     * 创建订单并写入订单明细。
     */
    OrderVO create(CreateOrderRequest request);

    /**
     * 查询当前用户订单分页列表。
     */
    PageResult<OrderVO> myOrders(String status, int page, int size);

    /**
     * 查询订单详情。
     */
    OrderVO detail(Long orderId);

    OrderVO internalDetail(Long orderId);

    PageResult<OrderVO> merchantOrders(Long merchantId, String status, int page, int size);

    int closeExpiredWaitPay(int limit);

    /**
     * 取消待支付订单。
     */
    void cancel(Long orderId);

    /**
     * 支付模块回调后标记订单已支付。
     */
    OrderVO markPaid(Long orderId, LocalDateTime paidAt);

    /**
     * 标记订单进入退款中。
     */
    OrderVO markRefunding(Long orderId);

    /**
     * 标记订单退款成功。
     */
    OrderVO markRefunded(Long orderId);

    /**
     * 核销模块回调后标记订单已核销。
     */
    OrderVO markVerified(Long orderId);

    /**
     * 分账或履约结束后标记订单完成。
     */
    OrderVO markCompleted(Long orderId);
}
