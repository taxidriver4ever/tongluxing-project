package com.tongluxing.user.service.impl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tongluxing.common.exception.BusinessException;
import com.tongluxing.common.result.ResultCode;
import com.tongluxing.common.utils.SnowflakeIdGenerator;
import com.tongluxing.user.mapper.UserDomainMapper;
import com.tongluxing.user.mapper.UserFollowMapper;
import com.tongluxing.user.dto.UserQueryDTO;
import com.tongluxing.user.dto.UserFollowQueryDTO;
import com.tongluxing.user.dto.request.CertificationRequest;
import com.tongluxing.user.vo.CertificationVO;
import com.tongluxing.user.vo.DrivingLicenseAuditDetailVO;
import com.tongluxing.user.vo.DrivingLicenseAuditSummaryVO;
import com.tongluxing.common.model.PageResult;
import com.tongluxing.user.vo.PublicProfileVO;
import com.tongluxing.user.dto.request.UpdateUserProfileRequest;
import com.tongluxing.user.vo.UserProfileVO;
import com.tongluxing.user.vo.FollowStatusVO;
import com.tongluxing.user.vo.FollowUserVO;
import com.tongluxing.user.vo.UserSearchVO;
import com.tongluxing.user.vo.PrivacySettingsVO;
import com.tongluxing.user.dto.request.UpdatePrivacySettingsRequest;
import com.tongluxing.user.service.UserService;
import com.tongluxing.user.support.CurrentUserContext;

import lombok.RequiredArgsConstructor;

