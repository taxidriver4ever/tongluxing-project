/**
 * 认证模块 HTTP 接口层。
 *
 * <p>Controller 负责路由、请求体绑定、Bean Validation 与统一 Result 包装，不直接访问
 * Redis、数据库或第三方认证接口。普通用户认证位于 {@code /v1/auth}，独立后台认证位于
 * {@code /v1/admin/auth}；两者虽都使用 Bearer 请求头，但令牌格式与存储完全隔离。</p>
 */
package com.tongluxing.auth.controller;
