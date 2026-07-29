package com.tongluxing.user.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 用户域及相关页面聚合模型集合。
 *
 * <p>该类集中放置多个模块复用的请求对象和返回对象，避免简单 DTO 在模块之间重复定义。
 * 其中 Request 主要用于接口入参校验，VO 主要用于接口返回展示。</p>
 */
public final class UserModels {

    /**
     * 工具类不允许实例化。
     */
    private UserModels() {
    }

    /**
     * 通用分页返回结构。
     *
     * <p>page 使用从 1 开始的业务页码；records 只包含当前页，total 表示相同过滤条件
     * 下的全部记录数。</p>
     *
     * @param records 当前页记录
     * @param total 总记录数
     * @param page 当前页码
     * @param size 每页数量
     */
    public record PageResult<T>(List<T> records, long total, int page, int size) {
    }

    /**
     * 修改用户资料请求。
     *
     * <p>字段均为可选，未传入表示保留原值。</p>
     *
     * @param nickname 昵称，最多 16 个字符
     * @param avatarImageKey 头像对象存储 Key
     * @param gender 性别编码：0 未知、1 男、2 女
     * @param birthday 生日
     * @param cityCode 标准城市编码
     * @param cityName 用于展示的城市名称
     * @param bio 个人简介
     */
    public record UpdateUserProfileRequest(
            @Size(max = 16) String nickname,
            @Size(max = 512) String avatarImageKey,
            @Min(0) @Max(2) Integer gender,
            LocalDate birthday,
            @Size(max = 12) String cityCode,
            @Size(max = 32) String cityName,
            @Size(max = 100) String bio
    ) {
    }

    /**
     * 驾驶证认证提交请求，字段由小程序 OCR 后经用户确认。
     *
     * <p>姓名和完整证件号进入 Service 后会先加密再落库；图片字段只传对象存储 Key，
     * 不接受客户端拼装的临时访问 URL。</p>
     *
     * @param holderName 持证人姓名
     * @param licenseNo 完整驾驶证号
     * @param vehicleClass 准驾车型
     * @param firstIssueDate 初次领证日期
     * @param validFrom 当前有效期开始日期
     * @param validTo 当前有效期截止日期
     * @param issuingAuthority 发证机关
     * @param licenseFrontImageKey 驾驶证主页图片 Key
     * @param licenseBackImageKey 可选的驾驶证副页图片 Key
     * @param recognitionSource MINIPROGRAM_OCR 或 MANUAL_UPLOAD
     */
    public record CertificationRequest(
            @NotBlank @Size(max = 64) String holderName,
            @NotBlank @Pattern(regexp = "^[0-9A-Za-z]{6,32}$") String licenseNo,
            @NotBlank @Size(max = 32) String vehicleClass,
            LocalDate firstIssueDate,
            LocalDate validFrom,
            LocalDate validTo,
            @Size(max = 128) String issuingAuthority,
            @NotBlank @Size(max = 512) String licenseFrontImageKey,
            @Size(max = 512) String licenseBackImageKey,
            @NotBlank @Pattern(regexp = "MINIPROGRAM_OCR|MANUAL_UPLOAD") String recognitionSource
    ) {
    }

    /**
     * 邀请码绑定请求。
     *
     * @param inviteCode 待绑定的邀请码，去除首尾空白后最长 16 个字符
     */
    public record InviteBindRequest(@NotBlank @Size(max = 16) String inviteCode) {
    }

    /**
     * 订单锁定优惠券请求。
     *
     * @param orderId 要锁定优惠券的订单 ID
     * @param amount 订单参与优惠计算的非负金额
     */
    public record CouponLockRequest(@NotNull Long orderId, @NotNull @DecimalMin("0.00") BigDecimal amount) {
    }

    /**
     * 订单支付结果通知请求。
     *
     * @param orderId 已锁券订单 ID
     * @param payStatus SUCCESS、FAILED 或 CANCELLED
     */
    public record CouponOrderResultRequest(
            @NotNull Long orderId,
            @NotBlank @Pattern(regexp = "SUCCESS|FAILED|CANCELLED") String payStatus
    ) {
    }

