package com.tongluxing.vehicle.service.impl;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tongluxing.common.exception.BusinessException;
import com.tongluxing.common.result.ResultCode;
import com.tongluxing.common.utils.SnowflakeIdGenerator;
import com.tongluxing.user.support.CurrentUserContext;
import com.tongluxing.vehicle.dto.CreateVehicleRequest;
import com.tongluxing.vehicle.dto.SubmitVehicleCertificationRequest;
import com.tongluxing.vehicle.dto.UpdateVehicleRequest;
import com.tongluxing.vehicle.dto.VehicleAuthSubmitRequest;
import com.tongluxing.vehicle.entity.VehicleCertification;
import com.tongluxing.vehicle.entity.VehicleCertificationImage;
import com.tongluxing.vehicle.entity.VehicleProfile;
import com.tongluxing.vehicle.mapper.VehicleAuditLogMapper;
import com.tongluxing.vehicle.mapper.VehicleCertificationMapper;
import com.tongluxing.vehicle.mapper.VehicleCertificationImageMapper;
import com.tongluxing.vehicle.mapper.VehicleProfileMapper;
import com.tongluxing.vehicle.service.VehicleService;
import com.tongluxing.vehicle.support.VehicleDataCipher;
import com.tongluxing.vehicle.vo.PublicVehicleCardResponse;
import com.tongluxing.vehicle.vo.PageResult;
import com.tongluxing.vehicle.vo.VehicleCertificationAuditDetailVO;
import com.tongluxing.vehicle.vo.VehicleCertificationAuditSummaryVO;
import com.tongluxing.vehicle.vo.VehicleCertificationImageVO;
import com.tongluxing.vehicle.vo.VehicleCertificationResponse;
import com.tongluxing.vehicle.vo.VehicleAuthEligibilityResponse;
import com.tongluxing.vehicle.vo.VehicleAuthStatusResponse;
import com.tongluxing.vehicle.vo.VehicleListResponse;
import com.tongluxing.vehicle.vo.VehicleResponse;

import lombok.RequiredArgsConstructor;

/**
 * 车辆模块业务实现。
 *
 * <p>实现车辆资料维护、车辆认证提交、默认车辆设置、缓存读写和操作审计。
 * 本类不暴露敏感明文，车牌号、VIN 等字段在入库前会生成密文或脱敏值。</p>
 */
@Service
@RequiredArgsConstructor
public class VehicleServiceImpl implements VehicleService {

    /** 单用户每小时最多创建车辆次数。 */
    private static final int CREATE_LIMIT = 10;
    /** 单用户每天最多提交认证次数。 */
    private static final int CERTIFICATION_SUBMIT_LIMIT = 3;
    /** 未提交认证状态。 */
    private static final String CERTIFICATION_UNSUBMITTED = "UNSUBMITTED";
    /** 认证待审核状态。 */
    private static final String CERTIFICATION_PENDING = "PENDING";
    /** 认证通过状态。 */
    private static final String CERTIFICATION_APPROVED = "APPROVED";

    /** 当前用户车辆列表缓存 key。 */
    private static final String LIST_CACHE_KEY = "vehicle:cache:list:%d";
    /** 车辆详情缓存 key。 */
    private static final String DETAIL_CACHE_KEY = "vehicle:cache:detail:%d";
    /** 公开车辆卡片缓存 key。 */
    private static final String PUBLIC_CARD_CACHE_KEY = "vehicle:cache:public-card:%d";
    /** 创建车辆限流 key。 */
    private static final String CREATE_RL_KEY = "vehicle:rl:create:%d";
    /** 提交车辆认证限流 key。 */
    private static final String CERTIFICATION_RL_KEY = "vehicle:rl:cert-submit:%d";

    /** 当前登录用户上下文。 */
    private final CurrentUserContext currentUserContext;
    /** Redis 用于缓存车辆查询结果和简单限流计数。 */
    private final StringRedisTemplate redisTemplate;
    /** JSON 工具，用于缓存和审计快照序列化。 */
    private final ObjectMapper objectMapper;
    /** 车辆档案 Mapper。 */
    private final VehicleProfileMapper vehicleProfileMapper;
    /** 车辆认证 Mapper。 */
    private final VehicleCertificationMapper certificationMapper;
    /** 车辆认证图片 Mapper。 */
    private final VehicleCertificationImageMapper certificationImageMapper;
    /** 车辆审计日志 Mapper。 */
    private final VehicleAuditLogMapper auditLogMapper;
    /** 车牌、VIN 和发动机号的版本化 AES-GCM 加密组件。 */
    private final VehicleDataCipher vehicleDataCipher;

