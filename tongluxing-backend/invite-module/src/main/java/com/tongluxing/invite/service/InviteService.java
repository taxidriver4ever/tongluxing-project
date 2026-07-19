package com.tongluxing.invite.service;

import com.tongluxing.invite.integration.InviteFacade;
import com.tongluxing.invite.model.InviteModels.*;

/**
 * 邀请模块业务服务接口。
 *
 * <p>继承 {@link InviteFacade}，既服务当前模块 HTTP 接口，也向组队等模块提供内部调用能力。</p>
 */
public interface InviteService extends InviteFacade {

    /** 获取当前登录用户的邀请码。 */
    InviteCodeVO currentCode();

    /** 当前登录用户绑定邀请码；重复提交返回原关系。 */
    InviteBindVO bindCurrent(String inviteCode);

    /** 查询当前登录用户的邀请奖励进度。 */
    InviteRewardProgressVO currentProgress();

    /** 分页查询当前登录用户的邀请记录。 */
    PageResult<InvitationVO> currentRecords(String status, int page, int size);
}
