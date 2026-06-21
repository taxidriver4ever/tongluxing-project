package com.tongdao.user.integration;

import java.util.List;

import com.tongdao.user.model.UserModels.TeamMatchVO;
import com.tongdao.user.model.UserModels.TripDraftVO;

/** Cross-module application port. Implementations belong in the assembling application, never in another module's Mapper layer. */
public interface UserModuleFacade {

    record PublishOutcome(Long tripId, Long publishedId) { }

    PublishOutcome publishDraft(Long userId, TripDraftVO draft, String publishType, String bizId);

    List<TeamMatchVO> recommendTeams(Long userId, TripDraftVO draft, int page, int size);

    boolean isInviteBindingEligible(Long userId);
}
