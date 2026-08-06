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
import com.tongluxing.user.dto.request.CertificationRequest;
import com.tongluxing.user.vo.CertificationVO;
import com.tongluxing.user.vo.PublicProfileVO;
import com.tongluxing.user.dto.request.UpdateUserProfileRequest;
import com.tongluxing.user.vo.UserProfileVO;
import com.tongluxing.user.vo.FollowStatusVO;
import com.tongluxing.user.vo.FollowUserVO;
import com.tongluxing.user.vo.UserSearchVO;
import com.tongluxing.user.vo.PrivacySettingsVO;
import com.tongluxing.user.dto.request.UpdatePrivacySettingsRequest;
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
    /** 用户领域统一服务入口；Controller 不直接依赖 Mapper 或 Redis。 */
    private final UserService userService;

    /**
     * 查询当前登录用户的完整个人资料。
     *
     * @return 包含同路行号、基础资料和最新认证状态的统一成功响应
     */
    @GetMapping("/me")
    public Result<UserProfileVO> me() {
        // 用户 ID 由 Service 从认证上下文获取，接口不接受可被伪造的 userId 参数。
        return Result.success(userService.getCurrentProfile());
    }

    /**
     * 修改当前登录用户的个人资料。
     *
     * <p>请求字段允许部分更新，未传入的字段由服务层保留原值。</p>
     *
     * @param request 通过 Bean Validation 校验的资料增量
     * @return 数据库更新后的完整用户资料
     */
    @PutMapping("/me/profile")
    public Result<UserProfileVO> updateProfile(@Valid @RequestBody UpdateUserProfileRequest request) {
        // @Valid 在进入业务层前完成长度、性别范围等基础格式校验。
        return Result.success(userService.updateCurrentProfile(request));
    }

    /** 查询当前登录用户的主页、车辆、定位和通知等全部隐私开关。 */
    @GetMapping("/me/privacy-settings")
    public Result<PrivacySettingsVO> privacySettings() {
        return Result.success(userService.getCurrentPrivacySettings());
    }

    /**
     * 增量修改当前登录用户的隐私设置。
     *
     * <p>未传字段保持不变；枚举字段由请求模型上的正则约束提前校验。</p>
     */
    @PutMapping("/me/privacy-settings")
    public Result<PrivacySettingsVO> updatePrivacySettings(
            @Valid @RequestBody UpdatePrivacySettingsRequest request) {
        // 具体合并旧值、持久化和公开名片缓存失效均由 Service 在事务中完成。
        return Result.success(userService.updateCurrentPrivacySettings(request));
    }

    /**
     * 提交驾驶证认证材料并自动认证。
     *
     * <p>请求只接收用户确认后的 OCR/手工录入结果；敏感字段加密、重复申请检查、
     * 材料完整性校验和自动通过由 Service 完成。</p>
     */
    @PostMapping("/me/certifications")
    public Result<CertificationVO> certify(@Valid @RequestBody CertificationRequest request) {
        // @Valid 保证证件号格式、图片 Key 和识别来源满足接口契约。
        return Result.success(userService.submitCertification(request));
    }

    /**
     * 查询当前用户最近一次驾驶证认证状态和驳回原因。
     *
     * @return 从未申请时也返回 UNSUBMITTED 状态对象，而不是 null
     */
    @GetMapping("/me/certifications/latest")
    public Result<CertificationVO> latestCertification() {
        return Result.success(userService.getLatestCertification());
    }

    /**
     * 查询指定用户对外公开展示的资料。
     *
     * @param userId 目标平台用户 ID
     * @return 已按目标用户隐私开关裁剪的公开资料
     */
    @GetMapping("/{userId}/public-profile")
    public Result<PublicProfileVO> publicProfile(@PathVariable Long userId) {
        // PRIVATE 用户由 Service 按“主页不存在”处理，Controller 不绕过隐私规则。
        return Result.success(userService.getPublicProfile(userId));
    }

    /**
     * 行程搜索页“同路人”标签使用的公开用户搜索。
     *
     * @param keyword 可选关键词，可匹配昵称、城市、简介、同路行号、用户 ID 或完整手机号
     * @param page 从 1 开始的页码，异常值由 Service 归一化
     * @param size 每页数量，Service 最大限制为 50
     */
    @GetMapping("/search")
    public Result<List<UserSearchVO>> search(@RequestParam(required = false) String keyword,
                                             @RequestParam(defaultValue = "1") int page,
                                             @RequestParam(defaultValue = "20") int size) {
        // 搜索结果还会包含当前登录用户相对每个候选人的双向关注状态。
        return Result.success(userService.searchPublicUsers(keyword, page, size));
    }

    /** 当前登录用户关注目标用户，并返回操作后的双向关系与目标关注计数。 */
    @PostMapping("/{userId}/follow")
    public Result<FollowStatusVO> follow(@PathVariable Long userId) {
        // 重复或并发关注由 Service 按幂等成功处理。
        return Result.success(userService.follow(userId));
    }

    /** 当前登录用户取消关注目标用户；关系不存在时同样返回成功后的真实状态。 */
    @DeleteMapping("/{userId}/follow")
    public Result<FollowStatusVO> unfollow(@PathVariable Long userId) {
        return Result.success(userService.unfollow(userId));
    }

    /** 查询当前登录用户与目标用户之间的正向、反向及互关状态。 */
    @GetMapping("/{userId}/follow-status")
    public Result<FollowStatusVO> followStatus(@PathVariable Long userId) {
        return Result.success(userService.getFollowStatus(userId));
    }

    /** 当前登录用户的粉丝列表，前端不再依赖本地缓存的 userId。 */
    @GetMapping("/me/followers")
    public Result<List<FollowUserVO>> myFollowers(@RequestParam(defaultValue = "1") int page,
                                                  @RequestParam(defaultValue = "20") int size) {
        // “我的”接口不接收 userId，避免前端缓存错误或恶意替换导致水平越权。
        return Result.success(userService.getMyFollowers(page, size));
    }

    /** “谁关注了我”的真实未读关系数，不再由前端用固定数字模拟。 */
    @GetMapping("/me/followers/unread-count")
    public Result<java.util.Map<String, Long>> myFollowersUnreadCount() {
        // 使用具名字段保持响应可扩展，前端可直接读取 unreadCount。
        return Result.success(java.util.Map.of(
                "unreadCount", userService.countMyUnreadFollowerNotifications()));
    }

    /** 用户实际打开关注列表后再清除未读状态。 */
    @PostMapping("/me/followers/read")
    public Result<Void> markMyFollowersRead() {
        // 只有真正打开列表时调用，避免仅进入消息中心就提前清空红点。
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
        // 互关由数据库正反向关系共同确定，不依赖前端本地推算。
        return Result.success(userService.getMyMutualFollows(page, size));
    }

    /**
     * 查询指定用户的粉丝列表。
     *
     * <p>查看他人列表时，Service 会先校验其公开主页是否可访问。</p>
     */
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
    /** 查询指定用户主动关注的人；他人主页隐藏时不会暴露其关系列表。 */
