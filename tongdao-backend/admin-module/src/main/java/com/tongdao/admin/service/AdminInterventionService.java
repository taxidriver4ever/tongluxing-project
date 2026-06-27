package com.tongdao.admin.service;

import com.tongdao.admin.dto.AdminInterventionRequest;
import com.tongdao.admin.vo.AdminInterventionResultVO;

/**
 * 运营后台人工干预服务。
 */
public interface AdminInterventionService {

    /** 对拼团活动执行人工干预。 */
    AdminInterventionResultVO interveneGroupbuy(Long activityId, AdminInterventionRequest request);
}
