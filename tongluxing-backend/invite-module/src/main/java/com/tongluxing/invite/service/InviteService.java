package com.tongluxing.invite.service;

import com.tongluxing.invite.dto.InviteBindRequest;
import com.tongluxing.invite.integration.InviteFacade;
import com.tongluxing.invite.model.InviteModels.*;

/** 邀请模块业务服务接口。 */
public interface InviteService extends InviteFacade {

    InviteCodeVO currentCode();

    InviteQrVO currentQr();

    InviteQrValidationVO validateQr(String token);

    InviteBindStatusVO currentBindStatus();

    InvitePreviewVO previewCurrent(String inviteCode);

    InviteBindVO bindCurrent(InviteBindRequest request);

    InviteRewardProgressVO currentProgress();

    PageResult<InvitationVO> currentRecords(String status, int page, int size);
}
