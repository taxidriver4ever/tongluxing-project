package com.tongluxing.order.service;

import java.time.LocalDateTime;

import com.tongluxing.order.dto.CreateOrderRequest;
import com.tongluxing.order.dto.OrderPreviewRequest;
import com.tongluxing.order.vo.OrderPreviewVO;
import com.tongluxing.order.vo.OrderVO;
import com.tongluxing.order.vo.PageResult;

/**
 * 订单模块业务服务接口。
 *
 * <p>封装订单试算、创建、查询、取消、支付回调和履约状态流转。</p>
 */
public interface OrderService {

    /**
     * 下单前金额试算。
     *
     * @param request 试算请求
     * @return 价格拆分结果
     */
    OrderPreviewVO preview(OrderPreviewRequest request);

    /**
     * 创建订单并写入订单明细。
     *
     * @param request 创建订单请求
     * @return 创建后的订单详情
     */
    OrderVO create(CreateOrderRequest request);

    /**
     * 查询当前用户订单分页列表。
     *
     * @param status 订单状态过滤条件，可为空
     * @param page 页码，从 1 开始
     * @param size 每页条数
     * @return 当前用户订单分页数据
     */
    PageResult<OrderVO> myOrders(String status, int page, int size);

    /**
     * 查询订单详情。
     *
     * @param orderId 订单 ID
     * @return 当前用户可见的订单详情
     */
    OrderVO detail(Long orderId);

    /**
     * 查询内部流程可见的订单详情，不校验当前用户归属。
     *
     * @param orderId 订单 ID
     * @return 订单详情
     */
    OrderVO internalDetail(Long orderId);

    /**
     * 查询商家订单分页列表。
     *
     * @param merchantId 商家 ID
     * @param status 订单状态过滤条件，可为空
     * @param page 页码，从 1 开始
     * @param size 每页条数
     * @return 商家订单分页数据
     */
    PageResult<OrderVO> merchantOrders(Long merchantId, String status, int page, int size);

    /**
     * 关闭超时未支付订单。
     *
     * @param limit 单次最多关闭数量
     * @return 成功关闭的订单数量
     */
    int closeExpiredWaitPay(int limit);

    /**
     * 取消待支付订单。
     *
     * @param orderId 订单 ID
     */
    void cancel(Long orderId);

    /**
     * 支付模块回调后标记订单已支付。
     *
     * @param orderId 订单 ID
     * @param paidAt 支付成功时间；为空时使用当前时间
     * @return 更新后的订单详情
     */
    OrderVO markPaid(Long orderId, LocalDateTime paidAt);

    /**
     * 标记订单进入退款中。
     *
     * @param orderId 订单 ID
     * @return 更新后的订单详情
     */
    OrderVO markRefunding(Long orderId);

    /**
     * 标记订单退款成功。
     *
     * @param orderId 订单 ID
     * @return 更新后的订单详情
     */
    OrderVO markRefunded(Long orderId);

    /**
     * 核销模块回调后标记订单已核销。
     *
     * @param orderId 订单 ID
     * @return 更新后的订单详情
     */
    OrderVO markVerified(Long orderId);

    /**
     * 分账或履约结束后标记订单完成。
     *
     * @param orderId 订单 ID
     * @return 更新后的订单详情
     */
    OrderVO markCompleted(Long orderId);
}
