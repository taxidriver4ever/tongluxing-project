package com.tongluxing.chat.dto;

import jakarta.validation.constraints.Size;

/**
 * 封装加入申请请求参数。
 * 字段上的 Jakarta Validation 约束在进入业务层前完成格式和取值范围校验。
 *
 * @param message 申请说明，允许为空，最长 120 个字符
 */
public record JoinApplicationRequest(@Size(max = 120) String message) {
}
