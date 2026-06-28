package com.tongdao.trip.integration;

/**
 * 行程模块访问用户资料的跨模块端口。
 */
public interface TripUserProfilePort {

    /**
     * 查询当前登录用户的基础资料。
     */
    TripUserProfileDTO getCurrentProfile();

    /**
     * 行程模块需要的用户资料最小字段集合。
     */
    record TripUserProfileDTO(
            Long userId,
            String nickname
    ) {
    }
}
