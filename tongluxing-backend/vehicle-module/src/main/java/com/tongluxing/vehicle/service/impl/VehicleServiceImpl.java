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
    /** 用户服务，用于校验车辆认证前必须完成驾驶证认证。 */
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
        // 用户 ID 必须从服务端认证上下文获取，不能相信客户端传入的归属信息。
        Long userId = currentUserContext.requireUserId();
        String cacheKey = LIST_CACHE_KEY.formatted(userId);

        // 列表数据读多写少，先读取用户维度缓存，命中后避免重复查询数据库。
        VehicleListResponse cached = readJson(cacheKey, VehicleListResponse.class);
        if (cached != null) {
            return cached;
        }

        // Mapper 已按“默认车辆优先、创建时间倒序”排序；这里只负责响应模型转换。
        List<VehicleResponse> vehicles = vehicleProfileMapper.findByUserId(userId)
                .stream()
                .map(this::toVehicleResponse)
                .toList();
        VehicleListResponse response = new VehicleListResponse(vehicles);

        // 缓存完整响应而不是数据库实体，防止加密车牌等内部字段进入 Redis 返回链路。
        writeJson(cacheKey, response, Duration.ofMinutes(20));
        return response;
    }

    /** 创建车辆档案；认证通过前不允许自动成为默认车辆。 */
    @Override
    @Transactional
    public VehicleResponse createVehicle(CreateVehicleRequest request) {
        // 第一步：确认登录身份并限制创建频率，防止恶意批量制造车辆档案。
        Long userId = currentUserContext.requireUserId();
        checkRateLimit(CREATE_RL_KEY.formatted(userId), CREATE_LIMIT, Duration.ofHours(1), "车辆创建太频繁，请稍后再试");

        // 第二步：构建初始车辆实体。ID 在应用侧生成，便于插入后直接用于缓存和审计。
        LocalDateTime now = LocalDateTime.now();
        VehicleProfile vehicle = new VehicleProfile();
        vehicle.setId(SnowflakeIdGenerator.nextId());
        vehicle.setUserId(userId);

        // 车牌先统一大小写和空格，再分别保存可检索密文与仅用于展示的脱敏值。
        String normalizedPlateNo = normalizePlateNo(request.plateNo());
        vehicle.setPlateNoCipher(cipher(normalizedPlateNo));
        vehicle.setPlateNoMask(maskPlateNo(normalizedPlateNo));

        // 新建档案还未提交认证，因此不能直接成为默认车辆。
        vehicle.setCertificationStatus(CERTIFICATION_UNSUBMITTED);
        vehicle.setDefaultFlag(0);
        vehicle.setCreatedAt(now);
        vehicle.setUpdatedAt(now);
        vehicle.setDeleted(0);
        fillVehicle(vehicle, request);

        // 第三步：持久化后清理列表/详情缓存，并留下可追溯的创建审计记录。
        vehicleProfileMapper.insert(vehicle);
        clearVehicleCaches(userId, vehicle.getId());
        insertAuditLog(vehicle.getId(), userId, "CREATE", null, vehicle, "创建车辆");
        return toVehicleResponse(vehicle);
    }

    /** 查询当前用户拥有的车辆详情，避免越权读取。 */
    @Override
    public VehicleResponse getVehicle(Long vehicleId) {
        Long userId = currentUserContext.requireUserId();

        // 即使缓存中存在详情，也要先检查车辆归属，避免知道 vehicleId 后越权读取缓存。
        VehicleProfile owned = requireOwnedVehicle(vehicleId, userId);
        String cacheKey = DETAIL_CACHE_KEY.formatted(vehicleId);
        VehicleResponse cached = readJson(cacheKey, VehicleResponse.class);
        if (cached != null) {
            return cached;
        }

        // 缓存中仅保存对外响应；敏感字段的密文不会通过详情接口暴露。
        VehicleResponse response = toVehicleResponse(owned);
        writeJson(cacheKey, response, Duration.ofMinutes(20));
        return response;
    }

    /** 更新车辆展示资料，并写入更新前后的审计快照。 */
    @Override
    @Transactional
    public VehicleResponse updateVehicle(Long vehicleId, UpdateVehicleRequest request) {
        Long userId = currentUserContext.requireUserId();

        // before 保留数据库原始状态；复制后再修改，确保审计的前后快照不会指向同一对象。
        VehicleProfile before = requireOwnedVehicle(vehicleId, userId);
        VehicleProfile vehicle = copyVehicle(before);

        // 更新请求只允许修改展示字段，不允许在此接口修改车牌、认证状态或默认标记。
        fillVehicle(vehicle, request);
        vehicle.setUpdatedAt(LocalDateTime.now());
        vehicleProfileMapper.update(vehicle);
        clearVehicleCaches(userId, vehicleId);
        insertAuditLog(vehicleId, userId, "UPDATE", before, vehicle, "更新车辆");
        // 重新查询数据库而不是直接返回内存对象，保证响应体现数据库最终状态。
        return toVehicleResponse(requireOwnedVehicle(vehicleId, userId));
    }

    /** 逻辑删除车辆，并清理相关缓存。 */
    @Override
    @Transactional
    public void deleteVehicle(Long vehicleId) {
        Long userId = currentUserContext.requireUserId();
        VehicleProfile before = requireOwnedVehicle(vehicleId, userId);

        // 待审核记录仍可能被后台处理，禁止此时移除，避免认证记录失去有效车辆载体。
        if (CERTIFICATION_PENDING.equals(before.getCertificationStatus())) {
            throw new BusinessException(409, "车辆认证正在审核，暂时不能移除");
        }
        if ("REJECTED".equals(before.getCertificationStatus())) {
            throw new BusinessException(409, "认证驳回车辆请使用“删除驳回记录”操作");
        }

        // 采用逻辑删除保留审计和历史关联；Mapper 同时清除默认车辆标记。
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

        // 必须以最新认证记录为准；非驳回状态不能通过此入口清理历史。
        VehicleCertification latest = certificationMapper.findLatestByVehicleId(vehicleId);
        if (latest == null || !"REJECTED".equals(latest.getStatus())) {
            throw new BusinessException(409, "只有认证驳回的车辆才能删除认证历史");
        }
        // 先清认证附件及驳回记录，再逻辑删除车辆卡片，整个过程由事务保证原子性。
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

        // 先清空再设置，配合事务使一个用户最终只保留一辆主要车辆。
        vehicleProfileMapper.clearDefault(userId, now);
        vehicleProfileMapper.setDefault(vehicleId, userId, now);
        clearVehicleCaches(userId, vehicleId);
        // 回查 after 用于返回与审计，避免审计快照仍携带旧的 defaultFlag。
        VehicleProfile after = requireOwnedVehicle(vehicleId, userId);
        insertAuditLog(vehicleId, userId, "SET_DEFAULT", before, after, "设置默认车辆");
        return toVehicleResponse(after);
    }

    /** 提交车辆认证资料，认证状态同步写回车辆档案。 */
    @Override
    @Transactional
    public VehicleCertificationResponse submitCertification(Long vehicleId, SubmitVehicleCertificationRequest request) {
        // 阶段一：校验登录身份、提交频率和车辆归属；P0 不再检查驾驶证前置。
        Long userId = currentUserContext.requireUserId();
        checkRateLimit(CERTIFICATION_RL_KEY.formatted(userId), CERTIFICATION_SUBMIT_LIMIT, Duration.ofHours(24), "车辆认证提交太频繁，请明天再试");
        requireOwnedVehicle(vehicleId, userId);

        // 车牌规范化后再参与重复认证检查，避免空格或大小写差异绕过规则。
        String normalizedPlateNo = normalizePlateNo(request.plateNo());

        // 阶段二：同一车辆存在待审核或已通过申请时不允许重复提交；被驳回后可以重提。
        VehicleCertification latest = certificationMapper.findLatestByVehicleId(vehicleId);
        if (latest != null && List.of("PENDING", "APPROVED").contains(latest.getStatus())) {
            throw new BusinessException(409, "车辆认证正在审核或已通过，不能重复提交");
        }
        // 同时检查全平台是否已有相同车牌的已通过记录，避免一车多认证。
        ensurePlateNotApproved(normalizedPlateNo);

        // 阶段三：组装认证主记录。敏感字段保存加密值，对外查询只使用 mask 字段。
        LocalDateTime now = LocalDateTime.now();
        VehicleCertification certification = new VehicleCertification();
        certification.setId(SnowflakeIdGenerator.nextId());
        certification.setVehicleId(vehicleId);
        certification.setUserId(userId);
        certification.setOwnerName(defaultText(request.ownerName(), "未识别"));
        certification.setPlateNoCipher(cipher(normalizedPlateNo));
        certification.setPlateNoMask(maskPlateNo(normalizedPlateNo));
        certification.setVehicleType(defaultText(request.vehicleType(), "小型汽车"));
        certification.setVinCipher(cipher(defaultText(request.vin(), "UNKNOWN")));
        certification.setVinMask(StringUtils.hasText(request.vin()) ? maskVin(request.vin()) : "未识别");
        certification.setEngineNoCipher(cipher(defaultText(request.engineNo(), "UNKNOWN")));
        certification.setEngineNoMask(StringUtils.hasText(request.engineNo()) ? maskEngineNo(request.engineNo()) : "未识别");
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

        // 阶段四：车辆照片按请求顺序逐条入库。sortNo 从 0 递增，保证展示顺序稳定。
        int sortNo = 0;
        for (SubmitVehicleCertificationRequest.VehicleImageRequest item : request.vehicleImages() == null ? List.<SubmitVehicleCertificationRequest.VehicleImageRequest>of() : request.vehicleImages()) {
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

        // 先记录原始提交动作，再执行内测自动审核，审计链中仍能区分“提交”和“审核”。
        insertAuditLog(vehicleId, userId, "CERTIFICATION", null, certification, "提交车辆认证");
        // 内测阶段资料完整即自动通过；仍保留认证记录和审核审计，后续恢复人工审核无需迁移数据。
        applyCertificationAuditResult(certification.getId(), CERTIFICATION_APPROVED, null, 0L);
        return toCertificationResponse(certificationMapper.findById(certification.getId()));
    }

    /** 查询车辆最近一次认证记录；没有记录时返回未提交状态。 */
    @Override
    public VehicleCertificationResponse getCertification(Long vehicleId) {
        Long userId = currentUserContext.requireUserId();

        // 查询前校验归属，认证记录本身包含证件信息，不能按 vehicleId 公开读取。
        requireOwnedVehicle(vehicleId, userId);
        VehicleCertification certification = certificationMapper.findLatestByVehicleId(vehicleId);
        if (certification == null) {
            // 使用显式 UNSUBMITTED 响应简化客户端状态机，避免客户端把 null 当作异常。
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
        // requireUserId 同时承担接口鉴权作用；此接口不接受用户 ID 参数。
        currentUserContext.requireUserId();

        // P0 不再要求驾驶证前置；这里只检查车牌是否已经被认证占用。
        String normalizedPlateNo = normalizePlateNo(plateNumber);

        // 查询时同时兼容 v2 密文与历史 Base64 值，避免升级后重复认证老车辆。
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
        // 阶段一：校验登录身份，并确保车牌尚未被其他通过记录占用。
        Long userId = currentUserContext.requireUserId();
        String normalizedPlateNo = normalizePlateNo(request.plateNumber());
        ensurePlateNotApproved(normalizedPlateNo);

        // 阶段二：优先按新加密格式复用车辆档案；未找到时再兼容查询历史 Base64 数据。
        String plateCipher = cipher(normalizedPlateNo);
        VehicleProfile vehicle = vehicleProfileMapper.findByUserIdAndPlateNoCipher(userId, plateCipher);
        if (vehicle == null) {
            vehicle = vehicleProfileMapper.findByUserIdAndPlateNoCipher(
                    userId, vehicleDataCipher.legacyEncoded(normalizedPlateNo));
        }
        if (vehicle == null) {
            // 用户名下没有同车牌档案时创建基础车辆；未上传外观图时以行驶证正页作为临时封面。
            String coverImage = firstVehicleImageOrRegistration(request);
            VehicleResponse created = createVehicle(new CreateVehicleRequest(
                    normalizedPlateNo, defaultText(request.vehicleBrand(), "待补充"),
                    defaultText(request.vehicleModel(), "待补充"), "轿车",
                    defaultText(request.vehicleColor(), "未填写"), 5, "", coverImage));
            vehicle = vehicleProfileMapper.findByIdAndUserId(created.vehicleId(), userId);
        } else {
            // 已有档案只同步品牌、车型、颜色和封面，不改变车辆归属及认证主键。
            VehicleProfile before = copyVehicle(vehicle);
            vehicle.setBrand(defaultText(request.vehicleBrand(), vehicle.getBrand()));
            vehicle.setModel(defaultText(request.vehicleModel(), vehicle.getModel()));
            vehicle.setColor(defaultText(request.vehicleColor(), vehicle.getColor()));
            vehicle.setVehiclePhotoImageKey(firstVehicleImageOrRegistration(request));
            vehicle.setUpdatedAt(LocalDateTime.now());
            vehicleProfileMapper.update(vehicle);
            clearVehicleCaches(userId, vehicle.getId());
            insertAuditLog(vehicle.getId(), userId, "UPDATE", before, vehicle, "车辆认证同步基础资料");
        }

        // 阶段三：把两类图片转换为统一的认证附件命令；行驶证在前、车辆外观图在后。
        List<SubmitVehicleCertificationRequest.VehicleImageRequest> images = new java.util.ArrayList<>();
        request.registrationLicenseImages().forEach(url -> images.add(
                new SubmitVehicleCertificationRequest.VehicleImageRequest("REGISTRATION_LICENSE", url)));
        if (request.vehicleImages() != null) {
            request.vehicleImages().forEach(url -> images.add(
                    new SubmitVehicleCertificationRequest.VehicleImageRequest("VEHICLE", url)));
        }

        // 阶段四：复用标准认证提交入口，确保限流、重复校验、加密、审计及自动审核规则一致。
        submitCertification(vehicle.getId(), new SubmitVehicleCertificationRequest(
                "", normalizedPlateNo, "轿车", "", "", null, null, "",
                request.registrationLicenseImages().get(0),
                request.registrationLicenseImages().size() > 1 ? request.registrationLicenseImages().get(1) : null,
                images, "MANUAL_UPLOAD"));
        // 重新查询最新记录，因为内测自动审核已可能把 PENDING 更新为 APPROVED。
        return toVehicleAuthStatus(certificationMapper.findLatestByVehicleId(vehicle.getId()));
    }

    /** 返回外观图首图；没有外观图时复用行驶证正页作为临时车辆封面。 */
    private String firstVehicleImageOrRegistration(VehicleAuthSubmitRequest request) {
        if (request.vehicleImages() != null && !request.vehicleImages().isEmpty()) {
            return normalize(request.vehicleImages().get(0));
        }
        return normalize(request.registrationLicenseImages().get(0));
    }

    private String defaultText(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    /** 查询当前用户最近一次车辆认证申请，并转换为产品约定状态。 */
    @Override
    public VehicleAuthStatusResponse getMyVehicleAuthStatus() {
        Long userId = currentUserContext.requireUserId();

        // 只读取当前未删除车辆关联的最新认证，已删除车辆的历史不会污染用户当前状态。
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
        // 页码最小为 1，单页最多 100 条，避免后台误传参数形成全表大查询。
        int normalizedPage = Math.max(page, 1);
        int normalizedSize = Math.min(Math.max(size, 1), 100);

        // 状态统一转为数据库枚举值；关键词只去除首尾空白，具体模糊匹配由 XML 完成。
        String normalizedStatus = normalizeStatusFilter(status);
        String normalizedKeyword = normalize(keyword);

        // 数据列表与总数使用完全相同的过滤条件，确保分页元数据和页面内容一致。
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
        // 后台详情按认证申请主键读取，不按 vehicleId 读取，便于查看每一次历史提交。
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
        // 先规范化审核结果和原因；通过时原因强制置空，驳回时校验原因长度。
        String normalizedResult = normalizeAuditResult(auditResult);
        String normalizedReason = normalizeRejectReason(normalizedResult, rejectReason);

        // 查询当前状态用于存在性与幂等校验，只有 PENDING 记录允许被审核。
        VehicleCertification certification = certificationMapper.findById(certificationId);
        if (certification == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "车辆认证申请不存在");
        }
        if (!CERTIFICATION_PENDING.equals(certification.getStatus())) {
            throw new BusinessException(409, "车辆认证申请已审核");
        }
        if (CERTIFICATION_APPROVED.equals(normalizedResult)) {
            // 审核通过前再次执行全平台去重，防止两条待审申请并发通过同一车牌。
            String normalizedPlateNo = normalizePlateNo(decipher(certification.getPlateNoCipher()));
            ensurePlateNotApproved(normalizedPlateNo);
        }
        LocalDateTime now = LocalDateTime.now();

        // SQL 的 where 条件包含 status='PENDING'；changed != 1 表示发生并发审核或状态变化。
        int changed = certificationMapper.updateAudit(
                certificationId, normalizedResult, normalizedReason, operatorId, now);
        if (changed != 1) {
            throw new BusinessException(409, "车辆认证状态已发生变化，请刷新后重试");
        }

        // 认证表保存每次申请，车辆档案保存当前汇总状态，两处必须在同一事务内同步。
        vehicleProfileMapper.updateCertificationStatus(
                certification.getVehicleId(), certification.getUserId(), normalizedResult, now);
        clearVehicleCaches(certification.getUserId(), certification.getVehicleId());

        // 审计 after 快照从数据库回查，保证包含最终审核人和审核时间。
        insertAuditLog(certification.getVehicleId(), certification.getUserId(), "CERTIFICATION_AUDIT",
                certification, certificationMapper.findById(certificationId), normalizedReason);
        return toAuditDetail(certificationMapper.findById(certificationId));
    }

    /** 查询车辆公开卡片信息，供跨模块展示使用。 */
    @Override
    public PublicVehicleCardResponse getPublicCard(Long vehicleId) {
        String cacheKey = PUBLIC_CARD_CACHE_KEY.formatted(vehicleId);

        // 公开卡片不含敏感明文，可短期缓存供行程、用户主页等高频场景复用。
        PublicVehicleCardResponse cached = readJson(cacheKey, PublicVehicleCardResponse.class);
        if (cached != null) {
            return cached;
        }

        // 公开查询仍过滤逻辑删除车辆，但不校验当前登录用户是否为车主。
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
        // Mapper 优先默认车辆，其次优先已认证车辆；用户没有车辆时按接口约定返回 null。
        VehicleProfile vehicle = vehicleProfileMapper.findMainByUserId(userId);
        return vehicle == null ? null : toPublicCardResponse(vehicle);
    }

    /** 校验车辆属于当前用户，防止越权操作。 */
    private VehicleProfile requireOwnedVehicle(Long vehicleId, Long userId) {
        // 把 vehicleId 和 userId 同时放入 SQL 条件，避免“先查车辆、再在内存判断”产生越权窗口。
        VehicleProfile vehicle = vehicleProfileMapper.findByIdAndUserId(vehicleId, userId);
        if (vehicle == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "车辆不存在");
        }
        return vehicle;
    }

    /** 填充创建车辆时允许写入的字段。 */
    private void fillVehicle(VehicleProfile vehicle, CreateVehicleRequest request) {
        // 所有可选文本统一转换为空串，避免数据库 null 与空串造成前端重复判空。
        vehicle.setBrand(normalize(request.brand()));
        vehicle.setModel(normalize(request.model()));
        vehicle.setVehicleType(normalize(request.vehicleType()));
        vehicle.setColor(normalize(request.color()));
        // 创建时未提供座位数使用产品默认值 5。
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
        // 更新时未提供座位数表示保持原值，而不是重置为创建默认值。
        vehicle.setSeatCount(request.seatCount() == null ? vehicle.getSeatCount() : request.seatCount());
        vehicle.setEnergyType(normalize(request.energyType()));
        vehicle.setVehiclePhotoImageKey(normalize(request.vehiclePhotoImageKey()));
    }

    /** 转换为车辆详情响应。 */
    private VehicleResponse toVehicleResponse(VehicleProfile vehicle) {
        // 车辆档案只保存当前认证状态，响应还需要最新申请 ID、驳回原因和审核时间。
        VehicleCertification certification = certificationMapper.findLatestByVehicleId(vehicle.getId());
        return new VehicleResponse(
                vehicle.getId(),
                vehicle.getUserId(),
                // 永远返回脱敏车牌，密文仅用于服务端去重和后台授权查看。
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
        // 认证详情同样只返回脱敏证件号；图片列表按 sortNo 排序后单独组装。
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
                // 未提交或被驳回时允许再次提交，待审和通过状态均禁止重复提交。
                CERTIFICATION_UNSUBMITTED.equals(certification.getStatus()) || "REJECTED".equals(certification.getStatus())
        );
    }

    /** 转换后台审核详情并解码授权字段。 */
    private VehicleCertificationAuditDetailVO toAuditDetail(VehicleCertification certification) {
        // 后台审核详情需要车辆品牌等档案字段，因此补查车辆；历史车辆不存在时返回空展示值。
        VehicleProfile vehicle = vehicleProfileMapper.findById(certification.getVehicleId());
        return new VehicleCertificationAuditDetailVO(
                certification.getId(), certification.getVehicleId(), certification.getUserId(),
                // 只有后台审核模型会解密车牌、VIN 和发动机号，用户侧响应不会进入此转换方法。
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
        // 新版产品协议使用 PASS/REJECT；数据库仍沿用 APPROVED/REJECTED，需在边界层转换。
        String status = switch (certification.getStatus()) {
            case "APPROVED" -> "PASS";
            case "REJECTED" -> "REJECT";
            default -> certification.getStatus();
        };
        return new VehicleAuthStatusResponse(certification.getId(), certification.getVehicleId(), status,
                certification.getRejectReason(), certification.getSubmittedAt(), certification.getReviewedAt());
    }

    /** 查询一条认证申请的附件，并裁剪成只包含类型和资源标识的响应对象。 */
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
        // 手工复制所有持久化字段，避免 Lombok/序列化复制隐藏字段遗漏或改变类型。
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
        // 一次车辆写操作会同时影响“我的车辆”、车辆详情和跨模块公开卡片三个读模型。
        redisTemplate.delete(LIST_CACHE_KEY.formatted(userId));
        redisTemplate.delete(DETAIL_CACHE_KEY.formatted(vehicleId));
        redisTemplate.delete(PUBLIC_CARD_CACHE_KEY.formatted(vehicleId));
    }

    /** 基于 Redis 计数器实现简单频率限制。 */
    private void checkRateLimit(String key, int limit, Duration ttl, String message) {
        // Redis INCR 原子递增；首次出现 key 时设置统计窗口的过期时间。
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1) {
            redisTemplate.expire(key, ttl);
        }
        if (count != null && count > limit) {
            // Redis 暂不可用并返回 null 时不阻断主业务；只有明确超过阈值才拒绝。
            throw new BusinessException(message);
        }
    }

    /** 写入车辆操作审计日志。 */
    private void insertAuditLog(Long vehicleId, Long userId, String operationType, Object before, Object after, String remark) {
        // 前后对象在进入 Mapper 前转换为 JSON 快照，后续对象变化不会影响历史审计内容。
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
            // null 表示操作前或操作后确实不存在对象，例如 CREATE 的 before 和 REMOVE 的 after。
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            // 审计序列化属于辅助能力，失败时保留合法空 JSON，不能回滚核心车辆操作。
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
            // 版本升级后旧缓存可能无法反序列化；删除后由调用方自然回源数据库。
            redisTemplate.delete(key);
            return null;
        }
    }

    /** 写入 JSON 缓存；序列化失败时删除旧缓存，避免返回脏数据。 */
    private void writeJson(String key, Object value, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(value), ttl);
        } catch (JsonProcessingException ignored) {
            // 不写入半成品 JSON，同时删除可能存在的旧值，保证下一次请求回源获取新结构。
            redisTemplate.delete(key);
        }
    }

    /** 查询全平台是否已有相同车牌的通过认证记录。 */
    private VehicleCertification findApprovedCertification(String normalizedPlateNo) {
        // 新格式用于当前数据，legacyEncoded 仅用于升级期匹配历史记录。
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
        // 去除普通空格并统一大写，使“粤A12345”和“粤a 12345”得到相同检索密文。
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

    /** 规范化后台列表状态筛选；空串表示不按状态过滤。 */
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

    /** 校验并规范化后台审核结果，只接受可落库的终态。 */
    private String normalizeAuditResult(String auditResult) {
        String value = normalize(auditResult).toUpperCase();
        if (!List.of("APPROVED", "REJECTED").contains(value)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "审核结果仅支持 APPROVED 或 REJECTED");
        }
        return value;
    }

    /**
     * 规范化审核原因：通过时不保留无意义原因，驳回时必须提供 2~255 字符说明。
     */
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
            // 极短异常值没有足够字符可保留头尾，直接返回便于后台识别数据问题。
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
        // VIN 正常为 17 位：保留前三位和后四位，中间统一隐藏。
        String value = vin.trim();
        return value.substring(0, 3) + "********" + value.substring(value.length() - 4);
    }

    /** 发动机号脱敏展示。 */
    private String maskEngineNo(String engineNo) {
        if (!StringUtils.hasText(engineNo) || engineNo.trim().length() <= 4) {
            return normalize(engineNo);
        }
        // 发动机号仅保留末四位，满足用户核对需要且降低敏感信息暴露。
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
