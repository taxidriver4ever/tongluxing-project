package com.tongluxing.chat.group;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotBlank;
/**
 * 封装行程确认单响应请求参数。
 * 字段上的 Jakarta Validation 约束在进入业务层前完成格式和取值范围校验。
 */
public record TripConfirmationRespondRequest(
 @NotBlank @Pattern(regexp="CONFIRMED|REJECTED") String status,
 @Size(max=300) String reason) {}
