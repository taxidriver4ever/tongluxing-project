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
 * 商家基础资料数据访问接口。
 */
@Mapper
public interface MerchantProfileMapper {

    /**
     * 根据登录用户查询其商家主体。
     */
    @Select("""
            select id merchantId,
                   user_id userId,
                   merchant_name merchantName,
                   category,
                   contact_name contactName,
                   contact_phone_mask contactPhoneMask,
                   province_code provinceCode,
                   city_code cityCode,
                   address,
                   longitude,
                   latitude,
                   cover_image_key coverImageKey,
                   description,
                   audit_status auditStatus,
                   merchant_level merchantLevel,
                   score,
                   commission_rate commissionRate,
                   rank_weight rankWeight,
                   exclusion_radius_km exclusionRadiusKm,
                   status,
                   created_at createdAt,
                   updated_at updatedAt
            from merchant_profile
            where user_id = #{userId}
              and deleted = 0
            limit 1
            """)
    MerchantQueryDTO findByUserId(@Param("userId") Long userId);

    /**
     * 根据商家 ID 查询商家资料。
     */
    @Select("""
            select id merchantId,
                   user_id userId,
                   merchant_name merchantName,
                   category,
                   contact_name contactName,
                   contact_phone_mask contactPhoneMask,
                   province_code provinceCode,
                   city_code cityCode,
                   address,
                   longitude,
                   latitude,
                   cover_image_key coverImageKey,
                   description,
                   audit_status auditStatus,
                   merchant_level merchantLevel,
                   score,
                   commission_rate commissionRate,
                   rank_weight rankWeight,
                   exclusion_radius_km exclusionRadiusKm,
                   status,
                   created_at createdAt,
                   updated_at updatedAt
            from merchant_profile
            where id = #{merchantId}
              and deleted = 0
            limit 1
            """)
    MerchantQueryDTO findById(@Param("merchantId") Long merchantId);

    /**
     * 新增待审核商家入驻资料。
     */
    @Insert("""
            insert into merchant_profile(
                id, user_id, merchant_name, category, contact_name,
                contact_phone_cipher, contact_phone_mask, province_code, city_code,
                address, longitude, latitude, cover_image_key, description,
                license_image_key, qualification_json, bank_account_cipher, bank_name,
                audit_status, merchant_level, score, commission_rate, rank_weight,
                exclusion_radius_km, status, created_at, updated_at, deleted
            )
            values (
                #{id}, #{userId}, #{merchantName}, #{category}, #{contactName},
                #{contactPhoneCipher}, #{contactPhoneMask}, #{provinceCode}, #{cityCode},
                #{address}, #{longitude}, #{latitude}, '', '',
                #{licenseImageKey}, #{qualificationJson}, #{bankAccountCipher}, #{bankName},
                'PENDING', 'L1', 0.00, 0.0800, 1.0000,
                0.00, 'ACTIVE', #{now}, #{now}, 0
            )
            """)
    int insertApplication(@Param("id") Long id,
                          @Param("userId") Long userId,
                          @Param("merchantName") String merchantName,
                          @Param("category") String category,
                          @Param("contactName") String contactName,
                          @Param("contactPhoneCipher") String contactPhoneCipher,
                          @Param("contactPhoneMask") String contactPhoneMask,
                          @Param("provinceCode") String provinceCode,
                          @Param("cityCode") String cityCode,
                          @Param("address") String address,
                          @Param("longitude") BigDecimal longitude,
                          @Param("latitude") BigDecimal latitude,
                          @Param("licenseImageKey") String licenseImageKey,
                          @Param("qualificationJson") String qualificationJson,
                          @Param("bankAccountCipher") String bankAccountCipher,
                          @Param("bankName") String bankName,
                          @Param("now") LocalDateTime now);

    /**
     * 更新商家资料基础字段。
     */
    @Update("""
            update merchant_profile
            set merchant_name = #{merchantName},
                category = #{category},
                contact_name = #{contactName},
                contact_phone_cipher = coalesce(#{contactPhoneCipher}, contact_phone_cipher),
                contact_phone_mask = #{contactPhoneMask},
                address = #{address},
                longitude = #{longitude},
                latitude = #{latitude},
                cover_image_key = #{coverImageKey},
                description = #{description},
                updated_at = #{now}
            where id = #{merchantId}
              and user_id = #{userId}
              and deleted = 0
            """)
    int updateProfile(@Param("merchantId") Long merchantId,
                      @Param("userId") Long userId,
                      @Param("merchantName") String merchantName,
                      @Param("category") String category,
                      @Param("contactName") String contactName,
                      @Param("contactPhoneCipher") String contactPhoneCipher,
                      @Param("contactPhoneMask") String contactPhoneMask,
                      @Param("address") String address,
                      @Param("longitude") BigDecimal longitude,
                      @Param("latitude") BigDecimal latitude,
                      @Param("coverImageKey") String coverImageKey,
                      @Param("description") String description,
                      @Param("now") LocalDateTime now);
}
