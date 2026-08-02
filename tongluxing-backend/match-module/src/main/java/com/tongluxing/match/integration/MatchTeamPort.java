package com.tongluxing.match.integration;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 匹配模块访问车队数据的跨模块端口。
 *
 * <p>该端口隔离 team-module 的实体与服务实现。匹配模块只能查询公开车队摘要、
 * 用户关系和公开成员，或提交标准入队申请，不能直接新增成员或授予群聊权限。</p>
 */
public interface MatchTeamPort {

    /**
     * 查询公开且活跃的车队列表，作为推荐候选池。
     *
     * @param limit 最多返回的车队数
     * @return 公开活跃车队摘要
     */
    List<MatchTeamDTO> listPublicActiveTeams(int limit);

    /**
     * 查询目标行程对应的可加入车队。
     *
     * @param tripId 目标行程 ID
     * @return 当前活跃车队；未建队或已关闭时返回 null
     */
    MatchTeamDTO findActiveTeamByTripId(Long tripId);

    /**
     * 查询用户是否已入队或存在待审核申请。
     *
     * @param teamId 车队 ID
     * @param userId 用户 ID
     * @return 任一关系存在时为 true
     */
    boolean hasActiveMembershipOrPending(Long teamId, Long userId);

    /**
     * 查询当前用户相对车队的关系。
     *
     * @param teamId 车队 ID
     * @param userId 用户 ID
     * @return OWNER、JOINED、PENDING、REJECTED 或 NONE 等状态
     */
    String relationshipStatus(Long teamId, Long userId);

    /**
     * 查询公开详情允许展示的成员摘要。
     *
     * @param teamId 车队 ID
     * @param limit 最大成员数
     * @return 已脱敏公开成员
     */
    List<MatchMemberDTO> listPublicMembers(Long teamId, int limit);

    /**
     * 提交入队申请。
     *
     * @param teamId 目标车队 ID
     * @param message 给队长的说明
     * @param applicantVehicleId 申请车辆 ID
     * @param joinQuestionJson 结构化申请补充信息
     * @return 新建或幂等取得的申请 ID
     */
    Long apply(Long teamId, String message, Long applicantVehicleId, String joinQuestionJson);

    /**
     * 车队匹配所需的最小字段集合。
     *
     * @param teamId 车队 ID
     * @param tripId 车队关联行程 ID
     * @param ownerUserId 队长用户 ID
     * @param teamName 车队名称
     * @param teamDesc 入队要求或车队简介
     * @param notice 车队公告
     * @param startName 起点名称
     * @param endName 终点名称
     * @param departureTime 出发时间
     * @param currentMemberCount 当前成员数
     * @param maxMemberCount 最大成员数
     */
    record MatchTeamDTO(
            Long teamId,
            Long tripId,
            Long ownerUserId,
            String teamName,
            String teamDesc,
            String notice,
            String startName,
            String endName,
            LocalDateTime departureTime,
            Integer currentMemberCount,
            Integer maxMemberCount
    ) {
    }

    /**
     * 公开行程详情中的成员最小摘要。
     *
     * @param userId 成员用户 ID
     * @param nickname 昵称
     * @param avatarImageKey 头像资源 Key
     * @param role 车队角色
     * @param certificationStatus 驾驶认证状态
     * @param totalTripCount 累计行程数
     * @param totalDistanceMeters 累计里程（米）
     */
    record MatchMemberDTO(
            Long userId, String nickname, String avatarImageKey, String role,
            String certificationStatus, Integer totalTripCount, Long totalDistanceMeters
    ) {
    }
}
