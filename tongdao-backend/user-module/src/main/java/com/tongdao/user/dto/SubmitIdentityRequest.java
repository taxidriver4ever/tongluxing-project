package com.tongdao.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SubmitIdentityRequest(
        @NotBlank(message = "真实姓名不能为空")
        @Size(max = 64, message = "真实姓名不能超过64个字符")
        String realName,

        @NotBlank(message = "身份证号不能为空")
        @Pattern(regexp = "^[0-9A-Za-z]{15,18}$", message = "身份证号格式不正确")
        String idCardNo,

        @Size(max = 512, message = "认证图片地址不能超过512个字符")
        String faceImageUrl
) {
}
