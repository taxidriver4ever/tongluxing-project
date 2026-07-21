package com.tongluxing.chat.group;
import java.util.Map;
import jakarta.validation.constraints.*;
public record ChatGroupItemRequest(
 @NotBlank @Pattern(regexp="ANNOUNCEMENT|POLL|REMINDER|TRIP_CONFIRM") String itemType,
 @NotBlank @Size(max=120) String title,@Size(max=2000) String content,Map<String,Object> payload){}
