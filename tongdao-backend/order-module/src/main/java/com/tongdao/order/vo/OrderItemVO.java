package com.tongdao.order.vo;

import java.math.BigDecimal;

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

