package com.tongdao.admin.service.impl;

import java.time.Duration;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tongdao.admin.dto.AdminConfigUpdateRequest;
import com.tongdao.admin.entity.AdminAuditLog;
import com.tongdao.admin.entity.AdminOperationConfig;
import com.tongdao.admin.entity.AdminOperationConfigVersion;
import com.tongdao.admin.mapper.AdminAuditLogMapper;
import com.tongdao.admin.mapper.AdminOperationConfigMapper;
import com.tongdao.admin.mapper.AdminOperationConfigVersionMapper;
import com.tongdao.admin.service.AdminConfigService;
import com.tongdao.admin.vo.AdminConfigVO;
import com.tongdao.common.exception.BusinessException;
import com.tongdao.common.result.ResultCode;
import com.tongdao.common.utils.SnowflakeIdGenerator;

import lombok.RequiredArgsConstructor;

/**
 * 运营配置服务实现。
 *
 * <p>配置采用主表 + 版本表方式保存：主表记录当前版本，版本表保留每次变更内容。</p>
 */
@Service
@RequiredArgsConstructor
public class AdminConfigServiceImpl implements AdminConfigService {

    /** 配置主表 Mapper。 */
    private final AdminOperationConfigMapper configMapper;
    /** 配置版本表 Mapper。 */
    private final AdminOperationConfigVersionMapper versionMapper;
    /** 审计日志 Mapper，用于 requestId 幂等查询。 */
    private final AdminAuditLogMapper auditLogMapper;
    /** 后台通用支撑组件。 */
    private final AdminSupport support;

    /** 查询当前生效配置，优先读取 Redis 缓存。 */
    @Override
    public AdminConfigVO getConfig(String configDomain, String configKey) {
        String domain = normalizeDomain(configDomain);
        String key = normalizeKey(configKey);
        String redisKey = "admin:config:%s:%s".formatted(domain, key);
        AdminConfigVO cached = support.readJson(redisKey, AdminConfigVO.class);
        if (cached != null) {
            return cached;
        }
        AdminOperationConfig config = configMapper.findByKey(domain, key);
        if (config == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "运营配置不存在");
        }
        AdminOperationConfigVersion version = versionMapper.findByConfigAndVersion(config.getId(),
                config.getCurrentVersion());
        AdminConfigVO result = toVO(config, version);
        support.writeJson(redisKey, result, Duration.ofMinutes(10));
        return result;
    }

    /** 更新配置并写入新版本；同一 requestId 重复提交时保持幂等。 */
    @Override
    @Transactional
    public AdminConfigVO updateConfig(String configDomain, AdminConfigUpdateRequest request) {
        String domain = normalizeDomain(configDomain);
        String key = normalizeKey(request.configKey());
        String idemKey = "admin:idem:config:%s".formatted(request.requestId());
        AdminConfigVO cached = support.readJson(idemKey, AdminConfigVO.class);
        if (cached != null) {
            return cached;
        }
        // Redis 幂等缓存失效时，仍通过审计日志的 requestId 判断是否已经处理过。
        AdminAuditLog existedLog = auditLogMapper.findByRequestId(request.requestId());
        if (existedLog != null) {
            return getConfig(domain, key);
        }

        LocalDateTime now = LocalDateTime.now();
        AdminOperationConfig config = configMapper.findByKey(domain, key);
        if (config == null) {
            // 首次更新某个配置键时创建主配置记录，当前版本从 0 开始递增。
            config = new AdminOperationConfig();
            config.setId(SnowflakeIdGenerator.nextId());
            config.setConfigDomain(domain);
            config.setConfigKey(key);
            config.setCurrentVersion(0);
            config.setConfigStatus("ACTIVE");
            config.setEffectiveAt(request.effectiveAt() == null ? now : request.effectiveAt());
            config.setCreatedAt(now);
            config.setUpdatedAt(now);
            config.setDeleted(0);
            configMapper.insert(config);
        }

        int nextVersion = config.getCurrentVersion() + 1;
        AdminOperationConfigVersion version = new AdminOperationConfigVersion();
        version.setId(SnowflakeIdGenerator.nextId());
        version.setConfigId(config.getId());
        version.setConfigDomain(domain);
        version.setConfigKey(key);
        version.setVersionNo(nextVersion);
        version.setConfigValue(request.configValue());
        version.setEffectiveAt(request.effectiveAt() == null ? now : request.effectiveAt());
        version.setOperatorId(request.operatorId());
        version.setChangeReason(request.changeReason());
        version.setCreatedAt(now);
        versionMapper.insert(version);

        config.setCurrentVersion(nextVersion);
        config.setConfigStatus("ACTIVE");
        config.setEffectiveAt(version.getEffectiveAt());
        config.setUpdatedAt(now);
        configMapper.updateVersion(config);

        AdminConfigVO result = toVO(config, version);
        // 记录配置变更审计，并清理旧配置缓存。
        support.audit("CONFIG_UPDATE", "admin-module", "ADMIN_OPERATION_CONFIG", String.valueOf(config.getId()),
                request.requestId(), request.operatorId(), request.changeReason(), AdminSupport.SUCCESS,
                null, request.configValue());
        support.deleteRedis("admin:config:%s:%s".formatted(domain, key));
        support.writeJson(idemKey, result, Duration.ofHours(24));
        return result;
    }

    /** 校验并标准化配置域。 */
    private String normalizeDomain(String configDomain) {
        String domain = support.normalize(configDomain);
        if (!"GROWTH".equals(domain) && !"INVITE".equals(domain) && !"COUPON".equals(domain)
                && !"SYSTEM".equals(domain)) {
            throw new BusinessException("配置域仅支持 GROWTH、INVITE、COUPON、SYSTEM");
        }
        return domain;
    }

    /** 校验并标准化配置键。 */
    private String normalizeKey(String configKey) {
        if (configKey == null || configKey.isBlank()) {
            throw new BusinessException("配置键不能为空");
        }
        return configKey.trim();
    }

    /** 组合配置主表和版本表数据为前端响应。 */
    private AdminConfigVO toVO(AdminOperationConfig config, AdminOperationConfigVersion version) {
        return new AdminConfigVO(config.getId(), config.getConfigDomain(), config.getConfigKey(),
                config.getCurrentVersion(), version == null ? null : version.getConfigValue(),
                config.getConfigStatus(), config.getEffectiveAt(), config.getUpdatedAt());
    }
}
