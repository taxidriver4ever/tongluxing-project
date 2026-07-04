package com.tongluxing.storage.service;

import com.tongluxing.storage.dto.ConfirmUploadRequest;
import com.tongluxing.storage.dto.PresignUploadRequest;
import com.tongluxing.storage.vo.ConfirmUploadResponse;
import com.tongluxing.storage.vo.DeleteFileResponse;
import com.tongluxing.storage.vo.PresignDownloadResponse;
import com.tongluxing.storage.vo.PresignUploadResponse;

/**
 * 统一文件存储服务。
 */
public interface StorageService {

    /**
     * 获取前端直传 MinIO 的预签名上传 URL。
     */
    PresignUploadResponse presignUpload(PresignUploadRequest request);

    /**
     * 确认前端已完成直传，并写入 file_storage。
     */
    ConfirmUploadResponse confirmUpload(ConfirmUploadRequest request);

    /**
     * 获取短期有效下载 URL。
     */
    PresignDownloadResponse presignDownload(Long fileId, String bucket, String objectKey);

    /**
     * 逻辑删除文件记录。
     */
    DeleteFileResponse deleteFile(Long fileId);
}
