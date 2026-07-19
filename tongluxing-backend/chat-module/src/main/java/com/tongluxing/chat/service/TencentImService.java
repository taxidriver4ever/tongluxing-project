package com.tongluxing.chat.service;

import com.tongluxing.chat.vo.ImUserSigResponse;

/**
 * 腾讯云 IM 集成服务。
 *
 * <p>只封装 IM 侧 UserSig、群组创建/销毁和成员维护能力，业务会话数据仍以本地数据库为准。</p>
 */
public interface TencentImService {

    /** 腾讯云 IM 必要配置是否完整。 */
    boolean isConfigured();

    /** 为指定 IM 用户 ID 生成 UserSig。 */
    String generateUserSig(String userId);

    /** 为当前登录用户生成 IM 登录票据。 */
    ImUserSigResponse generateCurrentUserSig();

    /** 在腾讯云 IM 创建群组。 */
    void createGroup(String groupId, String ownerUserId, String groupName);

    /** 销毁腾讯云 IM 群组。 */
    void destroyGroup(String groupId);

    /** 添加腾讯云 IM 群成员。 */
    void addGroupMember(String groupId, String userId);

    /** 删除腾讯云 IM 群成员。 */
    void removeGroupMember(String groupId, String userId);

    /** 以群成员身份向腾讯 IM 群发送文本消息。 */
    String sendGroupText(String groupId, String senderUserId, String content);
}
