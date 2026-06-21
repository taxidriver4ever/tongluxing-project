package com.tongdao.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SendMessageRequest(
        @NotBlank @Size(max = 20) String messageType,
        @NotBlank @Size(max = 1000) String content
) {
}
