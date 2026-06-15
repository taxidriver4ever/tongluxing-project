package com.tongdao.vehicle.service.impl;

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
import com.tongdao.user.support.CurrentUserContext;
import com.tongdao.vehicle.dto.CreateVehicleRequest;
import com.tongdao.vehicle.dto.SubmitVehicleCertificationRequest;
import com.tongdao.vehicle.dto.UpdateVehicleRequest;
import com.tongdao.vehicle.entity.VehicleCertification;
import com.tongdao.vehicle.entity.VehicleProfile;
import com.tongdao.vehicle.mapper.VehicleAuditLogMapper;
import com.tongdao.vehicle.mapper.VehicleCertificationMapper;
import com.tongdao.vehicle.mapper.VehicleProfileMapper;
import com.tongdao.vehicle.service.VehicleService;
import com.tongdao.vehicle.vo.PublicVehicleCardResponse;
import com.tongdao.vehicle.vo.VehicleCertificationResponse;
import com.tongdao.vehicle.vo.VehicleListResponse;
import com.tongdao.vehicle.vo.VehicleResponse;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class VehicleServiceImpl implements VehicleService {

    private static final int CREATE_LIMIT = 10;
    private static final int CERTIFICATION_SUBMIT_LIMIT = 3;
    private static final String CERTIFICATION_UNSUBMITTED = "UNSUBMITTED";
    private static final String CERTIFICATION_PENDING = "PENDING";

    private static final String LIST_CACHE_KEY = "vehicle:cache:list:%d";
    private static final String DETAIL_CACHE_KEY = "vehicle:cache:detail:%d";
    private static final String PUBLIC_CARD_CACHE_KEY = "vehicle:cache:public-card:%d";
    private static final String CREATE_RL_KEY = "vehicle:rl:create:%d";
    private static final String CERTIFICATION_RL_KEY = "vehicle:rl:cert-submit:%d";

    private final CurrentUserContext currentUserContext;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final VehicleProfileMapper vehicleProfileMapper;
    private final VehicleCertificationMapper certificationMapper;
    private final VehicleAuditLogMapper auditLogMapper;

    @Override
    public VehicleListResponse getMyVehicles() {
        Long userId = currentUserContext.requireUserId();
        String cacheKey = LIST_CACHE_KEY.formatted(userId);
        VehicleListResponse cached = readJson(cacheKey, VehicleListResponse.class);
        if (cached != null) {
            return cached;
        }

        List<VehicleResponse> vehicles = vehicleProfileMapper.findByUserId(userId)
                .stream()
                .map(this::toVehicleResponse)
                .toList();
        VehicleListResponse response = new VehicleListResponse(vehicles);
        writeJson(cacheKey, response, Duration.ofMinutes(20));
        return response;
    }

    @Override
    @Transactional
    public VehicleResponse createVehicle(CreateVehicleRequest request) {
        Long userId = currentUserContext.requireUserId();
        checkRateLimit(CREATE_RL_KEY.formatted(userId), CREATE_LIMIT, Duration.ofHours(1), "车辆创建太频繁，请稍后再试");

        LocalDateTime now = LocalDateTime.now();
        VehicleProfile vehicle = new VehicleProfile();
        vehicle.setId(SnowflakeIdGenerator.nextId());
        vehicle.setUserId(userId);
        vehicle.setPlateNoCipher(cipher(request.plateNo()));
        vehicle.setPlateNoMask(maskPlateNo(request.plateNo()));
        vehicle.setCertificationStatus(CERTIFICATION_UNSUBMITTED);
        vehicle.setDefaultFlag(vehicleProfileMapper.countByUserId(userId) == 0 ? 1 : 0);
        vehicle.setCreatedAt(now);
        vehicle.setUpdatedAt(now);
        vehicle.setDeleted(0);
        fillVehicle(vehicle, request);
        vehicleProfileMapper.insert(vehicle);
        clearVehicleCaches(userId, vehicle.getId());
        insertAuditLog(vehicle.getId(), userId, "CREATE", null, vehicle, "创建车辆");
        return toVehicleResponse(vehicle);
    }

    @Override
    public VehicleResponse getVehicle(Long vehicleId) {
        Long userId = currentUserContext.requireUserId();
        VehicleProfile owned = requireOwnedVehicle(vehicleId, userId);
        String cacheKey = DETAIL_CACHE_KEY.formatted(vehicleId);
        VehicleResponse cached = readJson(cacheKey, VehicleResponse.class);
        if (cached != null) {
            return cached;
        }

        VehicleResponse response = toVehicleResponse(owned);
        writeJson(cacheKey, response, Duration.ofMinutes(20));
        return response;
    }

    @Override
    @Transactional
    public VehicleResponse updateVehicle(Long vehicleId, UpdateVehicleRequest request) {
        Long userId = currentUserContext.requireUserId();
        VehicleProfile before = requireOwnedVehicle(vehicleId, userId);
        VehicleProfile vehicle = copyVehicle(before);
        fillVehicle(vehicle, request);
        vehicle.setUpdatedAt(LocalDateTime.now());
        vehicleProfileMapper.update(vehicle);
        clearVehicleCaches(userId, vehicleId);
        insertAuditLog(vehicleId, userId, "UPDATE", before, vehicle, "更新车辆");
        return toVehicleResponse(requireOwnedVehicle(vehicleId, userId));
    }

    @Override
    @Transactional
    public void deleteVehicle(Long vehicleId) {
        Long userId = currentUserContext.requireUserId();
        VehicleProfile before = requireOwnedVehicle(vehicleId, userId);
        int rows = vehicleProfileMapper.logicDelete(vehicleId, userId, LocalDateTime.now());
        if (rows == 0) {
            throw new BusinessException(ResultCode.NOT_FOUND, "车辆不存在");
        }
        clearVehicleCaches(userId, vehicleId);
        insertAuditLog(vehicleId, userId, "DELETE", before, null, "删除车辆");
    }

    @Override
    @Transactional
    public VehicleResponse setDefaultVehicle(Long vehicleId) {
        Long userId = currentUserContext.requireUserId();
        VehicleProfile before = requireOwnedVehicle(vehicleId, userId);
        LocalDateTime now = LocalDateTime.now();
        vehicleProfileMapper.clearDefault(userId, now);
        vehicleProfileMapper.setDefault(vehicleId, userId, now);
        clearVehicleCaches(userId, vehicleId);
        VehicleProfile after = requireOwnedVehicle(vehicleId, userId);
        insertAuditLog(vehicleId, userId, "SET_DEFAULT", before, after, "设置默认车辆");
        return toVehicleResponse(after);
    }

    @Override
    @Transactional
    public VehicleCertificationResponse submitCertification(Long vehicleId, SubmitVehicleCertificationRequest request) {
        Long userId = currentUserContext.requireUserId();
        checkRateLimit(CERTIFICATION_RL_KEY.formatted(userId), CERTIFICATION_SUBMIT_LIMIT, Duration.ofHours(24), "车辆认证提交太频繁，请明天再试");
        requireOwnedVehicle(vehicleId, userId);

        LocalDateTime now = LocalDateTime.now();
        VehicleCertification certification = new VehicleCertification();
        certification.setId(SnowflakeIdGenerator.nextId());
        certification.setVehicleId(vehicleId);
        certification.setUserId(userId);
        certification.setOwnerName(normalize(request.ownerName()));
        certification.setPlateNoCipher(cipher(request.plateNo()));
        certification.setPlateNoMask(maskPlateNo(request.plateNo()));
        certification.setVinCipher(cipher(request.vin()));
        certification.setVinMask(maskVin(request.vin()));
        certification.setEngineNoMask(maskEngineNo(request.engineNo()));
        certification.setLicenseImageKey(normalize(request.licenseImageKey()));
        certification.setStatus(CERTIFICATION_PENDING);
        certification.setRejectReason("");
        certification.setSubmittedAt(now);
        certificationMapper.insert(certification);
        vehicleProfileMapper.updateCertificationStatus(vehicleId, userId, CERTIFICATION_PENDING, now);
        clearVehicleCaches(userId, vehicleId);
        insertAuditLog(vehicleId, userId, "CERTIFICATION", null, certification, "提交车辆认证");
        return toCertificationResponse(certification);
    }

    @Override
    public VehicleCertificationResponse getCertification(Long vehicleId) {
        Long userId = currentUserContext.requireUserId();
        requireOwnedVehicle(vehicleId, userId);
        VehicleCertification certification = certificationMapper.findLatestByVehicleId(vehicleId);
        if (certification == null) {
            return new VehicleCertificationResponse(vehicleId, "", "", "", "", "", CERTIFICATION_UNSUBMITTED, "", null, null);
        }
        return toCertificationResponse(certification);
    }

    @Override
    public PublicVehicleCardResponse getPublicCard(Long vehicleId) {
        String cacheKey = PUBLIC_CARD_CACHE_KEY.formatted(vehicleId);
        PublicVehicleCardResponse cached = readJson(cacheKey, PublicVehicleCardResponse.class);
        if (cached != null) {
            return cached;
        }

        VehicleProfile vehicle = vehicleProfileMapper.findById(vehicleId);
        if (vehicle == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "车辆不存在");
        }
        PublicVehicleCardResponse response = toPublicCardResponse(vehicle);
        writeJson(cacheKey, response, Duration.ofMinutes(15));
        return response;
    }

    private VehicleProfile requireOwnedVehicle(Long vehicleId, Long userId) {
        VehicleProfile vehicle = vehicleProfileMapper.findByIdAndUserId(vehicleId, userId);
        if (vehicle == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "车辆不存在");
        }
        return vehicle;
    }

    private void fillVehicle(VehicleProfile vehicle, CreateVehicleRequest request) {
        vehicle.setBrand(normalize(request.brand()));
        vehicle.setModel(normalize(request.model()));
        vehicle.setVehicleType(normalize(request.vehicleType()));
        vehicle.setColor(normalize(request.color()));
        vehicle.setSeatCount(request.seatCount() == null ? 5 : request.seatCount());
        vehicle.setEnergyType(normalize(request.energyType()));
        vehicle.setVehiclePhotoImageKey(normalize(request.vehiclePhotoImageKey()));
    }

    private void fillVehicle(VehicleProfile vehicle, UpdateVehicleRequest request) {
        vehicle.setBrand(normalize(request.brand()));
        vehicle.setModel(normalize(request.model()));
        vehicle.setVehicleType(normalize(request.vehicleType()));
        vehicle.setColor(normalize(request.color()));
        vehicle.setSeatCount(request.seatCount() == null ? vehicle.getSeatCount() : request.seatCount());
        vehicle.setEnergyType(normalize(request.energyType()));
        vehicle.setVehiclePhotoImageKey(normalize(request.vehiclePhotoImageKey()));
    }

    private VehicleResponse toVehicleResponse(VehicleProfile vehicle) {
        return new VehicleResponse(
                vehicle.getId(),
                vehicle.getUserId(),
                vehicle.getPlateNoMask(),
                vehicle.getBrand(),
                vehicle.getModel(),
                vehicle.getVehicleType(),
                vehicle.getColor(),
                vehicle.getSeatCount(),
                vehicle.getEnergyType(),
                vehicle.getVehiclePhotoImageKey(),
                vehicle.getCertificationStatus(),
                isTrue(vehicle.getDefaultFlag())
        );
    }

    private VehicleCertificationResponse toCertificationResponse(VehicleCertification certification) {
        return new VehicleCertificationResponse(
                certification.getVehicleId(),
                certification.getOwnerName(),
                certification.getPlateNoMask(),
                certification.getVinMask(),
                certification.getEngineNoMask(),
                certification.getLicenseImageKey(),
                certification.getStatus(),
                certification.getRejectReason(),
                certification.getSubmittedAt(),
                certification.getReviewedAt()
        );
    }

    private PublicVehicleCardResponse toPublicCardResponse(VehicleProfile vehicle) {
        return new PublicVehicleCardResponse(
                vehicle.getId(),
                vehicle.getBrand(),
                vehicle.getModel(),
                vehicle.getVehicleType(),
                vehicle.getColor(),
                vehicle.getPlateNoMask(),
                vehicle.getCertificationStatus(),
                isTrue(vehicle.getDefaultFlag())
        );
    }

    private VehicleProfile copyVehicle(VehicleProfile source) {
        VehicleProfile vehicle = new VehicleProfile();
        vehicle.setId(source.getId());
        vehicle.setUserId(source.getUserId());
        vehicle.setPlateNoCipher(source.getPlateNoCipher());
        vehicle.setPlateNoMask(source.getPlateNoMask());
        vehicle.setBrand(source.getBrand());
        vehicle.setModel(source.getModel());
        vehicle.setVehicleType(source.getVehicleType());
        vehicle.setColor(source.getColor());
        vehicle.setSeatCount(source.getSeatCount());
        vehicle.setEnergyType(source.getEnergyType());
        vehicle.setVehiclePhotoImageKey(source.getVehiclePhotoImageKey());
        vehicle.setCertificationStatus(source.getCertificationStatus());
        vehicle.setDefaultFlag(source.getDefaultFlag());
        vehicle.setCreatedAt(source.getCreatedAt());
        vehicle.setUpdatedAt(source.getUpdatedAt());
        vehicle.setDeleted(source.getDeleted());
        return vehicle;
    }

    private void clearVehicleCaches(Long userId, Long vehicleId) {
        redisTemplate.delete(LIST_CACHE_KEY.formatted(userId));
        redisTemplate.delete(DETAIL_CACHE_KEY.formatted(vehicleId));
        redisTemplate.delete(PUBLIC_CARD_CACHE_KEY.formatted(vehicleId));
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

    private void insertAuditLog(Long vehicleId, Long userId, String operationType, Object before, Object after, String remark) {
        auditLogMapper.insert(
                SnowflakeIdGenerator.nextId(),
                vehicleId,
                userId,
                operationType,
                toJson(before),
                toJson(after),
                remark,
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
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return Base64.getEncoder().encodeToString(value.trim().getBytes(StandardCharsets.UTF_8));
    }

    private String maskPlateNo(String plateNo) {
        if (!StringUtils.hasText(plateNo)) {
            return "";
        }
        String value = plateNo.trim();
        if (value.length() <= 2) {
            return value;
        }
        if (value.length() <= 4) {
            return value.substring(0, 1) + "**" + value.substring(value.length() - 1);
        }
        return value.substring(0, 2) + "****" + value.substring(value.length() - 1);
    }

    private String maskVin(String vin) {
        if (!StringUtils.hasText(vin) || vin.trim().length() < 8) {
            return normalize(vin);
        }
        String value = vin.trim();
        return value.substring(0, 3) + "********" + value.substring(value.length() - 4);
    }

    private String maskEngineNo(String engineNo) {
        if (!StringUtils.hasText(engineNo) || engineNo.trim().length() <= 4) {
            return normalize(engineNo);
        }
        String value = engineNo.trim();
        return "****" + value.substring(value.length() - 4);
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }

    private boolean isTrue(Integer value) {
        return value != null && value == 1;
    }
}
