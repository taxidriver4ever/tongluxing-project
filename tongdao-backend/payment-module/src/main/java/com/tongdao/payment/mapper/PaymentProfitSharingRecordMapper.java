package com.tongdao.payment.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.tongdao.payment.entity.PaymentProfitSharingRecord;

@Mapper
public interface PaymentProfitSharingRecordMapper {
    @Insert("""
            insert into payment_profit_sharing_record
                (id, order_id, merchant_id, verification_id, sharing_no, wx_sharing_id,
                 total_amount, platform_commission_amount, merchant_amount, commission_rate,
                 sharing_status, callback_payload, shared_at, created_at, updated_at, deleted)
            values
                (#{id}, #{orderId}, #{merchantId}, #{verificationId}, #{sharingNo}, #{wxSharingId},
                 #{totalAmount}, #{platformCommissionAmount}, #{merchantAmount}, #{commissionRate},
                 #{sharingStatus}, #{callbackPayload}, #{sharedAt}, #{createdAt}, #{updatedAt}, 0)
            """)
    void insert(PaymentProfitSharingRecord record);

    @Select("""
            select id, order_id, merchant_id, verification_id, sharing_no, wx_sharing_id,
                   total_amount, platform_commission_amount, merchant_amount, commission_rate,
                   sharing_status, callback_payload, shared_at, created_at, updated_at, deleted
            from payment_profit_sharing_record
            where id = #{id}
              and deleted = 0
            limit 1
            """)
    PaymentProfitSharingRecord findById(@Param("id") Long id);
}

