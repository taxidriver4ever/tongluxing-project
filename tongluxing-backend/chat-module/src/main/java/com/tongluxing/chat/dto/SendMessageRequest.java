package com.tongluxing.chat.dto;

import java.util.Map;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 发送消息请求。
 */
public record SendMessageRequest(
        /** 消息类型，例如 TEXT、IMAGE。 */
        @NotBlank @Size(max = 20) String messageType,
        /** 消息正文内容。 */
        @Size(max = 1000) String content,
        /** 图片、文件、位置或业务卡片扩展数据。 */
        Map<String, Object> payload
) {
}
