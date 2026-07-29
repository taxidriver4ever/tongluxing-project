/**
 * 认证与授权配置。
 *
 * <p>本包绑定 JWT、微信小程序和联调 Admin 配置，并声明全局 Spring Security 过滤链。
 * 配置类只描述策略和依赖，不执行登录业务；密钥和生产凭据必须由部署环境注入，
 * 禁止硬编码到客户端或日志。</p>
 */
package com.tongluxing.auth.config;
