package com.tongluxing;

import java.util.List;

import org.springframework.stereotype.Component;

import com.tongluxing.match.integration.MatchTeamPort;
import com.tongluxing.team.entity.Team;
import com.tongluxing.team.mapper.TeamMapper;

import lombok.RequiredArgsConstructor;

/**
 * 匹配模块访问车队模块的适配器。
 *
 * <p>应用层负责把 team-module 的实体数据转换为 match-module 所需的端口 DTO。</p>
 */
@Component
@RequiredArgsConstructor
public class MatchTeamAdapter implements MatchTeamPort {
    private final TeamMapper teamMapper;

    /**
     * 查询公开活跃车队，作为匹配推荐候选池。
     *
     * @param limit 最大查询数量
     * @return 匹配模块车队摘要列表
     */
    @Override
    public List<MatchTeamDTO> listPublicActiveTeams(int limit) {
        return teamMapper.findPublicActive(limit).stream()
                .map(this::toDTO)
                .toList();
    }

    /**
     * 将车队实体转换为匹配模块所需的最小字段集合。
     *
     * @param team 车队实体
     * @return 匹配模块车队 DTO
     */
    private MatchTeamDTO toDTO(Team team) {
        return new MatchTeamDTO(team.getId(), team.getTripId(), team.getOwnerUserId(), team.getTeamName(),
                team.getStartName(), team.getEndName(), team.getDepartureTime(), team.getCurrentMemberCount(),
                team.getMaxMemberCount());
    }
}
