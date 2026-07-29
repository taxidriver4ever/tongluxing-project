package com.tongluxing.admin.sos;
import java.math.BigDecimal;
import jakarta.validation.constraints.*;
/**
 * 封装SOS 紧急事件创建请求参数。
 * 字段上的 Jakarta Validation 约束在进入业务层前完成格式和取值范围校验。
 */
public record CreateSosEventRequest(
        @NotBlank @Size(max=128) String requestId,
        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal latitude,
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal longitude,
        @DecimalMin("0.0") @DecimalMax("10000.0") BigDecimal locationAccuracyMeters,
        @NotBlank @Size(max=255) String address,
        @Size(max=500) String message) {}
