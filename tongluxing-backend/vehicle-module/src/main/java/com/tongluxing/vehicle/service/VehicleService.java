package com.tongluxing.vehicle.service;

import com.tongluxing.vehicle.dto.CreateVehicleRequest;
import com.tongluxing.vehicle.dto.SubmitVehicleCertificationRequest;
import com.tongluxing.vehicle.dto.UpdateVehicleRequest;
import com.tongluxing.vehicle.vo.PublicVehicleCardResponse;
import com.tongluxing.vehicle.vo.VehicleCertificationResponse;
import com.tongluxing.vehicle.vo.VehicleListResponse;
import com.tongluxing.vehicle.vo.VehicleResponse;

/**
 * 车辆模块业务服务。
 *
 * <p>统一承载车辆档案、认证资料、默认车辆和公开车辆卡片的业务规则。</p>
 */
public interface VehicleService {

    /** 查询当前登录用户的全部车辆。 */
    VehicleListResponse getMyVehicles();

    /** 创建当前登录用户的车辆档案。 */
    VehicleResponse createVehicle(CreateVehicleRequest request);

    /** 查询当前登录用户名下指定车辆详情。 */
    VehicleResponse getVehicle(Long vehicleId);

    /** 更新当前登录用户名下指定车辆资料。 */
    VehicleResponse updateVehicle(Long vehicleId, UpdateVehicleRequest request);

    /** 逻辑删除当前登录用户名下指定车辆。 */
    void deleteVehicle(Long vehicleId);

    /** 设置当前登录用户名下默认车辆。 */
    VehicleResponse setDefaultVehicle(Long vehicleId);

    /** 提交车辆认证资料，进入待审核状态。 */
    VehicleCertificationResponse submitCertification(Long vehicleId, SubmitVehicleCertificationRequest request);

    /** 查询车辆最近一次认证记录；未提交时返回未认证状态。 */
    VehicleCertificationResponse getCertification(Long vehicleId);

    /** 查询车辆公开卡片信息，不包含敏感明文字段。 */
    PublicVehicleCardResponse getPublicCard(Long vehicleId);
}
