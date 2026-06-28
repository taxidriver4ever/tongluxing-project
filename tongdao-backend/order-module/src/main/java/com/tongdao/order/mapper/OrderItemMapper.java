package com.tongdao.order.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.tongdao.order.entity.OrderItem;
/**
 * 订单明细 Mapper。
 */
@Mapper
public interface OrderItemMapper {

    /**
     * 新增订单商品明细。
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
