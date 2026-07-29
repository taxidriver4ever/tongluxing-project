package com.tongluxing.chat.group;
import java.math.BigDecimal;
import jakarta.validation.constraints.*;
/**
 * 封装聊天位置共享请求参数。
 * 字段上的 Jakarta Validation 约束在进入业务层前完成格式和取值范围校验。
 */
public record ChatLocationRequest(
 @NotNull @DecimalMin("-90") @DecimalMax("90") BigDecimal latitude,
 @NotNull @DecimalMin("-180") @DecimalMax("180") BigDecimal longitude,
 @DecimalMin("0") @DecimalMax("400") BigDecimal speed,boolean sharing){}
