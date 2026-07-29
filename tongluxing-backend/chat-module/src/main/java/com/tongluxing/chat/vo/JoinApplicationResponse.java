package com.tongluxing.chat.vo;

/**
 * 加入申请对外返回视图。
 * 该模型只承载客户端需要的数据，避免直接暴露数据库实体及内部实现字段。
 */
public record JoinApplicationResponse(
        String applicationId, String conversationId, String conversationName,
        String applicantUserId, String nickname, String avatarImageKey,
        String applicationMessage, String status, String createdAt,
        boolean following, boolean followedByTarget, boolean mutual) {
}
