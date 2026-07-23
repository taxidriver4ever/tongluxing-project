package com.tongluxing;

import org.springframework.web.bind.annotation.*;
import com.tongluxing.common.result.Result;
import com.tongluxing.auth.entity.AuthAccount;
import com.tongluxing.auth.mapper.AuthAccountMapper;
import com.tongluxing.growth.service.GrowthService;
import com.tongluxing.user.model.UserModels.UserHomepageVO;
import com.tongluxing.user.service.UserService;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class UserHomepageController {
    private final UserService userService;
    private final GrowthService growthService;
    private final AuthAccountMapper authAccountMapper;
    private final IpProvinceResolver ipProvinceResolver;

    @GetMapping("/v1/users/{userId}/homepage")
    public Result<UserHomepageVO> homepage(@PathVariable Long userId) {
        var profile = userService.getPublicProfile(userId);
        AuthAccount account = authAccountMapper.findByUserId(userId);
        String province = ipProvinceResolver.resolve(
                account == null ? null : account.getLastLoginIp(), profile.cityName());
        return Result.success(new UserHomepageVO(profile,
                growthService.getSummary(userId), growthService.getBadgeWall(userId), province,
                userService.getFollowStatus(userId)));
    }
}
