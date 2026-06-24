package com.tongdao.user.service;

import com.tongdao.user.model.UserModels.CertificationRequest;
import com.tongdao.user.model.UserModels.CertificationVO;
import com.tongdao.user.model.UserModels.PublicProfileVO;
import com.tongdao.user.model.UserModels.UpdateUserProfileRequest;
import com.tongdao.user.model.UserModels.UserProfileVO;

/**
 * 用户模块业务服务接口。
 *
 * <p>定义用户资料、实名认证和公开主页相关能力，供 Controller 调用。</p>
 */
public interface UserService {

    /**
     * 查询当前登录用户的完整个人资料。
     */
    UserProfileVO getCurrentProfile();

    /**
     * 修改当前登录用户资料，并返回修改后的最新资料。
     */
    UserProfileVO updateCurrentProfile(UpdateUserProfileRequest request);

    /**
     * 提交当前登录用户的实名认证申请。
     */
    CertificationVO submitCertification(CertificationRequest request);

    /**
     * 查询指定用户的公开主页资料。
     */
    PublicProfileVO getPublicProfile(Long userId);
}
