package com.tongluxing.match.vo;

/** 带行程上下文的咨询请求结果。 */
public record TripConsultationResponse(
        String tripId, String tripTitle, String senderUserId, String receiverUserId, String status
) {
}
