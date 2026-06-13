package com.tongdao.user.service;

import com.tongdao.user.dto.EmergencyContactRequest;
import com.tongdao.user.dto.SubmitIdentityRequest;
import com.tongdao.user.dto.UpdateUserPrivacyRequest;
import com.tongdao.user.dto.UpdateUserProfileRequest;
import com.tongdao.user.vo.EmergencyContactListResponse;
import com.tongdao.user.vo.EmergencyContactResponse;
import com.tongdao.user.vo.IdentityStatusResponse;
import com.tongdao.user.vo.PublicUserProfileResponse;
import com.tongdao.user.vo.UserPrivacyResponse;
import com.tongdao.user.vo.UserProfileResponse;

public interface UserService {

    UserProfileResponse getCurrentProfile();

    UserProfileResponse updateCurrentProfile(UpdateUserProfileRequest request);

    UserPrivacyResponse getCurrentPrivacy();

    UserPrivacyResponse updateCurrentPrivacy(UpdateUserPrivacyRequest request);

    IdentityStatusResponse submitIdentity(SubmitIdentityRequest request);

    IdentityStatusResponse getIdentityStatus();

    PublicUserProfileResponse getPublicProfile(Long userId);

    EmergencyContactListResponse getEmergencyContacts();

    EmergencyContactResponse addEmergencyContact(EmergencyContactRequest request);

    EmergencyContactResponse updateEmergencyContact(Long contactId, EmergencyContactRequest request);

    void deleteEmergencyContact(Long contactId);
}