    /**
     * 地点请求对象，用于行程起点、终点和途经点。
     *
     * <p>经纬度采用 WGS84 数值范围校验：纬度 -90~90，经度 -180~180。</p>
     *
     * @param name 地点简称
     * @param address 完整地址
     * @param latitude 纬度
     * @param longitude 经度
     */
    public record LocationRequest(
            @NotBlank @Size(max = 64) String name,
            @NotBlank @Size(max = 255) String address,
            @NotNull @DecimalMin("-90") @DecimalMax("90") BigDecimal latitude,
            @NotNull @DecimalMin("-180") @DecimalMax("180") BigDecimal longitude
    ) {
    }

    /**
     * 行程草稿创建/修改请求。
     *
     * <p>起点和终点必须完整有效，途经点可以为空；人数、天数在模型层限制合理范围，
     * 出发时间是否满足业务提前量由对应行程 Service 判断。</p>
     *
     * @param startLocation 行程起点
     * @param endLocation 行程终点
     * @param waypoints 可选途经点列表
     * @param departureTime 计划出发时间
     * @param durationDays 预计持续天数
     * @param peopleCount 计划出行人数
     * @param remark 草稿备注
     */
    public record TripDraftRequest(
            @NotNull @Valid LocationRequest startLocation,
            @NotNull @Valid LocationRequest endLocation,
            @Valid List<LocationRequest> waypoints,
            @NotNull LocalDateTime departureTime,
            @NotNull @Min(1) @Max(365) Integer durationDays,
            @NotNull @Min(1) @Max(100) Integer peopleCount,
            @Size(max = 255) String remark
    ) {
    }

    /**
     * 草稿发布请求。
     *
     * <p>publishType 决定发布为普通行程还是组队行程。</p>
     *
     * @param publishType TRIP 或 TEAM
     */
    public record PublishDraftRequest(@NotBlank @Pattern(regexp = "TRIP|TEAM") String publishType) {
    }

    /**
     * 当前用户完整资料返回对象。
     *
     * <p>该对象用于“我的资料”，因此包含生日、城市编码等可编辑字段；不包含驾驶证
     * 明文、隐私设置或数据库审计字段。</p>
     *
     * @param userId 平台用户 ID
     * @param tongluxingId 公开且不可变的同路行号
     * @param nickname 昵称
     * @param avatarImageKey 头像资源 Key
     * @param gender 性别编码
     * @param birthday 生日
     * @param cityCode 城市编码
     * @param cityName 城市名称
     * @param bio 个人简介
     * @param profileStatus 资料状态
     * @param drivingLicenseCertificationStatus 最新驾驶证认证状态
     */
    public record UserProfileVO(
            Long userId, String tongluxingId, String nickname, String avatarImageKey, Integer gender, LocalDate birthday,
            String cityCode, String cityName, String bio, String profileStatus, String drivingLicenseCertificationStatus
    ) {
    }

    /**
     * 当前用户可编辑的公开资料、定位与通知权限。
     *
     * <p>profileVisibility 控制整个公开主页；cityVisible、bioVisible 和
     * tripStatsVisible 是主页允许公开后的细粒度字段开关。</p>
     *
     * @param profileVisibility 主页可见性：PUBLIC 或 PRIVATE
     * @param vehicleVisibility 车辆可见性：PUBLIC、TEAM_ONLY 或 PRIVATE
     * @param inviteEnabled 是否启用邀请能力
     * @param cityVisible 是否公开城市
     * @param bioVisible 是否公开简介
     * @param tripStatsVisible 是否公开行程统计
     * @param levelVisible 是否公开等级
     * @param locationEnabled 是否授权定位能力
     * @param notificationEnabled 是否允许通知
     */
    public record PrivacySettingsVO(
            String profileVisibility,
            String vehicleVisibility,
            Boolean inviteEnabled,
            Boolean cityVisible,
            Boolean bioVisible,
            Boolean tripStatsVisible,
            Boolean levelVisible,
            Boolean locationEnabled,
            Boolean notificationEnabled
    ) {
    }

