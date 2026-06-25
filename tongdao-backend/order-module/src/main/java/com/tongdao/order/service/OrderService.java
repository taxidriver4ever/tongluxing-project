package com.tongdao.order.service;

import java.time.LocalDateTime;

import com.tongdao.order.dto.CreateOrderRequest;
import com.tongdao.order.dto.OrderPreviewRequest;
import com.tongdao.order.vo.OrderPreviewVO;
import com.tongdao.order.vo.OrderVO;
import com.tongdao.order.vo.PageResult;

public interface OrderService {
    OrderPreviewVO preview(OrderPreviewRequest request);

    OrderVO create(CreateOrderRequest request);

    PageResult<OrderVO> myOrders(String status, int page, int size);

    OrderVO detail(Long orderId);

    void cancel(Long orderId);

    OrderVO markPaid(Long orderId, LocalDateTime paidAt);

    OrderVO markRefunding(Long orderId);

    OrderVO markRefunded(Long orderId);

    OrderVO markVerified(Long orderId);

    OrderVO markCompleted(Long orderId);
}

