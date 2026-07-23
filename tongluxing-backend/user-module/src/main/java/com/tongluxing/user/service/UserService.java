package com.tongluxing.user.service;

import com.tongluxing.user.model.UserModels.CertificationRequest;
import com.tongluxing.user.model.UserModels.CertificationVO;
import com.tongluxing.user.model.UserModels.DrivingLicenseAuditDetailVO;
import com.tongluxing.user.model.UserModels.DrivingLicenseAuditSummaryVO;
import com.tongluxing.user.model.UserModels.PageResult;
import com.tongluxing.user.model.UserModels.PublicProfileVO;
import com.tongluxing.user.model.UserModels.UpdateUserProfileRequest;
import com.tongluxing.user.model.UserModels.UserProfileVO;
import com.tongluxing.user.model.UserModels.FollowStatusVO;
import com.tongluxing.user.model.UserModels.FollowUserVO;
import java.util.List;

/**
 * 用户模块业务服务接口。
 *
 * <p>定义用户资料、驾驶证认证和公开主页相关能力，供 Controller 与后台模块调用。</p>
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
     * 提交当前登录用户的驾驶证认证申请。
     */
    CertificationVO submitCertification(CertificationRequest request);

    /** 查询当前用户最近一次驾驶证认证状态。 */
    CertificationVO getLatestCertification();

    /** 后台分页查询驾驶证认证申请。 */
    PageResult<DrivingLicenseAuditSummaryVO> pageDrivingLicenseCertifications(
            String status, String keyword, int page, int size);

    /** 后台查询驾驶证认证详情。 */
    DrivingLicenseAuditDetailVO getDrivingLicenseCertificationForAudit(Long certificationId);

    /** 后台应用驾驶证人工审核结果。 */
    DrivingLicenseAuditDetailVO applyDrivingLicenseAuditResult(
            Long certificationId, String auditResult, String rejectReason, Long operatorId);

    /**
     * 查询指定用户的公开主页资料。
     */
    PublicProfileVO getPublicProfile(Long userId);

    /**
     * 查询聊天场景使用的基础用户资料。
     *
     * <p>该方法只返回昵称、头像和公开统计等聊天必需字段，不受公开主页隐藏开关影响，
     * 仅供已完成会话成员权限校验的内部模块调用。</p>
     */
    PublicProfileVO getChatMemberProfile(Long userId);

    FollowStatusVO follow(Long userId);

    FollowStatusVO unfollow(Long userId);

    FollowStatusVO getFollowStatus(Long userId);

    List<FollowUserVO> getFollowers(Long userId, int page, int size);

    List<FollowUserVO> getFollowing(Long userId, int page, int size);
}
