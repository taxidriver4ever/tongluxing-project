package com.tongluxing.trip.integration;

/**
 * 行程模块查询用户是否已经参加其他活跃车队的跨模块端口。
 */
public interface TripParticipationPort {

    /**
     * 查询用户当前参加的活跃车队所绑定的行程 ID。
     *
     * @return 没有参加活跃车队时返回 null
     */
    Long findActiveParticipatingTripId(Long userId);
}
