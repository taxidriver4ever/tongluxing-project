package com.tongluxing.chat.group;
import jakarta.validation.constraints.*;
/**
 * 封装聊天风控审核请求参数。
 * 字段上的 Jakarta Validation 约束在进入业务层前完成格式和取值范围校验。
 */
public record ChatRiskReviewRequest(@NotBlank @Pattern(regexp="RESOLVED|REJECTED|FLAGGED") String decision,
 @NotBlank @Size(max=500) String note){}
