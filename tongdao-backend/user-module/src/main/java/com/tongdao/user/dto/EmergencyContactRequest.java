package com.tongdao.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record EmergencyContactRequest(
        @NotBlank(message = "联系人姓名不能为空")
        @Size(max = 64, message = "联系人姓名不能超过64个字符")
        String contactName,

        @Size(max = 32, message = "关系不能超过32个字符")
        String relation,

        @NotBlank(message = "联系人手机号不能为空")
        @Pattern(regexp = "^1[3-9]\\d{9}$", message = "联系人手机号格式不正确")
        String phone,

        Boolean isDefault
) {
}
