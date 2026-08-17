package com.tongluxing.match.service;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.tongluxing.common.utils.SnowflakeIdGenerator;
import com.tongluxing.match.mapper.MatchRecommendLogMapper;
import com.tongluxing.match.mapper.MatchRecommendLogMapper.RecommendLogRow;

import lombok.extern.slf4j.Slf4j;

/** 非关键推荐曝光日志的异步批量提交器。 */
@Service
@Slf4j
public class RecommendImpressionService {
    private final MatchRecommendLogMapper mapper;
    private final Executor executor;

    public RecommendImpressionService(MatchRecommendLogMapper mapper,
            @Qualifier("recommendImpressionExecutor") Executor executor) {
        this.mapper = mapper;
        this.executor = executor;
    }

    public void submit(List<ImpressionCommand> commands) {
        if (commands == null || commands.isEmpty()) return;
        List<ImpressionCommand> snapshot = List.copyOf(commands);
        try {
            executor.execute(() -> writeBatch(snapshot));
        } catch (RejectedExecutionException exception) {
            log.warn("recommend_impression_queue_full dropped={}", snapshot.size());
        }
    }

    private void writeBatch(List<ImpressionCommand> commands) {
        try {
            List<RecommendLogRow> rows = commands.stream().map(command -> new RecommendLogRow(
                    SnowflakeIdGenerator.nextId(), command.userId(), command.tripId(), command.targetTripId(),
                    command.targetTeamId(), "DISCOVER_COMPANION", "IMPRESSION", command.requestId(), null)).toList();
            mapper.batchInsert(rows);
        } catch (Exception exception) {
            log.error("recommend_impression_batch_failed size={} reason={}",
                    commands.size(), exception.getClass().getSimpleName(), exception);
        }
    }

    public record ImpressionCommand(Long userId, Long tripId, Long targetTripId,
                                    Long targetTeamId, String requestId) { }
}

