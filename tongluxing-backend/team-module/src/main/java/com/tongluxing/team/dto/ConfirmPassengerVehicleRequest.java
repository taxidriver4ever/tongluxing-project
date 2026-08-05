package com.tongluxing.team.dto;

import jakarta.validation.constraints.NotNull;

/** 被关联车主确认或拒绝同车乘客。 */
public record ConfirmPassengerVehicleRequest(@NotNull Boolean approved) {
}