    /** 查询当前用户车辆列表，优先读缓存。 */
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

    /** 创建车辆档案；认证通过前不允许自动成为默认车辆。 */
    @Override
    @Transactional
    public VehicleResponse createVehicle(CreateVehicleRequest request) {
        Long userId = currentUserContext.requireUserId();
        checkRateLimit(CREATE_RL_KEY.formatted(userId), CREATE_LIMIT, Duration.ofHours(1), "车辆创建太频繁，请稍后再试");

        LocalDateTime now = LocalDateTime.now();
        VehicleProfile vehicle = new VehicleProfile();
        vehicle.setId(SnowflakeIdGenerator.nextId());
        vehicle.setUserId(userId);
        String normalizedPlateNo = normalizePlateNo(request.plateNo());
        vehicle.setPlateNoCipher(cipher(normalizedPlateNo));
        vehicle.setPlateNoMask(maskPlateNo(normalizedPlateNo));
        vehicle.setCertificationStatus(CERTIFICATION_UNSUBMITTED);
        vehicle.setDefaultFlag(0);
        vehicle.setCreatedAt(now);
        vehicle.setUpdatedAt(now);
        vehicle.setDeleted(0);
        fillVehicle(vehicle, request);
        vehicleProfileMapper.insert(vehicle);
        clearVehicleCaches(userId, vehicle.getId());
        insertAuditLog(vehicle.getId(), userId, "CREATE", null, vehicle, "创建车辆");
        return toVehicleResponse(vehicle);
    }

    /** 查询当前用户拥有的车辆详情，避免越权读取。 */
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

    /** 更新车辆展示资料，并写入更新前后的审计快照。 */
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

    /** 逻辑删除车辆，并清理相关缓存。 */
    @Override
    @Transactional
    public void deleteVehicle(Long vehicleId) {
        Long userId = currentUserContext.requireUserId();
        VehicleProfile before = requireOwnedVehicle(vehicleId, userId);
        if (CERTIFICATION_PENDING.equals(before.getCertificationStatus())) {
            throw new BusinessException(409, "车辆认证正在审核，暂时不能移除");
        }
        if ("REJECTED".equals(before.getCertificationStatus())) {
            throw new BusinessException(409, "认证驳回车辆请使用“删除驳回记录”操作");
        }
        int rows = vehicleProfileMapper.logicDelete(vehicleId, userId, LocalDateTime.now());
        if (rows == 0) {
            throw new BusinessException(ResultCode.NOT_FOUND, "车辆不存在");
        }
        clearVehicleCaches(userId, vehicleId);
        insertAuditLog(vehicleId, userId, "REMOVE", before, null, "用户移除车辆");
    }

    /** 删除被驳回的认证历史，并移除对应车辆卡片。 */
    @Override
    @Transactional
    public void deleteRejectedCertificationHistory(Long vehicleId) {
        Long userId = currentUserContext.requireUserId();
        VehicleProfile before = requireOwnedVehicle(vehicleId, userId);
        VehicleCertification latest = certificationMapper.findLatestByVehicleId(vehicleId);
        if (latest == null || !"REJECTED".equals(latest.getStatus())) {
            throw new BusinessException(409, "只有认证驳回的车辆才能删除认证历史");
        }
        certificationImageMapper.deleteByVehicleId(vehicleId);
        certificationMapper.deleteRejectedByVehicleAndUser(vehicleId, userId);
        int rows = vehicleProfileMapper.logicDelete(vehicleId, userId, LocalDateTime.now());
        if (rows == 0) {
            throw new BusinessException(ResultCode.NOT_FOUND, "车辆不存在");
        }
        clearVehicleCaches(userId, vehicleId);
        insertAuditLog(vehicleId, userId, "DELETE_REJECTED_HISTORY", before, null,
                "删除认证驳回历史和车辆卡片");
    }

