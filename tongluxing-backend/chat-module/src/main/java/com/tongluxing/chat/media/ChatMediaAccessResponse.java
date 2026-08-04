package com.tongluxing.chat.media;

/**
 * 聊天媒体短期访问结果。
 *
 * <p>只向 App 返回展示图片所需的临时 URL 和有效期，不返回 MinIO bucket、objectKey，
 * 防止聊天协议和普通下载接口暴露内部存储目录。</p>
 */
public record ChatMediaAccessResponse(
        Long fileId,
        String downloadUrl,
        Integer expireSeconds) {
}