    /**
     * 隐私设置增量更新请求。
     *
     * <p>所有字段均可为空：null 表示保持数据库旧值，false 表示明确关闭布尔能力。</p>
     *
     * @param profileVisibility 新的主页可见性
     * @param vehicleVisibility 新的车辆可见性
     * @param inviteEnabled 是否启用邀请能力
     * @param cityVisible 是否公开城市
     * @param bioVisible 是否公开简介
     * @param tripStatsVisible 是否公开行程统计
     * @param levelVisible 是否公开等级
     * @param locationEnabled 是否授权定位能力
     * @param notificationEnabled 是否允许通知
     */
    public record UpdatePrivacySettingsRequest(
            @Pattern(regexp = "PUBLIC|PRIVATE") String profileVisibility,
            @Pattern(regexp = "PUBLIC|TEAM_ONLY|PRIVATE") String vehicleVisibility,
            Boolean inviteEnabled,
            Boolean cityVisible,
            Boolean bioVisible,
            Boolean tripStatsVisible,
            Boolean levelVisible,
            Boolean locationEnabled,
            Boolean notificationEnabled
    ) {
    }

    /**
     * 驾驶证认证状态返回对象。
     *
     * @param certificationId 认证申请主键；UNSUBMITTED 时为空
     * @param userId 申请所属用户 ID
     * @param status UNSUBMITTED、PENDING、APPROVED 或 REJECTED
     * @param rejectReason 驳回原因
     * @param submittedAt 提交时间
     * @param reviewedAt 人工审核完成时间
     * @param canResubmit 当前状态是否允许再次提交
     */
    public record CertificationVO(
            Long certificationId, Long userId, String status, String rejectReason,
            LocalDateTime submittedAt, LocalDateTime reviewedAt, Boolean canResubmit
    ) {
    }

    /**
     * 后台驾驶证认证列表项。
     *
     * <p>列表只返回脱敏证件号；持证人姓名虽需解密展示，但完整证件号只在进入审核
     * 详情时按需解密，以缩小敏感数据暴露范围。</p>
     *
     * @param certificationId 认证申请主键
     * @param userId 申请用户 ID
     * @param holderName 解密后的持证人姓名
     * @param licenseNoMasked 脱敏驾驶证号
     * @param vehicleClass 准驾车型
     * @param validTo 有效期截止日期
     * @param status 当前认证状态
     * @param submittedAt 提交时间
     */
    public record DrivingLicenseAuditSummaryVO(
            Long certificationId, Long userId, String holderName, String licenseNoMasked,
            String vehicleClass, LocalDate validTo, String status, LocalDateTime submittedAt
    ) {
    }

    /**
     * 后台驾驶证认证详情，图片 URL 由 admin-module 按需补充。
     *
     * <p>该模型包含完整姓名和驾驶证号，只能用于已经完成后台权限校验的审核链路，
     * 不得复用于普通用户接口或写入业务日志。</p>
     *
     * @param certificationId 认证申请主键
     * @param userId 申请用户 ID
     * @param holderName 解密后的持证人姓名
     * @param licenseNo 解密后的完整驾驶证号
     * @param vehicleClass 准驾车型
     * @param firstIssueDate 初次领证日期
     * @param validFrom 有效期开始日期
     * @param validTo 有效期截止日期
     * @param issuingAuthority 发证机关
     * @param licenseFrontImageKey 主页图片 Key
     * @param licenseBackImageKey 副页图片 Key
     * @param recognitionSource 识别来源
     * @param status 认证状态
     * @param rejectReason 驳回原因
     * @param submittedAt 提交时间
     * @param reviewedAt 审核完成时间
     */
    public record DrivingLicenseAuditDetailVO(
            Long certificationId, Long userId, String holderName, String licenseNo, String vehicleClass,
            LocalDate firstIssueDate, LocalDate validFrom, LocalDate validTo, String issuingAuthority,
            String licenseFrontImageKey, String licenseBackImageKey, String recognitionSource,
            String status, String rejectReason, LocalDateTime submittedAt, LocalDateTime reviewedAt
    ) {
    }