    /** 设置默认车辆；先清空再设置，保证单用户唯一默认车辆。 */
    @Override
    @Transactional
    public VehicleResponse setDefaultVehicle(Long vehicleId) {
        Long userId = currentUserContext.requireUserId();
        VehicleProfile before = requireOwnedVehicle(vehicleId, userId);
        if (!CERTIFICATION_APPROVED.equals(before.getCertificationStatus())) {
            throw new BusinessException(409, "只有认证通过的车辆才能设为主要车辆");
        }
        LocalDateTime now = LocalDateTime.now();
        vehicleProfileMapper.clearDefault(userId, now);
        vehicleProfileMapper.setDefault(vehicleId, userId, now);
        clearVehicleCaches(userId, vehicleId);
        VehicleProfile after = requireOwnedVehicle(vehicleId, userId);
        insertAuditLog(vehicleId, userId, "SET_DEFAULT", before, after, "设置默认车辆");
        return toVehicleResponse(after);
    }

    /** 提交车辆认证资料，认证状态同步写回车辆档案。 */
    @Override
    @Transactional
    public VehicleCertificationResponse submitCertification(Long vehicleId, SubmitVehicleCertificationRequest request) {
        Long userId = currentUserContext.requireUserId();
        checkRateLimit(CERTIFICATION_RL_KEY.formatted(userId), CERTIFICATION_SUBMIT_LIMIT, Duration.ofHours(24), "车辆认证提交太频繁，请明天再试");
        requireOwnedVehicle(vehicleId, userId);
        String normalizedPlateNo = normalizePlateNo(request.plateNo());

        VehicleCertification latest = certificationMapper.findLatestByVehicleId(vehicleId);
        if (latest != null && List.of("PENDING", "APPROVED").contains(latest.getStatus())) {
            throw new BusinessException(409, "车辆认证正在审核或已通过，不能重复提交");
        }
        ensurePlateNotApproved(normalizedPlateNo);

        LocalDateTime now = LocalDateTime.now();
        VehicleCertification certification = new VehicleCertification();
        certification.setId(SnowflakeIdGenerator.nextId());
        certification.setVehicleId(vehicleId);
        certification.setUserId(userId);
        certification.setOwnerName(normalize(request.ownerName()));
        certification.setPlateNoCipher(cipher(normalizedPlateNo));
        certification.setPlateNoMask(maskPlateNo(normalizedPlateNo));
        certification.setVehicleType(normalize(request.vehicleType()));
        certification.setVinCipher(cipher(request.vin()));
        certification.setVinMask(maskVin(request.vin()));
        certification.setEngineNoCipher(cipher(request.engineNo()));
        certification.setEngineNoMask(maskEngineNo(request.engineNo()));
        certification.setRegisterDate(request.registerDate());
        certification.setIssueDate(request.issueDate());
        certification.setIssuingAuthority(normalize(request.issuingAuthority()));
        certification.setLicenseFrontImageKey(normalize(request.licenseFrontImageKey()));
        certification.setLicenseBackImageKey(normalize(request.licenseBackImageKey()));
        certification.setRecognitionSource(normalize(request.recognitionSource()));
        certification.setStatus(CERTIFICATION_PENDING);
        certification.setRejectReason("");
        certification.setSubmittedAt(now);
        certificationMapper.insert(certification);
        int sortNo = 0;
        for (SubmitVehicleCertificationRequest.VehicleImageRequest item : request.vehicleImages()) {
            VehicleCertificationImage image = new VehicleCertificationImage();
            image.setId(SnowflakeIdGenerator.nextId());
            image.setCertificationId(certification.getId());
            image.setVehicleId(vehicleId);
            image.setImageType(normalize(item.imageType()));
            image.setImageKey(normalize(item.imageKey()));
            image.setSortNo(sortNo++);
            image.setCreatedAt(now);
            image.setDeleted(0);
            certificationImageMapper.insert(image);
        }
        vehicleProfileMapper.updateCertificationStatus(vehicleId, userId, CERTIFICATION_PENDING, now);
        clearVehicleCaches(userId, vehicleId);
        insertAuditLog(vehicleId, userId, "CERTIFICATION", null, certification, "提交车辆认证");
        return toCertificationResponse(certification);
    }

