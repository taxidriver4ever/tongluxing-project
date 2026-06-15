package com.tongdao.vehicle.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.tongdao.vehicle.entity.VehicleCertification;

@Mapper
public interface VehicleCertificationMapper {

    @Select("""
            select id, vehicle_id, user_id, owner_name, plate_no_cipher, plate_no_mask, vin_cipher, vin_mask,
                   engine_no_mask, license_image_key, status, reject_reason, submitted_at, reviewed_at, reviewer_id
            from vehicle_certification
            where vehicle_id = #{vehicleId}
            order by submitted_at desc
            limit 1
            """)
    VehicleCertification findLatestByVehicleId(@Param("vehicleId") Long vehicleId);

    @Insert("""
            insert into vehicle_certification
                (id, vehicle_id, user_id, owner_name, plate_no_cipher, plate_no_mask, vin_cipher, vin_mask,
                 engine_no_mask, license_image_key, status, reject_reason, submitted_at, reviewed_at, reviewer_id)
            values
                (#{id}, #{vehicleId}, #{userId}, #{ownerName}, #{plateNoCipher}, #{plateNoMask}, #{vinCipher}, #{vinMask},
                 #{engineNoMask}, #{licenseImageKey}, #{status}, #{rejectReason}, #{submittedAt}, #{reviewedAt}, #{reviewerId})
            """)
    void insert(VehicleCertification certification);
}
