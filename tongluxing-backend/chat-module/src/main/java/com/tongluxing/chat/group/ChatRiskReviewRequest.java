package com.tongluxing.chat.group;
import jakarta.validation.constraints.*;
public record ChatRiskReviewRequest(@NotBlank @Pattern(regexp="RESOLVED|REJECTED|FLAGGED") String decision,
 @NotBlank @Size(max=500) String note){}
