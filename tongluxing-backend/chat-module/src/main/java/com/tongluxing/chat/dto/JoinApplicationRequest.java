package com.tongluxing.chat.dto;

import jakarta.validation.constraints.Size;

public record JoinApplicationRequest(@Size(max = 120) String message) {
}
