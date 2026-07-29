package com.tongluxing.admin.sos;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
/**
 * 封装SOS 紧急事件结案请求参数。
 * 字段上的 Jakarta Validation 约束在进入业务层前完成格式和取值范围校验。
 */
public record ResolveSosEventRequest(@NotBlank @Size(max=500) String resolutionNote) {}