    /**
     * 用户公开主页资料返回对象。
     *
     * <p>只包含允许公开展示的基础信息。用户关闭某一细粒度开关后，相应字符串返回
     * 空串、统计数值返回 0，使响应结构保持稳定。</p>
     *
     * @param userId 平台用户 ID
     * @param tongluxingId 同路行号
     * @param nickname 昵称
     * @param avatarImageKey 头像资源 Key
     * @param cityName 允许公开时的城市名称
     * @param bio 允许公开时的个人简介
     * @param drivingLicenseCertificationStatus 最新认证状态
     * @param totalTripCount 允许公开时的累计行程数
     * @param totalDistanceMeters 允许公开时的累计距离（米）
     * @param totalDurationMinutes 允许公开时的累计时长（分钟）
     * @param completedWaypointCount 允许公开时的累计途经点数
     */
    public record PublicProfileVO(
            Long userId, String tongluxingId, String nickname, String avatarImageKey, String cityName, String bio,
            String drivingLicenseCertificationStatus, Integer totalTripCount, Long totalDistanceMeters,
            Long totalDurationMinutes, Integer completedWaypointCount
    ) {
        public PublicProfileVO(Long userId, String nickname, String avatarImageKey, String cityName, String bio,
                               String drivingLicenseCertificationStatus) {
            // 兼容只提供基础名片的旧调用方，统计字段使用安全的零值。
            this(userId, null, nickname, avatarImageKey, cityName, bio,
                    drivingLicenseCertificationStatus, 0, 0L, 0L, 0);
        }

        /**
         * 兼容尚未传递同路行号、但需要携带行程统计的内部调用。
         */
        public PublicProfileVO(Long userId, String nickname, String avatarImageKey, String cityName, String bio,
                               String drivingLicenseCertificationStatus, Integer totalTripCount,
                               Long totalDistanceMeters, Long totalDurationMinutes,
                               Integer completedWaypointCount) {
            this(userId, null, nickname, avatarImageKey, cityName, bio,
                    drivingLicenseCertificationStatus, totalTripCount, totalDistanceMeters,
                    totalDurationMinutes, completedWaypointCount);
        }
    }

    /**
     * 当前用户与目标用户的关注关系及公开计数。
     *
     * @param userId 目标用户 ID
     * @param following 当前用户是否关注目标
     * @param followedByTarget 目标是否反向关注当前用户
     * @param mutual 两个方向是否同时存在
     * @param followerCount 目标用户粉丝数
     * @param followingCount 目标用户关注数
     */
    public record FollowStatusVO(
            Long userId, Boolean following, Boolean followedByTarget, Boolean mutual,
            Long followerCount, Long followingCount
    ) {
    }

    /**
     * 粉丝/关注列表中的公开用户摘要。
     *
     * <p>followedAt 的方向由列表语境决定；following、followedByTarget 和 mutual
     * 始终以当前登录用户为观察者，前端无需再次推导关系。</p>
     *
     * @param userId 列表用户 ID
     * @param nickname 昵称
     * @param avatarImageKey 头像资源 Key
     * @param certificationStatus 最新认证状态
     * @param totalTripCount 累计行程数
     * @param totalDistanceMeters 累计行驶距离（米）
     * @param followedAt 关注关系建立时间
     * @param following 当前用户是否关注列表用户
     * @param followedByTarget 列表用户是否关注当前用户
     * @param mutual 是否互相关注
     */
    public record FollowUserVO(
            Long userId, String nickname, String avatarImageKey, String certificationStatus,
            Integer totalTripCount, Long totalDistanceMeters, LocalDateTime followedAt,
            Boolean following, Boolean followedByTarget, Boolean mutual
    ) {
    }

