package com.tongluxing.admin.service;

import com.tongluxing.admin.dto.AdminInterventionRequest;
import com.tongluxing.admin.vo.AdminInterventionResultVO;

/**
 * 运营后台人工干预服务。
 */
public interface AdminInterventionService {

    /** 对拼团活动执行人工干预。 */
    AdminInterventionResultVO interveneGroupbuy(Long activityId, AdminInterventionRequest request);
}
