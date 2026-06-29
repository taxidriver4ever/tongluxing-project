package com.tongdao.storage.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * MinIO 存储配置。
 *
 * <p>敏感信息通过 .env 注入，代码和默认配置中不保存真实密钥。</p>
 */
@ConfigurationProperties(prefix = "storage.minio")
public record MinioStorageProperties(
        String endpoint,
        String publicEndpoint,
        String accessKey,
        String secretKey,
        String bucket,
        Integer uploadExpireSeconds,
        Integer downloadExpireSeconds
) {
    /**
     * 上传预签名 URL 默认有效期。
     */
    public int uploadExpire() {
        return uploadExpireSeconds == null ? 600 : uploadExpireSeconds;
    }

    /**
     * 下载预签名 URL 默认有效期。
     */
    public int downloadExpire() {
        return downloadExpireSeconds == null ? 600 : downloadExpireSeconds;
    }
}
