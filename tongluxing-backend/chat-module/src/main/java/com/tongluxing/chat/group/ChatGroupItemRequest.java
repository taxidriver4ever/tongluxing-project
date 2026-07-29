package com.tongluxing.chat.group;
import java.util.Map;
import jakarta.validation.constraints.*;
/**
 * 封装群聊协作事项请求参数。
 * 字段上的 Jakarta Validation 约束在进入业务层前完成格式和取值范围校验。
 */
public record ChatGroupItemRequest(
 @NotBlank @Pattern(regexp="ANNOUNCEMENT|POLL|REMINDER|TRIP_CONFIRM") String itemType,
 @NotBlank @Size(max=120) String title,@Size(max=2000) String content,Map<String,Object> payload){}