    /**
     * 同路人搜索结果，包含公开资料摘要和当前用户的关注关系。
     *
     * <p>该模型将可搜索的公开名片、行程活跃度、关注计数和按钮状态合并，供搜索页
     * 直接渲染；不会包含生日、隐私开关或认证证件信息。</p>
     *
     * @param userId 用户 ID
     * @param tongluxingId 同路行号
     * @param nickname 昵称
     * @param avatarImageKey 头像资源 Key
     * @param cityName 公开城市
     * @param bio 公开简介
     * @param certificationStatus 最新认证状态
     * @param totalTripCount 累计行程数
     * @param totalDistanceMeters 累计距离（米）
     * @param followerCount 粉丝数
     * @param followingCount 关注数
     * @param following 当前用户是否关注该用户
     * @param followedByTarget 该用户是否关注当前用户
     * @param mutual 是否互相关注
     */
    public record UserSearchVO(
            Long userId, String tongluxingId, String nickname, String avatarImageKey, String cityName, String bio,
            String certificationStatus, Integer totalTripCount, Long totalDistanceMeters,
            Long followerCount, Long followingCount,
            Boolean following, Boolean followedByTarget, Boolean mutual
    ) {
    }

    /**
     * 成长值概览返回对象。
     *
     * @param totalPoints 当前累计成长值
     * @param levelCode 当前等级编码
     * @param nextLevelPoints 距离下一等级所需的目标成长值
     */
    public record GrowthSummaryVO(Integer totalPoints, String levelCode, Integer nextLevelPoints) {
    }

    /**
     * 成长值流水返回对象。
     *
     * <p>pointDelta 正数表示增加、负数表示扣减；balanceAfter 是该笔变动后的余额，
     * 便于客户端展示和后台审计。</p>
     *
     * @param id 流水主键
     * @param bizType 产生变动的业务类型
     * @param bizId 关联业务单据 ID
     * @param pointDelta 本次成长值增减量
     * @param balanceAfter 变动后的成长值余额
     * @param remark 展示或审计备注
     * @param createdAt 流水创建时间
     */
    public record GrowthLogVO(
            Long id, String bizType, String bizId, Integer pointDelta, Integer balanceAfter,
            String remark, LocalDateTime createdAt
    ) {
    }

    /**
     * 单个徽章展示对象。
     *
     * <p>currentValue 与 threshold 描述当前完成进度；awardedAt 非空表示已经获得。</p>
     *
     * @param badgeId 徽章主键
     * @param badgeCode 稳定业务编码
     * @param badgeName 展示名称
     * @param badgeImageKey 图片资源 Key
     * @param conditionDescription 获得条件说明
     * @param eventType 驱动进度的事件类型
     * @param currentValue 当前进度
     * @param threshold 达成阈值
     * @param awardedAt 获得时间
     */
    public record BadgeVO(
            Long badgeId, String badgeCode, String badgeName, String badgeImageKey,
            String conditionDescription, String eventType, Integer currentValue, Integer threshold,
            LocalDateTime awardedAt
    ) {
    }

    /**
     * 徽章墙返回对象。
     *
     * <p>earned 表示已获得徽章，locked 表示尚未获得但可展示的徽章。</p>
     *
     * @param earned 已获得徽章
     * @param locked 尚未获得的可见徽章
     */
    public record BadgeWallVO(List<BadgeVO> earned, List<BadgeVO> locked) {
    }

    /**
     * 用户邀请码返回对象。
     *
     * @param inviteCode 用户当前邀请码
     * @param scene 适用业务场景
     * @param enabled 当前是否允许使用
     */
    public record InviteCodeVO(String inviteCode, String scene, Boolean enabled) {
    }

    /**
     * 邀请绑定结果返回对象。
     *
     * @param inviterUserId 邀请人用户 ID
     * @param inviteeUserId 被邀请人用户 ID
     * @param status 绑定关系状态
     * @param boundAt 成功绑定时间
     */
    public record InviteBindVO(Long inviterUserId, Long inviteeUserId, String status, LocalDateTime boundAt) {
    }

    /**
     * 单条邀请关系返回对象。
     *
     * @param relationId 邀请关系主键
     * @param inviteeUserId 被邀请人用户 ID
     * @param inviteCode 绑定时使用的邀请码
     * @param status 关系状态
     * @param boundAt 绑定时间
     * @param firstTeamCompletedAt 被邀请人首次完成组队时间
     */
    public record InvitationVO(
            Long relationId, Long inviteeUserId, String inviteCode, String status,
            LocalDateTime boundAt, LocalDateTime firstTeamCompletedAt
    ) {
    }

