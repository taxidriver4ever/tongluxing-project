/**
 * 用户模块外部系统集成预留包。
 *
 * <p>用于隔离 OCR、对象存储、实名认证平台等外部能力的适配代码。集成实现应把第三方
 * 协议转换为用户领域可理解的结果，并在此边界处理超时和第三方异常，避免外部模型
 * 直接渗透到 Service 与 Controller。</p>
 */
package com.tongluxing.user.integration;
