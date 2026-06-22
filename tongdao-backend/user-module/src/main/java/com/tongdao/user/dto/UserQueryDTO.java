package com.tongdao.user.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.Data;

@Data
public class UserQueryDTO {
    private Long id;
    private Long userId;
    private String nickname;
    private String avatarImageKey;
    private Integer gender;
    private LocalDate birthday;
    private String cityCode;
    private String cityName;
    private String bio;
    private String profileStatus;
    private String certificationStatus;
    private String rejectReason;
    private LocalDateTime submittedAt;
    private LocalDateTime reviewedAt;
    private String profileVisibility;
    private String vehicleVisibility;
    private Boolean inviteEnabledFlag;
}
