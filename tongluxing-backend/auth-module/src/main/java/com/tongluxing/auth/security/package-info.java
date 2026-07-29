/**
 * 认证模块安全基础设施。
 *
 * <p>本包负责两类 Bearer 会话：普通用户使用 HS256 JWT 携带身份，并由 Redis 保存
 * 可撤销状态；Web Admin 使用安全随机不透明 Token，Redis 只保存 Token 摘要。
 * 请求过滤器把有效会话转换为 Spring Security Authentication，异常处理器保证过滤链
 * 中的 401、403 和“被其他设备登录”都输出统一 JSON。</p>
 */
package com.tongluxing.auth.security;
