package com.tongluxing.customerservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 分配客服工单请求。
 *
 * @param operatorId 处理该工单的运营人员 ID
 */
public record AssignTicketRequest(
        Long operatorId,
        @NotBlank @Size(max = 128) String requestId
) {
}
