package com.tongdao.vehicle.entity;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class VehicleProfile {
    private Long id;
    private Long userId;
    private String plateNoCipher;
    private String plateNoMask;
    private String brand;
    private String model;
    private String vehicleType;
    private String color;
    private Integer seatCount;
    private String energyType;
    private String vehiclePhotoImageKey;
    private String certificationStatus;
    private Integer defaultFlag;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;
}
