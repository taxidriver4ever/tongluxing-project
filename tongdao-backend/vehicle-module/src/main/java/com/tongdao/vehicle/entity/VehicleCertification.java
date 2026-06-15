package com.tongdao.vehicle.entity;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class VehicleCertification {
    private Long id;
    private Long vehicleId;
    private Long userId;
    private String ownerName;
    private String plateNoCipher;
    private String plateNoMask;
    private String vinCipher;
    private String vinMask;
    private String engineNoMask;
    private String licenseImageKey;
    private String status;
    private String rejectReason;
    private LocalDateTime submittedAt;
    private LocalDateTime reviewedAt;
    private Long reviewerId;
}
