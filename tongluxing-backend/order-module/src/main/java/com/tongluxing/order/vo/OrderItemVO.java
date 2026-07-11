package com.tongluxing.order.vo;

import java.math.BigDecimal;

/**
 * 订单商品明细响应视图。
 *
 * @param itemId 明细 ID
 * @param productId 商品 ID
 * @param productName 下单时固化的商品名称
 * @param productType 下单时固化的商品类型
 * @param unitPrice 下单时固化的商品单价
 * @param quantity 购买数量
 * @param totalAmount 明细总额
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

