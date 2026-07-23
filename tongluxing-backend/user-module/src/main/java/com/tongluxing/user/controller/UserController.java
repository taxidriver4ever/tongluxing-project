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
