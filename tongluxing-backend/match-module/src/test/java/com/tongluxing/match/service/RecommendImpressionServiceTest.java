package com.tongluxing.match.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import com.tongluxing.match.mapper.MatchRecommendLogMapper;
import com.tongluxing.match.service.RecommendImpressionService.ImpressionCommand;

class RecommendImpressionServiceTest {

    @Test
    void submitQueuesOneBatchWithoutWritingOnRequestThread() {
        MatchRecommendLogMapper mapper = mock(MatchRecommendLogMapper.class);
        AtomicReference<Runnable> queued = new AtomicReference<>();
        RecommendImpressionService service = new RecommendImpressionService(mapper, queued::set);

        service.submit(commands());

        verify(mapper, never()).batchInsert(anyList());
        assertNotNull(queued.get());
        queued.get().run();
        verify(mapper).batchInsert(anyList());
    }

    @Test
    void databaseFailureDoesNotEscapeFromAsyncTask() {
        MatchRecommendLogMapper mapper = mock(MatchRecommendLogMapper.class);
        doThrow(new IllegalStateException("database unavailable")).when(mapper).batchInsert(anyList());
        RecommendImpressionService service = new RecommendImpressionService(mapper, Runnable::run);
        assertDoesNotThrow(() -> service.submit(commands()));
    }

    @Test
    void fullQueueDropsImpressionsWithoutBlockingOrWritingSynchronously() {
        MatchRecommendLogMapper mapper = mock(MatchRecommendLogMapper.class);
        Executor rejectingExecutor = task -> { throw new RejectedExecutionException("full"); };
        RecommendImpressionService service = new RecommendImpressionService(mapper, rejectingExecutor);
        assertDoesNotThrow(() -> service.submit(commands()));
        verify(mapper, never()).batchInsert(anyList());
    }

    private List<ImpressionCommand> commands() {
        return List.of(new ImpressionCommand(1L, null, 11L, 101L, "request"),
                new ImpressionCommand(1L, null, 12L, 102L, "request"));
    }
}