/**
 * 用户模块业务实现。
 *
 * <p>主要负责用户资料读写、驾驶证认证、公开主页隐私控制，以及 Redis 缓存维护。
 * 默认资料和隐私设置采用懒初始化方式，避免注册流程必须一次性写入所有用户域数据。</p>
 */
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {
    /** 用于 AES-GCM 加密时生成随机 IV。 */
    private static final SecureRandom RANDOM = new SecureRandom();

    /** 当前用户完整资料缓存 Key。 */
    private static final String PROFILE_CACHE = "user:cache:profile:v3:%d";

    /** 用户公开资料缓存 Key。 */
    private static final String PUBLIC_CACHE = "user:cache:public-card:v3:%d";

    /** 统一解析 Spring Security 中的当前登录用户，避免各方法自行猜测 principal 类型。 */
    private final CurrentUserContext currentUserContext;

    /** 用户资料、隐私和驾驶证认证数据访问入口。 */
    private final UserDomainMapper mapper;

    /** 关注关系及关注通知数据访问入口。 */
    private final UserFollowMapper followMapper;

    /** 存取个人资料和公开名片缓存；缓存不可用时业务仍可回源数据库。 */
    private final StringRedisTemplate redis;

    /** 把不可变 VO 序列化为 JSON 存入 Redis，并在命中时恢复为对应类型。 */
    private final ObjectMapper objectMapper;

    /** 从配置读取的敏感数据主密钥原文，运行时经 SHA-256 派生为 AES-256 密钥。 */
    @Value("${tongluxing.user.data-encryption-key}")
    private String encryptionKey;

    /**
     * 查询当前登录用户资料。
     *
     * <p>优先读取 Redis 缓存；缓存不存在时查询数据库并回填缓存。</p>
     */
    @Override
    public UserProfileVO getCurrentProfile() {
        // 任何“我的资料”请求都先确定可靠的登录用户 ID，绝不接受客户端传入 userId。
        long userId = currentUserContext.requireUserId();

        // 完整资料变化不频繁，优先读 30 分钟缓存以减少资料、统计、认证状态的聚合查询。
        UserProfileVO cached = cacheGet(PROFILE_CACHE.formatted(userId), UserProfileVO.class);
        if (cached != null) {
            // 缓存对象已经是对外 VO，不会把查询 DTO 中的密文或内部字段带出。
            return cached;
        }

        // 缓存未命中或 Redis 异常时回源数据库；profile() 还会补齐历史用户的默认数据。
        UserProfileVO result = profile(userId);

        // 回填失败会被 cachePut 吞掉，数据库读取成功的结果仍正常返回。
        cachePut(PROFILE_CACHE.formatted(userId), result, Duration.ofMinutes(30));
        return result;
    }

    /**
     * 修改当前登录用户资料。
     *
     * <p>请求中的空字段表示“不修改”，因此会先读取旧资料再合并新值。
     * 修改后清理完整资料和公开资料缓存，保证后续读取到最新数据。</p>
     */
    @Override
    @Transactional
    public UserProfileVO updateCurrentProfile(UpdateUserProfileRequest request) {
        // 更新目标只能是当前登录用户，避免水平越权修改他人资料。
        long userId = currentUserContext.requireUserId();

        // 先读取完整旧值，因为该接口采用 PATCH 式语义：null 表示“不修改”。
        UserProfileVO old = profile(userId);

        // 把每个请求字段与旧值合并后一次性更新，防止未传字段被 SQL 写成 null。
        mapper.updateProfile(userId, value(request.nickname(), old.nickname()), value(request.avatarImageKey(), old.avatarImageKey()),
                request.gender() == null ? old.gender() : request.gender(), request.birthday() == null ? old.birthday() : request.birthday(),
                value(request.cityCode(), old.cityCode()), value(request.cityName(), old.cityName()),
                value(request.bio(), old.bio()), LocalDateTime.now());


        // 昵称、头像、城市和简介同时存在于私有资料与公开名片中，所以两个缓存都必须失效。
        redis.delete(java.util.List.of(PROFILE_CACHE.formatted(userId), PUBLIC_CACHE.formatted(userId)));

        // 重新查询而不是直接拼装返回值，以数据库最终落库内容作为响应依据。
        return profile(userId);
    }

    /**
     * 提交驾驶证材料并自动认证。
     *
     * <p>该流程先检查重复申请，再验证日期和正反面材料完整性，加密敏感字段后直接
     * 创建 APPROVED 记录。认证状态会出现在个人资料和公开名片，因此写入成功后必须
     * 同时清除两类缓存；记录仍完整保留，供 Web Admin 查询和追溯。</p>
     */
    @Override
    @Transactional
    public CertificationVO submitCertification(CertificationRequest request) {
        // 认证归属于当前登录用户，客户端不能替其他账号提交证件。
        long userId = currentUserContext.requireUserId();

        // 历史账号可能尚无 user_profile；认证前补齐资料与隐私默认行。
        ensureProfile(userId);
        UserQueryDTO latest = mapper.findLatestCertification(userId);
        if (latest != null && java.util.List.of("PENDING", "APPROVED").contains(latest.getCertificationStatus())) {
            // 历史待处理记录禁止重复排队；已通过时禁止创建第二份有效认证。
            throw new BusinessException(409, "认证正在审核或已通过");
        }

        // 整次写入使用同一个时间戳，保证 submitted_at/created_at/updated_at 一致。
        LocalDateTime now = LocalDateTime.now();
        validateDates(request);
        validateCompleteCertificationMaterials(request);

        // 姓名和完整证件号入库前加密；列表只使用脱敏号，减少非必要解密。
        // Mapper 固定写入 APPROVED，并把 reviewed_at 设为当前时间，表示系统自动通过。
        mapper.insertCertification(SnowflakeIdGenerator.nextId(), userId,
                encrypt(request.holderName().trim()), encrypt(request.licenseNo().trim()),
                maskLicenseNo(request.licenseNo()), request.vehicleClass().trim(), request.firstIssueDate(),
                request.validFrom(), request.validTo(), trimToEmpty(request.issuingAuthority()),
                request.licenseFrontImageKey().trim(), trimToEmpty(request.licenseBackImageKey()),
                request.recognitionSource().trim(), now);

        // 最新认证状态已经由 UNSUBMITTED/REJECTED 变为 APPROVED，旧资料缓存不可继续使用。
        redis.delete(java.util.List.of(PROFILE_CACHE.formatted(userId), PUBLIC_CACHE.formatted(userId)));

        // 回查刚提交的最新记录，统一通过 certification() 计算 canResubmit。
        return certification(mapper.findLatestCertification(userId));
    }

    /** 查询当前用户最近一次驾驶证认证状态。 */
    @Override
    public CertificationVO getLatestCertification() {
        long userId = currentUserContext.requireUserId();
        UserQueryDTO latest = mapper.findLatestCertification(userId);
        if (latest == null) {
            // 用显式 UNSUBMITTED 对象代替 null，前端无需区分“无记录”和“接口无数据”。
            return new CertificationVO(null, userId, "UNSUBMITTED", null, null, null, true);
        }
        // 有记录时由统一转换函数根据状态推导是否允许重新提交。
        return certification(latest);
    }

    /** 后台分页查询驾驶证认证申请。 */
    @Override
    public PageResult<DrivingLicenseAuditSummaryVO> pageDrivingLicenseCertifications(
            String status, String keyword, int page, int size) {
        // 后台调用方即使传入 0 或负页码，也统一从第 1 页查询。
        int normalizedPage = Math.max(page, 1);

        // 每页限制在 1~100，避免一次读取并解密过多敏感认证数据。
        int normalizedSize = Math.min(Math.max(size, 1), 100);

        // 状态需要先转大写并校验白名单，不能把任意字符串带入业务查询。
        String normalizedStatus = normalizeStatusFilter(status);
        String normalizedKeyword = trimToEmpty(keyword);

        // Mapper 返回姓名密文；只有授权后台列表在这里解密，证件号仍只展示脱敏值。
        List<DrivingLicenseAuditSummaryVO> records = mapper.pageCertifications(
                        normalizedStatus, normalizedKeyword, (normalizedPage - 1) * normalizedSize, normalizedSize)
                .stream()
                .map(row -> new DrivingLicenseAuditSummaryVO(
                        row.getId(), row.getUserId(), decrypt(row.getHolderNameCipher()), row.getLicenseNoMask(),
                        row.getVehicleClass(), row.getValidTo(), row.getCertificationStatus(), row.getSubmittedAt()))
                .toList();

        // count 使用相同过滤条件，确保 total 与当前页记录属于同一个结果集合。
        long total = mapper.countCertifications(normalizedStatus, normalizedKeyword);
        return new PageResult<>(records, total, normalizedPage, normalizedSize);
    }

    /** 后台查询驾驶证认证详情。 */
    @Override
    public DrivingLicenseAuditDetailVO getDrivingLicenseCertificationForAudit(Long certificationId) {
        // 详情查询按认证申请主键定位，而不是按 userId 猜测“最新一条”。
        UserQueryDTO row = mapper.findCertificationById(certificationId);
        if (row == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "驾驶证认证申请不存在");
        }

        // 仅在后台审核专用转换函数内解密姓名和完整驾驶证号。
        return auditDetail(row);
    }

    /** 后台应用驾驶证人工审核结果。 */
    @Override
    @Transactional
    public DrivingLicenseAuditDetailVO applyDrivingLicenseAuditResult(
            Long certificationId, String auditResult, String rejectReason, Long operatorId) {
        // 先归一化状态和原因，使数据库中只出现约定枚举及合法长度的驳回理由。
        String normalizedResult = normalizeAuditResult(auditResult);
        String normalizedReason = normalizeRejectReason(normalizedResult, rejectReason);

        // 读取审核前快照既用于存在性/状态检查，也用于稍后定位要失效的用户缓存。
        UserQueryDTO before = mapper.findCertificationById(certificationId);
        if (before == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "驾驶证认证申请不存在");
        }
        if (!"PENDING".equals(before.getCertificationStatus())) {
            // APPROVED/REJECTED 都是终态，不允许通过重复调用覆盖首次审核结论。
            throw new BusinessException(409, "驾驶证认证申请已审核");
        }

        // SQL 再次要求 status=PENDING，防止检查后到更新前被另一审核员抢先处理。
        int changed = mapper.updateCertificationAudit(
                certificationId, normalizedResult, normalizedReason, operatorId, LocalDateTime.now());
        if (changed != 1) {
            // 影响行数不是 1 说明发生并发状态变化，要求调用方刷新而不是静默覆盖。
            throw new BusinessException(409, "驾驶证认证状态已发生变化，请刷新后重试");
        }

        // 审核结果会改变用户资料/名片上的认证徽标，必须清理该被审核用户的两个缓存。
        redis.delete(List.of(PROFILE_CACHE.formatted(before.getUserId()), PUBLIC_CACHE.formatted(before.getUserId())));

        // 回查并返回数据库中的最终审核详情，包含审核时间和规范化后的原因。
        return auditDetail(mapper.findCertificationById(certificationId));
    }

    /**
     * 查询用户公开主页资料。
     *
     * <p>公开主页受隐私设置控制：当 profileVisibility 为 PRIVATE 时，
     * 对外表现为“用户主页不存在”，避免泄露该用户是否存在或主动隐藏的信息。</p>
     */
    @Override
    public PublicProfileVO getPublicProfile(Long userId) {
        // 历史账号可能只有认证账号和行程数据，没有初始化 user_profile/user_privacy。
        // 公开主页首次访问时补齐默认资料，避免合法用户被错误返回为“主页不存在”。
        ensureProfile(userId);

        // 公开名片已经完成隐私裁剪，可以安全缓存；TTL 比私有资料短以更快响应隐私变化。
        PublicProfileVO cached = cacheGet(PUBLIC_CACHE.formatted(userId), PublicProfileVO.class);
        if (cached != null) {
            return cached;
        }
        UserQueryDTO row = mapper.findProfile(userId);
        if (row == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户主页不存在");
        }
        UserQueryDTO privacy = ensurePrivacy(userId);
        if ("PRIVATE".equals(privacy.getProfileVisibility())) {
            // 使用 NOT_FOUND 而不是“已隐藏”，避免向无权限调用方确认账号存在。
            throw new BusinessException(ResultCode.NOT_FOUND, "用户主页不存在");
        }

        // 总开关允许展示主页后，城市、简介和统计仍分别受细粒度开关控制。
        UserProfileVO profile = profile(row);
        boolean showCity = Boolean.TRUE.equals(privacy.getCityVisibleFlag());
        boolean showBio = Boolean.TRUE.equals(privacy.getBioVisibleFlag());
        boolean showStats = Boolean.TRUE.equals(privacy.getTripStatsVisibleFlag());

        // 被隐藏的字符串返回空串、数值返回 0，保持响应结构稳定且不泄露原值。
        PublicProfileVO result = new PublicProfileVO(profile.userId(), profile.tongluxingId(),
                displayNickname(profile.nickname(), profile.tongluxingId()), profile.avatarImageKey(),
                showCity ? profile.cityName() : "", showBio ? profile.bio() : "",
                profile.drivingLicenseCertificationStatus(),
                showStats ? row.getTotalTripCount() : 0, showStats ? row.getTotalDistanceMeters() : 0L,
                showStats ? row.getTotalDurationMinutes() : 0L,
                showStats ? row.getCompletedWaypointCount() : 0);
        cachePut(PUBLIC_CACHE.formatted(userId), result, Duration.ofMinutes(15));
        return result;
    }

    /** 读取当前登录用户的全部隐私开关；首次访问会创建默认设置。 */
    @Override
    public PrivacySettingsVO getCurrentPrivacySettings() {
        // userId 来自认证上下文，防止客户端读取他人的非公开开关组合。
        return privacy(ensurePrivacy(currentUserContext.requireUserId()));
    }

    /**
     * 读取指定用户隐私设置，供已经完成自身权限校验的内部聚合模块使用。
     *
     * <p>该内部能力不等同于公开 HTTP 接口，调用方不得把完整开关直接暴露给普通用户。</p>
     */
    @Override
    public PrivacySettingsVO getPrivacySettings(Long userId) {
        return privacy(ensurePrivacy(userId));
    }

    /** 合并并保存当前登录用户的隐私设置增量修改。 */
    @Override
    @Transactional
    public PrivacySettingsVO updateCurrentPrivacySettings(UpdatePrivacySettingsRequest request) {
        long userId = currentUserContext.requireUserId();

        // 先取得完整旧设置；请求中的 null 表示保持原值，而不是关闭该能力。
        UserQueryDTO row = ensurePrivacy(userId);

        // 每个非空字段独立覆盖，让客户端可以只修改某一个开关。
        if (request.profileVisibility() != null) row.setProfileVisibility(request.profileVisibility());
        if (request.vehicleVisibility() != null) row.setVehicleVisibility(request.vehicleVisibility());
        if (request.inviteEnabled() != null) row.setInviteEnabledFlag(request.inviteEnabled());
        if (request.cityVisible() != null) row.setCityVisibleFlag(request.cityVisible());
        if (request.bioVisible() != null) row.setBioVisibleFlag(request.bioVisible());
        if (request.tripStatsVisible() != null) row.setTripStatsVisibleFlag(request.tripStatsVisible());
        if (request.levelVisible() != null) row.setLevelVisibleFlag(request.levelVisible());
        if (request.locationEnabled() != null) row.setLocationEnabledFlag(request.locationEnabled());
        if (request.notificationEnabled() != null) row.setNotificationEnabledFlag(request.notificationEnabled());

        // 使用服务端时间记录修改时刻，避免客户端伪造审计时间。
        row.setUpdatedAt(LocalDateTime.now());
        mapper.updatePrivacy(row);

        // 主页总开关及城市/简介/统计开关都会改变公开名片内容，立即让缓存失效。
        redis.delete(PUBLIC_CACHE.formatted(userId));

        // 回查数据库并返回规范的 Boolean VO，确保响应是实际持久化结果。
        return privacy(mapper.findPrivacy(userId));
    }

    /**
     * 把数据库隐私 DTO 转换为稳定的接口模型。
     *
     * <p>{@code Boolean.TRUE.equals} 会把数据库映射异常产生的 null 安全解释为关闭，
     * 避免自动拆箱空指针，也遵循隐私字段“默认不额外暴露”的原则。</p>
     */
    private PrivacySettingsVO privacy(UserQueryDTO row) {
        return new PrivacySettingsVO(row.getProfileVisibility(), row.getVehicleVisibility(),
                Boolean.TRUE.equals(row.getInviteEnabledFlag()), Boolean.TRUE.equals(row.getCityVisibleFlag()),
                Boolean.TRUE.equals(row.getBioVisibleFlag()), Boolean.TRUE.equals(row.getTripStatsVisibleFlag()),
                Boolean.TRUE.equals(row.getLevelVisibleFlag()), Boolean.TRUE.equals(row.getLocationEnabledFlag()),
                Boolean.TRUE.equals(row.getNotificationEnabledFlag()));
    }

    /**
     * 查询聊天成员基础资料。聊天模块已经先校验会话成员身份，因此这里不再应用公开主页可见性，
     * 并直接读取数据库，保证用户刚修改的头像能够立即出现在聊天记录和群成员列表中。
     */
    @Override
    public PublicProfileVO getChatMemberProfile(Long userId) {
        // 聊天中的历史/迁移用户也必须有可显示的默认昵称和资料行。
        ensureProfile(userId);

        // 不走 PUBLIC_CACHE：它已按公开主页隐私裁剪，可能缺少会话成员需要的字段。
        UserQueryDTO row = mapper.findProfile(userId);
        if (row == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户资料不存在");
        }
        UserProfileVO profile = profile(row);

        // 调用方必须先证明请求者是会话成员；这里返回会话展示所需的完整基础资料。
        return new PublicProfileVO(profile.userId(), profile.tongluxingId(),
                displayNickname(profile.nickname(), profile.tongluxingId()), profile.avatarImageKey(),
                profile.cityName(), profile.bio(), profile.drivingLicenseCertificationStatus(),
                row.getTotalTripCount(), row.getTotalDistanceMeters(), row.getTotalDurationMinutes(),
                row.getCompletedWaypointCount());
    }

    @Override
    @Transactional
    public FollowStatusVO follow(Long userId) {
        // 关注者只能是当前登录账号，被关注者由路径参数指定。
        Long currentUserId = currentUserContext.requireUserId();
        if (currentUserId.equals(userId)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "不能关注自己");
        }
        // 确保关注发起人的公开资料行存在，避免粉丝列表 INNER JOIN 时丢失该关系。
        ensureProfile(currentUserId);

        // 查询公开主页同时验证目标存在且允许公开；隐藏主页不能被公开关注入口探测。
        getPublicProfile(userId);
        if (followMapper.exists(currentUserId, userId) == 0) {
            try {
                // 关系与通知共用同一业务时间，并在事务内一起成功或回滚。
                LocalDateTime now = LocalDateTime.now();
                Long relationId = SnowflakeIdGenerator.nextId();
                followMapper.insert(relationId, currentUserId, userId, now);

                // requestId 关联本次关注关系，使通知可以追踪到唯一业务事件。
                followMapper.insertFollowNotification(
                        SnowflakeIdGenerator.nextId(), currentUserId, userId,
                        "USER_FOLLOW:" + relationId, now);
            } catch (DuplicateKeyException ignored) {
                // 两个并发请求都通过 exists 检查时，唯一索引保留一条关系；重复请求按成功处理。
            }
        }

        // 返回重新读取的双向关系和计数，便于前端一次刷新所有关注按钮与数字。
        return followStatus(currentUserId, userId);
    }

    /**
     * 取消关注目标用户。
     *
     * <p>删除不存在的关系影响 0 行，仍视为成功，从而支持客户端重试。</p>
     */
    @Override
    @Transactional
    public FollowStatusVO unfollow(Long userId) {
        Long currentUserId = currentUserContext.requireUserId();
        if (!currentUserId.equals(userId)) {
            // 自己与自己本就不存在合法关注关系，无需执行无意义删除。
            followMapper.delete(currentUserId, userId);
        }

        // 无论此前是否存在关系，都返回操作后的真实状态。
        return followStatus(currentUserId, userId);
    }

    /** 查询当前登录用户与目标用户之间的双向关注状态和目标计数。 */
    @Override
    public FollowStatusVO getFollowStatus(Long userId) {
        return followStatus(currentUserContext.requireUserId(), userId);
    }

    /** 匿名查看公开主页时仍返回真实粉丝/关注计数，但不伪造任何双向关系。 */
    @Override
    public FollowStatusVO getPublicFollowStatus(Long userId) {
        return new FollowStatusVO(userId, false, false, false,
                followMapper.countFollowers(userId), followMapper.countFollowing(userId));
    }

    /** 查询当前登录用户的粉丝列表，避免客户端自行传递并信任“我的 userId”。 */
    @Override
    public List<FollowUserVO> getMyFollowers(int page, int size) {
        Long currentUserId = currentUserContext.requireUserId();
        return getFollowers(currentUserId, page, size);
    }

    /** 统计当前登录用户尚未阅读的“新关注”通知数量。 */
    @Override
    public long countMyUnreadFollowerNotifications() {
        // 只把认证上下文中的用户 ID 交给 Mapper，避免读取他人的通知状态。
        return followMapper.countUnreadFollowerNotifications(
                currentUserContext.requireUserId());
    }

    @Override
    @Transactional
    public void markMyFollowerNotificationsRead() {
        // 单一服务端时间用于本次批量更新的 read_at 与 updated_at。
        followMapper.markFollowerNotificationsRead(
                currentUserContext.requireUserId(), LocalDateTime.now());
    }

    @Override
    public List<FollowUserVO> getMyFollowing(int page, int size) {
        Long currentUserId = currentUserContext.requireUserId();
        return getFollowing(currentUserId, page, size);
    }

    @Override
    public List<FollowUserVO> getMyMutualFollows(int page, int size) {
        Long currentUserId = currentUserContext.requireUserId();

        // 确保当前账号用户域资料存在；如果用户状态异常，profile() 会统一处理。
        profile(currentUserId);
        int safePage = Math.max(1, page);
        int safeSize = Math.max(1, Math.min(size, 50));

        // Mapper 已用自连接筛出互关关系，Java 层只补充相对当前用户的关系字段。
        return followMapper.mutualFollows(
                        currentUserId, (safePage - 1) * safeSize, safeSize)
                .stream()
                .map(row -> followUser(row, currentUserId))
                .toList();
    }

    @Override
    public List<FollowUserVO> getFollowers(Long userId, int page, int size) {
        Long currentUserId = currentUserContext.requireUserId();
        if (!currentUserId.equals(userId)) {
            // 他人账号必须通过公开主页可见性校验。
            getPublicProfile(userId);
        } else {
            // 自己的列表不受 profileVisibility=PRIVATE 限制。
            profile(userId);
        }
        int safePage = Math.max(1, page);
        int safeSize = Math.max(1, Math.min(size, 50));
        // offset 使用经过归一化的页码与页大小，避免负数进入 SQL LIMIT。
        return followMapper.followers(userId, (safePage - 1) * safeSize, safeSize).stream()
                .map(row -> followUser(row, currentUserId)).toList();
    }

    @Override
    public List<FollowUserVO> getFollowing(Long userId, int page, int size) {
        Long currentUserId = currentUserContext.requireUserId();
        if (!currentUserId.equals(userId)) {
            getPublicProfile(userId);
        } else {
            profile(userId);
        }
        int safePage = Math.max(1, page);
        int safeSize = Math.max(1, Math.min(size, 50));
        return followMapper.following(userId, (safePage - 1) * safeSize, safeSize).stream()
                .map(row -> followUser(row, currentUserId)).toList();
    }

    private FollowStatusVO followStatus(Long currentUserId, Long targetUserId) {
        // 自己对自己固定为非关注，避免执行两次没有意义的关系查询。
        boolean following = !currentUserId.equals(targetUserId)
                && followMapper.exists(currentUserId, targetUserId) > 0;

        // 反向检查用于判断“对方是否关注我”；两个方向都存在时才是互关。
        boolean followedByTarget = !currentUserId.equals(targetUserId)
                && followMapper.exists(targetUserId, currentUserId) > 0;

        // 计数始终属于目标用户，用于公开主页或列表按钮旁的数字展示。
        return new FollowStatusVO(targetUserId, following, followedByTarget,
                following && followedByTarget, followMapper.countFollowers(targetUserId),
                followMapper.countFollowing(targetUserId));
    }

    @Override
    public List<UserSearchVO> searchPublicUsers(String keyword, int page, int size) {
        Long currentUserId = currentUserContext.requireUserId();
        int safePage = Math.max(1, page);
        int safeSize = Math.max(1, Math.min(size, 50));
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        boolean exactPhoneSearch = normalizedKeyword.matches("^1[3-9]\\d{9}$");
        boolean incompletePhoneSearch = normalizedKeyword.matches("^1\\d{0,10}$") && !exactPhoneSearch;
        boolean tongluxingIdSearch = normalizedKeyword.matches("(?i)^TLX[0-9A-Z]*$");
        if (incompletePhoneSearch) {
            // 手机号搜索只接受完整的 11 位大陆手机号，禁止使用号段或手机号片段枚举用户。
            return List.of();
        }

        // 对每个公开资料查询关系状态，构造前端可直接渲染的完整搜索项。
        return mapper.searchPublicProfiles(normalizedKeyword, exactPhoneSearch, tongluxingIdSearch, currentUserId,
                        (safePage - 1) * safeSize, safeSize).stream()
                .map(row -> {
                    // 关系是相对于当前用户的动态信息，不能缓存进所有人共用的公开资料。
                    FollowStatusVO relation = followStatus(currentUserId, row.getUserId());
                    return new UserSearchVO(row.getUserId(), row.getTongluxingId(),
                            displayNickname(row.getNickname(), row.getTongluxingId()), row.getAvatarImageKey(),
                            row.getCityName(), row.getBio(), row.getCertificationStatus(),
                            row.getTotalTripCount(), row.getTotalDistanceMeters(),
                            relation.followerCount(), relation.followingCount(), relation.following(),
                            relation.followedByTarget(), relation.mutual());
                }).toList();
    }

    private FollowUserVO followUser(UserFollowQueryDTO row, Long currentUserId) {
        // Mapper DTO 只含列表公共字段；这里补齐当前用户视角下的关注方向。
        FollowStatusVO relation = followStatus(currentUserId, row.getUserId());
        return new FollowUserVO(row.getUserId(), StringUtils.hasText(row.getNickname()) ? row.getNickname() : "同路行车友", row.getAvatarImageKey(),
                row.getCertificationStatus(), row.getTotalTripCount(), row.getTotalDistanceMeters(),
                row.getFollowedAt(), relation.following(), relation.followedByTarget(), relation.mutual());
    }

    /**
     * 查询用户完整资料，必要时先初始化默认资料和隐私设置。
     */
    private UserProfileVO profile(long userId) {
        // 懒初始化让注册链路无需与用户域强耦合，同时兼容上线前已存在的历史账号。
        ensureProfile(userId);

        // 初始化后再次查询可得到同路行号、统计和最新认证状态的完整聚合结果。
        return profile(mapper.findProfile(userId));
    }

    /**
     * 将数据库查询对象转换为接口返回的用户资料 VO。
     */
    private UserProfileVO profile(UserQueryDTO row) {
        // 显式逐字段映射，确保密文、隐私开关和审核人等内部字段不会意外进入响应。
        return new UserProfileVO(row.getUserId(), row.getTongluxingId(),
                displayNickname(row.getNickname(), row.getTongluxingId()), row.getAvatarImageKey(), row.getGender(),
                row.getBirthday(), row.getCityCode(), row.getCityName(), row.getBio(),
                row.getProfileStatus(), row.getCertificationStatus());
    }

    /**
     * 确保用户资料记录存在。
     *
     * <p>并发首次访问时可能同时插入默认资料，唯一键冲突可以安全忽略，
     * 因为说明其他请求已经完成初始化。</p>
     */
    private void ensureProfile(long userId) {
        // 先查询再创建，绝大多数已初始化用户只产生一次读取，不执行写操作。
        UserQueryDTO profile = mapper.findProfile(userId);

        if (profile == null) {
            for (int attempt = 0; attempt < 8; attempt++) {
                String tongluxingId = generateTongluxingId();
                try {
                    mapper.insertProfile(SnowflakeIdGenerator.nextId(), userId, tongluxingId, LocalDateTime.now());
                    break;
                } catch (DuplicateKeyException collision) {
                    // user_id 冲突表示并发请求已经创建；公开号冲突则生成新号码重试。
                    if (mapper.findProfile(userId) != null) break;
                    if (attempt == 7) throw collision;
                }
            }
        } else if (profile.getTongluxingId() == null || profile.getTongluxingId().isBlank()) {
            for (int attempt = 0; attempt < 8; attempt++) {
                try {
                    mapper.updateTongluxingId(userId, generateTongluxingId(), LocalDateTime.now());
                    break;
                } catch (DuplicateKeyException collision) {
                    if (attempt == 7) throw collision;
                }
            }
        }

        // 资料与隐私是一组基础数据，任何 ensureProfile 调用后两者都应可查询。
        ensurePrivacy(userId);
    }

    /**
     * 生成与内部 userId 无关的随机公开编号。该编号仅用于展示和公开搜索，
     * 不参与登录、鉴权或数据库关联。
     */
    private String generateTongluxingId() {
        final String alphabet = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
        StringBuilder value = new StringBuilder("TLX");
        for (int i = 0; i < 10; i++) value.append(alphabet.charAt(RANDOM.nextInt(alphabet.length())));
        return value.toString();
    }

    private String displayNickname(String nickname, String tongluxingId) {
        if (!StringUtils.hasText(nickname)) {
            return "同路行用户";
        }
        String normalized = nickname.trim();
        // 历史版本曾把同路行号写入 nickname；它只是公开编号，不能作为昵称展示。
        if (StringUtils.hasText(tongluxingId)
                && normalized.equalsIgnoreCase(tongluxingId.trim())) {
            return "同路行用户";
        }
        return normalized;
    }

    /**
     * 确保用户隐私设置存在，不存在时创建默认公开配置。
     */
    private UserQueryDTO ensurePrivacy(long userId) {
        // 已有设置直接返回，避免每次访问公开主页都触发写操作。
        UserQueryDTO row = mapper.findPrivacy(userId);
        if (row != null) {
            return row;
        }
        try {
            // 默认值集中定义在 INSERT SQL 中，使所有懒初始化入口得到一致配置。
            mapper.insertPrivacy(SnowflakeIdGenerator.nextId(), userId, LocalDateTime.now());
        } catch (DuplicateKeyException ignored) {
            // 其他并发请求已创建隐私设置，重新查询即可。
        }
        // 无论本请求创建还是并发请求创建，都重新查询数据库获得最终设置。
        return mapper.findPrivacy(userId);
    }

    /**
     * 将驾驶证认证查询结果转换为接口返回对象。
     */
    private CertificationVO certification(UserQueryDTO row) {
        String status = row.getCertificationStatus();

        // 只有未提交或被驳回时可以再次申请；待审核和已通过都必须禁止重复提交。
        return new CertificationVO(row.getId(), row.getUserId(), status,
                row.getRejectReason(), row.getSubmittedAt(), row.getReviewedAt(),
                "UNSUBMITTED".equals(status) || "REJECTED".equals(status));
    }

    /**
     * 转换后台审核详情并解密授权字段。
     *
     * <p>解密范围严格限制在审核专用 VO；普通资料、公开主页和列表不会调用本方法。</p>
     */
    private DrivingLicenseAuditDetailVO auditDetail(UserQueryDTO row) {
        // 姓名与证件号只在内存中生成明文，不回写数据库，也不记录日志。
        return new DrivingLicenseAuditDetailVO(
                row.getId(), row.getUserId(), decrypt(row.getHolderNameCipher()), decrypt(row.getLicenseNoCipher()),
                row.getVehicleClass(), row.getFirstIssueDate(), row.getValidFrom(), row.getValidTo(),
                row.getIssuingAuthority(), row.getLicenseFrontImageKey(), row.getLicenseBackImageKey(),
                row.getRecognitionSource(), row.getCertificationStatus(), row.getRejectReason(),
                row.getSubmittedAt(), row.getReviewedAt());
    }

    /**
     * 校验驾驶证认证材料是否齐全。
     *
     * <p>姓名、证件号、准驾车型、识别来源和主页图片已由 Bean Validation 保证；
     * 此处额外要求副页图片存在，确保与 App、小程序的“正反面必传”规则一致。
     * 材料不完整时直接拒绝提交，不生成无法自动处理的 PENDING 记录。</p>
     */
    private void validateCompleteCertificationMaterials(CertificationRequest request) {
        if (!StringUtils.hasText(request.licenseBackImageKey())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "请上传完整的驾驶证正面和背面");
        }
    }

    /**
     * 校验驾驶证有效期日期的先后关系。
     *
     * <p>日期字段允许 OCR 未识别时为空；只有起止日期都存在时才比较。</p>
     */
    private void validateDates(CertificationRequest request) {
        if (request.validFrom() != null && request.validTo() != null
                && request.validTo().isBefore(request.validFrom())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "驾驶证有效期结束日期不能早于开始日期");
        }
    }

    /**
     * 归一化后台列表的可选认证状态过滤条件。
     *
     * @return 空字符串表示不过滤，否则返回大写后的合法状态
     */
    private String normalizeStatusFilter(String status) {
        String value = trimToEmpty(status).toUpperCase();
        if (value.isEmpty()) {
            // MyBatis 动态 SQL 把空字符串解释为“不添加状态条件”。
            return "";
        }
        if (!List.of("PENDING", "APPROVED", "REJECTED").contains(value)) {
            // 白名单既统一接口语义，也避免数据库出现无法识别的状态查询。
            throw new BusinessException(ResultCode.BAD_REQUEST, "认证状态不合法");
        }
        return value;
    }

    /** 将人工审核结果归一为 APPROVED 或 REJECTED 两种终态。 */
    private String normalizeAuditResult(String auditResult) {
        String value = trimToEmpty(auditResult).toUpperCase();
        if (!List.of("APPROVED", "REJECTED").contains(value)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "审核结果仅支持 APPROVED 或 REJECTED");
        }
        return value;
    }

    /**
     * 根据审核结果规范化驳回原因。
     *
     * <p>驳回必须说明原因；通过时无论调用方是否传值都强制保存 null，避免“已通过但
     * 带驳回原因”的矛盾数据。</p>
     */
    private String normalizeRejectReason(String auditResult, String rejectReason) {
        String value = trimToEmpty(rejectReason);
        if ("REJECTED".equals(auditResult) && (value.length() < 2 || value.length() > 255)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "驳回原因长度应为 2 到 255 个字符");
        }
        return "APPROVED".equals(auditResult) ? null : value;
    }

    /**
     * 生成用于后台列表的驾驶证号脱敏值。
     *
     * <p>较长号码保留前三位和后三位；极短号码只保留首尾字符。完整号码始终以密文
     * 形式保存，脱敏值不能用于恢复原证件号。</p>
     */
    private String maskLicenseNo(String licenseNo) {
        String value = licenseNo.trim();
        if (value.length() <= 6) {
            // 输入校验当前要求至少 6 位，此分支也兼容未来规则调整或历史短号码。
            return value.substring(0, 1) + "****" + value.substring(value.length() - 1);
        }
        return value.substring(0, 3) + "********" + value.substring(value.length() - 3);
    }

    /**
     * 加密敏感文本字段。
     *
     * <p>使用配置密钥派生 SHA-256 对称密钥，并使用 AES-GCM 生成随机 IV。
     * 返回值会把 IV 和密文拼接后进行 Base64 编码，便于数据库保存。</p>
     */
    private String encrypt(String value) {
        try {
            // 对可变长度配置口令做 SHA-256，得到 AES-256 所需的固定 32 字节密钥。
            byte[] key = MessageDigest.getInstance("SHA-256").digest(encryptionKey.getBytes(StandardCharsets.UTF_8));

            // GCM 推荐使用 12 字节随机 IV；每次随机化使相同明文也产生不同密文。
            byte[] iv = new byte[12];
            RANDOM.nextBytes(iv);

            // 128 位认证标签不仅保密，还能在解密时发现密文被篡改或密钥不匹配。
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));

            // IV 无需保密，但解密必须使用；将其放在密文前形成自包含载荷。
            byte[] result = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, result, 0, iv.length);
            System.arraycopy(encrypted, 0, result, iv.length, encrypted.length);
            // Base64 把二进制载荷转换为可安全存入 varchar 字段的文本。
            return Base64.getEncoder().encodeToString(result);
        } catch (Exception e) {
            // 不把底层算法、密钥或明文写入异常信息，避免敏感细节泄露。
            throw new BusinessException(ResultCode.INTERNAL_SERVER_ERROR, "敏感数据加密失败");
        }
    }

    /**
     * 解密仅供后台授权审核详情使用的敏感字段。
     *
     * <p>按 encrypt() 的“12 字节 IV + GCM 密文/认证标签”格式执行逆过程。</p>
     */
    private String decrypt(String value) {
        if (value == null || value.isBlank()) {
            // 兼容历史空字段，避免为无内容的可选值触发解码异常。
            return "";
        }
        try {
            // 使用与加密完全相同的派生规则恢复 AES 密钥。
            byte[] key = MessageDigest.getInstance("SHA-256").digest(encryptionKey.getBytes(StandardCharsets.UTF_8));
            byte[] payload = Base64.getDecoder().decode(value);

            // 前 12 字节是 IV，其余部分包含密文和 GCM 认证标签。
            byte[] iv = java.util.Arrays.copyOfRange(payload, 0, 12);
            byte[] encrypted = java.util.Arrays.copyOfRange(payload, 12, payload.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
            // doFinal 会同时验证认证标签；数据被篡改时不会返回不可信明文。
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception exception) {
            // 对外统一为业务错误，不泄露究竟是格式损坏、密钥错误还是认证失败。
            throw new BusinessException(ResultCode.INTERNAL_SERVER_ERROR, "敏感数据解密失败");
        }
    }

    /**
     * 从 Redis 读取缓存并反序列化。
     *
     * <p>缓存读取失败不影响主流程，直接返回 null 走数据库查询。</p>
     */
    private <T> T cacheGet(String key, Class<T> type) {
        try {
            // Redis 保存 JSON 字符串；不存在时不做反序列化并以 null 表示未命中。
            String value = redis.opsForValue().get(key);
            return value == null ? null : objectMapper.readValue(value, type);
        } catch (Exception ignored) {
            // Redis 超时、连接失败或旧 JSON 不兼容都降级为缓存未命中，随后回源数据库。
            return null;
        }
    }

    /**
     * 写入 Redis 缓存。
     *
     * <p>缓存写入失败不阻断业务返回，因为数据库仍然是最终可信数据源。</p>
     */
    private void cachePut(String key, Object value, Duration ttl) {
        try {
            // 使用带 TTL 的原子 SET，避免成功写入后因未设置过期时间形成永久脏缓存。
            redis.opsForValue().set(key, objectMapper.writeValueAsString(value), ttl);
        } catch (Exception ignored) {
            // 缓存失败不影响主链路。
        }
    }

    /**
     * 合并可选字符串字段：未传入则保留旧值，传入则去除首尾空白。
     */
    private String value(String candidate, String old) {
        // 显式传入空字符串表示“清空字段”，因此只有 null 才代表保留旧值。
        return candidate == null ? old : candidate.trim();
    }

    /** 把可选字符串统一为去除首尾空白的非 null 值，便于动态 SQL 判断。 */
    private String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
    /** 在用户实际打开粉丝页后，把当前账号的全部新关注通知标记为已读。 */
    /** 查询当前登录用户主动关注的人。 */
    /**
     * 查询当前用户与对方互相关注的用户列表。
     *
     * <p>页码至少为 1、每页限制为 1~50，防止异常参数制造超大数据库查询。</p>
     */
    /**
     * 查询指定用户的粉丝。
     *
     * <p>查看别人列表前复用公开主页检查其存在性和可见性；查看自己时读取私有资料，
     * 不会因为自己关闭公开主页而阻止管理自己的粉丝。</p>
     */
    /** 查询指定用户主动关注的人，权限和分页规则与粉丝列表一致。 */
    /**
     * 搜索可公开展示的用户，并补充其与当前用户的关注关系。
     *
     * <p>空关键词表示浏览，页大小最多 50；Mapper 负责公开性过滤，Service 负责加入
     * 当前登录用户相关的双向关系和关注计数。</p>
     */
