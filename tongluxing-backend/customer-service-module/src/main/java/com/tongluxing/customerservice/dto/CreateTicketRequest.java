package com.tongluxing.customerservice.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 创建客服工单请求。
 *
 * @param scene 工单场景，例如 REFUND、ORDER_DETAIL、VERIFICATION、COMPLAINT
 * @param targetType 关联对象类型，例如 ORDER、GROUPBUY、VERIFICATION；可为空
 * @param targetId 关联对象 ID；可为空
 * @param title 工单标题
 * @param content 用户提交的问题内容
 * @param imageKeys 图片附件对象存储 Key 列表
 * @param requestId 幂等请求号，避免重复提交创建多张工单
 */
public record CreateTicketRequest(
        @NotBlank @Size(max = 64) String scene,
        @Size(max = 64) String targetType,
        @Size(max = 64) String targetId,
        @NotBlank @Size(max = 128) String title,
        @NotBlank @Size(max = 2048) String content,
        List<@Size(max = 512) String> imageKeys,
        @NotBlank @Size(max = 128) String requestId) {
}
