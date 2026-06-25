package com.tongdao.admin.service;

import com.tongdao.admin.dto.AdminConfigUpdateRequest;
import com.tongdao.admin.vo.AdminConfigVO;

public interface AdminConfigService {

    AdminConfigVO getConfig(String configDomain, String configKey);

    AdminConfigVO updateConfig(String configDomain, AdminConfigUpdateRequest request);
}
