package com.tongdao.storage.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 获取上传预签名 URL 请求。
 */
public record PresignUploadRequest(
        @NotBlank @Size(max = 255) String fileName,
        @NotBlank @Size(max = 128) String contentType,
        @NotNull @Positive Long fileSize,
        @NotBlank @Size(max = 64) String bizType,
        @Size(max = 128) String bizId
) {
}
