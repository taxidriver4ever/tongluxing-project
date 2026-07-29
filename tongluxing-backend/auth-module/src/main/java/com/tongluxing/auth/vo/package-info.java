/**
 * 认证模块响应 VO。
 *
 * <p>这些不可变 record 构成客户端可见的认证响应，只暴露令牌、必要账号状态和过期时间。
 * 数据库主键、密码哈希、Redis JTI、内部失败原因等安全敏感实现细节不会通过 VO 返回。</p>
 */
package com.tongluxing.auth.vo;
