package com.tongluxing.vehicle.mapper;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.tongluxing.vehicle.entity.VehicleProfile;

/**
 * 车辆档案 Mapper。
 *
 * <p>负责 vehicle_profile 表的增删改查；所有查询默认过滤逻辑删除数据。</p>
 */
@Mapper
public interface VehicleProfileMapper {

    /** 查询用户全部未删除车辆，默认车辆优先，其次按创建时间倒序。 */
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
            where user_id=#{userId} and deleted=0
            order by is_default desc, certification_status='APPROVED' desc, created_at desc
            limit 1
            """)
    VehicleProfile findMainByUserId(@Param("userId") Long userId);

    /** 查询当前用户拥有的指定车辆，用于鉴权和详情读取。 */
    @Select("""
            select id, user_id, plate_no_cipher, plate_no_mask, brand, model, vehicle_type, color,
                   seat_count, energy_type, vehicle_photo_image_key, certification_status, is_default as default_flag,
                   created_at, updated_at, deleted
            from vehicle_profile
            where id = #{vehicleId} and user_id = #{userId} and deleted = 0
            limit 1
            """)
    VehicleProfile findByIdAndUserId(@Param("vehicleId") Long vehicleId, @Param("userId") Long userId);

    /** 按车辆 ID 查询公开可展示车辆信息，不校验所属用户。 */
    @Select("""
            select id, user_id, plate_no_cipher, plate_no_mask, brand, model, vehicle_type, color,
                   seat_count, energy_type, vehicle_photo_image_key, certification_status, is_default as default_flag,
                   created_at, updated_at, deleted
            from vehicle_profile
            where id = #{vehicleId} and deleted = 0
            limit 1
            """)
    VehicleProfile findById(@Param("vehicleId") Long vehicleId);

    /** 按用户与车牌密文复用已有车辆档案，避免认证提交重复建车。 */
    @Select("""
            select id, user_id, plate_no_cipher, plate_no_mask, brand, model, vehicle_type, color,
                   seat_count, energy_type, vehicle_photo_image_key, certification_status, is_default as default_flag,
                   created_at, updated_at, deleted
            from vehicle_profile
            where user_id=#{userId} and plate_no_cipher=#{plateNoCipher} and deleted=0
            order by created_at desc limit 1
            """)
    VehicleProfile findByUserIdAndPlateNoCipher(@Param("userId") Long userId,
                                                @Param("plateNoCipher") String plateNoCipher);

    /** 统计用户未删除车辆数量，用于创建首辆车时自动设置默认车辆。 */
    @Select("""
            select count(1)
            from vehicle_profile
            where user_id = #{userId} and deleted = 0
            """)
    int countByUserId(@Param("userId") Long userId);

    /** 插入车辆档案。 */
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

    /** 更新车辆展示资料，不修改车牌号、默认标记和认证状态。 */
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

    /** 逻辑删除车辆，并取消默认车辆标记。 */
    @Update("""
            update vehicle_profile
            set deleted = 1, is_default = 0, updated_at = #{updatedAt}
            where id = #{vehicleId} and user_id = #{userId} and deleted = 0
            """)
    int logicDelete(@Param("vehicleId") Long vehicleId, @Param("userId") Long userId, @Param("updatedAt") LocalDateTime updatedAt);

    /** 清除用户全部默认车辆标记，配合 setDefault 保证单用户只有一辆默认车。 */
    @Update("""
            update vehicle_profile
            set is_default = 0, updated_at = #{updatedAt}
            where user_id = #{userId} and deleted = 0
            """)
    void clearDefault(@Param("userId") Long userId, @Param("updatedAt") LocalDateTime updatedAt);

    /** 将指定车辆设置为默认车辆。 */
    @Update("""
            update vehicle_profile
            set is_default = 1, updated_at = #{updatedAt}
            where id = #{vehicleId} and user_id = #{userId} and deleted = 0
            """)
    void setDefault(@Param("vehicleId") Long vehicleId, @Param("userId") Long userId, @Param("updatedAt") LocalDateTime updatedAt);

    /** 同步车辆认证状态。 */
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
