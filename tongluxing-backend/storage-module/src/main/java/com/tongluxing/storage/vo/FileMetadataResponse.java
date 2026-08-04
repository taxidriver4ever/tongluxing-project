package com.tongluxing.storage.vo;

/**
 * 文件业务元数据。
 *
 * <p>该对象不包含 bucket、objectKey 或预签名 URL，供聊天等业务模块执行归属、
 * 上传者和状态校验，避免把 MinIO 内部路径泄露给业务客户端。</p>
 */
public record FileMetadataResponse(
        Long fileId,
        String bizType,
        String bizId,
        Long userId,
        String contentType,
        Long fileSize,
        String uploadStatus) {
}
