package com.tongluxing.vehicle.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.tongluxing.vehicle.entity.VehicleCertificationImage;

/** 车辆认证图片明细 Mapper。 */
@Mapper
public interface VehicleCertificationImageMapper {

    /** 插入一张认证附件，图片二进制内容由对象存储管理。 */
    @Insert("""
            insert into vehicle_certification_image
                (id,certification_id,vehicle_id,image_type,image_key,sort_no,created_at,deleted)
            values
                (#{id},#{certificationId},#{vehicleId},#{imageType},#{imageKey},#{sortNo},#{createdAt},0)
            """)
    int insert(VehicleCertificationImage image);

    /** 按认证申请查询未删除附件，按业务排序号稳定返回。 */
    @Select("""
            select id,certification_id,vehicle_id,image_type,image_key,sort_no,created_at,deleted
            from vehicle_certification_image
            where certification_id=#{certificationId} and deleted=0
            order by sort_no asc,id asc
            """)
    List<VehicleCertificationImage> findByCertificationId(@Param("certificationId") Long certificationId);

    /** 删除驳回记录时删除该车辆关联的认证图片明细，SQL 位于 XML。 */
    int deleteByVehicleId(@Param("vehicleId") Long vehicleId);
}
