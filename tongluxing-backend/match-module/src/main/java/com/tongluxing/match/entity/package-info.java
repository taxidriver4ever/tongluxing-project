/**
 * 匹配模块数据库实体包。
 *
 * <p>实体与 match_route_snapshot、match_result 表字段对应，只用于 Mapper 与 Service
 * 之间的持久化传输。实体包含内部评分明细、逻辑删除和审计时间，不可直接作为 HTTP
 * 响应返回。</p>
 */
package com.tongluxing.match.entity;
