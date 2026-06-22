package com.tongdao.user.controller;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tongdao.common.result.Result;
import com.tongdao.user.model.UserModels.CertificationRequest;
import com.tongdao.user.model.UserModels.CertificationVO;
import com.tongdao.user.model.UserModels.PublicProfileVO;
import com.tongdao.user.model.UserModels.UpdateUserProfileRequest;
import com.tongdao.user.model.UserModels.UserProfileVO;
import com.tongdao.user.service.UserService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/users")
public class UserController {
    private final UserService userService;

    @GetMapping("/me")
    public Result<UserProfileVO> me() { return Result.success(userService.getCurrentProfile()); }

    @PutMapping("/me/profile")
    public Result<UserProfileVO> updateProfile(@Valid @RequestBody UpdateUserProfileRequest request) {
        return Result.success(userService.updateCurrentProfile(request));
    }

    @PostMapping("/me/certifications")
    public Result<CertificationVO> certify(@Valid @RequestBody CertificationRequest request) {
        return Result.success(userService.submitCertification(request));
    }

    @GetMapping("/{userId}/public-profile")
    public Result<PublicProfileVO> publicProfile(@PathVariable Long userId) {
        return Result.success(userService.getPublicProfile(userId));
    }
}
