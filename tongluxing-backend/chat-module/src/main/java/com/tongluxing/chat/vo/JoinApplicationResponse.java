package com.tongluxing.chat.vo;

/**
 * 加入申请对外返回视图。
 * 该模型只承载客户端需要的数据，避免直接暴露数据库实体及内部实现字段。
 *
 * @param applicationId 申请主键
 * @param conversationId 目标会话 ID
 * @param conversationName 目标群聊名称
 * @param applicantUserId 申请人用户 ID
 * @param nickname 申请人的公开昵称
 * @param avatarImageKey 申请人的头像对象 Key
 * @param applicationMessage 申请说明
 * @param status 当前审核状态
 * @param createdAt 申请创建时间
 * @param following 当前用户是否关注申请人
 * @param followedByTarget 申请人是否关注当前用户
 * @param mutual 双方是否互相关注
 */
public record JoinApplicationResponse(
        String applicationId, String conversationId, String conversationName,
        String applicantUserId, String nickname, String avatarImageKey,
        String applicationMessage, String status, String createdAt,
        boolean following, boolean followedByTarget, boolean mutual) {
}
