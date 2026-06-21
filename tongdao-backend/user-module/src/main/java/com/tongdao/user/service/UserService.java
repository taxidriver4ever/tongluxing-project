package com.tongdao.user.service;

import java.math.BigDecimal;
import java.util.List;

import com.tongdao.user.model.UserModels.AvailableCouponVO;
import com.tongdao.user.model.UserModels.BadgeWallVO;
import com.tongdao.user.model.UserModels.CertificationRequest;
import com.tongdao.user.model.UserModels.CertificationVO;
import com.tongdao.user.model.UserModels.CouponDeductionVO;
import com.tongdao.user.model.UserModels.CouponLockRequest;
import com.tongdao.user.model.UserModels.CouponOrderResultRequest;
import com.tongdao.user.model.UserModels.GrowthLogVO;
import com.tongdao.user.model.UserModels.GrowthSummaryVO;
import com.tongdao.user.model.UserModels.InvitationVO;
import com.tongdao.user.model.UserModels.InviteBindVO;
import com.tongdao.user.model.UserModels.InviteCodeVO;
import com.tongdao.user.model.UserModels.InviteRewardProgressVO;
import com.tongdao.user.model.UserModels.PageResult;
import com.tongdao.user.model.UserModels.PublicProfileVO;
import com.tongdao.user.model.UserModels.PublishResultVO;
import com.tongdao.user.model.UserModels.TeamMatchVO;
import com.tongdao.user.model.UserModels.TripDraftRequest;
import com.tongdao.user.model.UserModels.TripDraftVO;
import com.tongdao.user.model.UserModels.UpdateUserProfileRequest;
import com.tongdao.user.model.UserModels.UserCouponDetailVO;
import com.tongdao.user.model.UserModels.UserDashboardVO;
import com.tongdao.user.model.UserModels.UserHomepageVO;
import com.tongdao.user.model.UserModels.UserProfileVO;
import com.tongdao.user.model.UserModels.CouponSummaryVO;

public interface UserService {
    UserProfileVO getCurrentProfile();
    UserProfileVO updateCurrentProfile(UpdateUserProfileRequest request);
    CertificationVO submitCertification(CertificationRequest request);
    PublicProfileVO getPublicProfile(Long userId);
    UserDashboardVO getDashboard();
    GrowthSummaryVO getGrowth();
    PageResult<GrowthLogVO> getGrowthLogs(int page, int size);
    BadgeWallVO getBadges();
    UserHomepageVO getHomepage(Long userId);
    InviteCodeVO getInviteCode();
    InviteBindVO bindInvite(String inviteCode);
    InviteBindVO bindInviteForNewUser(Long userId, String inviteCode);
    PageResult<InvitationVO> getInvitations(String status, int page, int size);
    InviteRewardProgressVO getInvitationRewards();
    PageResult<CouponSummaryVO> getCoupons(String status, String type, int page, int size);
    UserCouponDetailVO getCoupon(Long id);
    List<AvailableCouponVO> getAvailableCoupons(String orderType, Long merchantId, BigDecimal amount);
    CouponDeductionVO lockCoupon(Long id, CouponLockRequest request);
    void handleOrderResult(CouponOrderResultRequest request);
    TripDraftVO createDraft(TripDraftRequest request);
    PageResult<TripDraftVO> getDrafts(String status, int page, int size);
    TripDraftVO updateDraft(Long id, TripDraftRequest request);
    void deleteDraft(Long id);
    PublishResultVO publishDraft(Long id, String publishType);
    PageResult<TeamMatchVO> recommendTeams(Long id, int page, int size);
}
