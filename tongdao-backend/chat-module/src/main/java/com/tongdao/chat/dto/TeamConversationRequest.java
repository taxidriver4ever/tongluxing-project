package com.tongdao.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TeamConversationRequest(
        @NotNull Long teamId,
        @NotNull Long ownerUserId,
        @NotBlank @Size(max = 64) String conversationName
) {
}
