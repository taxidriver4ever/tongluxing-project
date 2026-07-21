package com.tongluxing.chat.group;
import java.util.Map;
import jakarta.validation.constraints.*;
public record ChatReportRequest(
 @NotBlank @Pattern(regexp="CONVERSATION|MEMBER|MESSAGE") String targetType,
 @NotBlank @Size(max=64) String targetId,@NotBlank @Size(max=32) String reportType,
 @NotBlank @Size(max=500) String reason,Map<String,Object> evidence){}
