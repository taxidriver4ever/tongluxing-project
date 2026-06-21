package com.tongdao.user.service.impl;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tongdao.common.exception.BusinessException;
import com.tongdao.common.result.ResultCode;
import com.tongdao.common.utils.SnowflakeIdGenerator;
import com.tongdao.user.integration.UserModuleFacade;
import com.tongdao.user.integration.UserModuleFacade.PublishOutcome;
import com.tongdao.user.mapper.UserDomainMapper;
import com.tongdao.user.model.UserModels.AvailableCouponVO;
import com.tongdao.user.model.UserModels.BadgeVO;
import com.tongdao.user.model.UserModels.BadgeWallVO;
import com.tongdao.user.model.UserModels.CertificationRequest;
import com.tongdao.user.model.UserModels.CertificationVO;
import com.tongdao.user.model.UserModels.CouponCountVO;
import com.tongdao.user.model.UserModels.CouponDeductionVO;
import com.tongdao.user.model.UserModels.CouponLockRequest;
import com.tongdao.user.model.UserModels.CouponOrderResultRequest;
import com.tongdao.user.model.UserModels.CouponSummaryVO;
import com.tongdao.user.model.UserModels.GrowthLogVO;
import com.tongdao.user.model.UserModels.GrowthSummaryVO;
import com.tongdao.user.model.UserModels.InvitationSummaryVO;
import com.tongdao.user.model.UserModels.InvitationVO;
import com.tongdao.user.model.UserModels.InviteBindVO;
import com.tongdao.user.model.UserModels.InviteCodeVO;
import com.tongdao.user.model.UserModels.InviteRewardProgressVO;
import com.tongdao.user.model.UserModels.LocationRequest;
import com.tongdao.user.model.UserModels.LocationVO;
import com.tongdao.user.model.UserModels.PageResult;
import com.tongdao.user.model.UserModels.PublicProfileVO;
import com.tongdao.user.model.UserModels.PublishResultVO;
import com.tongdao.user.model.UserModels.TeamMatchVO;
import com.tongdao.user.model.UserModels.TripDraftRequest;
import com.tongdao.user.model.UserModels.TripDraftVO;
import com.tongdao.user.model.UserModels.UpdateUserProfileRequest;
import com.tongdao.user.model.UserModels.UserCouponDetailVO;
import com.tongdao.user.model.UserModels.UserDashboardVO;
import com.tongdao.user.model.UserModels.UserHomepageVO;
import com.tongdao.user.model.UserModels.UserProfileVO;
import com.tongdao.user.service.UserService;
import com.tongdao.user.support.CurrentUserContext;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String PROFILE_CACHE = "user:cache:profile:%d";
    private static final String PUBLIC_CACHE = "user:cache:public-card:%d";
    private static final String DASHBOARD_CACHE = "user:cache:dashboard:%d";
    private static final String GROWTH_CACHE = "user:cache:growth:%d";
    private static final String BADGES_CACHE = "user:cache:badges:%d";
    private static final String INVITE_SUMMARY_CACHE = "user:cache:invite-summary:%d";
    private static final String COUPON_COUNT_CACHE = "user:cache:coupon-count:%d";
    private static final String DRAFT_CACHE = "user:cache:trip-draft:%d";
    private static final DefaultRedisScript<Long> COMPARE_DELETE = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end", Long.class);

    private final CurrentUserContext currentUserContext;
    private final UserDomainMapper mapper;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<UserModuleFacade> facadeProvider;

    @Value("${tongdao.user.data-encryption-key:change-this-user-data-key}")
    private String encryptionKey;

    @Override
    public UserProfileVO getCurrentProfile() {
        long userId = currentUserContext.requireUserId();
        UserProfileVO cached = cacheGet(PROFILE_CACHE.formatted(userId), UserProfileVO.class);
        if (cached != null) return cached;
        UserProfileVO value = profile(userId);
        cachePut(PROFILE_CACHE.formatted(userId), value, Duration.ofMinutes(30));
        return value;
    }

    @Override
    @Transactional
    public UserProfileVO updateCurrentProfile(UpdateUserProfileRequest request) {
        long userId = currentUserContext.requireUserId();
        rateLimit("user:rl:profile-update:" + userId, 10, Duration.ofSeconds(60));
        UserProfileVO old = profile(userId);
        mapper.updateProfile(userId, value(request.nickname(), old.nickname()), value(request.avatarImageKey(), old.avatarImageKey()),
                request.gender() == null ? old.gender() : request.gender(), request.birthday() == null ? old.birthday() : request.birthday(),
                value(request.cityCode(), old.cityCode()), value(request.cityName(), old.cityName()),
                value(request.bio(), old.bio()), LocalDateTime.now());
        clearUserCaches(userId);
        return profile(userId);
    }

    @Override
    @Transactional
    public CertificationVO submitCertification(CertificationRequest request) {
        long userId = currentUserContext.requireUserId();
        ensureProfile(userId);
        Map<String, Object> latest = mapper.findLatestCertification(userId);
        if (latest != null && List.of("PENDING", "APPROVED").contains(str(latest, "certificationStatus"))) {
            throw conflict("认证正在审核或已通过");
        }
        long id = SnowflakeIdGenerator.nextId();
        LocalDateTime now = LocalDateTime.now();
        mapper.insertCertification(id, userId, encrypt(request.realName()), encrypt(request.idCardNo()),
                request.drivingLicenseImageKey().trim(), request.faceImageKey().trim(), now);
        clearUserCaches(userId);
        return certification(mapper.findLatestCertification(userId));
    }

    @Override
    public PublicProfileVO getPublicProfile(Long userId) {
        PublicProfileVO cached = cacheGet(PUBLIC_CACHE.formatted(userId), PublicProfileVO.class);
        if (cached != null) return cached;
        Map<String, Object> profileRow = mapper.findProfile(userId);
        if (profileRow == null) throw notFound("用户主页不存在");
        Map<String, Object> privacy = ensurePrivacy(userId);
        if ("PRIVATE".equals(str(privacy, "profileVisibility"))) throw new BusinessException(ResultCode.NOT_FOUND, "用户主页不存在");
        UserProfileVO p = profile(profileRow);
        PublicProfileVO result = new PublicProfileVO(p.userId(), p.nickname(), p.avatarImageKey(), p.cityName(), p.bio(), p.certificationStatus());
        cachePut(PUBLIC_CACHE.formatted(userId), result, Duration.ofMinutes(15));
        return result;
    }

    @Override
    public UserDashboardVO getDashboard() {
        long userId = currentUserContext.requireUserId();
        UserDashboardVO cached = cacheGet(DASHBOARD_CACHE.formatted(userId), UserDashboardVO.class);
        if (cached != null) return cached;
        GrowthSummaryVO growth = growth(userId);
        InviteRewardProgressVO inviteProgress = invitationRewards(userId);
        UserDashboardVO value = new UserDashboardVO(profile(userId), growth, couponCount(userId),
                new InvitationSummaryVO(inviteProgress.validInviteCount(), inviteProgress.nextRewardNeed()), latestDraft(userId));
        cachePut(DASHBOARD_CACHE.formatted(userId), value, Duration.ofMinutes(5));
        return value;
    }

    @Override
    public GrowthSummaryVO getGrowth() {
        return growth(currentUserContext.requireUserId());
    }

    @Override
    public PageResult<GrowthLogVO> getGrowthLogs(int page, int size) {
        long userId = currentUserContext.requireUserId();
        int p = page(page), s = size(size);
        List<GrowthLogVO> rows = mapper.findGrowthLogs(userId, (p - 1) * s, s).stream().map(this::growthLog).toList();
        return new PageResult<>(rows, mapper.countGrowthLogs(userId), p, s);
    }

    @Override
    public BadgeWallVO getBadges() {
        return badges(currentUserContext.requireUserId());
    }

    @Override
    public UserHomepageVO getHomepage(Long userId) {
        return new UserHomepageVO(getPublicProfile(userId), growth(userId), badges(userId));
    }

    @Override
    @Transactional
    public InviteCodeVO getInviteCode() {
        long userId = currentUserContext.requireUserId();
        Map<String, Object> row = mapper.findInviteCodeByUser(userId);
        if (row == null) {
            String code = "TD" + Long.toUnsignedString(SnowflakeIdGenerator.nextId(), 36).toUpperCase();
            if (code.length() > 16) code = code.substring(code.length() - 16);
            mapper.insertInviteCode(SnowflakeIdGenerator.nextId(), userId, code, LocalDateTime.now());
            row = mapper.findInviteCodeByUser(userId);
        }
        try { redis.opsForValue().set("user:cache:invite-code:" + str(row, "inviteCode"), String.valueOf(userId), Duration.ofHours(24)); }
        catch (RuntimeException ignored) { }
        return new InviteCodeVO(str(row, "inviteCode"), str(row, "inviteCode"), bool(row, "enabledFlag"));
    }

    @Override
    @Transactional
    public InviteBindVO bindInvite(String inviteCode) {
        long invitee = currentUserContext.requireUserId();
        UserModuleFacade facade = facadeProvider.getIfAvailable();
        if (facade == null || !facade.isInviteBindingEligible(invitee)) throw conflict("仅新注册用户可以绑定邀请码");
        return bindInvite(invitee, inviteCode);
    }

    @Override
    @Transactional
    public InviteBindVO bindInviteForNewUser(Long userId, String inviteCode) {
        return bindInvite(userId, inviteCode);
    }

    private InviteBindVO bindInvite(long invitee, String inviteCode) {
        rateLimit("user:rl:invite-bind:" + invitee, 5, Duration.ofSeconds(60));
        if (mapper.findInviteRelationIdByInvitee(invitee) != null) throw conflict("邀请关系已存在");
        Map<String, Object> code = mapper.findInviteCode(inviteCode.trim().toUpperCase());
        if (code == null || !bool(code, "enabledFlag")) throw conflict("邀请码不存在或已停用");
        long inviter = lng(code, "userId");
        if (inviter == invitee) throw conflict("不能绑定自己的邀请码");
        if (mapper.createsInviteCycle(inviter, invitee) > 0) throw conflict("邀请关系不能形成循环");
        long id = SnowflakeIdGenerator.nextId();
        LocalDateTime now = LocalDateTime.now();
        try {
            mapper.insertInviteRelation(id, inviter, invitee, str(code, "inviteCode"), now);
        } catch (DuplicateKeyException exception) {
            throw conflict("邀请关系已存在");
        }
        safeDelete(INVITE_SUMMARY_CACHE.formatted(inviter), DASHBOARD_CACHE.formatted(inviter));
        return new InviteBindVO(inviter, invitee, "BOUND", now);
    }

    @Override
    public PageResult<InvitationVO> getInvitations(String status, int page, int size) {
        long userId = currentUserContext.requireUserId();
        int p = page(page), s = size(size);
        List<InvitationVO> rows = mapper.findInvitations(userId, status, (p - 1) * s, s).stream().map(this::invitation).toList();
        return new PageResult<>(rows, mapper.countInvitations(userId, status), p, s);
    }

    @Override
    public InviteRewardProgressVO getInvitationRewards() {
        return invitationRewards(currentUserContext.requireUserId());
    }

    @Override
    public PageResult<CouponSummaryVO> getCoupons(String status, String type, int page, int size) {
        long userId = currentUserContext.requireUserId();
        int p = page(page), s = size(size);
        List<CouponSummaryVO> rows = mapper.findCoupons(userId, status, type, (p - 1) * s, s).stream().map(this::couponSummary).toList();
        return new PageResult<>(rows, mapper.countCoupons(userId, status, type), p, s);
    }

    @Override
    public UserCouponDetailVO getCoupon(Long id) {
        Map<String, Object> row = mapper.findCoupon(id, currentUserContext.requireUserId());
        if (row == null) throw notFound("优惠券不存在");
        return couponDetail(row);
    }

    @Override
    public List<AvailableCouponVO> getAvailableCoupons(String orderType, Long merchantId, BigDecimal amount) {
        if (!StringUtils.hasText(orderType) || amount == null || amount.signum() < 0) throw badRequest("订单参数不完整");
        return mapper.findAvailableCoupons(currentUserContext.requireUserId(), merchantId, amount).stream()
                .filter(row -> scopeAllows(str(row, "scopeJson"), orderType, merchantId))
                .map(this::availableCoupon).toList();
    }

    @Override
    @Transactional
    public CouponDeductionVO lockCoupon(Long id, CouponLockRequest request) {
        String lockKey = "user:lock:coupon:" + id;
        String lockValue = String.valueOf(request.orderId());
        if (!tryLock(lockKey, lockValue, Duration.ofMinutes(15))) throw conflict("优惠券正在被其他订单使用");
        LocalDateTime now = LocalDateTime.now();
        if (mapper.lockCoupon(id, request.orderId(), request.amount(), now) == 0) {
            unlock(lockKey, lockValue);
            throw conflict("优惠券状态冲突或不满足使用规则");
        }
        Map<String, Object> row = mapper.findLockedCoupon(id, request.orderId());
        long userId = lng(row, "userId");
        clearCouponCaches(userId);
        return new CouponDeductionVO(id, request.orderId(), decimal(row, "discountAmount"), "LOCKED");
    }

    @Override
    @Transactional
    public void handleOrderResult(CouponOrderResultRequest request) {
        LocalDateTime now = LocalDateTime.now();
        List<Long> couponIds = mapper.findLockedCouponIdsByOrder(request.orderId());
        int changed = "SUCCESS".equals(request.payStatus()) ? mapper.confirmCoupons(request.orderId(), now) : mapper.releaseCoupons(request.orderId(), now);
        if (changed == 0) throw conflict("订单优惠券状态冲突");
        couponIds.forEach(id -> unlock("user:lock:coupon:" + id, String.valueOf(request.orderId())));
        deleteMatching("user:cache:coupon-*");
        deleteMatching("user:cache:dashboard:*");
    }

    @Override
    @Transactional
    public TripDraftVO createDraft(TripDraftRequest request) {
        long userId = currentUserContext.requireUserId();
        long id = SnowflakeIdGenerator.nextId();
        LocalDateTime now = LocalDateTime.now();
        mapper.insertDraft(id, userId, json(request.startLocation()), json(request.endLocation()), json(list(request.waypoints())),
                request.departureTime(), request.durationDays(), request.peopleCount(), text(request.remark()), now);
        clearDraftCaches(userId);
        return draft(mapper.findDraft(id, userId));
    }

    @Override
    public PageResult<TripDraftVO> getDrafts(String status, int page, int size) {
        long userId = currentUserContext.requireUserId();
        int p = page(page), s = size(size);
        List<TripDraftVO> rows = mapper.findDrafts(userId, status, (p - 1) * s, s).stream().map(this::draft).toList();
        return new PageResult<>(rows, mapper.countDrafts(userId, status), p, s);
    }

    @Override
    @Transactional
    public TripDraftVO updateDraft(Long id, TripDraftRequest request) {
        long userId = currentUserContext.requireUserId();
        if (mapper.updateDraft(id, userId, json(request.startLocation()), json(request.endLocation()), json(list(request.waypoints())),
                request.departureTime(), request.durationDays(), request.peopleCount(), text(request.remark()), LocalDateTime.now()) == 0) {
            throw conflict("草稿不存在或状态不允许修改");
        }
        clearDraftCaches(userId);
        return draft(mapper.findDraft(id, userId));
    }

    @Override
    @Transactional
    public void deleteDraft(Long id) {
        long userId = currentUserContext.requireUserId();
        if (mapper.deleteDraft(id, userId, LocalDateTime.now()) == 0) throw conflict("草稿不存在或状态不允许删除");
        clearDraftCaches(userId);
    }

    @Override
    @Transactional
    public PublishResultVO publishDraft(Long id, String publishType) {
        long userId = currentUserContext.requireUserId();
        requireCertified(userId);
        TripDraftVO draft = draft(mapper.findDraftForUpdate(id, userId));
        if (draft == null) throw notFound("行程草稿不存在");
        if (!"DRAFT".equals(draft.draftStatus())) throw conflict("草稿状态不允许发布");
        UserModuleFacade facade = facadeProvider.getIfAvailable();
        if (facade == null) throw new BusinessException(ResultCode.INTERNAL_SERVER_ERROR, "行程发布服务暂不可用");
        PublishOutcome outcome = facade.publishDraft(userId, draft, publishType, "trip-draft:" + id);
        if (outcome == null || outcome.tripId() == null || outcome.publishedId() == null
                || mapper.markDraftPublished(id, userId, outcome.tripId(), LocalDateTime.now()) == 0) {
            throw conflict("草稿已被其他请求处理");
        }
        clearDraftCaches(userId);
        return new PublishResultVO(id, publishType, outcome.publishedId());
    }

    @Override
    public PageResult<TeamMatchVO> recommendTeams(Long id, int page, int size) {
        long userId = currentUserContext.requireUserId();
        TripDraftVO draft = draft(mapper.findDraft(id, userId));
        if (draft == null) throw notFound("行程草稿不存在");
        int p = page(page), s = size(size);
        UserModuleFacade facade = facadeProvider.getIfAvailable();
        List<TeamMatchVO> rows = facade == null ? List.of() : facade.recommendTeams(userId, draft, p, s);
        rows = rows == null ? List.of() : rows;
        return new PageResult<>(rows, rows.size(), p, s);
    }

    private UserProfileVO profile(long userId) {
        ensureProfile(userId);
        return profile(mapper.findProfile(userId));
    }

    private UserProfileVO profile(Map<String, Object> row) {
        long userId = lng(row, "userId");
        return new UserProfileVO(userId, str(row, "nickname"), str(row, "avatarImageKey"), integer(row, "gender"),
                date(row, "birthday"), str(row, "cityCode"), str(row, "cityName"), str(row, "bio"),
                str(row, "profileStatus"), str(row, "certificationStatus"));
    }

    private void ensureProfile(long userId) {
        if (mapper.findProfile(userId) != null) return;
        try {
            mapper.insertProfile(SnowflakeIdGenerator.nextId(), userId, LocalDateTime.now());
        } catch (DuplicateKeyException ignored) {
        }
        ensurePrivacy(userId);
    }

    private Map<String, Object> ensurePrivacy(long userId) {
        Map<String, Object> row = mapper.findPrivacy(userId);
        if (row != null) return row;
        try {
            mapper.insertPrivacy(SnowflakeIdGenerator.nextId(), userId, LocalDateTime.now());
        } catch (DuplicateKeyException ignored) {
        }
        return mapper.findPrivacy(userId);
    }

    private GrowthSummaryVO growth(long userId) {
        GrowthSummaryVO cached = cacheGet(GROWTH_CACHE.formatted(userId), GrowthSummaryVO.class);
        if (cached != null) return cached;
        Map<String, Object> row = mapper.findGrowth(userId);
        if (row == null) {
            try { mapper.insertGrowth(SnowflakeIdGenerator.nextId(), userId, LocalDateTime.now()); } catch (DuplicateKeyException ignored) { }
            row = mapper.findGrowth(userId);
        }
        int points = integer(row, "totalPoints");
        Integer next = mapper.findNextLevelPoints(points);
        GrowthSummaryVO result = new GrowthSummaryVO(points, str(row, "levelCode"), next == null ? 0 : Math.max(0, next - points));
        cachePut(GROWTH_CACHE.formatted(userId), result, Duration.ofMinutes(30));
        return result;
    }

    private BadgeWallVO badges(long userId) {
        BadgeWallVO cached = cacheGet(BADGES_CACHE.formatted(userId), BadgeWallVO.class);
        if (cached != null) return cached;
        List<BadgeVO> earned = mapper.findEarnedBadges(userId).stream().map(this::badge).toList();
        List<BadgeVO> locked = mapper.findLockedBadges(userId).stream().map(this::badge).toList();
        BadgeWallVO result = new BadgeWallVO(earned, locked);
        cachePut(BADGES_CACHE.formatted(userId), result, Duration.ofMinutes(30));
        return result;
    }

    private InviteRewardProgressVO invitationRewards(long userId) {
        InviteRewardProgressVO cached = cacheGet(INVITE_SUMMARY_CACHE.formatted(userId), InviteRewardProgressVO.class);
        if (cached != null) return cached;
        int count = mapper.countValidInvitations(userId);
        InviteRewardProgressVO result = new InviteRewardProgressVO(count, nextRewardNeed(count), mapper.findGrantedRewardRules(userId));
        cachePut(INVITE_SUMMARY_CACHE.formatted(userId), result, Duration.ofMinutes(15));
        return result;
    }

    private CouponCountVO couponCount(long userId) {
        CouponCountVO cached = cacheGet(COUPON_COUNT_CACHE.formatted(userId), CouponCountVO.class);
        if (cached != null) return cached;
        CouponCountVO result = new CouponCountVO(mapper.countAvailableCoupons(userId), mapper.countExpiringCoupons(userId));
        cachePut(COUPON_COUNT_CACHE.formatted(userId), result, Duration.ofMinutes(10));
        return result;
    }

    private TripDraftVO latestDraft(long userId) {
        TripDraftVO cached = cacheGet(DRAFT_CACHE.formatted(userId), TripDraftVO.class);
        if (cached != null) return cached;
        TripDraftVO result = draft(mapper.findLatestDraft(userId));
        if (result != null) cachePut(DRAFT_CACHE.formatted(userId), result, Duration.ofMinutes(15));
        return result;
    }

    private void requireCertified(long userId) {
        Map<String, Object> row = mapper.findLatestCertification(userId);
        if (row == null || !"APPROVED".equals(str(row, "certificationStatus"))) {
            throw new BusinessException(ResultCode.FORBIDDEN, "USER_CERTIFICATION_REQUIRED");
        }
    }

    private TripDraftVO draft(Map<String, Object> row) {
        if (row == null) return null;
        return new TripDraftVO(lng(row, "draftId"), location(str(row, "startJson")), location(str(row, "endJson")),
                locations(str(row, "waypointJson")), time(row, "departureTime"), integer(row, "durationDays"),
                integer(row, "peopleCount"), str(row, "remark"), str(row, "draftStatus"), nullableLong(row, "publishedTripId"), time(row, "updatedAt"));
    }

    private LocationVO location(String json) {
        if (!StringUtils.hasText(json)) return null;
        try {
            LocationRequest v = objectMapper.readValue(json, LocationRequest.class);
            return new LocationVO(v.name(), v.address(), v.latitude(), v.longitude());
        } catch (JsonProcessingException e) {
            throw new BusinessException(ResultCode.INTERNAL_SERVER_ERROR, "草稿位置数据损坏");
        }
    }

    private List<LocationVO> locations(String json) {
        if (!StringUtils.hasText(json)) return List.of();
        try {
            List<LocationRequest> values = objectMapper.readValue(json, new TypeReference<>() { });
            return values.stream().map(v -> new LocationVO(v.name(), v.address(), v.latitude(), v.longitude())).toList();
        } catch (JsonProcessingException e) {
            throw new BusinessException(ResultCode.INTERNAL_SERVER_ERROR, "草稿途经点数据损坏");
        }
    }

    private String json(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JsonProcessingException e) { throw badRequest("请求数据无法序列化"); }
    }

    private boolean scopeAllows(String scopeJson, String orderType, Long merchantId) {
        if (!StringUtils.hasText(scopeJson)) return true;
        try {
            Map<String, Object> scope = objectMapper.readValue(scopeJson, new TypeReference<>() { });
            Object orderTypes = scope.get("orderTypes");
            Object merchantIds = scope.get("merchantIds");
            boolean orderOk = !(orderTypes instanceof List<?> list) || list.isEmpty() || list.contains(orderType);
            boolean merchantOk = !(merchantIds instanceof List<?> list) || list.isEmpty() || merchantId != null && list.stream().anyMatch(v -> merchantId.toString().equals(v.toString()));
            return orderOk && merchantOk;
        } catch (JsonProcessingException e) {
            return false;
        }
    }

    private String encrypt(String value) {
        try {
            byte[] key = MessageDigest.getInstance("SHA-256").digest(encryptionKey.getBytes(StandardCharsets.UTF_8));
            byte[] iv = new byte[12]; RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            byte[] result = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, result, 0, iv.length); System.arraycopy(encrypted, 0, result, iv.length, encrypted.length);
            return Base64.getEncoder().encodeToString(result);
        } catch (Exception e) {
            throw new BusinessException(ResultCode.INTERNAL_SERVER_ERROR, "敏感数据加密失败");
        }
    }

    private void rateLimit(String key, int limit, Duration ttl) {
        try {
            Long count = redis.opsForValue().increment(key);
            if (count != null && count == 1) redis.expire(key, ttl);
            if (count != null && count > limit) throw new BusinessException(429, "USER_OPERATION_TOO_FREQUENT");
        } catch (BusinessException e) { throw e; }
        catch (RuntimeException ignored) { }
    }

    private boolean tryLock(String key, String value, Duration ttl) {
        try { return !Boolean.FALSE.equals(redis.opsForValue().setIfAbsent(key, value, ttl)); }
        catch (RuntimeException ignored) { return true; }
    }

    private void unlock(String key, String value) {
        try { redis.execute(COMPARE_DELETE, List.of(key), value); }
        catch (RuntimeException ignored) { }
    }

    private <T> T cacheGet(String key, Class<T> type) {
        try {
            String json = redis.opsForValue().get(key);
            return StringUtils.hasText(json) ? objectMapper.readValue(json, type) : null;
        } catch (Exception ignored) { return null; }
    }

    private void cachePut(String key, Object value, Duration ttl) {
        try { redis.opsForValue().set(key, objectMapper.writeValueAsString(value), jitter(ttl)); }
        catch (Exception ignored) { }
    }

    private Duration jitter(Duration ttl) {
        return ttl.plusSeconds((long) (ttl.toSeconds() * RANDOM.nextDouble(0.0, 0.1)));
    }

    private void safeDelete(String... keys) {
        try { redis.delete(List.of(keys)); } catch (RuntimeException ignored) { }
    }

    private void deleteMatching(String pattern) {
        try { Set<String> keys = redis.keys(pattern); if (keys != null && !keys.isEmpty()) redis.delete(keys); }
        catch (RuntimeException ignored) { }
    }

    private void clearUserCaches(long userId) {
        safeDelete(PROFILE_CACHE.formatted(userId), PUBLIC_CACHE.formatted(userId), DASHBOARD_CACHE.formatted(userId));
    }

    private void clearCouponCaches(long userId) {
        safeDelete(COUPON_COUNT_CACHE.formatted(userId), DASHBOARD_CACHE.formatted(userId));
        deleteMatching("user:cache:coupon-page:" + userId + ":*");
    }

    private void clearDraftCaches(long userId) {
        safeDelete(DRAFT_CACHE.formatted(userId), DASHBOARD_CACHE.formatted(userId));
    }

    private CertificationVO certification(Map<String, Object> r) { return new CertificationVO(lng(r,"id"),lng(r,"userId"),str(r,"certificationStatus"),str(r,"rejectReason"),time(r,"submittedAt"),time(r,"reviewedAt")); }
    private GrowthLogVO growthLog(Map<String, Object> r) { return new GrowthLogVO(lng(r,"id"),str(r,"bizType"),str(r,"bizId"),integer(r,"pointDelta"),integer(r,"balanceAfter"),str(r,"remark"),time(r,"createdAt")); }
    private BadgeVO badge(Map<String, Object> r) { return new BadgeVO(lng(r,"badgeId"),str(r,"badgeCode"),str(r,"badgeName"),str(r,"badgeImageKey"),time(r,"awardedAt")); }
    private InvitationVO invitation(Map<String, Object> r) { return new InvitationVO(lng(r,"relationId"),lng(r,"inviteeUserId"),str(r,"inviteCode"),str(r,"status"),time(r,"boundAt"),time(r,"firstTeamCompletedAt")); }
    private CouponSummaryVO couponSummary(Map<String, Object> r) { return new CouponSummaryVO(lng(r,"id"),lng(r,"templateId"),str(r,"couponName"),str(r,"couponType"),decimal(r,"thresholdAmount"),decimal(r,"discountAmount"),str(r,"couponStatus"),time(r,"validStartAt"),time(r,"validEndAt")); }
    private UserCouponDetailVO couponDetail(Map<String, Object> r) { return new UserCouponDetailVO(lng(r,"id"),lng(r,"templateId"),str(r,"couponName"),str(r,"couponType"),nullableLong(r,"issuerId"),decimal(r,"thresholdAmount"),decimal(r,"discountAmount"),str(r,"scopeJson"),str(r,"couponStatus"),time(r,"validStartAt"),time(r,"validEndAt"),nullableLong(r,"lockedOrderId"),nullableLong(r,"usedOrderId")); }
    private AvailableCouponVO availableCoupon(Map<String, Object> r) { return new AvailableCouponVO(lng(r,"id"),str(r,"couponName"),decimal(r,"deductionAmount"),time(r,"validEndAt")); }

    private int nextRewardNeed(int count) { int[] levels={1,3,5,10}; for(int level:levels) if(count<level) return level-count; return 0; }
    private int page(int value) { return Math.max(1,value); }
    private int size(int value) { return Math.min(100,Math.max(1,value)); }
    private String value(String candidate,String old) { return candidate==null?old:candidate.trim(); }
    private String text(String value) { return value==null?"":value.trim(); }
    private <T> List<T> list(List<T> value) { return value==null?Collections.emptyList():value; }
    private String str(Map<String,Object> m,String k) { Object v=m==null?null:m.get(k); return v==null?"":v.toString(); }
    private long lng(Map<String,Object> m,String k) { Object v=m.get(k); return v instanceof Number n?n.longValue():Long.parseLong(v.toString()); }
    private Long nullableLong(Map<String,Object> m,String k) { Object v=m==null?null:m.get(k); return v==null?null:(v instanceof Number n?n.longValue():Long.valueOf(v.toString())); }
    private int integer(Map<String,Object> m,String k) { Object v=m.get(k); return v instanceof Number n?n.intValue():Integer.parseInt(v.toString()); }
    private boolean bool(Map<String,Object> m,String k) { Object v=m.get(k); return v instanceof Boolean b?b:v instanceof Number n?n.intValue()!=0:Boolean.parseBoolean(String.valueOf(v)); }
    private BigDecimal decimal(Map<String,Object> m,String k) { Object v=m.get(k); return v instanceof BigDecimal b?b:new BigDecimal(v.toString()); }
    private LocalDate date(Map<String,Object> m,String k) { Object v=m.get(k); return v==null?null:v instanceof LocalDate d?d:LocalDate.parse(v.toString()); }
    private LocalDateTime time(Map<String,Object> m,String k) { Object v=m==null?null:m.get(k); return v==null?null:v instanceof LocalDateTime d?d:LocalDateTime.parse(v.toString().replace(' ','T')); }
    private BusinessException conflict(String message) { return new BusinessException(409,message); }
    private BusinessException badRequest(String message) { return new BusinessException(ResultCode.BAD_REQUEST,message); }
    private BusinessException notFound(String message) { return new BusinessException(ResultCode.NOT_FOUND,message); }
}
