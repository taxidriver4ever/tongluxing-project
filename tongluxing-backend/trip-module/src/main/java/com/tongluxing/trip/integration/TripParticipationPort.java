package com.tongluxing.trip.integration;

import java.util.List;

/**
 * 行程模块查询用户是否正在参加其他“进行中”车队的跨模块端口。
 */
public interface TripParticipationPort {

    /**
     * 查询用户当前作为队员参加的进行中行程 ID。
     *
     * @return 没有参加进行中行程时返回 null
     */
    Long findRunningParticipatingTripId(Long userId);

    /** 查询指定行程当前全部有效参与者，用于开启前并发校验。 */
    List<Long> findActiveParticipantUserIds(Long tripId);
}
