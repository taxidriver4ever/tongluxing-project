package com.tongluxing.storage.service.impl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tongluxing.common.exception.BusinessException;
import com.tongluxing.common.result.ResultCode;
import com.tongluxing.common.utils.SnowflakeIdGenerator;
import com.tongluxing.storage.config.MinioStorageProperties;
import com.tongluxing.storage.dto.ConfirmUploadRequest;
import com.tongluxing.storage.dto.PresignUploadRequest;
import com.tongluxing.storage.entity.FileStorage;
import com.tongluxing.storage.mapper.FileStorageMapper;
import com.tongluxing.storage.service.StorageService;
import com.tongluxing.storage.vo.ConfirmUploadResponse;
import com.tongluxing.storage.vo.DeleteFileResponse;
import com.tongluxing.storage.vo.PresignDownloadResponse;
import com.tongluxing.storage.vo.PresignUploadResponse;
import com.tongluxing.user.support.CurrentUserContext;

import io.minio.BucketExistsArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.http.Method;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * MinIO 文件存储服务实现。
 *
 * <p>这里采用“前端直传 MinIO”的方式：后端只签发短期上传 URL，并在确认阶段校验 object
 * 是否真实存在，随后把 bucket/objectKey 等元数据写入 file_storage。</p>
 */
@Service
public class StorageServiceImpl implements StorageService {
    private static final long MAX_IMAGE_SIZE_BYTES = 10L * 1024 * 1024;
    private static final java.util.Set<String> IMAGE_BIZ_TYPES = java.util.Set.of(
            "USER_AVATAR", "USER_DRIVER_LICENSE_FRONT", "USER_DRIVER_LICENSE_BACK",
            "VEHICLE_LICENSE_FRONT", "VEHICLE_LICENSE_BACK", "VEHICLE_PHOTO_FRONT",
            "VEHICLE_PHOTO_REAR", "VEHICLE_PHOTO_SIDE", "VEHICLE_PHOTO_OTHER",
            "MERCHANT_COVER", "MERCHANT_LICENSE", "MERCHANT_QUALIFICATION",
            "MERCHANT_PRODUCT_IMAGE", "MERCHANT_QR", "TRIP_COVER", "CHAT_IMAGE"
    );
    private static final java.util.Set<String> ALLOWED_IMAGE_CONTENT_TYPES = java.util.Set.of(
            "image/jpeg", "image/png", "image/webp", "image/heic", "image/heif"
    );
    private static final String UPLOAD_SESSION_PREFIX = "storage:upload:session:";
    private static final String CONFIRM_IDEM_PREFIX = "storage:confirm:idem:";
    private static final String FILE_META_PREFIX = "storage:file:meta:";
    private static final String DELETE_LOCK_PREFIX = "storage:delete:lock:";

    private final MinioStorageProperties properties;
    private final MinioClient internalMinioClient;
    private final MinioClient publicMinioClient;
    private final FileStorageMapper mapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final CurrentUserContext currentUserContext;

    public StorageServiceImpl(MinioStorageProperties properties,
                              @Qualifier("internalMinioClient") MinioClient internalMinioClient,
                              @Qualifier("publicMinioClient") MinioClient publicMinioClient,
                              FileStorageMapper mapper,
                              StringRedisTemplate redisTemplate,
                              ObjectMapper objectMapper,
                              CurrentUserContext currentUserContext) {
        this.properties = properties;
        this.internalMinioClient = internalMinioClient;
        this.publicMinioClient = publicMinioClient;
        this.mapper = mapper;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.currentUserContext = currentUserContext;
    }

