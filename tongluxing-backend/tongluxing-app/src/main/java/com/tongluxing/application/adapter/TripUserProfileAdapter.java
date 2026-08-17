package com.tongluxing.application.adapter;

import org.springframework.stereotype.Component;

import com.tongluxing.trip.integration.TripUserProfilePort;
import com.tongluxing.user.vo.UserProfileVO;
import com.tongluxing.user.service.UserService;

import lombok.RequiredArgsConstructor;

/**
 * 行程模块访问用户资料模块的适配器。
 *
 * <p>用于行程创建等场景读取当前登录用户的基础资料快照。</p>
 */
@Component
@RequiredArgsConstructor
public class TripUserProfileAdapter implements TripUserProfilePort {
    private final UserService userService;

    /**
     * 查询当前用户资料，并转换为行程模块所需 DTO。
     *
     * @return 当前用户资料摘要；未登录或资料不存在时返回 null
     */
    @Override
    public TripUserProfileDTO getCurrentProfile() {
        UserProfileVO profile = userService.getCurrentProfile();
        if (profile == null) {
            return null;
        }
        return new TripUserProfileDTO(profile.userId(), profile.nickname());
    }
}
