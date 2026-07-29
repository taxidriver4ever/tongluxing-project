package com.tongluxing.auth.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 用户动态角色查询 Mapper。
 *
 * <p>角色不固化在 JWT 中，而是在每个认证请求建立 Security 上下文时按 userId 查询。
 * 因此商家审核通过或角色撤销后，下一个请求即可看到最新权限，无需重新登录换 Token。</p>
 */
@Mapper
public interface AuthRoleMapper {
    /**
     * 查询用户拥有的业务角色代码。
     *
     * @param userId 已通过 Token 认证的业务用户 ID
     * @return 原始角色代码列表，例如 MERCHANT；过滤器会统一添加 ROLE_ 前缀
     */
    @Select("select role_code from auth_user_role where user_id=#{userId}")
    List<String> findRoleCodes(@Param("userId") Long userId);
}