    /**
     * 生成上传预签名 URL，并将短期上传会话写入 Redis。
     */
    @Override
    public PresignUploadResponse presignUpload(PresignUploadRequest request) {
        Long userId = currentUserContext.requireUserId();
        validateUploadRequest(request);
        String bucket = properties.bucket();
        String objectKey = buildObjectKey(request.bizType(), request.fileName(), userId);
        ensureBucket(bucket);
        try {
            String uploadUrl = publicMinioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.PUT)
                    .bucket(bucket)
                    .object(objectKey)
                    .expiry(properties.uploadExpire())
                    .build());
            saveUploadSession(bucket, objectKey, request, userId);
            return new PresignUploadResponse(uploadUrl, bucket, objectKey, properties.uploadExpire());
        } catch (Exception exception) {
            throw new BusinessException(ResultCode.INTERNAL_SERVER_ERROR.getCode(), "生成上传签名失败", exception);
        }
    }

    /**
     * 校验 MinIO object 后写入文件元数据。重复确认同一个 object 时返回已有记录。
     */
    @Override
    @Transactional
    public ConfirmUploadResponse confirmUpload(ConfirmUploadRequest request) {
        Long userId = currentUserContext.requireUserId();
        validateBucket(request.bucket());
        validateObjectKey(request.objectKey());
        FileStorage existed = mapper.findByObject(request.bucket(), request.objectKey());
        if (existed != null) {
            return new ConfirmUploadResponse(existed.getId(), existed.getBucket(),
                    existed.getObjectKey(), existed.getUploadStatus());
        }
        validateUploadSession(request, userId);
        StatObjectResponse stat = statObject(request.bucket(), request.objectKey());
        if (stat.size() != request.fileSize()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "文件大小与上传确认信息不一致");
        }
        long fileId = SnowflakeIdGenerator.nextId();
        try {
            mapper.insertConfirmed(fileId, request.bucket(), request.objectKey(), trim(request.fileName()),
                    trim(request.contentType()), request.fileSize(), trim(request.bizType()), trimToNull(request.bizId()),
                    userId, LocalDateTime.now());
        } catch (DuplicateKeyException exception) {
            FileStorage duplicate = mapper.findByObject(request.bucket(), request.objectKey());
            if (duplicate != null) {
                return new ConfirmUploadResponse(duplicate.getId(), duplicate.getBucket(),
                        duplicate.getObjectKey(), duplicate.getUploadStatus());
            }
            throw exception;
        }
        cacheConfirmResult(request.bucket(), request.objectKey(), fileId);
        return new ConfirmUploadResponse(fileId, request.bucket(), request.objectKey(), "CONFIRMED");
    }

    /**
     * 根据 fileId 或 bucket/objectKey 生成短期下载 URL。
     */
    @Override
    public PresignDownloadResponse presignDownload(Long fileId, String bucket, String objectKey) {
        FileStorage file = resolveFile(fileId, bucket, objectKey);
        try {
            String downloadUrl = publicMinioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(file.getBucket())
                    .object(file.getObjectKey())
                    .expiry(properties.downloadExpire())
                    .build());
            return new PresignDownloadResponse(file.getId(), file.getBucket(), file.getObjectKey(),
                    downloadUrl, properties.downloadExpire());
        } catch (Exception exception) {
            throw new BusinessException(ResultCode.INTERNAL_SERVER_ERROR.getCode(), "生成下载签名失败", exception);
        }
    }

    /**
     * 逻辑删除文件记录，不默认物理删除 MinIO object，便于后续补偿和审计。
     */
    @Override
    public DeleteFileResponse deleteFile(Long fileId) {
        FileStorage file = mapper.findById(fileId);
        if (file == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "文件不存在");
        }
        String lockKey = DELETE_LOCK_PREFIX + fileId;
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(lockKey, "1", 30, TimeUnit.SECONDS);
        if (!Boolean.TRUE.equals(locked)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "文件正在删除中");
        }
        try {
            mapper.markDeleted(fileId, LocalDateTime.now());
            redisTemplate.delete(FILE_META_PREFIX + fileId);
            return new DeleteFileResponse(fileId, true);
        } finally {
            redisTemplate.delete(lockKey);
        }
    }

    /**
     * 创建业务隔离的 objectKey，避免前端传入路径导致覆盖任意对象。
     */
    private String buildObjectKey(String bizType, String fileName, Long userId) {
        String normalizedBiz = trim(bizType).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
        String extension = extension(fileName);
        LocalDate today = LocalDate.now();
        return "%s/%04d/%02d/%02d/%s/%s%s".formatted(
                normalizedBiz,
                today.getYear(),
                today.getMonthValue(),
                today.getDayOfMonth(),
                userId,
                UUID.randomUUID().toString().replace("-", ""),
                extension);
    }

    /**
     * 确认 bucket 存在。开发环境首次启动时会自动创建默认 bucket。
     */
    private void ensureBucket(String bucket) {
        try {
            boolean exists = internalMinioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                internalMinioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }
        } catch (Exception exception) {
            throw new BusinessException(ResultCode.INTERNAL_SERVER_ERROR.getCode(), "初始化 MinIO bucket 失败", exception);
        }
    }

    /**
     * 读取 MinIO object 元数据，用于确认前端确实完成直传。
     */
    private StatObjectResponse statObject(String bucket, String objectKey) {
        try {
            return internalMinioClient.statObject(StatObjectArgs.builder().bucket(bucket).object(objectKey).build());
        } catch (Exception exception) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "文件尚未上传或无法访问");
        }
    }

    /**
     * 保存预签名上传会话，确认阶段用于基础防串改校验。
     */
    private void saveUploadSession(String bucket, String objectKey, PresignUploadRequest request, Long userId) {
        UploadSession session = new UploadSession(bucket, objectKey, trim(request.fileName()),
                trim(request.contentType()), request.fileSize(), trim(request.bizType()),
                trimToNull(request.bizId()), userId);
        try {
            redisTemplate.opsForValue().set(UPLOAD_SESSION_PREFIX + hash(objectKey),
                    objectMapper.writeValueAsString(session), properties.uploadExpire(), TimeUnit.SECONDS);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ResultCode.INTERNAL_SERVER_ERROR.getCode(), "保存上传会话失败", exception);
        }
    }

    /**
     * 校验确认请求和预签名阶段的上传会话是否一致。
     */
    private void validateUploadSession(ConfirmUploadRequest request, Long userId) {
        String json = redisTemplate.opsForValue().get(UPLOAD_SESSION_PREFIX + hash(request.objectKey()));
        if (!StringUtils.hasText(json)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "上传会话已过期，请重新获取上传签名");
        }
        try {
            UploadSession session = objectMapper.readValue(json, UploadSession.class);
            if (!session.bucket().equals(request.bucket())
                    || !session.objectKey().equals(request.objectKey())
                    || !session.fileSize().equals(request.fileSize())
                    || !session.bizType().equals(trim(request.bizType()))
                    || !session.userId().equals(userId)) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "上传确认信息与预签名会话不一致");
            }
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "上传会话数据无效");
        }
    }

    private void cacheConfirmResult(String bucket, String objectKey, Long fileId) {
        redisTemplate.opsForValue().set(CONFIRM_IDEM_PREFIX + bucket + ":" + hash(objectKey),
                String.valueOf(fileId), 24, TimeUnit.HOURS);
    }

    private FileStorage resolveFile(Long fileId, String bucket, String objectKey) {
        if (fileId != null) {
            FileStorage file = mapper.findById(fileId);
            if (file == null) {
                throw new BusinessException(ResultCode.NOT_FOUND, "文件不存在");
            }
            return file;
        }
        String resolvedBucket = StringUtils.hasText(bucket) ? bucket : properties.bucket();
        validateBucket(resolvedBucket);
        validateObjectKey(objectKey);
        FileStorage file = mapper.findByObject(resolvedBucket, objectKey);
        if (file == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "文件不存在");
        }
        return file;
    }

    private void validateBucket(String bucket) {
        if (!properties.bucket().equals(bucket)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "bucket 不允许访问");
        }
    }

    private void validateObjectKey(String objectKey) {
        if (!StringUtils.hasText(objectKey) || objectKey.contains("..") || objectKey.startsWith("/")) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "objectKey 不合法");
        }
    }

    /** 图片业务仅允许常见安全图片格式，并限制单张图片大小。 */
    private void validateUploadRequest(PresignUploadRequest request) {
        String bizType = trim(request.bizType()).toUpperCase(Locale.ROOT);
        if (!IMAGE_BIZ_TYPES.contains(bizType)) {
            return;
        }
        String contentType = trim(request.contentType()).toLowerCase(Locale.ROOT);
        if (!ALLOWED_IMAGE_CONTENT_TYPES.contains(contentType)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "仅支持 JPG、PNG、WEBP、HEIC 图片");
        }
        if (request.fileSize() > MAX_IMAGE_SIZE_BYTES) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "单张图片不能超过 10MB");
        }
    }

    private String extension(String fileName) {
        String cleaned = trim(fileName);
        int index = cleaned.lastIndexOf('.');
        if (index < 0 || index == cleaned.length() - 1) {
            return "";
        }
        String ext = cleaned.substring(index).toLowerCase(Locale.ROOT);
        return ext.matches("\\.[a-z0-9]{1,16}") ? ext : "";
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private String trimToNull(String value) {
        String trimmed = trim(value);
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new BusinessException(ResultCode.INTERNAL_SERVER_ERROR.getCode(), "生成对象哈希失败", exception);
        }
    }

    /**
     * Redis 中的短期上传会话。
     */
    private record UploadSession(String bucket, String objectKey, String fileName, String contentType,
                                 Long fileSize, String bizType, String bizId, Long userId) {
    }
}
