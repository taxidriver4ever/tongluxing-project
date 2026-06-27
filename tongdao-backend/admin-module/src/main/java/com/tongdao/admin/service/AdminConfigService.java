package com.tongdao.admin.service;

import com.tongdao.admin.dto.AdminConfigUpdateRequest;
import com.tongdao.admin.vo.AdminConfigVO;

/**
 * 运营配置服务。
 *
 * <p>负责配置读取、版本更新、幂等控制和操作审计。</p>
 */
public interface AdminConfigService {

    /** 按配置域和配置键查询当前生效配置。 */
    AdminConfigVO getConfig(String configDomain, String configKey);

    /** 更新配置并生成新的配置版本。 */
    AdminConfigVO updateConfig(String configDomain, AdminConfigUpdateRequest request);
}
