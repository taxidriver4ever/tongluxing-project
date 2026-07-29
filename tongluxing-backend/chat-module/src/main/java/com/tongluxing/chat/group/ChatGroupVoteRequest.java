package com.tongluxing.chat.group;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
/**
 * 封装群聊投票请求参数。
 * 字段上的 Jakarta Validation 约束在进入业务层前完成格式和取值范围校验。
 */
public record ChatGroupVoteRequest(@NotBlank @Size(max=64) String optionKey){}
