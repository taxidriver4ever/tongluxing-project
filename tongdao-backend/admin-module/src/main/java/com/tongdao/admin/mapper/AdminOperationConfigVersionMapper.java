package com.tongdao.admin.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.tongdao.admin.entity.AdminOperationConfigVersion;

/**
 * 运营配置版本 Mapper。
 */
@Mapper
public interface AdminOperationConfigVersionMapper {

    /** 新增一条配置版本。 */
    @Insert("""
            insert into admin_operation_config_version
                (id, config_id, config_domain, config_key, version_no,
                 config_value, effective_at, operator_id, change_reason, created_at)
            values
                (#{id}, #{configId}, #{configDomain}, #{configKey}, #{versionNo},
                 #{configValue}, #{effectiveAt}, #{operatorId}, #{changeReason}, #{createdAt})
            """)
    void insert(AdminOperationConfigVersion version);

    /** 根据配置 ID 和版本号查询指定版本。 */
    @Select("""
            select id, config_id, config_domain, config_key, version_no,
                   config_value, effective_at, operator_id, change_reason, created_at
            from admin_operation_config_version
            where config_id = #{configId}
              and version_no = #{versionNo}
            limit 1
            """)
    AdminOperationConfigVersion findByConfigAndVersion(@Param("configId") Long configId,
                                                       @Param("versionNo") Integer versionNo);

    /** 查询某个配置键的最新版本。 */
    @Select("""
            select id, config_id, config_domain, config_key, version_no,
                   config_value, effective_at, operator_id, change_reason, created_at
            from admin_operation_config_version
            where config_domain = #{configDomain}
              and config_key = #{configKey}
            order by version_no desc
            limit 1
            """)
    AdminOperationConfigVersion findLatest(@Param("configDomain") String configDomain,
                                           @Param("configKey") String configKey);
}