    /** 查询车辆最近一次认证记录；没有记录时返回未提交状态。 */
    @Override
    public VehicleCertificationResponse getCertification(Long vehicleId) {
        Long userId = currentUserContext.requireUserId();
        requireOwnedVehicle(vehicleId, userId);
        VehicleCertification certification = certificationMapper.findLatestByVehicleId(vehicleId);
        if (certification == null) {
            return new VehicleCertificationResponse(vehicleId, "", "", "", "", "", "", List.of(),
                    CERTIFICATION_UNSUBMITTED, "", null, null, true);
        }
        return toCertificationResponse(certification);
    }

    /**
     * 适配产品定义的车辆认证闭环接口。相同用户和车牌优先复用车辆档案，
     * 图片仅按 URL/资源标识保存，不触发 OCR。
     */
    @Override
    public VehicleAuthEligibilityResponse checkVehicleAuthEligibility(String plateNumber) {
        currentUserContext.requireUserId();
        String normalizedPlateNo = normalizePlateNo(plateNumber);
        VehicleCertification approved = findApprovedCertification(normalizedPlateNo);
        if (approved != null) {
            return new VehicleAuthEligibilityResponse(false, CERTIFICATION_APPROVED,
                    "该车辆已经认证通过，不能再次发送认证申请");
        }
        return new VehicleAuthEligibilityResponse(true, CERTIFICATION_UNSUBMITTED, "");
    }

    @Override
    @Transactional
    public VehicleAuthStatusResponse submitVehicleAuth(VehicleAuthSubmitRequest request) {
        Long userId = currentUserContext.requireUserId();
        String normalizedPlateNo = normalizePlateNo(request.plateNumber());
        ensurePlateNotApproved(normalizedPlateNo);
        String plateCipher = cipher(normalizedPlateNo);
        VehicleProfile vehicle = vehicleProfileMapper.findByUserIdAndPlateNoCipher(userId, plateCipher);
        if (vehicle == null) {
            vehicle = vehicleProfileMapper.findByUserIdAndPlateNoCipher(
                    userId, vehicleDataCipher.legacyEncoded(normalizedPlateNo));
        }
        if (vehicle == null) {
            VehicleResponse created = createVehicle(new CreateVehicleRequest(
                    normalizedPlateNo, request.vehicleBrand(), request.vehicleModel(), "轿车",
                    request.vehicleColor(), 5, "", request.vehicleImages().get(0)));
            vehicle = vehicleProfileMapper.findByIdAndUserId(created.vehicleId(), userId);
        } else {
            VehicleProfile before = copyVehicle(vehicle);
            vehicle.setBrand(normalize(request.vehicleBrand()));
            vehicle.setModel(normalize(request.vehicleModel()));
            vehicle.setColor(normalize(request.vehicleColor()));
            vehicle.setVehiclePhotoImageKey(normalize(request.vehicleImages().get(0)));
            vehicle.setUpdatedAt(LocalDateTime.now());
            vehicleProfileMapper.update(vehicle);
            clearVehicleCaches(userId, vehicle.getId());
            insertAuditLog(vehicle.getId(), userId, "UPDATE", before, vehicle, "车辆认证同步基础资料");
        }

        List<SubmitVehicleCertificationRequest.VehicleImageRequest> images = new java.util.ArrayList<>();
        request.registrationLicenseImages().forEach(url -> images.add(
                new SubmitVehicleCertificationRequest.VehicleImageRequest("REGISTRATION_LICENSE", url)));
        request.vehicleImages().forEach(url -> images.add(
                new SubmitVehicleCertificationRequest.VehicleImageRequest("VEHICLE", url)));

        submitCertification(vehicle.getId(), new SubmitVehicleCertificationRequest(
                "", normalizedPlateNo, "轿车", "", "", null, null, "",
                request.registrationLicenseImages().get(0), request.registrationLicenseImages().get(1),
                images, "MANUAL_UPLOAD"));
        return toVehicleAuthStatus(certificationMapper.findLatestByVehicleId(vehicle.getId()));
    }

    /** 查询当前用户最近一次车辆认证申请，并转换为产品约定状态。 */
    @Override
    public VehicleAuthStatusResponse getMyVehicleAuthStatus() {
        Long userId = currentUserContext.requireUserId();
        VehicleCertification certification = certificationMapper.findLatestByUserId(userId);
        if (certification == null) {
            return new VehicleAuthStatusResponse(null, null, CERTIFICATION_UNSUBMITTED, null, null, null);
        }
        return toVehicleAuthStatus(certification);
    }

