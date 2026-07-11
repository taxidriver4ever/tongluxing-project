package com.tongluxing.order.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.tongluxing.order.entity.OrderItem;

/**
 * 订单明细 Mapper。
 *
 * <p>封装 order_item 表的新增和按订单查询操作。</p>
 */
@Mapper
public interface OrderItemMapper {

    /**
     * 新增订单商品明细。
     *
     * @param item 订单商品明细实体
     */
    @Insert("""
            insert into order_item
                (id, order_id, product_id, product_name, product_type,
                 unit_price, quantity, total_amount, snapshot_json, created_at)
            values
                (#{id}, #{orderId}, #{productId}, #{productName}, #{productType},
                 #{unitPrice}, #{quantity}, #{totalAmount}, #{snapshotJson}, #{createdAt})
            """)
    void insert(OrderItem item);

    /**
     * 查询指定订单下的商品明细。
     *
     * @param orderId 订单 ID
     * @return 订单商品明细列表
     */
    @Select("""
            select id, order_id, product_id, product_name, product_type,
                   unit_price, quantity, total_amount, snapshot_json, created_at
            from order_item
            where order_id = #{orderId}
            order by id
            """)
    List<OrderItem> findByOrderId(@Param("orderId") Long orderId);
}
