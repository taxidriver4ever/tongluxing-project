package com.tongluxing.customerservice.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 回复客服工单请求。
 */
public record ReplyTicketRequest(
        @NotNull Long operatorId,
        @NotBlank @Size(max = 2048) String content,
        List<@Size(max = 512) String> imageKeys) {
}
