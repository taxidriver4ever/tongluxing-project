package com.tongluxing.trip.controller;

/**
 * 兼容保留类。
 *
 * <p>公开用户行程接口已经迁移到 match-module 的
 * {@code TripDiscoveryController}，避免接口只存在于启动模块时漏打包。
 * 此类不再注册 Spring MVC 路由。</p>
 */
@Deprecated
public final class TripPublicUserController {

    private TripPublicUserController() {
    }
}
