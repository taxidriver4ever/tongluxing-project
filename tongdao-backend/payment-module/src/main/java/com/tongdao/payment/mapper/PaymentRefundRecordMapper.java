package com.tongdao.payment.mapper;

import java.time.LocalDateTime;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.tongdao.payment.entity.PaymentRefundRecord;

@Mapper
public interface PaymentRefundRecordMapper {
    @Insert("""
            insert into payment_refund_record
                (id, order_id, refund_no, wx_refund_id, user_id, refund_amount,
                 refund_reason, refund_type, refund_status, audit_status, callback_payload,
                 requested_at, refunded_at, created_at, updated_at, deleted)
            values
                (#{id}, #{orderId}, #{refundNo}, #{wxRefundId}, #{userId}, #{refundAmount},
                 #{refundReason}, #{refundType}, #{refundStatus}, #{auditStatus}, #{callbackPayload},
                 #{requestedAt}, #{refundedAt}, #{createdAt}, #{updatedAt}, 0)
            """)
    void insert(PaymentRefundRecord record);

    @Select("""
            select id, order_id, refund_no, wx_refund_id, user_id, refund_amount,
                   refund_reason, refund_type, refund_status, audit_status, callback_payload,
                   requested_at, refunded_at, created_at, updated_at, deleted
            from payment_refund_record
            where id = #{refundId}
              and deleted = 0
            limit 1
            """)
    PaymentRefundRecord findById(@Param("refundId") Long refundId);

    @Update("""
            update payment_refund_record
            set wx_refund_id = #{wxRefundId},
                refund_status = 'SUCCESS',
                callback_payload = #{payload},
                refunded_at = #{refundedAt},
                updated_at = #{refundedAt}
            where id = #{refundId}
              and deleted = 0
            """)
    int markSuccess(@Param("refundId") Long refundId, @Param("wxRefundId") String wxRefundId,
                    @Param("payload") String payload, @Param("refundedAt") LocalDateTime refundedAt);
}

