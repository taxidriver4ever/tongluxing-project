package com.tongdao.storage.mapper;

import java.time.LocalDateTime;

import com.tongdao.storage.entity.FileStorage;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 文件元数据 Mapper。
 *
 * <p>业务表只保存 fileId 或 objectKey，文件的 bucket、contentType、大小等统一在本表维护。</p>
 */
@Mapper
public interface FileStorageMapper {

    /**
     * 根据主键查询未删除文件。
     */
    @Select("""
            select id,
                   bucket,
                   object_key objectKey,
                   original_file_name originalFileName,
                   content_type contentType,
                   file_size fileSize,
                   biz_type bizType,
                   biz_id bizId,
                   user_id userId,
                   storage_type storageType,
                   upload_status uploadStatus,
                   created_at createdAt,
                   updated_at updatedAt,
                   deleted
            from file_storage
            where id = #{id}
              and deleted = 0
            limit 1
            """)
    FileStorage findById(@Param("id") Long id);

    /**
     * 根据 bucket 和 objectKey 查询未删除文件。
     */
    @Select("""
            select id,
                   bucket,
                   object_key objectKey,
                   original_file_name originalFileName,
                   content_type contentType,
                   file_size fileSize,
                   biz_type bizType,
                   biz_id bizId,
                   user_id userId,
                   storage_type storageType,
                   upload_status uploadStatus,
                   created_at createdAt,
                   updated_at updatedAt,
                   deleted
            from file_storage
            where bucket = #{bucket}
              and object_key = #{objectKey}
              and deleted = 0
            limit 1
            """)
    FileStorage findByObject(@Param("bucket") String bucket, @Param("objectKey") String objectKey);

    /**
     * 写入已确认上传的文件记录。
     */
    @Insert("""
            insert into file_storage(
                id, bucket, object_key, original_file_name, content_type,
                file_size, biz_type, biz_id, user_id, storage_type,
                upload_status, created_at, updated_at, deleted
            )
            values(
                #{id}, #{bucket}, #{objectKey}, #{originalFileName}, #{contentType},
                #{fileSize}, #{bizType}, #{bizId}, #{userId}, 'MINIO',
                'CONFIRMED', #{now}, #{now}, 0
            )
            """)
    int insertConfirmed(@Param("id") Long id,
                        @Param("bucket") String bucket,
                        @Param("objectKey") String objectKey,
                        @Param("originalFileName") String originalFileName,
                        @Param("contentType") String contentType,
                        @Param("fileSize") Long fileSize,
                        @Param("bizType") String bizType,
                        @Param("bizId") String bizId,
                        @Param("userId") Long userId,
                        @Param("now") LocalDateTime now);

    /**
     * 将文件标记为删除。MinIO object 是否物理删除由服务层策略决定。
     */
    @Update("""
            update file_storage
            set upload_status = 'DELETED',
                deleted = 1,
                updated_at = #{now}
            where id = #{id}
              and deleted = 0
            """)
    int markDeleted(@Param("id") Long id, @Param("now") LocalDateTime now);
}
