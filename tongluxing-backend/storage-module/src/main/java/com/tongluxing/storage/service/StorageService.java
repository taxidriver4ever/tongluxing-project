package com.tongluxing.storage.service;

import com.tongluxing.storage.dto.ConfirmUploadRequest;
import com.tongluxing.storage.dto.PresignUploadRequest;
import com.tongluxing.storage.vo.ConfirmUploadResponse;
import com.tongluxing.storage.vo.DeleteFileResponse;
import com.tongluxing.storage.vo.FileMetadataResponse;
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
     * 读取不含 MinIO 内部路径的业务元数据，供上层模块执行访问权限校验。
     */
    FileMetadataResponse getMetadata(Long fileId);

    /**
     * 获取短期有效下载 URL。调用方需要先完成对应业务的访问权限校验。
     */
    PresignDownloadResponse presignDownload(Long fileId, String bucket, String objectKey);

    /**
     * 逻辑删除文件记录。
     */
    DeleteFileResponse deleteFile(Long fileId);
}
