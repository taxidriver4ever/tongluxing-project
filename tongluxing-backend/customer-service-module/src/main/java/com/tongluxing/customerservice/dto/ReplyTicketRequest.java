package com.tongluxing.customerservice.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 回复客服工单请求。
 *
 * @param operatorId 回复工单的运营人员 ID
 * @param content 回复内容
 * @param imageKeys 图片附件对象存储 Key 列表
 */
public record ReplyTicketRequest(
        Long operatorId,
        @NotBlank @Size(max = 2048) String content,
        List<@Size(max = 512) String> imageKeys,
        @NotBlank @Size(max = 128) String requestId) {
}
