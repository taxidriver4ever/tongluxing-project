package com.tongdao.match.integration;

import java.time.LocalDateTime;
import java.util.List;

public interface MatchTeamPort {

    List<MatchTeamDTO> listPublicActiveTeams(int limit);

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
