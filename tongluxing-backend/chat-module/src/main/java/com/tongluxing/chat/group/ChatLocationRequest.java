package com.tongluxing.chat.group;
import java.math.BigDecimal;
import jakarta.validation.constraints.*;
public record ChatLocationRequest(
 @NotNull @DecimalMin("-90") @DecimalMax("90") BigDecimal latitude,
 @NotNull @DecimalMin("-180") @DecimalMax("180") BigDecimal longitude,
 @DecimalMin("0") @DecimalMax("400") BigDecimal speed,boolean sharing){}
