package com.tongluxing.match.vo;

/**
 * 带行程上下文的咨询请求结果。
 *
 * @param tripId 被咨询行程 ID
 * @param tripTitle 冗余展示标题
 * @param senderUserId 咨询发起人 ID
 * @param receiverUserId 行程发起人/队长 ID
 * @param status PENDING 表示等待处理；DIRECT_ALLOWED 表示已有直接沟通权限
 */
public record TripConsultationResponse(
        String tripId, String tripTitle, String senderUserId, String receiverUserId, String status
) {
}
