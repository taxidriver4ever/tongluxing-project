package com.tongluxing.p0.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** 自动出发发现异常成员后，队长选择等待或忽略异常继续出发。 */
public record DepartureExceptionActionRequest(
        @NotBlank @Pattern(regexp = "WAIT|CONTINUE") String action
) {
}
