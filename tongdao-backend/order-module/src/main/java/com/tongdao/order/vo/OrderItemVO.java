package com.tongdao.order.vo;

import java.math.BigDecimal;
/**
 * OrderItemVO 视图响应对象。
 */

public record OrderItemVO(
        Long itemId,
        Long productId,
        String productName,
        String productType,
        BigDecimal unitPrice,
        Integer quantity,
        BigDecimal totalAmount
) {
}

