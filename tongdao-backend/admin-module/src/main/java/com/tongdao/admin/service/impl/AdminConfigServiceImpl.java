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

@Service
@RequiredArgsConstructor
public class AdminConfigServiceImpl implements AdminConfigService {
    private final AdminOperationConfigMapper configMapper;
    private final AdminOperationConfigVersionMapper versionMapper;
    private final AdminAuditLogMapper auditLogMapper;
    private final AdminSupport support;

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
        AdminAuditLog existedLog = auditLogMapper.findByRequestId(request.requestId());
        if (existedLog != null) {
            return getConfig(domain, key);
        }

        LocalDateTime now = LocalDateTime.now();
        AdminOperationConfig config = configMapper.findByKey(domain, key);
        if (config == null) {
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
        support.audit("CONFIG_UPDATE", "admin-module", "ADMIN_OPERATION_CONFIG", String.valueOf(config.getId()),
                request.requestId(), request.operatorId(), request.changeReason(), AdminSupport.SUCCESS,
                null, request.configValue());
        support.deleteRedis("admin:config:%s:%s".formatted(domain, key));
        support.writeJson(idemKey, result, Duration.ofHours(24));
        return result;
    }

    private String normalizeDomain(String configDomain) {
        String domain = support.normalize(configDomain);
        if (!"GROWTH".equals(domain) && !"INVITE".equals(domain) && !"COUPON".equals(domain)
                && !"SYSTEM".equals(domain)) {
            throw new BusinessException("配置域仅支持 GROWTH、INVITE、COUPON、SYSTEM");
        }
        return domain;
    }

    private String normalizeKey(String configKey) {
        if (configKey == null || configKey.isBlank()) {
            throw new BusinessException("配置键不能为空");
        }
        return configKey.trim();
    }

    private AdminConfigVO toVO(AdminOperationConfig config, AdminOperationConfigVersion version) {
        return new AdminConfigVO(config.getId(), config.getConfigDomain(), config.getConfigKey(),
                config.getCurrentVersion(), version == null ? null : version.getConfigValue(),
                config.getConfigStatus(), config.getEffectiveAt(), config.getUpdatedAt());
    }
}
