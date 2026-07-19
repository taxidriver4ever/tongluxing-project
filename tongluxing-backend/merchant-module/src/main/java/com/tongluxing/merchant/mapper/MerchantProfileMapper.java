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
            select m.id merchantId,
                   m.user_id userId,
                   m.merchant_name merchantName,
                   m.category,
                   m.contact_name contactName,
                   m.contact_phone_mask contactPhoneMask,
                   m.province_code provinceCode,
                   m.city_code cityCode,
                   m.address,
                   m.longitude,
                   m.latitude,
                   m.cover_image_key coverImageKey,
                   m.description,
                   m.license_image_key licenseImageKey,
                   m.qualification_json qualificationJson,
                   m.audit_status auditStatus,
                   coalesce(r.reject_reason, '') rejectReason,
                   r.reviewed_at reviewedAt,
                   m.merchant_level merchantLevel,
                   m.score,
                   m.commission_rate commissionRate,
                   m.rank_weight rankWeight,
                   m.exclusion_radius_km exclusionRadiusKm,
                   m.status,
                   m.created_at createdAt,
                   m.updated_at updatedAt
            from merchant_profile m
            left join merchant_application_review r on r.merchant_id = m.id
            where m.user_id = #{userId}
              and m.deleted = 0
            limit 1
            """)
    MerchantQueryDTO findByUserId(@Param("userId") Long userId);

    /**
     * 根据商家 ID 查询商家资料。
     */
    @Select("""
            select m.id merchantId,
                   m.user_id userId,
                   m.merchant_name merchantName,
                   m.category,
                   m.contact_name contactName,
                   m.contact_phone_mask contactPhoneMask,
                   m.province_code provinceCode,
                   m.city_code cityCode,
                   m.address,
                   m.longitude,
                   m.latitude,
                   m.cover_image_key coverImageKey,
                   m.description,
                   m.license_image_key licenseImageKey,
                   m.qualification_json qualificationJson,
                   m.audit_status auditStatus,
                   coalesce(r.reject_reason, '') rejectReason,
                   r.reviewed_at reviewedAt,
                   m.merchant_level merchantLevel,
                   m.score,
                   m.commission_rate commissionRate,
                   m.rank_weight rankWeight,
                   m.exclusion_radius_km exclusionRadiusKm,
                   m.status,
                   m.created_at createdAt,
                   m.updated_at updatedAt
            from merchant_profile m
            left join merchant_application_review r on r.merchant_id = m.id
            where m.id = #{merchantId}
              and m.deleted = 0
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

    @Update("""
            update merchant_profile
            set merchant_name=#{merchantName}, category=#{category}, contact_name=#{contactName},
                contact_phone_cipher=#{contactPhoneCipher}, contact_phone_mask=#{contactPhoneMask},
                address=#{address}, longitude=#{longitude}, latitude=#{latitude},
                cover_image_key=#{logoImageKey}, description=#{introduction},
                license_image_key=#{licenseImageKey}, qualification_json=#{qualificationJson},
                audit_status='PENDING', updated_at=#{now}
            where id=#{merchantId} and user_id=#{userId} and audit_status='REJECTED' and deleted=0
            """)
    int resubmitApplication(@Param("merchantId") Long merchantId, @Param("userId") Long userId,
                            @Param("merchantName") String merchantName, @Param("category") String category,
                            @Param("contactName") String contactName,
                            @Param("contactPhoneCipher") String contactPhoneCipher,
                            @Param("contactPhoneMask") String contactPhoneMask,
                            @Param("address") String address, @Param("longitude") BigDecimal longitude,
                            @Param("latitude") BigDecimal latitude, @Param("logoImageKey") String logoImageKey,
                            @Param("introduction") String introduction,
                            @Param("licenseImageKey") String licenseImageKey,
                            @Param("qualificationJson") String qualificationJson,
                            @Param("now") LocalDateTime now);

    @Update("""
            update merchant_profile set audit_status=#{auditStatus}, updated_at=#{now}
            where id=#{merchantId} and audit_status='PENDING' and deleted=0
            """)
    int updateAuditStatus(@Param("merchantId") Long merchantId, @Param("auditStatus") String auditStatus,
                          @Param("now") LocalDateTime now);

    @Insert("""
            insert into merchant_application_review(merchant_id,reject_reason,reviewer_id,reviewed_at,updated_at)
            values(#{merchantId},#{rejectReason},#{reviewerId},#{now},#{now})
            on duplicate key update reject_reason=values(reject_reason),reviewer_id=values(reviewer_id),
                                    reviewed_at=values(reviewed_at),updated_at=values(updated_at)
            """)
    int saveReview(@Param("merchantId") Long merchantId, @Param("rejectReason") String rejectReason,
                   @Param("reviewerId") Long reviewerId, @Param("now") LocalDateTime now);

    @Select("""
            select m.id merchantId, m.user_id userId, m.merchant_name merchantName, m.category,
                   m.contact_name contactName, m.contact_phone_mask contactPhoneMask,
                   m.address, m.longitude, m.latitude, m.cover_image_key coverImageKey,
                   m.description, m.license_image_key licenseImageKey, m.qualification_json qualificationJson,
                   m.audit_status auditStatus, coalesce(r.reject_reason,'') rejectReason,
                   r.reviewed_at reviewedAt, m.merchant_level merchantLevel, m.score,
                   m.commission_rate commissionRate, m.rank_weight rankWeight,
                   m.exclusion_radius_km exclusionRadiusKm, m.status,
                   m.created_at createdAt, m.updated_at updatedAt
            from merchant_profile m left join merchant_application_review r on r.merchant_id=m.id
            where (#{status}='' or m.audit_status=#{status}) and m.deleted=0
            order by m.created_at desc limit #{offset},#{size}
            """)
    java.util.List<MerchantQueryDTO> findApplications(@Param("status") String status,
                                                       @Param("offset") int offset, @Param("size") int size);

    @Select("""
            select count(*) from merchant_profile
            where (#{status}='' or audit_status=#{status}) and deleted=0
            """)
    long countApplications(@Param("status") String status);

    @Insert("""
            insert ignore into auth_user_role(user_id,role_code,granted_at)
            values(#{userId},'MERCHANT',#{now})
            """)
    int grantMerchantRole(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    @Insert("""
            insert into merchant_settlement_account(merchant_id,account_type,account_name,account_no_cipher,bank_name,created_at,updated_at,deleted)
            values(#{merchantId},#{accountType},#{accountName},#{accountNoCipher},#{bankName},#{now},#{now},0)
            on duplicate key update account_type=values(account_type),account_name=values(account_name),
                                    account_no_cipher=values(account_no_cipher),bank_name=values(bank_name),
                                    updated_at=values(updated_at),deleted=0
            """)
    int saveSettlement(@Param("merchantId") Long merchantId, @Param("accountType") String accountType,
                       @Param("accountName") String accountName, @Param("accountNoCipher") String accountNoCipher,
                       @Param("bankName") String bankName, @Param("now") LocalDateTime now);

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
