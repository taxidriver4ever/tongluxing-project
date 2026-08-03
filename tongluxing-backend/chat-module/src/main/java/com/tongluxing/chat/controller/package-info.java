/**
 * 聊天 HTTP 接口层。
 *
 * <p>负责请求参数校验、路径映射和统一结果包装；会话权限、状态流转、
 * 消息风控和腾讯 IM 同步均交由服务层处理，不在控制器中编写业务规则。</p>
 */
package com.tongluxing.chat.controller;
