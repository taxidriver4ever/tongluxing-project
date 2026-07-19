package com.tongluxing.chat.dto;

import jakarta.validation.constraints.NotNull;

public record ConversationSettingRequest(@NotNull Boolean muted, @NotNull Boolean pinned) {
}
