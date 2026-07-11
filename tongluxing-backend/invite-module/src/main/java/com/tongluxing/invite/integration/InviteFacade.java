package com.tongluxing.invite.integration;

import com.tongluxing.invite.dto.InviteRewardResult;
import com.tongluxing.invite.model.InviteModels.*;

/**
 * 邀请模块对其他模块开放的门面接口。
 *
 * <p>其他模块不直接依赖 Controller 或 Mapper，而是通过该接口完成邀请码、邀请关系和邀请奖励相关操作。</p>
 */
public interface InviteFacade {

    /** 获取指定用户的邀请码；不存在时由实现层自动生成。 */
    InviteCodeVO getCode(Long userId);

    /** 查询指定用户的邀请奖励进度。 */
    InviteRewardProgressVO getProgress(Long userId);

    /** 分页查询指定用户的邀请记录。 */
    PageResult<InvitationVO> getRecords(Long userId, String status, int page, int size);

    /** 处理被邀请人首次完成组队事件，并尝试发放邀请奖励。 */
    InviteRewardResult completeFirstTeam(Long userId, Long teamId, String bizId);

}
