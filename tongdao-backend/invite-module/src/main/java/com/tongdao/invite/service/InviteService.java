package com.tongdao.invite.service;

import com.tongdao.invite.integration.InviteFacade;
import com.tongdao.invite.model.InviteModels.*;

/**
 * 邀请模块业务服务接口。
 *
 * <p>继承 {@link InviteFacade}，既服务当前模块 HTTP 接口，也向认证、组队等模块提供内部调用能力。</p>
 */
public interface InviteService extends InviteFacade {

    /** 获取当前登录用户的邀请码。 */
    InviteCodeVO currentCode();

    /** 当前登录用户绑定邀请码。 */
    InviteBindVO bindCurrent(String code);

    /** 查询当前登录用户的邀请奖励进度。 */
    InviteRewardProgressVO currentProgress();

    /** 分页查询当前登录用户的邀请记录。 */
    PageResult<InvitationVO> currentRecords(String status, int page, int size);
}
