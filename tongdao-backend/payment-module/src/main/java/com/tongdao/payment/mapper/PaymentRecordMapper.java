package com.tongdao.payment.mapper;

import java.time.LocalDateTime;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.tongdao.payment.entity.PaymentRecord;
/**
 * 支付流水 Mapper。
 */
@Mapper
public interface PaymentRecordMapper {

    /**
     * 新增支付流水记录。
     */
    @Insert("""
            insert into payment_record
                (id, order_id, order_no, payment_no, wx_prepay_id, wx_transaction_id,
                 pay_channel, pay_amount, payment_status, callback_payload, paid_at,
                 created_at, updated_at, deleted)
            values
                (#{id}, #{orderId}, #{orderNo}, #{paymentNo}, #{wxPrepayId}, #{wxTransactionId},
                 #{payChannel}, #{payAmount}, #{paymentStatus}, #{callbackPayload}, #{paidAt},
                 #{createdAt}, #{updatedAt}, 0)
            """)
    void insert(PaymentRecord record);

    /**
     * 查询指定订单最新一条支付流水。
     */
    @Select("""
            select id, order_id, order_no, payment_no, wx_prepay_id, wx_transaction_id,
                   pay_channel, pay_amount, payment_status, callback_payload, paid_at,
                   created_at, updated_at, deleted
            from payment_record
            where order_id = #{orderId}
              and deleted = 0
            order by created_at desc
            limit 1
            """)
    PaymentRecord findLatestByOrderId(@Param("orderId") Long orderId);

    /**
     * 支付回调成功后更新支付流水状态。
     */
    @Update("""
            update payment_record
            set wx_transaction_id = #{transactionId},
                payment_status = 'SUCCESS',
                callback_payload = #{payload},
                paid_at = #{paidAt},
                updated_at = #{paidAt}
            where order_id = #{orderId}
              and deleted = 0
            """)
    int markSuccess(@Param("orderId") Long orderId, @Param("transactionId") String transactionId,
                    @Param("payload") String payload, @Param("paidAt") LocalDateTime paidAt);
}
