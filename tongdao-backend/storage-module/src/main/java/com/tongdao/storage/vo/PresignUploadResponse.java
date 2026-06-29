package com.tongdao.storage.vo;

/**
 * 上传预签名 URL 响应。
 */
public record PresignUploadResponse(String uploadUrl, String bucket, String objectKey, int expireSeconds) {
}
