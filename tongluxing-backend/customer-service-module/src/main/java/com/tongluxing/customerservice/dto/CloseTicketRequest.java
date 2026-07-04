package com.tongluxing.customerservice.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 关闭客服工单请求。
 */
public record CloseTicketRequest(@NotNull Long operatorId, @Size(max = 255) String remark) {
}
