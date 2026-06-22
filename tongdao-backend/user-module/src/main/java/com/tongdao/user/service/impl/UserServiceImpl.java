package com.tongdao.user.service.impl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tongdao.common.exception.BusinessException;
import com.tongdao.common.result.ResultCode;
import com.tongdao.common.utils.SnowflakeIdGenerator;
import com.tongdao.user.mapper.UserDomainMapper;
import com.tongdao.user.dto.UserQueryDTO;
import com.tongdao.user.model.UserModels.CertificationRequest;
import com.tongdao.user.model.UserModels.CertificationVO;
import com.tongdao.user.model.UserModels.PublicProfileVO;
import com.tongdao.user.model.UserModels.UpdateUserProfileRequest;
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

    private final CurrentUserContext currentUserContext;
    private final UserDomainMapper mapper;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    @Value("${tongdao.user.data-encryption-key:change-this-user-data-key}")
    private String encryptionKey;

    @Override
    public UserProfileVO getCurrentProfile() {
        long userId = currentUserContext.requireUserId();
        UserProfileVO cached = cacheGet(PROFILE_CACHE.formatted(userId), UserProfileVO.class);
        if (cached != null) return cached;
        UserProfileVO result = profile(userId);
        cachePut(PROFILE_CACHE.formatted(userId), result, Duration.ofMinutes(30));
        return result;
    }

    @Override
    @Transactional
    public UserProfileVO updateCurrentProfile(UpdateUserProfileRequest request) {
        long userId = currentUserContext.requireUserId();
        UserProfileVO old = profile(userId);
        mapper.updateProfile(userId, value(request.nickname(), old.nickname()), value(request.avatarImageKey(), old.avatarImageKey()),
                request.gender() == null ? old.gender() : request.gender(), request.birthday() == null ? old.birthday() : request.birthday(),
                value(request.cityCode(), old.cityCode()), value(request.cityName(), old.cityName()),
                value(request.bio(), old.bio()), LocalDateTime.now());
        redis.delete(java.util.List.of(PROFILE_CACHE.formatted(userId), PUBLIC_CACHE.formatted(userId)));
        return profile(userId);
    }

    @Override
    @Transactional
    public CertificationVO submitCertification(CertificationRequest request) {
        long userId = currentUserContext.requireUserId();
        ensureProfile(userId);
        UserQueryDTO latest = mapper.findLatestCertification(userId);
        if (latest != null && java.util.List.of("PENDING", "APPROVED").contains(latest.getCertificationStatus())) {
            throw new BusinessException(409, "认证正在审核或已通过");
        }
        LocalDateTime now = LocalDateTime.now();
        mapper.insertCertification(SnowflakeIdGenerator.nextId(), userId, encrypt(request.realName()), encrypt(request.idCardNo()),
                request.drivingLicenseImageKey().trim(), request.faceImageKey().trim(), now);
        redis.delete(java.util.List.of(PROFILE_CACHE.formatted(userId), PUBLIC_CACHE.formatted(userId)));
        return certification(mapper.findLatestCertification(userId));
    }

    @Override
    public PublicProfileVO getPublicProfile(Long userId) {
        PublicProfileVO cached = cacheGet(PUBLIC_CACHE.formatted(userId), PublicProfileVO.class);
        if (cached != null) return cached;
        UserQueryDTO row = mapper.findProfile(userId);
        if (row == null) throw new BusinessException(ResultCode.NOT_FOUND, "用户主页不存在");
        UserQueryDTO privacy = ensurePrivacy(userId);
        if ("PRIVATE".equals(privacy.getProfileVisibility())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户主页不存在");
        }
        UserProfileVO profile = profile(row);
        PublicProfileVO result = new PublicProfileVO(profile.userId(), profile.nickname(), profile.avatarImageKey(),
                profile.cityName(), profile.bio(), profile.certificationStatus());
        cachePut(PUBLIC_CACHE.formatted(userId), result, Duration.ofMinutes(15));
        return result;
    }

    private UserProfileVO profile(long userId) {
        ensureProfile(userId);
        return profile(mapper.findProfile(userId));
    }

    private UserProfileVO profile(UserQueryDTO row) {
        return new UserProfileVO(row.getUserId(), row.getNickname(), row.getAvatarImageKey(), row.getGender(),
                row.getBirthday(), row.getCityCode(), row.getCityName(), row.getBio(),
                row.getProfileStatus(), row.getCertificationStatus());
    }

    private void ensureProfile(long userId) {
        if (mapper.findProfile(userId) == null) {
            try { mapper.insertProfile(SnowflakeIdGenerator.nextId(), userId, LocalDateTime.now()); }
            catch (DuplicateKeyException ignored) { }
        }
        ensurePrivacy(userId);
    }

    private UserQueryDTO ensurePrivacy(long userId) {
        UserQueryDTO row = mapper.findPrivacy(userId);
        if (row != null) return row;
        try { mapper.insertPrivacy(SnowflakeIdGenerator.nextId(), userId, LocalDateTime.now()); }
        catch (DuplicateKeyException ignored) { }
        return mapper.findPrivacy(userId);
    }

    private CertificationVO certification(UserQueryDTO row) {
        return new CertificationVO(row.getId(), row.getUserId(), row.getCertificationStatus(),
                row.getRejectReason(), row.getSubmittedAt(), row.getReviewedAt());
    }

    private String encrypt(String value) {
        try {
            byte[] key = MessageDigest.getInstance("SHA-256").digest(encryptionKey.getBytes(StandardCharsets.UTF_8));
            byte[] iv = new byte[12];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            byte[] result = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, result, 0, iv.length);
            System.arraycopy(encrypted, 0, result, iv.length, encrypted.length);
            return Base64.getEncoder().encodeToString(result);
        } catch (Exception e) {
            throw new BusinessException(ResultCode.INTERNAL_SERVER_ERROR, "敏感数据加密失败");
        }
    }

    private <T> T cacheGet(String key, Class<T> type) {
        try { String value = redis.opsForValue().get(key); return value == null ? null : objectMapper.readValue(value, type); }
        catch (Exception ignored) { return null; }
    }

    private void cachePut(String key, Object value, Duration ttl) {
        try { redis.opsForValue().set(key, objectMapper.writeValueAsString(value), ttl); }
        catch (Exception ignored) { }
    }

    private String value(String candidate, String old) { return candidate == null ? old : candidate.trim(); }
}
