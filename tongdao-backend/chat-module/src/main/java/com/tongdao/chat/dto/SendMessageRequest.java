package com.tongdao.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 发送消息请求。
 */
public record SendMessageRequest(
        /** 消息类型，例如 TEXT、IMAGE。 */
        @NotBlank @Size(max = 20) String messageType,
        /** 消息正文内容。 */
        @NotBlank @Size(max = 1000) String content
) {
}
