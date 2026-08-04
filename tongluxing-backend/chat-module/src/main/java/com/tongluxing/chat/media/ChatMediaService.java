package com.tongluxing.chat.media;

import org.springframework.stereotype.Service;

import com.tongluxing.chat.entity.ChatConversationMember;
import com.tongluxing.chat.mapper.ChatConversationMemberMapper;
import com.tongluxing.common.exception.BusinessException;
import com.tongluxing.common.result.ResultCode;
import com.tongluxing.storage.service.StorageService;
import com.tongluxing.storage.vo.FileMetadataResponse;
import com.tongluxing.storage.vo.PresignDownloadResponse;
import com.tongluxing.user.support.CurrentUserContext;

import lombok.RequiredArgsConstructor;

/**
 * 聊天媒体访问服务。
 *
 * <p>图片二进制保存在 MinIO，腾讯 IM 自定义消息只携带 fileId/mediaId。
 * 客户端展示图片前必须通过本服务校验当前用户仍是对应会话成员，再获取短期 URL。</p>
 */
@Service
@RequiredArgsConstructor
public class ChatMediaService {

    private final StorageService storageService;
    private final ChatConversationMemberMapper memberMapper;
    private final CurrentUserContext currentUserContext;

    /**
     * 为当前群成员签发聊天媒体的短期访问 URL。
     */
    public ChatMediaAccessResponse accessUrl(Long fileId) {
        FileMetadataResponse file = storageService.getMetadata(fileId);
        if (!"CHAT_IMAGE".equals(file.bizType()) && !"CHAT_FILE".equals(file.bizType())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "该文件不是聊天媒体");
        }
        if (!"CONFIRMED".equals(file.uploadStatus()) || file.bizId() == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "聊天媒体尚未完成上传");
        }

        final Long conversationId;
        try {
            conversationId = Long.valueOf(file.bizId());
        } catch (NumberFormatException exception) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "聊天媒体业务绑定无效");
        }

        // 下载权限以本地业务成员关系为准，不能只因为知道 fileId 就获取 MinIO URL。
        Long userId = currentUserContext.requireUserId();
        ChatConversationMember member = memberMapper.findByConversationAndUser(conversationId, userId);
        if (member == null || !"ACTIVE".equals(member.getMemberStatus())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权访问该聊天媒体");
        }
        PresignDownloadResponse signed = storageService.presignDownload(fileId, null, null);
        // StorageService 的通用响应包含 bucket/objectKey；聊天接口只投影安全字段。
        return new ChatMediaAccessResponse(signed.fileId(), signed.downloadUrl(), signed.expireSeconds());
    }
}
