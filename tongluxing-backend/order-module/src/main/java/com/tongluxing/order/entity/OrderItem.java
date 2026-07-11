package com.tongluxing.order.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * 订单商品明细实体，对应 order_item 表。
 *
 * <p>下单时固化商品名称、类型、价格和快照，避免商品后续变更影响历史订单展示。</p>
 */
@Data
public class OrderItem {
    /** 明细主键。 */
    private Long id;
    /** 所属订单 ID。 */
    private Long orderId;
    /** 商品 ID。 */
    private Long productId;
    /** 下单时的商品名称快照。 */
    private String productName;
    /** 下单时的商品类型快照。 */
    private String productType;
    /** 下单时的商品单价。 */
    private BigDecimal unitPrice;
    /** 购买数量。 */
    private Integer quantity;
    /** 明细总额。 */
    private BigDecimal totalAmount;
    /** 商品完整快照 JSON。 */
    private String snapshotJson;
    /** 创建时间。 */
    private LocalDateTime createdAt;
}

