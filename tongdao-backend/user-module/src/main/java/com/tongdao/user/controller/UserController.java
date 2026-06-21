package com.tongdao.user.controller;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tongdao.common.result.Result;
import com.tongdao.user.model.UserModels.AvailableCouponVO;
import com.tongdao.user.model.UserModels.BadgeWallVO;
import com.tongdao.user.model.UserModels.CertificationRequest;
import com.tongdao.user.model.UserModels.CertificationVO;
import com.tongdao.user.model.UserModels.CouponSummaryVO;
import com.tongdao.user.model.UserModels.GrowthLogVO;
import com.tongdao.user.model.UserModels.GrowthSummaryVO;
import com.tongdao.user.model.UserModels.InvitationVO;
import com.tongdao.user.model.UserModels.InviteBindRequest;
import com.tongdao.user.model.UserModels.InviteBindVO;
import com.tongdao.user.model.UserModels.InviteCodeVO;
import com.tongdao.user.model.UserModels.InviteRewardProgressVO;
import com.tongdao.user.model.UserModels.PageResult;
import com.tongdao.user.model.UserModels.PublicProfileVO;
import com.tongdao.user.model.UserModels.PublishDraftRequest;
import com.tongdao.user.model.UserModels.PublishResultVO;
import com.tongdao.user.model.UserModels.TeamMatchVO;
import com.tongdao.user.model.UserModels.TripDraftRequest;
import com.tongdao.user.model.UserModels.TripDraftVO;
import com.tongdao.user.model.UserModels.UpdateUserProfileRequest;
import com.tongdao.user.model.UserModels.UserCouponDetailVO;
import com.tongdao.user.model.UserModels.UserDashboardVO;
import com.tongdao.user.model.UserModels.UserHomepageVO;
import com.tongdao.user.model.UserModels.UserProfileVO;
import com.tongdao.user.service.UserService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/users")
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public Result<UserProfileVO> me() { return Result.success(userService.getCurrentProfile()); }

    @PutMapping("/me/profile")
    public Result<UserProfileVO> updateProfile(@Valid @RequestBody UpdateUserProfileRequest request) {
        return Result.success(userService.updateCurrentProfile(request));
    }

    @PostMapping("/me/certifications")
    public Result<CertificationVO> certify(@Valid @RequestBody CertificationRequest request) {
        return Result.success(userService.submitCertification(request));
    }

    @GetMapping("/{userId}/public-profile")
    public Result<PublicProfileVO> publicProfile(@PathVariable Long userId) { return Result.success(userService.getPublicProfile(userId)); }

    @GetMapping("/me/dashboard")
    public Result<UserDashboardVO> dashboard() { return Result.success(userService.getDashboard()); }

    @GetMapping("/me/growth")
    public Result<GrowthSummaryVO> growth() { return Result.success(userService.getGrowth()); }

    @GetMapping("/me/growth/logs")
    public Result<PageResult<GrowthLogVO>> growthLogs(@RequestParam(defaultValue="1") int page,
                                                       @RequestParam(defaultValue="20") int size) {
        return Result.success(userService.getGrowthLogs(page, size));
    }

    @GetMapping("/me/badges")
    public Result<BadgeWallVO> badges() { return Result.success(userService.getBadges()); }

    @GetMapping("/{userId}/homepage")
    public Result<UserHomepageVO> homepage(@PathVariable Long userId) { return Result.success(userService.getHomepage(userId)); }

    @GetMapping("/me/invite-code")
    public Result<InviteCodeVO> inviteCode() { return Result.success(userService.getInviteCode()); }

    @PostMapping("/me/invitations/bind")
    public Result<InviteBindVO> bind(@Valid @RequestBody InviteBindRequest request) {
        return Result.success(userService.bindInvite(request.inviteCode()));
    }

    @GetMapping("/me/invitations")
    public Result<PageResult<InvitationVO>> invitations(@RequestParam(required=false) String status,
                                                        @RequestParam(defaultValue="1") int page,
                                                        @RequestParam(defaultValue="20") int size) {
        return Result.success(userService.getInvitations(status, page, size));
    }

    @GetMapping("/me/invitation-rewards")
    public Result<InviteRewardProgressVO> invitationRewards() { return Result.success(userService.getInvitationRewards()); }

    @GetMapping("/me/coupons")
    public Result<PageResult<CouponSummaryVO>> coupons(@RequestParam(required=false) String status,
                                                       @RequestParam(required=false) String type,
                                                       @RequestParam(defaultValue="1") int page,
                                                       @RequestParam(defaultValue="20") int size) {
        return Result.success(userService.getCoupons(status, type, page, size));
    }

    @GetMapping("/me/coupons/{id}")
    public Result<UserCouponDetailVO> coupon(@PathVariable Long id) { return Result.success(userService.getCoupon(id)); }

    @GetMapping("/me/coupons/available")
    public Result<List<AvailableCouponVO>> availableCoupons(@RequestParam String orderType,
                                                             @RequestParam(required=false) Long merchantId,
                                                             @RequestParam BigDecimal amount) {
        return Result.success(userService.getAvailableCoupons(orderType, merchantId, amount));
    }

    @PostMapping("/me/trip-drafts")
    public Result<TripDraftVO> createDraft(@Valid @RequestBody TripDraftRequest request) {
        return Result.success(userService.createDraft(request));
    }

    @GetMapping("/me/trip-drafts")
    public Result<PageResult<TripDraftVO>> drafts(@RequestParam(required=false) String status,
                                                  @RequestParam(defaultValue="1") int page,
                                                  @RequestParam(defaultValue="20") int size) {
        return Result.success(userService.getDrafts(status, page, size));
    }

    @PutMapping("/me/trip-drafts/{id}")
    public Result<TripDraftVO> updateDraft(@PathVariable Long id, @Valid @RequestBody TripDraftRequest request) {
        return Result.success(userService.updateDraft(id, request));
    }

    @DeleteMapping("/me/trip-drafts/{id}")
    public Result<Void> deleteDraft(@PathVariable Long id) { userService.deleteDraft(id); return Result.success(); }

    @PostMapping("/me/trip-drafts/{id}/publish")
    public Result<PublishResultVO> publish(@PathVariable Long id, @Valid @RequestBody PublishDraftRequest request) {
        return Result.success(userService.publishDraft(id, request.publishType()));
    }

    @GetMapping("/me/trip-drafts/{id}/team-recommendations")
    public Result<PageResult<TeamMatchVO>> recommendations(@PathVariable Long id,
                                                           @RequestParam(defaultValue="1") int page,
                                                           @RequestParam(defaultValue="20") int size) {
        return Result.success(userService.recommendTeams(id, page, size));
    }
}
