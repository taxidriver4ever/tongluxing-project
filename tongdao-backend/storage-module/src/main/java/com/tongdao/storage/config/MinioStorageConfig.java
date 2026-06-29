package com.tongdao.storage.config;

import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MinIO Client 配置。
 *
 * <p>内部 Client 使用服务端可访问地址，负责建桶、stat、删除；公开 Client 使用浏览器可访问地址，
 * 专门生成返回给前端的预签名 URL。</p>
 */
@Configuration
@EnableConfigurationProperties(MinioStorageProperties.class)
public class MinioStorageConfig {

    /**
     * 后端内部访问 MinIO 的 Client。
     */
    @Bean
    @Qualifier("internalMinioClient")
    public MinioClient internalMinioClient(MinioStorageProperties properties) {
        return MinioClient.builder()
                .endpoint(properties.endpoint())
                .credentials(properties.accessKey(), properties.secretKey())
                .build();
    }

    /**
     * 生成前端直传/下载预签名 URL 的 Client。
     */
    @Bean
    @Qualifier("publicMinioClient")
    public MinioClient publicMinioClient(MinioStorageProperties properties) {
        return MinioClient.builder()
                .endpoint(properties.publicEndpoint())
                .credentials(properties.accessKey(), properties.secretKey())
                .build();
    }
}
