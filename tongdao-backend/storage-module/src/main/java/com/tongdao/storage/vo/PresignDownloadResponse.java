package com.tongdao.storage.vo;

/**
 * 临时下载 URL 响应。
 */
public record PresignDownloadResponse(Long fileId, String bucket, String objectKey, String downloadUrl,
                                      int expireSeconds) {
}
