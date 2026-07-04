package com.tongluxing.storage.vo;

/**
 * 确认上传响应。
 */
public record ConfirmUploadResponse(Long fileId, String bucket, String objectKey, String uploadStatus) {
}
