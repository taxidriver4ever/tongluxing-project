package com.tongluxing.storage.vo;

/**
 * 删除文件响应。
 */
public record DeleteFileResponse(Long fileId, boolean deleted) {
}
