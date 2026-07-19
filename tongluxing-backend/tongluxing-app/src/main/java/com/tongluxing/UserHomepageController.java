package com.tongluxing;

import org.springframework.web.bind.annotation.*;
import com.tongluxing.common.result.Result;
import com.tongluxing.growth.service.GrowthService;
import com.tongluxing.user.model.UserModels.UserHomepageVO;
import com.tongluxing.user.service.UserService;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class UserHomepageController {
    private final UserService userService;
    private final GrowthService growthService;

    @GetMapping("/v1/users/{userId}/homepage")
    public Result<UserHomepageVO> homepage(@PathVariable Long userId) {
        return Result.success(new UserHomepageVO(userService.getPublicProfile(userId),
                growthService.getSummary(userId), growthService.getBadgeWall(userId)));
    }
}
