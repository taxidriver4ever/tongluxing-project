package com.tongluxing.merchant.mapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.tongluxing.merchant.dto.MerchantQueryDTO;

/**
 * 奖励合作商家池配置数据访问接口。
 */
@Mapper
public interface MerchantRewardPoolConfigMapper {

    /**
     * 查询商家的奖励池配置。
     */
    @Select("""
            select merchant_id merchantId,
                   enabled,
                   coupon_type couponType,
                   monthly_stock monthlyStock,
                   used_stock usedStock,
                   exposure_weight_bonus exposureWeightBonus
            from merchant_reward_pool_config
            where merchant_id = #{merchantId}
              and deleted = 0
            limit 1
            """)
    MerchantQueryDTO findByMerchant(@Param("merchantId") Long merchantId);

    /**
     * 新增奖励池配置。
     */
    @Insert("""
            insert into merchant_reward_pool_config(
                id, merchant_id, enabled, coupon_type, monthly_stock,
                used_stock, exposure_weight_bonus, created_at, updated_at, deleted
            )
            values (
                #{id}, #{merchantId}, #{enabled}, #{couponType}, #{monthlyStock},
                0, #{exposureWeightBonus}, #{now}, #{now}, 0
            )
            """)
    int insertConfig(@Param("id") Long id,
                     @Param("merchantId") Long merchantId,
                     @Param("enabled") Boolean enabled,
                     @Param("couponType") String couponType,
                     @Param("monthlyStock") Integer monthlyStock,
                     @Param("exposureWeightBonus") BigDecimal exposureWeightBonus,
                     @Param("now") LocalDateTime now);

    /**
     * 更新奖励池配置。
     */
    @Update("""
            update merchant_reward_pool_config
            set enabled = #{enabled},
                coupon_type = #{couponType},
                monthly_stock = #{monthlyStock},
                exposure_weight_bonus = #{exposureWeightBonus},
                updated_at = #{now}
            where merchant_id = #{merchantId}
              and deleted = 0
            """)
    int updateConfig(@Param("merchantId") Long merchantId,
                     @Param("enabled") Boolean enabled,
                     @Param("couponType") String couponType,
                     @Param("monthlyStock") Integer monthlyStock,
                     @Param("exposureWeightBonus") BigDecimal exposureWeightBonus,
                     @Param("now") LocalDateTime now);
}
