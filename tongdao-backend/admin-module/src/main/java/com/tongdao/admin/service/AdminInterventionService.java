package com.tongdao.admin.service;

import com.tongdao.admin.dto.AdminInterventionRequest;
import com.tongdao.admin.vo.AdminInterventionResultVO;

public interface AdminInterventionService {

    AdminInterventionResultVO interveneGroupbuy(Long activityId, AdminInterventionRequest request);
}
