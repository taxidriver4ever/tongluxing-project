package com.tongluxing.p0.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** 队长对脱队/失联异常执行提醒、忽略或移除成员。 */
public record TeamAlertActionRequest(
        @NotBlank @Pattern(regexp = "REMIND|IGNORE|REMOVE") String action
) {
}
