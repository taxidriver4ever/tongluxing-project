package com.tongdao.chat.dto;

import jakarta.validation.constraints.NotNull;

public record AddConversationMemberRequest(
        @NotNull Long userId
) {
}
