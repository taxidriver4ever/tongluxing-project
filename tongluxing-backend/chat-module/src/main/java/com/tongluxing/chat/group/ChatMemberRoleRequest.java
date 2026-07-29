package com.tongluxing.chat.group;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
/**
 * 封装群聊成员角色请求参数。
 * 字段上的 Jakarta Validation 约束在进入业务层前完成格式和取值范围校验。
 */
public record ChatMemberRoleRequest(@NotBlank @Pattern(regexp="ADMIN|MEMBER|NAVIGATOR") String role) {}
