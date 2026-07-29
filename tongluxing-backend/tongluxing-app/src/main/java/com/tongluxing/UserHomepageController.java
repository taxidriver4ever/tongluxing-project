package com.tongluxing;

import org.springframework.web.bind.annotation.*;
import com.tongluxing.common.result.Result;
import com.tongluxing.auth.entity.AuthAccount;
import com.tongluxing.auth.mapper.AuthAccountMapper;
import com.tongluxing.growth.service.GrowthService;
import com.tongluxing.user.model.UserModels.UserHomepageVO;
import com.tongluxing.user.model.UserModels.PublicVehicleSummaryVO;
import com.tongluxing.user.service.UserService;
import com.tongluxing.user.support.CurrentUserContext;
import com.tongluxing.vehicle.service.VehicleService;
import lombok.RequiredArgsConstructor;

/**
 * 负责用户主页相关 HTTP 接口的参数接收、校验和统一结果封装。
 * 具体业务规则委托给服务层，控制器本身不直接操作数据库。
 */
@RestController
@RequiredArgsConstructor
public class UserHomepageController {
    private final UserService userService;
    private final GrowthService growthService;
    private final AuthAccountMapper authAccountMapper;
    private final IpProvinceResolver ipProvinceResolver;
    private final CurrentUserContext currentUserContext;
    private final VehicleService vehicleService;

    @GetMapping("/v1/users/{userId}/homepage")
    /** 执行 homepage 对应的领域操作，并返回统一的业务结果。 */
    public Result<UserHomepageVO> homepage(@PathVariable Long userId) {
        Long currentUserId = currentUserContext.requireUserId();
        var profile = currentUserId.equals(userId)
                ? userService.getChatMemberProfile(userId)
                : userService.getPublicProfile(userId);
        AuthAccount account = authAccountMapper.findByUserId(userId);
        String province = ipProvinceResolver.resolve(
                account == null ? null : account.getLastLoginIp(), profile.cityName());
        var privacy = userService.getPrivacySettings(userId);
        var vehicle = (currentUserId.equals(userId) || "PUBLIC".equals(privacy.vehicleVisibility()))
                ? vehicleService.getPublicMainCard(userId) : null;
        PublicVehicleSummaryVO mainVehicle = vehicle == null ? null
                : new PublicVehicleSummaryVO(vehicle.brand(), vehicle.model(), vehicle.vehicleType());
        return Result.success(new UserHomepageVO(profile,
                Boolean.TRUE.equals(privacy.levelVisible()) ? growthService.getSummary(userId) : null,
                Boolean.TRUE.equals(privacy.levelVisible()) ? growthService.getBadgeWall(userId) : null,
                mainVehicle, province,
                userService.getFollowStatus(userId)));
    }
}
