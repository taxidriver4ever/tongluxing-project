/**
 * 匹配模块跨领域访问端口。
 *
 * <p>端口只暴露匹配计算和公开展示所需的最小行程、车队数据，隔离 trip-module 与
 * team-module 的内部实体。实现类负责把外部领域模型转换为本包 DTO，Service 不应
 * 绕过端口直接依赖其他模块的 Mapper。</p>
 */
package com.tongluxing.match.integration;
