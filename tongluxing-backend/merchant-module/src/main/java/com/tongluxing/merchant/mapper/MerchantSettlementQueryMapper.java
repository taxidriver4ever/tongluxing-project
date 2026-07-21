package com.tongluxing.merchant.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import com.tongluxing.merchant.vo.MerchantSettlementVO;

/** 商家结算只读查询，商家 ID 始终由登录用户映射。 */
@Mapper
public interface MerchantSettlementQueryMapper {
    @Select("select id from merchant_profile where user_id=#{userId} and audit_status='APPROVED' and status='ACTIVE' and deleted=0 limit 1")
    Long merchantId(@Param("userId") Long userId);

    @Select("""
            select s.id settlementId,s.order_id orderId,o.order_no orderNo,
                   s.total_amount totalAmount,s.platform_commission_amount commissionAmount,
                   s.merchant_amount merchantAmount,s.commission_rate commissionRate,
                   s.sharing_status settlementStatus,s.created_at createdAt,s.shared_at settledAt
            from payment_profit_sharing_record s join order_trade o on o.id=s.order_id
            where s.merchant_id=#{merchantId} and s.deleted=0
              and (#{status} is null or #{status}='' or s.sharing_status=#{status})
            order by s.created_at desc limit #{offset},#{size}
            """)
    List<MerchantSettlementVO> page(@Param("merchantId") Long merchantId, @Param("status") String status,
                                    @Param("offset") int offset, @Param("size") int size);

    @Select("select count(*) from payment_profit_sharing_record where merchant_id=#{merchantId} and deleted=0 and (#{status} is null or #{status}='' or sharing_status=#{status})")
    long count(@Param("merchantId") Long merchantId, @Param("status") String status);
}
