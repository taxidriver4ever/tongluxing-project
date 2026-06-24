package com.tongdao.merchant.mapper;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.tongdao.merchant.dto.MerchantQueryDTO;

/**
 * 商家推广码数据访问接口。
 */
@Mapper
public interface MerchantPromotionCodeMapper {

    /**
     * 查询商家推广码列表。
     */
    @Select("""
            select id,
                   merchant_id merchantId,
                   promotion_code promotionCode,
                   channel_name channelName,
                   scene,
                   qr_image_key qrImageKey,
                   status,
                   remark,
                   created_at createdAt
            from merchant_promotion_code
            where merchant_id = #{merchantId}
              and deleted = 0
            order by created_at desc
            """)
    List<MerchantQueryDTO> findByMerchant(@Param("merchantId") Long merchantId);

    /**
     * 查询单个推广码，必须属于当前商家。
     */
    @Select("""
            select id,
                   merchant_id merchantId,
                   promotion_code promotionCode,
                   channel_name channelName,
                   scene,
                   qr_image_key qrImageKey,
                   status,
                   remark,
                   created_at createdAt
            from merchant_promotion_code
            where id = #{promotionId}
              and merchant_id = #{merchantId}
              and deleted = 0
            limit 1
            """)
    MerchantQueryDTO findById(@Param("merchantId") Long merchantId, @Param("promotionId") Long promotionId);

    /**
     * 新增推广码。
     */
    @Insert("""
            insert into merchant_promotion_code(
                id, merchant_id, promotion_code, channel_name, scene,
                qr_image_key, status, remark, created_at, updated_at, deleted
            )
            values (
                #{id}, #{merchantId}, #{promotionCode}, #{channelName}, #{scene},
                #{qrImageKey}, 'ACTIVE', #{remark}, #{now}, #{now}, 0
            )
            """)
    int insertCode(@Param("id") Long id,
                   @Param("merchantId") Long merchantId,
                   @Param("promotionCode") String promotionCode,
                   @Param("channelName") String channelName,
                   @Param("scene") String scene,
                   @Param("qrImageKey") String qrImageKey,
                   @Param("remark") String remark,
                   @Param("now") LocalDateTime now);
}
