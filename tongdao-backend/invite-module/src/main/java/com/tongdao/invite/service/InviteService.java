package com.tongdao.invite.service;
import com.tongdao.invite.integration.InviteFacade;
import com.tongdao.user.model.UserModels.*;
public interface InviteService extends InviteFacade { InviteCodeVO currentCode(); InviteBindVO bindCurrent(String code); InviteRewardProgressVO currentProgress(); PageResult<InvitationVO> currentRecords(String status,int page,int size); }
