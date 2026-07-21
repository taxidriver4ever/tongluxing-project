package com.tongluxing.chat.group;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
public record ChatMemberRoleRequest(@NotBlank @Pattern(regexp="ADMIN|MEMBER|NAVIGATOR") String role) {}
