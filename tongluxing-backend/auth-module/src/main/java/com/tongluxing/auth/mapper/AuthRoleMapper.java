package com.tongluxing.auth.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 用户动态角色查询；审核通过后无需更换登录账号。 */
@Mapper
public interface AuthRoleMapper {
    @Select("select role_code from auth_user_role where user_id=#{userId}")
    List<String> findRoleCodes(@Param("userId") Long userId);
}
