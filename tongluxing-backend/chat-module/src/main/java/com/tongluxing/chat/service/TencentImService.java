package com.tongluxing.chat.service;

import com.tongluxing.chat.vo.ImUserSigResponse;

/**
 * 腾讯云 IM 集成服务。
 *
 * <p>只封装 IM 侧 UserSig、群组创建/销毁和成员维护能力，业务会话数据仍以本地数据库为准。</p>
 */
public interface TencentImService {

    /** 当前选择的聊天通道：MOCK 或 TENCENT_IM。 */
    String providerType();

    /** 腾讯云 IM 必要配置是否完整。 */
    boolean isConfigured();

    /** 为指定 IM 用户 ID 生成 UserSig。 */
    String generateUserSig(String userId);

    /** 为当前登录用户生成 IM 登录票据。 */
    ImUserSigResponse generateCurrentUserSig();

    /** 在腾讯云 IM 创建群组。 */
    void createGroup(String groupId, String ownerUserId, String groupName);

    /** 修改腾讯云 IM 群名称。 */
    void updateGroupName(String groupId, String groupName);

    /** 销毁腾讯云 IM 群组。 */
    void destroyGroup(String groupId);

    /** 添加腾讯云 IM 群成员。 */
    void addGroupMember(String groupId, String userId);

    /** 删除腾讯云 IM 群成员。 */
    void removeGroupMember(String groupId, String userId);

    /** 以群成员身份向腾讯 IM 群发送文本消息。 */
    String sendGroupText(String groupId, String senderUserId, String content);

    /**
     * 向腾讯 IM 群发送自定义 JSON 消息。
     *
     * <p>图片、文件和业务卡片只传 mediaId/fileId 或业务标识，不把 MinIO 临时 URL 写入消息。</p>
     */
    String sendGroupCustom(String groupId, String senderUserId, String data, String description, String extension);

    /** 以业务用户身份发送腾讯 IM 单聊文本消息。 */
    String sendC2CText(String receiverUserId, String senderUserId, String content);
}