    /** 后台分页查询车辆认证申请。 */
    @Override
    public PageResult<VehicleCertificationAuditSummaryVO> pageCertifications(
            String status, String keyword, int page, int size) {
        int normalizedPage = Math.max(page, 1);
        int normalizedSize = Math.min(Math.max(size, 1), 100);
        String normalizedStatus = normalizeStatusFilter(status);
        String normalizedKeyword = normalize(keyword);
        List<VehicleCertificationAuditSummaryVO> records = certificationMapper.page(
                        normalizedStatus, normalizedKeyword, (normalizedPage - 1) * normalizedSize, normalizedSize)
                .stream()
                .map(item -> new VehicleCertificationAuditSummaryVO(
                        item.getId(), item.getVehicleId(), item.getUserId(), item.getOwnerName(),
                        item.getPlateNoMask(), item.getVehicleType(), item.getStatus(), item.getSubmittedAt()))
                .toList();
        return new PageResult<>(records, certificationMapper.count(normalizedStatus, normalizedKeyword),
                normalizedPage, normalizedSize);
    }

    /** 后台查询车辆认证详情。 */
    @Override
    public VehicleCertificationAuditDetailVO getCertificationForAudit(Long certificationId) {
        VehicleCertification certification = certificationMapper.findById(certificationId);
        if (certification == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "车辆认证申请不存在");
        }
        return toAuditDetail(certification);
    }

    /** 后台应用车辆认证人工审核结果。 */
    @Override
    @Transactional
    public VehicleCertificationAuditDetailVO applyCertificationAuditResult(
            Long certificationId, String auditResult, String rejectReason, Long operatorId) {
        String normalizedResult = normalizeAuditResult(auditResult);
        String normalizedReason = normalizeRejectReason(normalizedResult, rejectReason);
        VehicleCertification certification = certificationMapper.findById(certificationId);
        if (certification == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "车辆认证申请不存在");
        }
        if (!CERTIFICATION_PENDING.equals(certification.getStatus())) {
            throw new BusinessException(409, "车辆认证申请已审核");
        }
        if (CERTIFICATION_APPROVED.equals(normalizedResult)) {
            String normalizedPlateNo = normalizePlateNo(decipher(certification.getPlateNoCipher()));
            ensurePlateNotApproved(normalizedPlateNo);
        }
        LocalDateTime now = LocalDateTime.now();
        int changed = certificationMapper.updateAudit(
                certificationId, normalizedResult, normalizedReason, operatorId, now);
        if (changed != 1) {
            throw new BusinessException(409, "车辆认证状态已发生变化，请刷新后重试");
        }
        vehicleProfileMapper.updateCertificationStatus(
                certification.getVehicleId(), certification.getUserId(), normalizedResult, now);
        clearVehicleCaches(certification.getUserId(), certification.getVehicleId());
        insertAuditLog(certification.getVehicleId(), certification.getUserId(), "CERTIFICATION_AUDIT",
                certification, certificationMapper.findById(certificationId), normalizedReason);
        return toAuditDetail(certificationMapper.findById(certificationId));
    }

    /** 查询车辆公开卡片信息，供跨模块展示使用。 */
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

    @Override
    public PublicVehicleCardResponse getPublicMainCard(Long userId) {
        VehicleProfile vehicle = vehicleProfileMapper.findMainByUserId(userId);
        return vehicle == null ? null : toPublicCardResponse(vehicle);
    }

    /** 校验车辆属于当前用户，防止越权操作。 */
    private VehicleProfile requireOwnedVehicle(Long vehicleId, Long userId) {
        VehicleProfile vehicle = vehicleProfileMapper.findByIdAndUserId(vehicleId, userId);
        if (vehicle == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "车辆不存在");
        }
        return vehicle;
    }

    /** 填充创建车辆时允许写入的字段。 */
    private void fillVehicle(VehicleProfile vehicle, CreateVehicleRequest request) {
        vehicle.setBrand(normalize(request.brand()));
        vehicle.setModel(normalize(request.model()));
        vehicle.setVehicleType(normalize(request.vehicleType()));
        vehicle.setColor(normalize(request.color()));
        vehicle.setSeatCount(request.seatCount() == null ? 5 : request.seatCount());
        vehicle.setEnergyType(normalize(request.energyType()));
        vehicle.setVehiclePhotoImageKey(normalize(request.vehiclePhotoImageKey()));
    }

    /** 填充更新车辆时允许修改的字段。 */
    private void fillVehicle(VehicleProfile vehicle, UpdateVehicleRequest request) {
        vehicle.setBrand(normalize(request.brand()));
        vehicle.setModel(normalize(request.model()));
        vehicle.setVehicleType(normalize(request.vehicleType()));
        vehicle.setColor(normalize(request.color()));
        vehicle.setSeatCount(request.seatCount() == null ? vehicle.getSeatCount() : request.seatCount());
        vehicle.setEnergyType(normalize(request.energyType()));
        vehicle.setVehiclePhotoImageKey(normalize(request.vehiclePhotoImageKey()));
    }

    /** 转换为车辆详情响应。 */
    private VehicleResponse toVehicleResponse(VehicleProfile vehicle) {
        VehicleCertification certification = certificationMapper.findLatestByVehicleId(vehicle.getId());
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
                isTrue(vehicle.getDefaultFlag()),
                certification == null ? null : certification.getId(),
                certification == null ? "" : normalize(certification.getRejectReason()),
                certification == null ? null : certification.getSubmittedAt(),
                certification == null ? null : certification.getReviewedAt()
        );
    }

    /** 转换为车辆认证响应。 */
    private VehicleCertificationResponse toCertificationResponse(VehicleCertification certification) {
        return new VehicleCertificationResponse(
                certification.getVehicleId(),
                certification.getOwnerName(),
                certification.getPlateNoMask(),
                certification.getVinMask(),
                certification.getEngineNoMask(),
                certification.getLicenseFrontImageKey(),
                certification.getLicenseBackImageKey(),
                certificationImages(certification.getId()),
                certification.getStatus(),
                certification.getRejectReason(),
                certification.getSubmittedAt(),
                certification.getReviewedAt(),
                CERTIFICATION_UNSUBMITTED.equals(certification.getStatus()) || "REJECTED".equals(certification.getStatus())
        );
    }

    /** 转换后台审核详情并解码授权字段。 */
    private VehicleCertificationAuditDetailVO toAuditDetail(VehicleCertification certification) {
        VehicleProfile vehicle = vehicleProfileMapper.findById(certification.getVehicleId());
        return new VehicleCertificationAuditDetailVO(
                certification.getId(), certification.getVehicleId(), certification.getUserId(),
                certification.getOwnerName(), decipher(certification.getPlateNoCipher()),
                vehicle == null ? "" : vehicle.getBrand(), vehicle == null ? "" : vehicle.getModel(),
                vehicle == null ? "" : vehicle.getColor(), certification.getVehicleType(),
                decipher(certification.getVinCipher()), decipher(certification.getEngineNoCipher()),
                certification.getRegisterDate(), certification.getIssueDate(), certification.getIssuingAuthority(),
                certification.getLicenseFrontImageKey(), certification.getLicenseBackImageKey(),
                certificationImages(certification.getId()), certification.getRecognitionSource(), certification.getStatus(),
                certification.getRejectReason(), certification.getSubmittedAt(), certification.getReviewedAt());
    }

    /** 将内部 APPROVED/REJECTED 状态映射为产品接口 PASS/REJECT。 */
    private VehicleAuthStatusResponse toVehicleAuthStatus(VehicleCertification certification) {
        String status = switch (certification.getStatus()) {
            case "APPROVED" -> "PASS";
            case "REJECTED" -> "REJECT";
            default -> certification.getStatus();
        };
        return new VehicleAuthStatusResponse(certification.getId(), certification.getVehicleId(), status,
                certification.getRejectReason(), certification.getSubmittedAt(), certification.getReviewedAt());
    }

    private List<VehicleCertificationImageVO> certificationImages(Long certificationId) {
        return certificationImageMapper.findByCertificationId(certificationId).stream()
                .map(image -> new VehicleCertificationImageVO(image.getImageType(), image.getImageKey()))
                .toList();
    }

    /** 转换为公开车辆卡片响应。 */
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

    /** 复制车辆实体，避免直接修改查询出的原始对象导致审计快照失真。 */
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

    /** 清理车辆列表、详情和公开卡片缓存。 */
    private void clearVehicleCaches(Long userId, Long vehicleId) {
        redisTemplate.delete(LIST_CACHE_KEY.formatted(userId));
        redisTemplate.delete(DETAIL_CACHE_KEY.formatted(vehicleId));
        redisTemplate.delete(PUBLIC_CARD_CACHE_KEY.formatted(vehicleId));
    }

    /** 基于 Redis 计数器实现简单频率限制。 */
    private void checkRateLimit(String key, int limit, Duration ttl, String message) {
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1) {
            redisTemplate.expire(key, ttl);
        }
        if (count != null && count > limit) {
            throw new BusinessException(message);
        }
    }

    /** 写入车辆操作审计日志。 */
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

    /** 序列化审计快照；序列化失败时返回空 JSON，避免审计异常影响主流程。 */
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

    /** 从 Redis 读取 JSON 缓存；缓存损坏时主动删除并走数据库。 */
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

    /** 写入 JSON 缓存；序列化失败时删除旧缓存，避免返回脏数据。 */
    private void writeJson(String key, Object value, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(value), ttl);
        } catch (JsonProcessingException ignored) {
            redisTemplate.delete(key);
        }
    }

    /** 查询全平台是否已有相同车牌的通过认证记录。 */
    private VehicleCertification findApprovedCertification(String normalizedPlateNo) {
        return certificationMapper.findApprovedByPlateNoCipher(
                cipher(normalizedPlateNo),
                vehicleDataCipher.legacyEncoded(normalizedPlateNo));
    }

    /** 已通过认证的相同车辆不允许再次提交。 */
    private void ensurePlateNotApproved(String normalizedPlateNo) {
        if (findApprovedCertification(normalizedPlateNo) != null) {
            throw new BusinessException(409, "该车辆已经认证通过，不能再次发送认证申请");
        }
    }

    /** 统一车牌格式，避免大小写和空格差异绕过重复校验。 */
    private String normalizePlateNo(String plateNo) {
        String value = normalize(plateNo).replace(" ", "").toUpperCase(Locale.ROOT);
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "车牌号不能为空");
        }
        return value;
    }

    /** 使用版本化 AES-GCM 加密车辆敏感字段。 */
    private String cipher(String value) {
        return vehicleDataCipher.encrypt(value);
    }

    /** 解密后台审核详情需要的敏感字段，并兼容旧 Base64 数据。 */
    private String decipher(String value) {
        return vehicleDataCipher.decrypt(value);
    }

    private String normalizeStatusFilter(String status) {
        String value = normalize(status).toUpperCase();
        if (value.isEmpty()) {
            return "";
        }
        if (!List.of("PENDING", "APPROVED", "REJECTED").contains(value)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "车辆认证状态不合法");
        }
        return value;
    }

    private String normalizeAuditResult(String auditResult) {
        String value = normalize(auditResult).toUpperCase();
        if (!List.of("APPROVED", "REJECTED").contains(value)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "审核结果仅支持 APPROVED 或 REJECTED");
        }
        return value;
    }

    private String normalizeRejectReason(String auditResult, String rejectReason) {
        String value = normalize(rejectReason);
        if ("REJECTED".equals(auditResult) && (value.length() < 2 || value.length() > 255)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "驳回原因长度应为 2 到 255 个字符");
        }
        return "APPROVED".equals(auditResult) ? null : value;
    }

    /** 车牌号脱敏展示。 */
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

    /** VIN 脱敏展示。 */
    private String maskVin(String vin) {
        if (!StringUtils.hasText(vin) || vin.trim().length() < 8) {
            return normalize(vin);
        }
        String value = vin.trim();
        return value.substring(0, 3) + "********" + value.substring(value.length() - 4);
    }

    /** 发动机号脱敏展示。 */
    private String maskEngineNo(String engineNo) {
        if (!StringUtils.hasText(engineNo) || engineNo.trim().length() <= 4) {
            return normalize(engineNo);
        }
        String value = engineNo.trim();
        return "****" + value.substring(value.length() - 4);
    }

    /** 去除首尾空白；空值统一转为空串。 */
    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }

    /** 将数据库中的 0/1 标记转换为布尔值。 */
    private boolean isTrue(Integer value) {
        return value != null && value == 1;
    }
}
