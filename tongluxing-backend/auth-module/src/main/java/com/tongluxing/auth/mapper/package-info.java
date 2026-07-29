/**
 * 认证模块 MyBatis Mapper。
 *
 * <p>Mapper 封装账号、密码、设备、角色和审计日志表的 SQL。这里不执行 Token 校验、
 * 密码比较或事务编排；涉及多表一致性的调用由 Service 层建立事务边界。所有查询默认
 * 排除逻辑删除数据，日志表则采用只追加策略。</p>
 */
package com.tongluxing.auth.mapper;
