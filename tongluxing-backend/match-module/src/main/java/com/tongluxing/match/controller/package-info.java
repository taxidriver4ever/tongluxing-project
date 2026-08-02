/**
 * 匹配模块 HTTP 控制器包。
 *
 * <p>负责绑定路径参数、查询参数和请求体，触发 Bean Validation，并把业务结果包装为
 * 统一 {@code Result}。控制器不计算距离、匹配分数或成员权限，相关规则统一交给
 * Service 与 team-module。</p>
 */
package com.tongluxing.match.controller;
