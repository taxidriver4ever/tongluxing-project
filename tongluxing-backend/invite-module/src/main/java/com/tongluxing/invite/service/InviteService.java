package com.tongluxing.invite.service;

import com.tongluxing.invite.dto.InviteBindRequest;
import com.tongluxing.invite.integration.InviteFacade;
import com.tongluxing.invite.model.InviteModels.*;

/**
 * 邀请模块用户侧业务服务契约。
 *
 * <p>继承 {@link InviteFacade} 以同时向其他后端模块提供邀请码、
 * 邀请记录和首次组队奖励的内部调用能力。</p>
 */
public interface InviteService extends InviteFacade {

    /** 查询当前登录用户邀请码。 */
    InviteCodeVO currentCode();

    /** 生成当前登录用户邀请二维码。 */
    InviteQrVO currentQr();

    /** 校验二维码 token，无效 token 以状态对象返回。 */
    InviteQrValidationVO validateQr(String token);

    /** 查询当前用户绑定资格或已绑定关系。 */
    InviteBindStatusVO currentBindStatus();

    /** 校验邀请码并预览邀请人公开资料。 */
    InvitePreviewVO previewCurrent(String inviteCode);

    /** 在注册窗口内幂等绑定邀请关系。 */
    InviteBindVO bindCurrent(InviteBindRequest request);

    /** 查询当前用户邀请数、下一节点与已发奖规则。 */
    InviteRewardProgressVO currentProgress();

    /** 分页查询当前用户的邀请关系明细。 */
    PageResult<InvitationVO> currentRecords(String status, int page, int size);
}
