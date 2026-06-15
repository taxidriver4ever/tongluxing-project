package com.tongdao.vehicle.service;

import com.tongdao.vehicle.dto.CreateVehicleRequest;
import com.tongdao.vehicle.dto.SubmitVehicleCertificationRequest;
import com.tongdao.vehicle.dto.UpdateVehicleRequest;
import com.tongdao.vehicle.vo.PublicVehicleCardResponse;
import com.tongdao.vehicle.vo.VehicleCertificationResponse;
import com.tongdao.vehicle.vo.VehicleListResponse;
import com.tongdao.vehicle.vo.VehicleResponse;

public interface VehicleService {

    VehicleListResponse getMyVehicles();

    VehicleResponse createVehicle(CreateVehicleRequest request);

    VehicleResponse getVehicle(Long vehicleId);

    VehicleResponse updateVehicle(Long vehicleId, UpdateVehicleRequest request);

    void deleteVehicle(Long vehicleId);

    VehicleResponse setDefaultVehicle(Long vehicleId);

    VehicleCertificationResponse submitCertification(Long vehicleId, SubmitVehicleCertificationRequest request);

    VehicleCertificationResponse getCertification(Long vehicleId);

    PublicVehicleCardResponse getPublicCard(Long vehicleId);
}
