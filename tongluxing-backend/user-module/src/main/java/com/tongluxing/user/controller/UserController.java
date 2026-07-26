package com.tongluxing.user.controller;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tongluxing.common.result.Result;
import com.tongluxing.user.model.UserModels.CertificationRequest;
import com.tongluxing.user.model.UserModels.CertificationVO;
import com.tongluxing.user.model.UserModels.PublicProfileVO;
import com.tongluxing.user.model.UserModels.UpdateUserProfileRequest;
import com.tongluxing.user.model.UserModels.UserProfileVO;
import com.tongluxing.user.model.UserModels.FollowStatusVO;
import com.tongluxing.user.model.UserModels.FollowUserVO;
import com.tongluxing.user.model.UserModels.UserSearchVO;
import com.tongluxing.user.model.UserModels.PrivacySettingsVO;
import com.tongluxing.user.model.UserModels.UpdatePrivacySettingsRequest;
import java.util.List;
import org.springframework.web.bind.annotation.RequestParam;
import com.tongluxing.user.service.UserService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 用户模块接口控制器。
 *
 * <p>负责当前用户资料查询/修改、驾驶证认证提交，以及公开主页查询。
 * Controller 只做请求接收、参数校验和结果包装，具体业务规则交给 {@link UserService}。</p>
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/users")
public class UserController {
    private final UserService userService;

    /**
     * 查询当前登录用户的完整个人资料。
     */
    @GetMapping("/me")
    public Result<UserProfileVO> me() {
        return Result.success(userService.getCurrentProfile());
    }

    /**
     * 修改当前登录用户的个人资料。
     *
     * <p>请求字段允许部分更新，未传入的字段由服务层保留原值。</p>
     */
    @PutMapping("/me/profile")
    public Result<UserProfileVO> updateProfile(@Valid @RequestBody UpdateUserProfileRequest request) {
        return Result.success(userService.updateCurrentProfile(request));
    }

    @GetMapping("/me/privacy-settings")
    public Result<PrivacySettingsVO> privacySettings() {
        return Result.success(userService.getCurrentPrivacySettings());
    }

    @PutMapping("/me/privacy-settings")
    public Result<PrivacySettingsVO> updatePrivacySettings(
            @Valid @RequestBody UpdatePrivacySettingsRequest request) {
        return Result.success(userService.updateCurrentPrivacySettings(request));
    }

    /**
     * 提交驾驶证认证申请。
     */
    @PostMapping("/me/certifications")
    public Result<CertificationVO> certify(@Valid @RequestBody CertificationRequest request) {
        return Result.success(userService.submitCertification(request));
    }

    /** 查询当前用户最近一次驾驶证认证状态和驳回原因。 */
    @GetMapping("/me/certifications/latest")
    public Result<CertificationVO> latestCertification() {
        return Result.success(userService.getLatestCertification());
    }

    /**
     * 查询指定用户对外公开展示的资料。
     */
    @GetMapping("/{userId}/public-profile")
    public Result<PublicProfileVO> publicProfile(@PathVariable Long userId) {
        return Result.success(userService.getPublicProfile(userId));
    }

    /** 行程搜索页“发起人”标签使用的公开用户搜索。 */
    @GetMapping("/search")
    public Result<List<UserSearchVO>> search(@RequestParam(required = false) String keyword,
                                             @RequestParam(defaultValue = "1") int page,
                                             @RequestParam(defaultValue = "20") int size) {
        return Result.success(userService.searchPublicUsers(keyword, page, size));
    }

    @PostMapping("/{userId}/follow")
    public Result<FollowStatusVO> follow(@PathVariable Long userId) {
        return Result.success(userService.follow(userId));
    }

    @DeleteMapping("/{userId}/follow")
    public Result<FollowStatusVO> unfollow(@PathVariable Long userId) {
        return Result.success(userService.unfollow(userId));
    }

    @GetMapping("/{userId}/follow-status")
    public Result<FollowStatusVO> followStatus(@PathVariable Long userId) {
        return Result.success(userService.getFollowStatus(userId));
    }

    /** 当前登录用户的粉丝列表，前端不再依赖本地缓存的 userId。 */
    @GetMapping("/me/followers")
    public Result<List<FollowUserVO>> myFollowers(@RequestParam(defaultValue = "1") int page,
                                                  @RequestParam(defaultValue = "20") int size) {
        return Result.success(userService.getMyFollowers(page, size));
    }

    /** “谁关注了我”的真实未读关系数，不再由前端用固定数字模拟。 */
    @GetMapping("/me/followers/unread-count")
    public Result<java.util.Map<String, Long>> myFollowersUnreadCount() {
        return Result.success(java.util.Map.of(
                "unreadCount", userService.countMyUnreadFollowerNotifications()));
    }

    /** 用户实际打开关注列表后再清除未读状态。 */
    @PostMapping("/me/followers/read")
    public Result<Void> markMyFollowersRead() {
        userService.markMyFollowerNotificationsRead();
        return Result.success();
    }

    /** 当前登录用户关注的用户列表。 */
    @GetMapping("/me/following")
    public Result<List<FollowUserVO>> myFollowing(@RequestParam(defaultValue = "1") int page,
                                                  @RequestParam(defaultValue = "20") int size) {
        return Result.success(userService.getMyFollowing(page, size));
    }

    /** 当前登录用户的互相关注列表。 */
    @GetMapping("/me/mutual-follows")
    public Result<List<FollowUserVO>> myMutualFollows(@RequestParam(defaultValue = "1") int page,
                                                      @RequestParam(defaultValue = "20") int size) {
        return Result.success(userService.getMyMutualFollows(page, size));
    }

    @GetMapping("/{userId}/followers")
    public Result<List<FollowUserVO>> followers(@PathVariable Long userId,
                                                @RequestParam(defaultValue = "1") int page,
                                                @RequestParam(defaultValue = "20") int size) {
        return Result.success(userService.getFollowers(userId, page, size));
    }

    @GetMapping("/{userId}/following")
    public Result<List<FollowUserVO>> following(@PathVariable Long userId,
                                                @RequestParam(defaultValue = "1") int page,
                                                @RequestParam(defaultValue = "20") int size) {
        return Result.success(userService.getFollowing(userId, page, size));
    }
}
