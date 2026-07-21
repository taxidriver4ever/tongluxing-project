package com.tongluxing.vehicle.mapper;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.tongluxing.vehicle.entity.VehicleCertification;

/**
 * 车辆认证记录 Mapper。
 */
@Mapper
public interface VehicleCertificationMapper {

    /** 查询车辆最近一次认证记录。 */
    @Select("""
            select id,vehicle_id,user_id,owner_name,plate_no_cipher,plate_no_mask,vehicle_type,vin_cipher,vin_mask,
                   engine_no_cipher,engine_no_mask,register_date,issue_date,issuing_authority,
                   license_front_image_key,license_back_image_key,recognition_source,status,reject_reason,
                   submitted_at,reviewed_at,reviewer_id
            from vehicle_certification
            where vehicle_id = #{vehicleId}
            order by submitted_at desc, id desc
            limit 1
            """)
    VehicleCertification findLatestByVehicleId(@Param("vehicleId") Long vehicleId);

    /** 查询当前用户最近一次车辆认证申请。 */
    @Select("""
            select id,vehicle_id,user_id,owner_name,plate_no_cipher,plate_no_mask,vehicle_type,vin_cipher,vin_mask,
                   engine_no_cipher,engine_no_mask,register_date,issue_date,issuing_authority,
                   license_front_image_key,license_back_image_key,recognition_source,status,reject_reason,
                   submitted_at,reviewed_at,reviewer_id
            from vehicle_certification
            where user_id=#{userId}
            order by submitted_at desc, id desc
            limit 1
            """)
    VehicleCertification findLatestByUserId(@Param("userId") Long userId);

    /** 按稳定车牌密文查询全平台已通过认证，阻止同一车辆重复认证。 */
    @Select("""
            select id,vehicle_id,user_id,owner_name,plate_no_cipher,plate_no_mask,vehicle_type,vin_cipher,vin_mask,
                   engine_no_cipher,engine_no_mask,register_date,issue_date,issuing_authority,
                   license_front_image_key,license_back_image_key,recognition_source,status,reject_reason,
                   submitted_at,reviewed_at,reviewer_id
            from vehicle_certification
            where status = 'APPROVED'
              and plate_no_cipher in (#{plateNoCipher}, #{legacyPlateNoCipher})
            order by reviewed_at desc, submitted_at desc, id desc
            limit 1
            """)
    VehicleCertification findApprovedByPlateNoCipher(
            @Param("plateNoCipher") String plateNoCipher,
            @Param("legacyPlateNoCipher") String legacyPlateNoCipher);

    /** 新增一条车辆认证提交记录。 */
    @Insert("""
            insert into vehicle_certification
                (id,vehicle_id,user_id,owner_name,plate_no_cipher,plate_no_mask,vehicle_type,vin_cipher,vin_mask,
                 engine_no_cipher,engine_no_mask,register_date,issue_date,issuing_authority,
                 license_front_image_key,license_back_image_key,recognition_source,status,reject_reason,
                 submitted_at,reviewed_at,reviewer_id)
            values
                (#{id},#{vehicleId},#{userId},#{ownerName},#{plateNoCipher},#{plateNoMask},#{vehicleType},#{vinCipher},#{vinMask},
                 #{engineNoCipher},#{engineNoMask},#{registerDate},#{issueDate},#{issuingAuthority},
                 #{licenseFrontImageKey},#{licenseBackImageKey},#{recognitionSource},#{status},#{rejectReason},
                 #{submittedAt},#{reviewedAt},#{reviewerId})
            """)
    void insert(VehicleCertification certification);

    /** 分页查询后台车辆认证列表，SQL 定义在 VehicleCertificationMapper.xml。 */
    List<VehicleCertification> page(@Param("status") String status, @Param("keyword") String keyword,
                                    @Param("offset") int offset, @Param("size") int size);

    /** 统计后台车辆认证列表，SQL 定义在 VehicleCertificationMapper.xml。 */
    long count(@Param("status") String status, @Param("keyword") String keyword);

    /** 按认证申请 ID 查询详情。 */
    @Select("""
            select id,vehicle_id,user_id,owner_name,plate_no_cipher,plate_no_mask,vehicle_type,vin_cipher,vin_mask,
                   engine_no_cipher,engine_no_mask,register_date,issue_date,issuing_authority,
                   license_front_image_key,license_back_image_key,recognition_source,status,reject_reason,
                   submitted_at,reviewed_at,reviewer_id
            from vehicle_certification where id=#{certificationId} limit 1
            """)
    VehicleCertification findById(@Param("certificationId") Long certificationId);

    /** 人工审核车辆认证，仅允许更新待审核申请。 */
    @Update("""
            update vehicle_certification
            set status=#{auditResult},reject_reason=coalesce(#{rejectReason},''),reviewer_id=#{operatorId},reviewed_at=#{now}
            where id=#{certificationId} and status='PENDING'
            """)
    int updateAudit(@Param("certificationId") Long certificationId,
                    @Param("auditResult") String auditResult,
                    @Param("rejectReason") String rejectReason,
                    @Param("operatorId") Long operatorId,
                    @Param("now") LocalDateTime now);
}
