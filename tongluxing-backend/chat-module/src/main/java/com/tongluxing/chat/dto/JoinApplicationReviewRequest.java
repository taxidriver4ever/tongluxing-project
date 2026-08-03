package com.tongluxing.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 封装加入申请审核请求参数。
 * 字段上的 Jakarta Validation 约束在进入业务层前完成格式和取值范围校验。
 *
 * @param decision 审核结论，只允许 APPROVED 或 REJECTED
 */
public record JoinApplicationReviewRequest(
        @NotBlank @Pattern(regexp = "APPROVED|REJECTED") String decision) {
}
