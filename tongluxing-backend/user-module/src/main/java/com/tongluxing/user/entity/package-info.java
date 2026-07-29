/**
 * 用户模块持久化实体预留包。
 *
 * <p>当前数据访问使用 MyBatis 查询 DTO，没有单独维护与数据表一一对应的实体类。
 * 如果后续引入通用 CRUD 或 ORM 实体，应只在此包表达表结构，不在实体中编排缓存、
 * 权限或隐私过滤等业务规则。</p>
 */
package com.tongluxing.user.entity;
