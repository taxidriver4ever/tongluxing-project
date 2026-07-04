package com.tongluxing.order.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Data;
/**
 * OrderItem 数据库实体。
 */

@Data
public class OrderItem {
    private Long id;
    private Long orderId;
    private Long productId;
    private String productName;
    private String productType;
    private BigDecimal unitPrice;
    private Integer quantity;
    private BigDecimal totalAmount;
    private String snapshotJson;
    private LocalDateTime createdAt;
}

