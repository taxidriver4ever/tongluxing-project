package com.tongluxing.customerservice.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 分配客服工单请求。
 *
 * @param operatorId 处理该工单的运营人员 ID
 */
public record AssignTicketRequest(
        @NotNull Long operatorId
) {
}