    /**
     * 邀请奖励进度返回对象。
     *
     * <p>grantedRuleCodes 保存已经发放的规则编码，用于避免重复展示或重复发奖。</p>
     *
     * @param validInviteCount 有效邀请数
     * @param nextRewardNeed 距离下一奖励所需人数
     * @param grantedRuleCodes 已发放规则编码
     */
    public record InviteRewardProgressVO(Integer validInviteCount, Integer nextRewardNeed, List<String> grantedRuleCodes) {
    }

    /**
     * 优惠券列表项返回对象。
     *
     * <p>thresholdAmount 是最低使用门槛，discountAmount 是抵扣金额；有效期与状态
     * 共同决定客户端是否显示为可用。</p>
     *
     * @param id 用户优惠券主键
     * @param templateId 优惠券模板主键
     * @param couponName 展示名称
     * @param couponType 类型
     * @param thresholdAmount 使用门槛
     * @param discountAmount 抵扣金额
     * @param couponStatus 当前状态
     * @param validStartAt 生效时间
     * @param validEndAt 失效时间
     */
    public record CouponSummaryVO(
            Long id, Long templateId, String couponName, String couponType, BigDecimal thresholdAmount,
            BigDecimal discountAmount, String couponStatus, LocalDateTime validStartAt, LocalDateTime validEndAt
    ) {
    }

    /**
     * 用户优惠券详情返回对象。
     *
     * <p>lockedOrderId 表示优惠券正被未完成订单占用，usedOrderId 表示最终核销订单，
     * 两者用于区分临时锁定与已使用状态。</p>
     *
     * @param id 用户优惠券主键
     * @param templateId 模板主键
     * @param couponName 展示名称
     * @param couponType 类型
     * @param issuerId 发放方 ID
     * @param thresholdAmount 使用门槛
     * @param discountAmount 抵扣金额
     * @param scopeJson 适用范围 JSON
     * @param couponStatus 当前状态
     * @param validStartAt 生效时间
     * @param validEndAt 失效时间
     * @param lockedOrderId 当前锁定订单
     * @param usedOrderId 最终核销订单
     */
    public record UserCouponDetailVO(
            Long id, Long templateId, String couponName, String couponType, Long issuerId,
            BigDecimal thresholdAmount, BigDecimal discountAmount, String scopeJson, String couponStatus,
            LocalDateTime validStartAt, LocalDateTime validEndAt, Long lockedOrderId, Long usedOrderId
    ) {
    }

    /**
     * 当前订单可用优惠券返回对象。
     *
     * @param id 用户优惠券主键
     * @param couponName 展示名称
     * @param deductionAmount 针对当前订单实际可抵扣的金额，而非模板固定面额
     * @param validEndAt 失效时间
     */
    public record AvailableCouponVO(Long id, String couponName, BigDecimal deductionAmount, LocalDateTime validEndAt) {
    }

    /**
     * 优惠券抵扣结果返回对象。
     *
     * <p>用于在锁券或支付回调后返回本次订单的实际抵扣与最新状态。</p>
     *
     * @param userCouponId 用户优惠券主键
     * @param orderId 关联订单 ID
     * @param deductionAmount 实际抵扣金额
     * @param status 锁定或核销状态
     */
    public record CouponDeductionVO(Long userCouponId, Long orderId, BigDecimal deductionAmount, String status) {
    }

    /**
     * 地点展示对象。
     *
     * <p>与 LocationRequest 分离，返回模型不携带输入校验注解。</p>
     *
     * @param name 地点简称
     * @param address 完整地址
     * @param latitude 纬度
     * @param longitude 经度
     */
    public record LocationVO(String name, String address, BigDecimal latitude, BigDecimal longitude) {
    }

