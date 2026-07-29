package com.tongluxing.chat.group;
import java.util.Map;
import jakarta.validation.constraints.*;
/**
 * 封装聊天举报请求参数。
 * 字段上的 Jakarta Validation 约束在进入业务层前完成格式和取值范围校验。
 */
public record ChatReportRequest(
 @NotBlank @Pattern(regexp="CONVERSATION|MEMBER|MESSAGE") String targetType,
 @NotBlank @Size(max=64) String targetId,@NotBlank @Size(max=32) String reportType,
 @NotBlank @Size(max=500) String reason,Map<String,Object> evidence){}
