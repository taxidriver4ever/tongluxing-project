package com.tongdao.admin.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.tongdao.admin.entity.AdminOperationConfig;

@Mapper
public interface AdminOperationConfigMapper {

    @Insert("""
            insert into admin_operation_config
                (id, config_domain, config_key, current_version, config_status,
                 effective_at, created_at, updated_at, deleted)
            values
                (#{id}, #{configDomain}, #{configKey}, #{currentVersion}, #{configStatus},
                 #{effectiveAt}, #{createdAt}, #{updatedAt}, 0)
            """)
    void insert(AdminOperationConfig config);

    @Select("""
            select id, config_domain, config_key, current_version, config_status,
                   effective_at, created_at, updated_at, deleted
            from admin_operation_config
            where config_domain = #{configDomain}
              and config_key = #{configKey}
              and deleted = 0
            limit 1
            """)
    AdminOperationConfig findByKey(@Param("configDomain") String configDomain,
                                   @Param("configKey") String configKey);

    @Update("""
            update admin_operation_config
            set current_version = #{currentVersion},
                config_status = #{configStatus},
                effective_at = #{effectiveAt},
                updated_at = #{updatedAt}
            where id = #{id}
              and deleted = 0
            """)
    int updateVersion(AdminOperationConfig config);
}
