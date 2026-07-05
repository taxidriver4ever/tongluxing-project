package com.tongluxing.customerservice.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 分配客服工单请求。
 */
public record AssignTicketRequest(
        @NotNull Long operatorId
) {
}