    /**
     * 行程草稿返回对象。
     *
     * <p>publishedTripId 仅在草稿已经发布时有值；updatedAt 用于草稿列表排序和
     * 多端编辑冲突提示。</p>
     *
     * @param draftId 草稿主键
     * @param startLocation 起点
     * @param endLocation 终点
     * @param waypoints 途经点列表
     * @param departureTime 计划出发时间
     * @param durationDays 预计天数
     * @param peopleCount 出行人数
     * @param remark 备注
     * @param draftStatus 草稿状态
     * @param publishedTripId 发布后生成的行程 ID
     * @param updatedAt 最后更新时间
     */
    public record TripDraftVO(
            Long draftId, LocationVO startLocation, LocationVO endLocation, List<LocationVO> waypoints,
            LocalDateTime departureTime, Integer durationDays, Integer peopleCount, String remark,
            String draftStatus, Long publishedTripId, LocalDateTime updatedAt
    ) {
    }

    /**
     * 草稿发布结果返回对象。
     *
     * @param draftId 原草稿主键
     * @param publishType 发布类型
     * @param publishedId 根据 publishType 指向普通行程或组队行程主键
     */
    public record PublishResultVO(Long draftId, String publishType, Long publishedId) {
    }

    /**
     * 组队匹配结果返回对象。
     *
     * <p>routeOverlapRate 表示路线重合比例，timeDifferenceMinutes 表示出发时间差，
     * 调用方可据此解释推荐排序。</p>
     *
     * @param teamId 组队 ID
     * @param teamName 组队名称
     * @param routeOverlapRate 路线重合比例
     * @param timeDifferenceMinutes 出发时间差（分钟）
     */
    public record TeamMatchVO(Long teamId, String teamName, BigDecimal routeOverlapRate, Long timeDifferenceMinutes) {
    }

    /**
     * 用户首页聚合数据返回对象。
     *
     * <p>把资料、成长值、优惠券、邀请进度和下一份草稿合并，减少首页多次网络请求。</p>
     *
     * @param profile 当前用户资料
     * @param growth 成长值概览
     * @param coupon 优惠券数量摘要
     * @param invitation 邀请进度摘要
     * @param nextTripDraft 下一份待处理行程草稿
     */
    public record UserDashboardVO(
            UserProfileVO profile, GrowthSummaryVO growth, CouponCountVO coupon,
            InvitationSummaryVO invitation, TripDraftVO nextTripDraft
    ) {
    }

    /**
     * 优惠券数量摘要。
     *
     * @param availableCount 当前可使用数量
     * @param expiringCount 即将过期数量
     */
    public record CouponCountVO(Integer availableCount, Integer expiringCount) {
    }

    /**
     * 邀请摘要信息。
     *
     * @param validInviteCount 已满足规则的有效邀请数
     * @param nextRewardNeed 距离下一档奖励仍需邀请的人数
     */
    public record InvitationSummaryVO(Integer validInviteCount, Integer nextRewardNeed) {
    }

    /**
     * 公开主页展示的用户主车辆摘要，不包含车牌号、车架号等敏感车辆信息。
     *
     * @param brand 车辆品牌
     * @param model 车型
     * @param vehicleType 车辆类型
     */
    public record PublicVehicleSummaryVO(String brand, String model, String vehicleType) {
    }

    /**
     * 用户公开主页聚合返回对象。
     *
     * <p>聚合公开资料、成长值、徽章墙、主车辆、IP 属地和关注状态。上游聚合服务必须
     * 根据用户隐私设置决定等级与车辆是否填充，不能仅依赖客户端隐藏。</p>
     *
     * @param profile 已裁剪的公开资料
     * @param growth 允许展示时的成长概览
     * @param badges 徽章墙
     * @param mainVehicle 允许展示时的主车辆摘要
     * @param ipProvince IP 归属省份
     * @param follow 当前用户与主页用户的关注关系
     */
    public record UserHomepageVO(PublicProfileVO profile, GrowthSummaryVO growth, BadgeWallVO badges,
                                 PublicVehicleSummaryVO mainVehicle, String ipProvince, FollowStatusVO follow) {
    }

    /**
     * 客服入口返回对象。
     *
     * @param scene 入口所属页面或业务场景
     * @param channel 客服渠道类型
     * @param target 渠道目标标识，例如会话或客服账号
     */
    public record CustomerServiceEntryVO(String scene, String channel, String target) {
    }
}
