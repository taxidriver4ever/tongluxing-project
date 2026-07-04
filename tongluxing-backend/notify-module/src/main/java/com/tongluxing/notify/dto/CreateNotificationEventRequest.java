package com.tongluxing.notify.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 内部业务事件创建通知请求。
 *
 * <p>业务模块通过 requestId 保证同一事件不会重复创建通知。</p>
 */
public record CreateNotificationEventRequest(
        @NotBlank @Size(max = 64) String eventType,
        @NotBlank @Size(max = 32) String receiverType,
        @NotNull Long receiverId,
        @Size(max = 64) String scene,
        @Size(max = 64) String targetType,
        @Size(max = 64) String targetId,
        @NotBlank @Size(max = 128) String title,
        @NotBlank @Size(max = 1024) String content,
        @NotBlank @Size(max = 128) String requestId) {
}
