/**
 * 认证模块请求 DTO。
 *
 * <p>本包中的 record 是 HTTP 层与业务层之间的输入契约：字段格式由 Jakarta
 * Bean Validation 做第一层拦截，验证码有效性、账号状态、密码规则、票据一次性等
 * 跨字段或依赖存储状态的规则仍由 Service 校验。DTO 不包含数据库实体，也不承担业务写入。</p>
 */
package com.tongluxing.auth.dto;
