package com.tongdao.storage.controller;

import com.tongdao.common.result.Result;
import com.tongdao.storage.dto.ConfirmUploadRequest;
import com.tongdao.storage.dto.PresignUploadRequest;
import com.tongdao.storage.service.StorageService;
import com.tongdao.storage.vo.ConfirmUploadResponse;
import com.tongdao.storage.vo.DeleteFileResponse;
import com.tongdao.storage.vo.PresignDownloadResponse;
import com.tongdao.storage.vo.PresignUploadResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 统一文件存储接口。
 *
 * <p>业务模块不接收文件流，前端通过这里获取签名后直传 MinIO，再调用确认接口写入文件元数据。</p>
 */
@RestController
@RequiredArgsConstructor
public class StorageController {
    private final StorageService storageService;

    /**
     * 获取上传预签名 URL。
     */
    @PostMapping("/v1/storage/presign-upload")
    public Result<PresignUploadResponse> presignUpload(@Valid @RequestBody PresignUploadRequest request) {
        return Result.success(storageService.presignUpload(request));
    }

    /**
     * 确认前端已完成直传，并写入 file_storage。
     */
    @PostMapping("/v1/storage/confirm-upload")
    public Result<ConfirmUploadResponse> confirmUpload(@Valid @RequestBody ConfirmUploadRequest request) {
        return Result.success(storageService.confirmUpload(request));
    }

    /**
     * 获取短期有效下载 URL。支持 fileId 或 bucket + objectKey 两种查询方式。
     */
    @GetMapping("/v1/storage/presign-download")
    public Result<PresignDownloadResponse> presignDownload(@RequestParam(required = false) Long fileId,
                                                           @RequestParam(required = false) String bucket,
                                                           @RequestParam(required = false) String objectKey) {
        return Result.success(storageService.presignDownload(fileId, bucket, objectKey));
    }

    /**
     * 逻辑删除文件记录。
     */
    @DeleteMapping("/v1/storage/files/{fileId}")
    public Result<DeleteFileResponse> deleteFile(@PathVariable Long fileId) {
        return Result.success(storageService.deleteFile(fileId));
    }
}
