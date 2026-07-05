package com.tongluxing.admin.service.impl;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tongluxing.admin.entity.AdminCompensationTask;
import com.tongluxing.admin.integration.AdminGroupbuyPort;
import com.tongluxing.admin.mapper.AdminCompensationTaskMapper;
import com.tongluxing.admin.service.AdminCompensationTaskService;

import lombok.RequiredArgsConstructor;

/**
 * 后台补偿任务消费实现。
 *
 * <p>该实现先消费不依赖第三方的本地任务，例如拼团状态干预。真实支付、分账等
 * 第三方任务仍由对应业务模块网关处理。</p>
 */
@Service
@RequiredArgsConstructor
public class AdminCompensationTaskServiceImpl implements AdminCompensationTaskService {

    private final AdminCompensationTaskMapper taskMapper;
    private final AdminGroupbuyPort groupbuyPort;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public int processDueTasks(int limit) {
        LocalDateTime now = LocalDateTime.now();
        int processed = 0;
        for (AdminCompensationTask task : taskMapper.listDueTasks(now, Math.min(Math.max(limit, 1), 100))) {
            try {
                if ("GROUPBUY_INTERVENE".equals(task.getBizType())) {
                    JsonNode payload = objectMapper.readTree(task.getRequestPayload());
                    Long activityId = payload.path("activityId").asLong(Long.parseLong(task.getBizId()));
                    groupbuyPort.applyIntervention(activityId, payload.path("action").asText(),
                            payload.path("reason").asText(), task.getIdempotentKey());
                }
                taskMapper.markSuccess(task.getId(), now);
                processed++;
            } catch (Exception ex) {
                taskMapper.markFailed(task.getId(), ex.getMessage(), now.plusMinutes(5), now);
            }
        }
        return processed;
    }
}
