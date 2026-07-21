package com.tongluxing.match.integration;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 匹配模块访问车队数据的跨模块端口。
 */
public interface MatchTeamPort {

    /**
     * 查询公开且活跃的车队列表，作为推荐候选池。
     */
    List<MatchTeamDTO> listPublicActiveTeams(int limit);

    /** 查询目标行程对应的可加入车队。 */
    MatchTeamDTO findActiveTeamByTripId(Long tripId);

    /** 提交入队申请，返回申请 ID。 */
    Long apply(Long teamId, String message);

    /**
     * 车队匹配所需的最小字段集合。
     */
    record MatchTeamDTO(
            Long teamId,
            Long tripId,
            Long ownerUserId,
            String teamName,
            String startName,
            String endName,
            LocalDateTime departureTime,
            Integer currentMemberCount,
            Integer maxMemberCount
    ) {
    }
}
