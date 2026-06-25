package com.tongdao;

import org.springframework.stereotype.Component;

import com.tongdao.trip.integration.TripUserProfilePort;
import com.tongdao.user.model.UserModels.UserProfileVO;
import com.tongdao.user.service.UserService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class TripUserProfileAdapter implements TripUserProfilePort {
    private final UserService userService;

    @Override
    public TripUserProfileDTO getCurrentProfile() {
        UserProfileVO profile = userService.getCurrentProfile();
        if (profile == null) {
            return null;
        }
        return new TripUserProfileDTO(profile.userId(), profile.nickname());
    }
}
