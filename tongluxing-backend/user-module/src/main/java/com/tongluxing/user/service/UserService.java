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
import com.tongluxing.user.model.UserModels.UserSearchVO;
import com.tongluxing.user.model.UserModels.PrivacySettingsVO;
import com.tongluxing.user.model.UserModels.UpdatePrivacySettingsRequest;
import java.util.List;

/**
 * 用户模块业务服务接口。
 *
 * <p>定义用户资料、驾驶证认证和公开主页相关能力，供 Controller 与后台模块调用。</p>
 */
public interface UserService {

    /**
     * 查询当前登录用户的完整个人资料。
     *
     * @return 用户资料及最新驾驶证认证状态；历史用户会在查询时完成默认资料初始化
     */
    UserProfileVO getCurrentProfile();

    /**
     * 修改当前登录用户资料，并返回修改后的最新资料。
     *
     * @param request 增量更新请求，值为 {@code null} 的字段保持不变
     * @return 数据库实际保存的最新完整资料
     */
    UserProfileVO updateCurrentProfile(UpdateUserProfileRequest request);

    /** @return 当前登录用户的全部隐私设置，尚未初始化时自动创建默认值 */
    PrivacySettingsVO getCurrentPrivacySettings();

    /**
     * 增量修改当前登录用户隐私设置。
     *
     * @param request 非空字段将覆盖旧设置
     * @return 持久化后的全部隐私设置
     */
    PrivacySettingsVO updateCurrentPrivacySettings(UpdatePrivacySettingsRequest request);

    /**
     * 供内部应用聚合层读取指定用户的公开范围。
     *
     * @param userId 目标平台用户 ID
     * @return 目标用户全部隐私开关；调用方不得直接作为公开接口响应
     */
    PrivacySettingsVO getPrivacySettings(Long userId);

    /**
     * 提交当前登录用户的驾驶证认证申请。
     *
     * @param request 用户确认后的证件信息、图片 Key 和识别来源
     * @return 新创建的 PENDING 认证状态
     * @throws com.tongluxing.common.exception.BusinessException 待审核、已通过或日期不合法时抛出
     */
    CertificationVO submitCertification(CertificationRequest request);

    /** @return 当前用户最新认证；从未申请时返回可重新提交的 UNSUBMITTED 对象 */
    CertificationVO getLatestCertification();

    /**
     * 后台分页查询驾驶证认证申请。
     *
     * @param status 可选状态过滤，只接受 PENDING、APPROVED、REJECTED
     * @param keyword 可选用户 ID 或脱敏证件号关键词
     * @param page 从 1 开始的页码
     * @param size 每页数量，最大 100
     */
    PageResult<DrivingLicenseAuditSummaryVO> pageDrivingLicenseCertifications(
            String status, String keyword, int page, int size);

    /** @return 包含授权解密字段的审核详情，仅供后台审核链路使用 */
    DrivingLicenseAuditDetailVO getDrivingLicenseCertificationForAudit(Long certificationId);

    /**
     * 后台应用驾驶证人工审核结果。
     *
     * @param certificationId 认证申请主键
     * @param auditResult APPROVED 或 REJECTED
     * @param rejectReason 驳回时必填，通过时会被清空
     * @param operatorId 后台审核员 ID
     * @return 持久化后的审核详情
     */
    DrivingLicenseAuditDetailVO applyDrivingLicenseAuditResult(
            Long certificationId, String auditResult, String rejectReason, Long operatorId);

    /**
     * 查询指定用户的公开主页资料。
     *
     * @param userId 目标用户 ID
     * @return 已按城市、简介和统计开关裁剪的资料
     * @throws com.tongluxing.common.exception.BusinessException 用户不存在或主页为 PRIVATE 时抛出
     */
    PublicProfileVO getPublicProfile(Long userId);

    /**
     * 查询聊天场景使用的基础用户资料。
     *
     * <p>该方法只返回昵称、头像和公开统计等聊天必需字段，不受公开主页隐藏开关影响，
     * 仅供已完成会话成员权限校验的内部模块调用。</p>
     */
    PublicProfileVO getChatMemberProfile(Long userId);

    /** 当前登录用户关注目标用户；重复关注按幂等成功处理。 */
    FollowStatusVO follow(Long userId);

    /** 当前登录用户取消关注目标用户；关系不存在时仍按成功处理。 */
    FollowStatusVO unfollow(Long userId);

    /** 查询当前登录用户与目标用户的双向关系及目标关注计数。 */
    FollowStatusVO getFollowStatus(Long userId);

    /** 查询当前登录用户的粉丝列表。 */
    List<FollowUserVO> getMyFollowers(int page, int size);

    /** @return 当前登录用户未读的新关注通知数 */
    long countMyUnreadFollowerNotifications();

    /** 把当前登录用户所有未读的新关注通知标记为已读。 */
    void markMyFollowerNotificationsRead();

    /** 查询当前登录用户主动关注的人。 */
    List<FollowUserVO> getMyFollowing(int page, int size);

    /** 查询当前登录用户的互相关注列表。 */
    List<FollowUserVO> getMyMutualFollows(int page, int size);

    /** 查询指定用户的粉丝；查看他人时受公开主页可见性限制。 */
    List<FollowUserVO> getFollowers(Long userId, int page, int size);

    /** 查询指定用户关注的人；查看他人时受公开主页可见性限制。 */
    List<FollowUserVO> getFollowing(Long userId, int page, int size);

    /**
     * 按昵称、城市、简介、同路行号、用户 ID 或完整手机号搜索可公开展示的同路人。
     *
     * @return 公开资料摘要以及每个用户相对当前登录用户的关注关系
     */
    List<UserSearchVO> searchPublicUsers(String keyword, int page, int size);
}
