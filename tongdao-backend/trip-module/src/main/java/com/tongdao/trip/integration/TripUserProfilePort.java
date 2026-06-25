package com.tongdao.trip.integration;

public interface TripUserProfilePort {

    TripUserProfileDTO getCurrentProfile();

    record TripUserProfileDTO(
            Long userId,
            String nickname
    ) {
    }
}
