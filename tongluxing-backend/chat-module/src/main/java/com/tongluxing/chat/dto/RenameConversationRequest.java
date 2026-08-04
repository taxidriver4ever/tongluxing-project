package com.tongluxing.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 修改群聊资料请求。
 *
 * @param name 新群名称。群头像后续也可以在该请求中增加字段，不需要另开接口。
 */
public record RenameConversationRequest(
        @NotBlank @Size(min = 2, max = 64) String name
) {
}
