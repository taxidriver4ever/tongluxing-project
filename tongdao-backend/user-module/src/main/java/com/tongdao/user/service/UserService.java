package com.tongdao.user.service;

import com.tongdao.user.model.UserModels.CertificationRequest;
import com.tongdao.user.model.UserModels.CertificationVO;
import com.tongdao.user.model.UserModels.PublicProfileVO;
import com.tongdao.user.model.UserModels.UpdateUserProfileRequest;
import com.tongdao.user.model.UserModels.UserProfileVO;

public interface UserService {
    UserProfileVO getCurrentProfile();
    UserProfileVO updateCurrentProfile(UpdateUserProfileRequest request);
    CertificationVO submitCertification(CertificationRequest request);
    PublicProfileVO getPublicProfile(Long userId);
}
