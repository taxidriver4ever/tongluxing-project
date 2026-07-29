/**
 * 用户模块数据传输对象包。
 *
 * <p>这里的 DTO 主要承接 MyBatis 查询结果，并在 Mapper 与 Service 之间传递数据。
 * DTO 可以包含数据库密文、审核字段和内部标志，因此不能绕过 Service 转换后直接
 * 返回给客户端。</p>
 */
package com.tongluxing.user.dto;
