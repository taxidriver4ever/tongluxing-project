package com.tongluxing.storage.entity;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 统一文件元数据实体。
 *
 * <p>对应 file_storage 表，只保存 bucket、objectKey 和业务元数据，不保存访问 URL。</p>
 */
@Data
public class FileStorage {
    private Long id;
    private String bucket;
    private String objectKey;
    private String originalFileName;
    private String contentType;
    private Long fileSize;
    private String bizType;
    private String bizId;
    private Long userId;
    private String storageType;
    private String uploadStatus;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;
}
