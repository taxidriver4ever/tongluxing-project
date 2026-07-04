package com.tongluxing.customerservice.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 创建客服工单请求。
 */
public record CreateTicketRequest(
        @NotBlank @Size(max = 64) String scene,
        @Size(max = 64) String targetType,
        @Size(max = 64) String targetId,
        @NotBlank @Size(max = 128) String title,
        @NotBlank @Size(max = 2048) String content,
        List<@Size(max = 512) String> imageKeys,
        @NotBlank @Size(max = 128) String requestId) {
}
