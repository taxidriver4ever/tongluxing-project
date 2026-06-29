package com.tongdao.storage.vo;

/**
 * 删除文件响应。
 */
public record DeleteFileResponse(Long fileId, boolean deleted) {
}
