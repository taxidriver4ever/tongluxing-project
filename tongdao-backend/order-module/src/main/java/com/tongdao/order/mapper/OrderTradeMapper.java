package com.tongdao.order.mapper;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.tongdao.order.entity.OrderTrade;
/**
 * 订单主表 Mapper。
 */
@Mapper
public interface OrderTradeMapper {

    /**
     * 新增订单主表记录。
     */
    @Insert("""
            insert into order_trade
                (id, order_no, user_id, merchant_id, product_id, activity_id,
                 original_amount, groupbuy_discount_amount, coupon_deduction_amount,
                 payable_amount, paid_amount, user_coupon_id, order_status,
                 payment_status, verification_status, refund_status, profit_sharing_status,
                 expire_at, paid_at, completed_at, remark, created_at, updated_at, deleted)
            values
                (#{id}, #{orderNo}, #{userId}, #{merchantId}, #{productId}, #{activityId},
                 #{originalAmount}, #{groupbuyDiscountAmount}, #{couponDeductionAmount},
                 #{payableAmount}, #{paidAmount}, #{userCouponId}, #{orderStatus},
                 #{paymentStatus}, #{verificationStatus}, #{refundStatus}, #{profitSharingStatus},
                 #{expireAt}, #{paidAt}, #{completedAt}, #{remark}, #{createdAt}, #{updatedAt}, 0)
            """)
    void insert(OrderTrade order);

    /**
     * 根据订单 ID 查询未删除订单。
     */
    @Select("""
            select id, order_no, user_id, merchant_id, product_id, activity_id,
                   original_amount, groupbuy_discount_amount, coupon_deduction_amount,
                   payable_amount, paid_amount, user_coupon_id, order_status,
                   payment_status, verification_status, refund_status, profit_sharing_status,
                   expire_at, paid_at, completed_at, remark, created_at, updated_at, deleted
            from order_trade
            where id = #{orderId}
              and deleted = 0
            limit 1
            """)
    OrderTrade findById(@Param("orderId") Long orderId);

    /**
     * 分页查询指定用户的订单列表，可按订单状态过滤。
     */
    @Select("""
            select id, order_no, user_id, merchant_id, product_id, activity_id,
                   original_amount, groupbuy_discount_amount, coupon_deduction_amount,
                   payable_amount, paid_amount, user_coupon_id, order_status,
                   payment_status, verification_status, refund_status, profit_sharing_status,
                   expire_at, paid_at, completed_at, remark, created_at, updated_at, deleted
            from order_trade
            where user_id = #{userId}
              and deleted = 0
              and (#{status} is null or #{status} = '' or order_status = #{status})
            order by created_at desc
            limit #{offset}, #{size}
            """)
    List<OrderTrade> findByUser(@Param("userId") Long userId, @Param("status") String status,
                                @Param("offset") int offset, @Param("size") int size);

    /**
     * 统计指定用户的订单数量，可按订单状态过滤。
     */
    @Select("""
            select count(1)
            from order_trade
            where user_id = #{userId}
              and deleted = 0
              and (#{status} is null or #{status} = '' or order_status = #{status})
            """)
    long countByUser(@Param("userId") Long userId, @Param("status") String status);

    /**
     * 仅允许用户取消自己的待支付订单。
     */
    @Update("""
            update order_trade
            set order_status = 'CANCELLED',
                payment_status = 'CLOSED',
                updated_at = #{now}
            where id = #{orderId}
              and user_id = #{userId}
              and order_status = 'WAIT_PAY'
              and deleted = 0
            """)
    int cancelWaitPay(@Param("orderId") Long orderId, @Param("userId") Long userId, @Param("now") LocalDateTime now);

    /**
     * 支付成功后推进订单到已支付和待核销状态。
     */
    @Update("""
            update order_trade
            set order_status = 'PAID',
                payment_status = 'SUCCESS',
                verification_status = 'WAIT_VERIFY',
                paid_amount = payable_amount,
                paid_at = #{paidAt},
                updated_at = #{paidAt}
            where id = #{orderId}
              and payment_status in ('WAIT_PAY', 'PAYING')
              and deleted = 0
            """)
    int markPaid(@Param("orderId") Long orderId, @Param("paidAt") LocalDateTime paidAt);

    /**
     * 更新订单退款状态，同时同步订单主状态。
     */
    @Update("""
            update order_trade
            set refund_status = #{refundStatus},
                order_status = #{orderStatus},
                updated_at = #{now}
            where id = #{orderId}
              and deleted = 0
            """)
    int updateRefundStatus(@Param("orderId") Long orderId, @Param("refundStatus") String refundStatus,
                           @Param("orderStatus") String orderStatus, @Param("now") LocalDateTime now);

    /**
     * 核销成功后推进订单到已核销和待分账状态。
     */
    @Update("""
            update order_trade
            set verification_status = 'VERIFIED',
                profit_sharing_status = 'WAIT_SHARING',
                order_status = 'VERIFIED',
                updated_at = #{now}
            where id = #{orderId}
              and payment_status = 'SUCCESS'
              and deleted = 0
            """)
    int markVerified(@Param("orderId") Long orderId, @Param("now") LocalDateTime now);

    /**
     * 分账或履约完成后标记订单完成。
     */
    @Update("""
            update order_trade
            set profit_sharing_status = 'SUCCESS',
                order_status = 'COMPLETED',
                completed_at = #{now},
                updated_at = #{now}
            where id = #{orderId}
              and deleted = 0
            """)
    int markCompleted(@Param("orderId") Long orderId, @Param("now") LocalDateTime now);
}
