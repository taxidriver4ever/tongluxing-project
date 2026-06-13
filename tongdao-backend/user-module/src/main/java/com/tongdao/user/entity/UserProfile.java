package com.tongdao.user.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.Data;

@Data
public class UserProfile {

    private Long id;
    private Long userId;
    private String nickname;
    private String avatarUrl;
    private Integer gender;
    private LocalDate birthday;
    private String cityCode;
    private String cityName;
    private String bio;
    private Integer profileCompletion;
    private String realNameStatus;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;
}
