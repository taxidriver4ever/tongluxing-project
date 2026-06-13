package com.tongdao.user.controller;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tongdao.common.result.Result;
import com.tongdao.user.dto.EmergencyContactRequest;
import com.tongdao.user.dto.SubmitIdentityRequest;
import com.tongdao.user.dto.UpdateUserPrivacyRequest;
import com.tongdao.user.dto.UpdateUserProfileRequest;
import com.tongdao.user.service.UserService;
import com.tongdao.user.vo.EmergencyContactListResponse;
import com.tongdao.user.vo.EmergencyContactResponse;
import com.tongdao.user.vo.IdentityStatusResponse;
import com.tongdao.user.vo.PublicUserProfileResponse;
import com.tongdao.user.vo.UserPrivacyResponse;
import com.tongdao.user.vo.UserProfileResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/users")
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public Result<UserProfileResponse> getCurrentProfile() {
        return Result.success(userService.getCurrentProfile());
    }

    @PutMapping("/me/profile")
    public Result<UserProfileResponse> updateCurrentProfile(@Valid @RequestBody UpdateUserProfileRequest request) {
        return Result.success(userService.updateCurrentProfile(request));
    }

    @GetMapping("/me/privacy")
    public Result<UserPrivacyResponse> getCurrentPrivacy() {
        return Result.success(userService.getCurrentPrivacy());
    }

    @PutMapping("/me/privacy")
    public Result<UserPrivacyResponse> updateCurrentPrivacy(@Valid @RequestBody UpdateUserPrivacyRequest request) {
        return Result.success(userService.updateCurrentPrivacy(request));
    }

    @PostMapping("/me/identity-certification")
    public Result<IdentityStatusResponse> submitIdentity(@Valid @RequestBody SubmitIdentityRequest request) {
        return Result.success(userService.submitIdentity(request));
    }

    @GetMapping("/me/identity-certification")
    public Result<IdentityStatusResponse> getIdentityStatus() {
        return Result.success(userService.getIdentityStatus());
    }

    @GetMapping("/{userId}/public-profile")
    public Result<PublicUserProfileResponse> getPublicProfile(@PathVariable Long userId) {
        return Result.success(userService.getPublicProfile(userId));
    }

    @GetMapping("/me/emergency-contacts")
    public Result<EmergencyContactListResponse> getEmergencyContacts() {
        return Result.success(userService.getEmergencyContacts());
    }

    @PostMapping("/me/emergency-contacts")
    public Result<EmergencyContactResponse> addEmergencyContact(@Valid @RequestBody EmergencyContactRequest request) {
        return Result.success(userService.addEmergencyContact(request));
    }

    @PutMapping("/me/emergency-contacts/{contactId}")
    public Result<EmergencyContactResponse> updateEmergencyContact(
            @PathVariable Long contactId,
            @Valid @RequestBody EmergencyContactRequest request
    ) {
        return Result.success(userService.updateEmergencyContact(contactId, request));
    }

    @DeleteMapping("/me/emergency-contacts/{contactId}")
    public Result<Void> deleteEmergencyContact(@PathVariable Long contactId) {
        userService.deleteEmergencyContact(contactId);
        return Result.success();
    }
}
