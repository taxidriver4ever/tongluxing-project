package com.tongluxing.customerservice.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 关闭客服工单请求。
 *
 * @param operatorId 关闭工单的运营人员 ID
 * @param remark 关闭备注；当前接口暂未落库，预留给后续审计明细
 */
public record CloseTicketRequest(@NotNull Long operatorId, @Size(max = 255) String remark) {
}
