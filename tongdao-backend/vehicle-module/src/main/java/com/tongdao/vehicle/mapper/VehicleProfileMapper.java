package com.tongdao.vehicle.mapper;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.tongdao.vehicle.entity.VehicleProfile;

@Mapper
public interface VehicleProfileMapper {

    @Select("""
            select id, user_id, plate_no_cipher, plate_no_mask, brand, model, vehicle_type, color,
                   seat_count, energy_type, vehicle_photo_image_key, certification_status, is_default as default_flag,
                   created_at, updated_at, deleted
            from vehicle_profile
            where user_id = #{userId} and deleted = 0
            order by is_default desc, created_at desc
            """)
    List<VehicleProfile> findByUserId(@Param("userId") Long userId);

    @Select("""
            select id, user_id, plate_no_cipher, plate_no_mask, brand, model, vehicle_type, color,
                   seat_count, energy_type, vehicle_photo_image_key, certification_status, is_default as default_flag,
                   created_at, updated_at, deleted
            from vehicle_profile
            where id = #{vehicleId} and user_id = #{userId} and deleted = 0
            limit 1
            """)
    VehicleProfile findByIdAndUserId(@Param("vehicleId") Long vehicleId, @Param("userId") Long userId);

    @Select("""
            select id, user_id, plate_no_cipher, plate_no_mask, brand, model, vehicle_type, color,
                   seat_count, energy_type, vehicle_photo_image_key, certification_status, is_default as default_flag,
                   created_at, updated_at, deleted
            from vehicle_profile
            where id = #{vehicleId} and deleted = 0
            limit 1
            """)
    VehicleProfile findById(@Param("vehicleId") Long vehicleId);

    @Select("""
            select count(1)
            from vehicle_profile
            where user_id = #{userId} and deleted = 0
            """)
    int countByUserId(@Param("userId") Long userId);

    @Insert("""
            insert into vehicle_profile
                (id, user_id, plate_no_cipher, plate_no_mask, brand, model, vehicle_type, color,
                 seat_count, energy_type, vehicle_photo_image_key, certification_status, is_default,
                 created_at, updated_at, deleted)
            values
                (#{id}, #{userId}, #{plateNoCipher}, #{plateNoMask}, #{brand}, #{model}, #{vehicleType}, #{color},
                 #{seatCount}, #{energyType}, #{vehiclePhotoImageKey}, #{certificationStatus}, #{defaultFlag},
                 #{createdAt}, #{updatedAt}, 0)
            """)
    void insert(VehicleProfile vehicle);

    @Update("""
            update vehicle_profile
            set brand = #{brand},
                model = #{model},
                vehicle_type = #{vehicleType},
                color = #{color},
                seat_count = #{seatCount},
                energy_type = #{energyType},
                vehicle_photo_image_key = #{vehiclePhotoImageKey},
                updated_at = #{updatedAt}
            where id = #{id} and user_id = #{userId} and deleted = 0
            """)
    void update(VehicleProfile vehicle);

    @Update("""
            update vehicle_profile
            set deleted = 1, is_default = 0, updated_at = #{updatedAt}
            where id = #{vehicleId} and user_id = #{userId} and deleted = 0
            """)
    int logicDelete(@Param("vehicleId") Long vehicleId, @Param("userId") Long userId, @Param("updatedAt") LocalDateTime updatedAt);

    @Update("""
            update vehicle_profile
            set is_default = 0, updated_at = #{updatedAt}
            where user_id = #{userId} and deleted = 0
            """)
    void clearDefault(@Param("userId") Long userId, @Param("updatedAt") LocalDateTime updatedAt);

    @Update("""
            update vehicle_profile
            set is_default = 1, updated_at = #{updatedAt}
            where id = #{vehicleId} and user_id = #{userId} and deleted = 0
            """)
    void setDefault(@Param("vehicleId") Long vehicleId, @Param("userId") Long userId, @Param("updatedAt") LocalDateTime updatedAt);

    @Update("""
            update vehicle_profile
            set certification_status = #{status}, updated_at = #{updatedAt}
            where id = #{vehicleId} and user_id = #{userId} and deleted = 0
            """)
    void updateCertificationStatus(
            @Param("vehicleId") Long vehicleId,
            @Param("userId") Long userId,
            @Param("status") String status,
            @Param("updatedAt") LocalDateTime updatedAt
    );
}
