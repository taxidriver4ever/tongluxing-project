package com.tongdao.user.service.impl;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tongdao.common.exception.BusinessException;
import com.tongdao.common.result.ResultCode;
import com.tongdao.common.utils.SnowflakeIdGenerator;
import com.tongdao.user.dto.EmergencyContactRequest;
import com.tongdao.user.dto.SubmitIdentityRequest;
import com.tongdao.user.dto.UpdateUserPrivacyRequest;
import com.tongdao.user.dto.UpdateUserProfileRequest;
import com.tongdao.user.entity.UserEmergencyContact;
import com.tongdao.user.entity.UserIdentityCertification;
import com.tongdao.user.entity.UserPrivacySetting;
import com.tongdao.user.entity.UserProfile;
import com.tongdao.user.mapper.UserEmergencyContactMapper;
import com.tongdao.user.mapper.UserIdentityCertificationMapper;
import com.tongdao.user.mapper.UserPrivacySettingMapper;
import com.tongdao.user.mapper.UserProfileAuditLogMapper;
import com.tongdao.user.mapper.UserProfileMapper;
import com.tongdao.user.service.UserService;
import com.tongdao.user.support.CurrentUserContext;
import com.tongdao.user.vo.EmergencyContactListResponse;
import com.tongdao.user.vo.EmergencyContactResponse;
import com.tongdao.user.vo.IdentityStatusResponse;
import com.tongdao.user.vo.PublicUserProfileResponse;
import com.tongdao.user.vo.UserPrivacyResponse;
import com.tongdao.user.vo.UserProfileResponse;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private static final int PROFILE_UPDATE_LIMIT = 10;
    private static final int IDENTITY_SUBMIT_LIMIT = 3;
    private static final String REAL_NAME_UNSUBMITTED = "UNSUBMITTED";
    private static final String REAL_NAME_PENDING = "PENDING";

    private static final String PROFILE_CACHE_KEY = "user:cache:profile:%d";
    private static final String PUBLIC_CARD_CACHE_KEY = "user:cache:public-card:%d";
    private static final String PRIVACY_HASH_KEY = "user:hash:privacy:%d";
    private static final String PROFILE_UPDATE_RL_KEY = "user:rl:profile-update:%d";
    private static final String IDENTITY_SUBMIT_RL_KEY = "user:rl:identity-submit:%d";

    private final CurrentUserContext currentUserContext;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final UserProfileMapper profileMapper;
    private final UserPrivacySettingMapper privacySettingMapper;
    private final UserIdentityCertificationMapper identityCertificationMapper;
    private final UserEmergencyContactMapper emergencyContactMapper;
    private final UserProfileAuditLogMapper auditLogMapper;

    @Override
    public UserProfileResponse getCurrentProfile() {
        Long userId = currentUserContext.requireUserId();
        String cacheKey = PROFILE_CACHE_KEY.formatted(userId);
        UserProfileResponse cached = readJson(cacheKey, UserProfileResponse.class);
        if (cached != null) {
            return cached;
        }

        UserProfileResponse response = toProfileResponse(ensureProfile(userId));
        writeJson(cacheKey, response, Duration.ofMinutes(30));
        return response;
    }

    @Override
    @Transactional
    public UserProfileResponse updateCurrentProfile(UpdateUserProfileRequest request) {
        Long userId = currentUserContext.requireUserId();
        checkRateLimit(PROFILE_UPDATE_RL_KEY.formatted(userId), PROFILE_UPDATE_LIMIT, Duration.ofSeconds(60), "资料修改太频繁，请稍后再试");

        UserProfile before = ensureProfile(userId);
        UserProfile profile = copyProfile(before);
        profile.setNickname(normalize(request.nickname()));
        profile.setAvatarUrl(normalize(request.avatarUrl()));
        profile.setGender(request.gender() == null ? 0 : request.gender());
        profile.setBirthday(request.birthday());
        profile.setCityCode(normalize(request.cityCode()));
        profile.setCityName(normalize(request.cityName()));
        profile.setBio(normalize(request.bio()));
        profile.setProfileCompletion(calculateCompletion(profile));
        profile.setUpdatedAt(LocalDateTime.now());
        profileMapper.updateProfile(profile);
        clearProfileCaches(userId);
        insertAuditLog(userId, "PROFILE", before, profile);
        UserProfileResponse response = toProfileResponse(profileMapper.findByUserId(userId));
        writeJson(PROFILE_CACHE_KEY.formatted(userId), response, Duration.ofMinutes(30));
        return response;
    }

    @Override
    public UserPrivacyResponse getCurrentPrivacy() {
        Long userId = currentUserContext.requireUserId();
        UserPrivacySetting setting = ensurePrivacy(userId);
        cachePrivacy(setting);
        return toPrivacyResponse(setting);
    }

    @Override
    @Transactional
    public UserPrivacyResponse updateCurrentPrivacy(UpdateUserPrivacyRequest request) {
        Long userId = currentUserContext.requireUserId();
        UserPrivacySetting before = ensurePrivacy(userId);
        UserPrivacySetting setting = copyPrivacy(before);
        setting.setProfileVisible(toTiny(request.profileVisible(), before.getProfileVisible()));
        setting.setPhoneVisible(toTiny(request.phoneVisible(), before.getPhoneVisible()));
        setting.setTripVisible(toTiny(request.tripVisible(), before.getTripVisible()));
        setting.setLocationVisible(toTiny(request.locationVisible(), before.getLocationVisible()));
        setting.setAllowTeamInvite(toTiny(request.allowTeamInvite(), before.getAllowTeamInvite()));
        setting.setAllowPrivateMessage(toTiny(request.allowPrivateMessage(), before.getAllowPrivateMessage()));
        setting.setUpdatedAt(LocalDateTime.now());
        privacySettingMapper.update(setting);
        redisTemplate.delete(PRIVACY_HASH_KEY.formatted(userId));
        redisTemplate.delete(PUBLIC_CARD_CACHE_KEY.formatted(userId));
        insertAuditLog(userId, "PRIVACY", before, setting);
        cachePrivacy(setting);
        return toPrivacyResponse(setting);
    }

    @Override
    @Transactional
    public IdentityStatusResponse submitIdentity(SubmitIdentityRequest request) {
        Long userId = currentUserContext.requireUserId();
        checkRateLimit(IDENTITY_SUBMIT_RL_KEY.formatted(userId), IDENTITY_SUBMIT_LIMIT, Duration.ofHours(24), "实名认证提交太频繁，请明天再试");

        ensureProfile(userId);
        LocalDateTime now = LocalDateTime.now();
        UserIdentityCertification certification = new UserIdentityCertification();
        certification.setId(SnowflakeIdGenerator.nextId());
        certification.setUserId(userId);
        certification.setRealName(request.realName());
        certification.setIdCardNoCipher(cipher(request.idCardNo()));
        certification.setIdCardNoMask(maskIdCard(request.idCardNo()));
        certification.setFaceImageUrl(normalize(request.faceImageUrl()));
        certification.setStatus(REAL_NAME_PENDING);
        certification.setRejectReason("");
        certification.setSubmittedAt(now);
        certification.setCreatedAt(now);
        certification.setUpdatedAt(now);
        identityCertificationMapper.insert(certification);
        profileMapper.updateRealNameStatus(userId, REAL_NAME_PENDING, now);
        clearProfileCaches(userId);
        insertAuditLog(userId, "IDENTITY", null, certification);
        return toIdentityResponse(certification);
    }

    @Override
    public IdentityStatusResponse getIdentityStatus() {
        Long userId = currentUserContext.requireUserId();
        UserIdentityCertification certification = identityCertificationMapper.findLatestByUserId(userId);
        if (certification == null) {
            return new IdentityStatusResponse(userId, "", "", "", REAL_NAME_UNSUBMITTED, "");
        }
        return toIdentityResponse(certification);
    }

    @Override
    public PublicUserProfileResponse getPublicProfile(Long userId) {
        String cacheKey = PUBLIC_CARD_CACHE_KEY.formatted(userId);
        PublicUserProfileResponse cached = readJson(cacheKey, PublicUserProfileResponse.class);
        if (cached != null) {
            return cached;
        }

        UserPrivacySetting privacy = ensurePrivacy(userId);
        if (!isTrue(privacy.getProfileVisible())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "用户资料不可见");
        }
        UserProfile profile = ensureProfile(userId);
        PublicUserProfileResponse response = new PublicUserProfileResponse(
                profile.getUserId(),
                profile.getNickname(),
                profile.getAvatarUrl(),
                profile.getCityName(),
                profile.getRealNameStatus(),
                false
        );
        writeJson(cacheKey, response, Duration.ofMinutes(15));
        return response;
    }

    @Override
    public EmergencyContactListResponse getEmergencyContacts() {
        Long userId = currentUserContext.requireUserId();
        List<EmergencyContactResponse> contacts = emergencyContactMapper.findByUserId(userId)
                .stream()
                .map(this::toEmergencyContactResponse)
                .toList();
        return new EmergencyContactListResponse(contacts);
    }

    @Override
    @Transactional
    public EmergencyContactResponse addEmergencyContact(EmergencyContactRequest request) {
        Long userId = currentUserContext.requireUserId();
        LocalDateTime now = LocalDateTime.now();
        boolean isDefault = Boolean.TRUE.equals(request.isDefault());
        if (isDefault) {
            emergencyContactMapper.clearDefault(userId, now);
        }
        UserEmergencyContact contact = new UserEmergencyContact();
        contact.setId(SnowflakeIdGenerator.nextId());
        contact.setUserId(userId);
        fillContact(contact, request);
        contact.setIsDefault(isDefault ? 1 : 0);
        contact.setCreatedAt(now);
        contact.setUpdatedAt(now);
        emergencyContactMapper.insert(contact);
        return toEmergencyContactResponse(contact);
    }

    @Override
    @Transactional
    public EmergencyContactResponse updateEmergencyContact(Long contactId, EmergencyContactRequest request) {
        Long userId = currentUserContext.requireUserId();
        UserEmergencyContact contact = emergencyContactMapper.findByIdAndUserId(contactId, userId);
        if (contact == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "紧急联系人不存在");
        }
        LocalDateTime now = LocalDateTime.now();
        boolean isDefault = Boolean.TRUE.equals(request.isDefault());
        if (isDefault) {
            emergencyContactMapper.clearDefault(userId, now);
        }
        fillContact(contact, request);
        contact.setIsDefault(isDefault ? 1 : 0);
        contact.setUpdatedAt(now);
        emergencyContactMapper.update(contact);
        return toEmergencyContactResponse(contact);
    }

    @Override
    @Transactional
    public void deleteEmergencyContact(Long contactId) {
        Long userId = currentUserContext.requireUserId();
        int rows = emergencyContactMapper.logicDelete(contactId, userId, LocalDateTime.now());
        if (rows == 0) {
            throw new BusinessException(ResultCode.NOT_FOUND, "紧急联系人不存在");
        }
    }

    private UserProfile ensureProfile(Long userId) {
        UserProfile profile = profileMapper.findByUserId(userId);
        if (profile != null) {
            return profile;
        }
        LocalDateTime now = LocalDateTime.now();
        profile = new UserProfile();
        profile.setId(SnowflakeIdGenerator.nextId());
        profile.setUserId(userId);
        profile.setNickname("");
        profile.setAvatarUrl("");
        profile.setGender(0);
        profile.setCityCode("");
        profile.setCityName("");
        profile.setBio("");
        profile.setProfileCompletion(0);
        profile.setRealNameStatus(REAL_NAME_UNSUBMITTED);
        profile.setCreatedAt(now);
        profile.setUpdatedAt(now);
        profileMapper.insert(profile);
        ensurePrivacy(userId);
        return profileMapper.findByUserId(userId);
    }

    private UserPrivacySetting ensurePrivacy(Long userId) {
        UserPrivacySetting setting = privacySettingMapper.findByUserId(userId);
        if (setting != null) {
            return setting;
        }
        LocalDateTime now = LocalDateTime.now();
        setting = new UserPrivacySetting();
        setting.setId(SnowflakeIdGenerator.nextId());
        setting.setUserId(userId);
        setting.setProfileVisible(1);
        setting.setPhoneVisible(0);
        setting.setTripVisible(1);
        setting.setLocationVisible(1);
        setting.setAllowTeamInvite(1);
        setting.setAllowPrivateMessage(1);
        setting.setCreatedAt(now);
        setting.setUpdatedAt(now);
        privacySettingMapper.insert(setting);
        return privacySettingMapper.findByUserId(userId);
    }

    private void fillContact(UserEmergencyContact contact, EmergencyContactRequest request) {
        contact.setContactName(request.contactName());
        contact.setRelation(normalize(request.relation()));
        contact.setPhoneCipher(cipher(request.phone()));
        contact.setPhoneMask(maskPhone(request.phone()));
    }

    private void cachePrivacy(UserPrivacySetting setting) {
        String key = PRIVACY_HASH_KEY.formatted(setting.getUserId());
        redisTemplate.opsForHash().put(key, "profileVisible", String.valueOf(setting.getProfileVisible()));
        redisTemplate.opsForHash().put(key, "phoneVisible", String.valueOf(setting.getPhoneVisible()));
        redisTemplate.opsForHash().put(key, "tripVisible", String.valueOf(setting.getTripVisible()));
        redisTemplate.opsForHash().put(key, "locationVisible", String.valueOf(setting.getLocationVisible()));
        redisTemplate.opsForHash().put(key, "allowTeamInvite", String.valueOf(setting.getAllowTeamInvite()));
        redisTemplate.opsForHash().put(key, "allowPrivateMessage", String.valueOf(setting.getAllowPrivateMessage()));
        redisTemplate.expire(key, Duration.ofMinutes(60));
    }

    private void clearProfileCaches(Long userId) {
        redisTemplate.delete(PROFILE_CACHE_KEY.formatted(userId));
        redisTemplate.delete(PUBLIC_CARD_CACHE_KEY.formatted(userId));
    }

    private void checkRateLimit(String key, int limit, Duration ttl, String message) {
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1) {
            redisTemplate.expire(key, ttl);
        }
        if (count != null && count > limit) {
            throw new BusinessException(message);
        }
    }

    private int calculateCompletion(UserProfile profile) {
        int score = 0;
        score += StringUtils.hasText(profile.getNickname()) ? 20 : 0;
        score += StringUtils.hasText(profile.getAvatarUrl()) ? 20 : 0;
        score += StringUtils.hasText(profile.getCityName()) ? 15 : 0;
        score += StringUtils.hasText(profile.getBio()) ? 15 : 0;
        score += profile.getBirthday() != null ? 15 : 0;
        score += profile.getGender() != null && profile.getGender() > 0 ? 15 : 0;
        return Math.min(score, 100);
    }

    private UserProfileResponse toProfileResponse(UserProfile profile) {
        return new UserProfileResponse(
                profile.getUserId(),
                profile.getNickname(),
                profile.getAvatarUrl(),
                profile.getGender(),
                profile.getBirthday(),
                profile.getCityCode(),
                profile.getCityName(),
                profile.getBio(),
                profile.getProfileCompletion(),
                profile.getRealNameStatus()
        );
    }

    private UserPrivacyResponse toPrivacyResponse(UserPrivacySetting setting) {
        return new UserPrivacyResponse(
                setting.getUserId(),
                isTrue(setting.getProfileVisible()),
                isTrue(setting.getPhoneVisible()),
                isTrue(setting.getTripVisible()),
                isTrue(setting.getLocationVisible()),
                isTrue(setting.getAllowTeamInvite()),
                isTrue(setting.getAllowPrivateMessage())
        );
    }

    private IdentityStatusResponse toIdentityResponse(UserIdentityCertification certification) {
        return new IdentityStatusResponse(
                certification.getUserId(),
                certification.getRealName(),
                certification.getIdCardNoMask(),
                certification.getFaceImageUrl(),
                certification.getStatus(),
                certification.getRejectReason()
        );
    }

    private EmergencyContactResponse toEmergencyContactResponse(UserEmergencyContact contact) {
        return new EmergencyContactResponse(
                contact.getId(),
                contact.getContactName(),
                contact.getRelation(),
                contact.getPhoneMask(),
                isTrue(contact.getIsDefault())
        );
    }

    private UserProfile copyProfile(UserProfile source) {
        UserProfile profile = new UserProfile();
        profile.setId(source.getId());
        profile.setUserId(source.getUserId());
        profile.setNickname(source.getNickname());
        profile.setAvatarUrl(source.getAvatarUrl());
        profile.setGender(source.getGender());
        profile.setBirthday(source.getBirthday());
        profile.setCityCode(source.getCityCode());
        profile.setCityName(source.getCityName());
        profile.setBio(source.getBio());
        profile.setProfileCompletion(source.getProfileCompletion());
        profile.setRealNameStatus(source.getRealNameStatus());
        profile.setCreatedAt(source.getCreatedAt());
        profile.setUpdatedAt(source.getUpdatedAt());
        profile.setDeleted(source.getDeleted());
        return profile;
    }

    private UserPrivacySetting copyPrivacy(UserPrivacySetting source) {
        UserPrivacySetting setting = new UserPrivacySetting();
        setting.setId(source.getId());
        setting.setUserId(source.getUserId());
        setting.setProfileVisible(source.getProfileVisible());
        setting.setPhoneVisible(source.getPhoneVisible());
        setting.setTripVisible(source.getTripVisible());
        setting.setLocationVisible(source.getLocationVisible());
        setting.setAllowTeamInvite(source.getAllowTeamInvite());
        setting.setAllowPrivateMessage(source.getAllowPrivateMessage());
        setting.setCreatedAt(source.getCreatedAt());
        setting.setUpdatedAt(source.getUpdatedAt());
        return setting;
    }

    private void insertAuditLog(Long userId, String bizType, Object before, Object after) {
        auditLogMapper.insert(
                SnowflakeIdGenerator.nextId(),
                userId,
                bizType,
                toJson(before),
                toJson(after),
                userId,
                "USER",
                LocalDateTime.now()
        );
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            return "{}";
        }
    }

    private <T> T readJson(String key, Class<T> clazz) {
        String json = redisTemplate.opsForValue().get(key);
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, clazz);
        } catch (JsonProcessingException exception) {
            redisTemplate.delete(key);
            return null;
        }
    }

    private void writeJson(String key, Object value, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(value), ttl);
        } catch (JsonProcessingException ignored) {
            redisTemplate.delete(key);
        }
    }

    private String cipher(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private String maskPhone(String phone) {
        if (!StringUtils.hasText(phone) || phone.length() < 11) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(7);
    }

    private String maskIdCard(String idCardNo) {
        if (!StringUtils.hasText(idCardNo) || idCardNo.length() < 8) {
            return idCardNo;
        }
        return idCardNo.substring(0, 4) + "**********" + idCardNo.substring(idCardNo.length() - 4);
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }

    private Integer toTiny(Boolean value, Integer defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        return value ? 1 : 0;
    }

    private boolean isTrue(Integer value) {
        return value != null && value == 1;
    }
}
