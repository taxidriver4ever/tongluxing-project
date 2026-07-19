package com.tongluxing.trip.job;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.tongluxing.trip.service.TripCreationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 每小时清理超过 7 天未编辑的未发布行程草稿。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TripDraftCleanupJob {
    private final TripCreationService tripCreationService;

    @Scheduled(cron = "${tongluxing.jobs.trip-draft-cleanup-cron:0 0 * * * ?}")
    public void cleanup() {
        int count = tripCreationService.cleanupExpiredDrafts();
        if (count > 0) log.info("Cleaned {} expired trip drafts", count);
    }
}
