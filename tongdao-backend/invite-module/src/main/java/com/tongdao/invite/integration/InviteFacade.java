package com.tongdao.invite.integration;
import com.tongdao.user.model.UserModels.*;
public interface InviteFacade {
    InviteCodeVO getCode(Long userId);
    InviteBindVO bind(Long inviteeUserId,String inviteCode);
    InviteRewardProgressVO getProgress(Long userId);
    PageResult<InvitationVO> getRecords(Long userId,String status,int page,int size);
    InviteRewardResult completeFirstTeam(Long userId,Long teamId,String bizId);
    record InviteRewardResult(Long relationId,Long inviterUserId,String rewardBizNo,String status){}
}
