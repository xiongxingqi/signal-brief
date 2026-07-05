package cn.name.celestrong.signalbrief.ingestion;

import cn.name.celestrong.signalbrief.article.ArticleCategory;
import cn.name.celestrong.signalbrief.config.FeedProperties;
import cn.name.celestrong.signalbrief.feed.FeedFetchException;
import cn.name.celestrong.signalbrief.feed.FeedFetchFailureType;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IngestionRunRecorderTest {

    private static final Instant NOW = Instant.parse("2026-07-05T00:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void finishesSuccessfulRun() {
        FakeRssIngestionRunMapper mapper = new FakeRssIngestionRunMapper();
        IngestionRunRecorder recorder = new IngestionRunRecorder(mapper, FIXED_CLOCK);
        IngestionRunRecorder.RunContext context = recorder.startRun(IngestionTriggerType.MANUAL, 2);
        FeedIngestionResult result = new FeedIngestionResult(2, 9, 5, 4, 0);

        recorder.finishRun(context, result);

        assertEquals(IngestionTriggerType.MANUAL, mapper.insertedTriggerType);
        assertEquals(IngestionRunStatus.RUNNING, mapper.insertedStatus);
        assertEquals(NOW, mapper.insertedStartedAt);
        assertEquals(2, mapper.insertedSourceCount);
        assertEquals(IngestionRunStatus.SUCCESS, mapper.finishedStatus);
        assertEquals(9, mapper.finishedFetchedCount);
        assertEquals(5, mapper.finishedInsertedCount);
        assertEquals(4, mapper.finishedSkippedCount);
        assertEquals(0, mapper.finishedFailedSourceCount);
    }

    @Test
    void marksPartialSuccessWhenAnySourceFails() {
        FakeRssIngestionRunMapper mapper = new FakeRssIngestionRunMapper();
        IngestionRunRecorder recorder = new IngestionRunRecorder(mapper, FIXED_CLOCK);
        IngestionRunRecorder.RunContext context = recorder.startRun(IngestionTriggerType.MANUAL, 2);

        recorder.finishRun(context, new FeedIngestionResult(2, 5, 3, 2, 1));

        assertEquals(IngestionRunStatus.PARTIAL_SUCCESS, mapper.finishedStatus);
    }

    @Test
    void marksFailedWhenAllSourcesFail() {
        FakeRssIngestionRunMapper mapper = new FakeRssIngestionRunMapper();
        IngestionRunRecorder recorder = new IngestionRunRecorder(mapper, FIXED_CLOCK);
        IngestionRunRecorder.RunContext context = recorder.startRun(IngestionTriggerType.MANUAL, 2);

        recorder.finishRun(context, new FeedIngestionResult(2, 0, 0, 0, 2));

        assertEquals(IngestionRunStatus.FAILED, mapper.finishedStatus);
    }

    @Test
    void failsRunWithCurrentAggregatedCounts() {
        FakeRssIngestionRunMapper mapper = new FakeRssIngestionRunMapper();
        IngestionRunRecorder recorder = new IngestionRunRecorder(mapper, FIXED_CLOCK);
        IngestionRunRecorder.RunContext context = recorder.startRun(IngestionTriggerType.MANUAL, 2);

        recorder.failRun(context, new FeedIngestionResult(2, 5, 3, 2, 1));

        assertEquals(IngestionRunStatus.FAILED, mapper.finishedStatus);
        assertEquals(5, mapper.finishedFetchedCount);
        assertEquals(3, mapper.finishedInsertedCount);
        assertEquals(2, mapper.finishedSkippedCount);
        assertEquals(1, mapper.finishedFailedSourceCount);
    }

    @Test
    void recordsSourceSuccess() {
        FakeRssIngestionRunMapper mapper = new FakeRssIngestionRunMapper();
        IngestionRunRecorder recorder = new IngestionRunRecorder(mapper, FIXED_CLOCK);
        IngestionRunRecorder.RunContext runContext = recorder.startRun(IngestionTriggerType.MANUAL, 1);
        FeedProperties.FeedSource source = source();
        IngestionRunRecorder.SourceRunContext sourceContext = recorder.startSource(runContext, source);

        recorder.recordSourceSuccess(sourceContext, new FeedIngestionResult(1, 5, 3, 2, 0));

        NewRssIngestionSourceRun sourceRun = mapper.insertedSourceRun;
        assertNotNull(sourceRun);
        assertEquals(IngestionSourceRunStatus.SUCCESS, sourceRun.status());
        assertEquals(source.name(), sourceRun.sourceName());
        assertEquals(source.url().toString(), sourceRun.sourceUrl());
        assertEquals(source.category(), sourceRun.category());
        assertEquals(5, sourceRun.fetchedCount());
        assertEquals(3, sourceRun.insertedCount());
        assertEquals(2, sourceRun.skippedCount());
        assertNull(sourceRun.failureType());
        assertNull(sourceRun.httpStatus());
        assertNull(sourceRun.attemptCount());
        assertNull(sourceRun.maxAttempts());
        assertNull(sourceRun.errorMessage());
        assertTrue(sourceRun.durationMillis() >= 0);
    }

    @Test
    void recordsFeedFetchFailureContext() {
        FakeRssIngestionRunMapper mapper = new FakeRssIngestionRunMapper();
        IngestionRunRecorder recorder = new IngestionRunRecorder(mapper, FIXED_CLOCK);
        IngestionRunRecorder.RunContext runContext = recorder.startRun(IngestionTriggerType.MANUAL, 1);
        IngestionRunRecorder.SourceRunContext sourceContext = recorder.startSource(runContext, source());
        FeedFetchException exception = new FeedFetchException(
                "服务暂不可用",
                FeedFetchFailureType.HTTP_STATUS,
                503,
                2,
                2,
                null
        );

        recorder.recordSourceFailure(sourceContext, exception);

        NewRssIngestionSourceRun sourceRun = mapper.insertedSourceRun;
        assertNotNull(sourceRun);
        assertEquals(IngestionSourceRunStatus.FAILED, sourceRun.status());
        assertEquals(FeedFetchFailureType.HTTP_STATUS, sourceRun.failureType());
        assertEquals(503, sourceRun.httpStatus());
        assertEquals(2, sourceRun.attemptCount());
        assertEquals(2, sourceRun.maxAttempts());
        assertEquals("服务暂不可用", sourceRun.errorMessage());
        assertEquals(0, sourceRun.fetchedCount());
        assertEquals(0, sourceRun.insertedCount());
        assertEquals(0, sourceRun.skippedCount());
    }

    @Test
    void recordsUnexpectedFailureForNonFetchException() {
        FakeRssIngestionRunMapper mapper = new FakeRssIngestionRunMapper();
        IngestionRunRecorder recorder = new IngestionRunRecorder(mapper, FIXED_CLOCK);
        IngestionRunRecorder.RunContext runContext = recorder.startRun(IngestionTriggerType.MANUAL, 1);
        IngestionRunRecorder.SourceRunContext sourceContext = recorder.startSource(runContext, source());

        recorder.recordSourceFailure(sourceContext, new IllegalStateException("boom"));

        NewRssIngestionSourceRun sourceRun = mapper.insertedSourceRun;
        assertNotNull(sourceRun);
        assertEquals(FeedFetchFailureType.UNEXPECTED, sourceRun.failureType());
        assertNull(sourceRun.httpStatus());
        assertNull(sourceRun.attemptCount());
        assertNull(sourceRun.maxAttempts());
        assertEquals("boom", sourceRun.errorMessage());
    }

    @Test
    void truncatesLongErrorMessage() {
        FakeRssIngestionRunMapper mapper = new FakeRssIngestionRunMapper();
        IngestionRunRecorder recorder = new IngestionRunRecorder(mapper, FIXED_CLOCK);
        IngestionRunRecorder.RunContext runContext = recorder.startRun(IngestionTriggerType.MANUAL, 1);
        IngestionRunRecorder.SourceRunContext sourceContext = recorder.startSource(runContext, source());

        recorder.recordSourceFailure(sourceContext, new IllegalStateException("x".repeat(2_000)));

        assertNotNull(mapper.insertedSourceRun);
        assertEquals(1_000, mapper.insertedSourceRun.errorMessage().length());
    }

    private FeedProperties.FeedSource source() {
        return new FeedProperties.FeedSource(
                "Spring Blog",
                URI.create("https://spring.io/blog.atom"),
                ArticleCategory.FRAMEWORK,
                true
        );
    }

    private static final class FakeRssIngestionRunMapper implements RssIngestionRunMapper {

        private long nextRunId = 100L;
        private IngestionTriggerType insertedTriggerType;
        private IngestionRunStatus insertedStatus;
        private Instant insertedStartedAt;
        private int insertedSourceCount;
        private NewRssIngestionSourceRun insertedSourceRun;
        private IngestionRunStatus finishedStatus;
        private int finishedFetchedCount;
        private int finishedInsertedCount;
        private int finishedSkippedCount;
        private int finishedFailedSourceCount;

        @Override
        public Long insertRun(
                IngestionTriggerType triggerType,
                IngestionRunStatus status,
                Instant startedAt,
                int sourceCount
        ) {
            insertedTriggerType = triggerType;
            insertedStatus = status;
            insertedStartedAt = startedAt;
            insertedSourceCount = sourceCount;
            return nextRunId++;
        }

        @Override
        public int insertSourceRun(NewRssIngestionSourceRun sourceRun) {
            insertedSourceRun = sourceRun;
            return 1;
        }

        @Override
        public int finishRun(
                Long runId,
                IngestionRunStatus status,
                Instant finishedAt,
                long durationMillis,
                int fetchedCount,
                int insertedCount,
                int skippedCount,
                int failedSourceCount
        ) {
            finishedStatus = status;
            finishedFetchedCount = fetchedCount;
            finishedInsertedCount = insertedCount;
            finishedSkippedCount = skippedCount;
            finishedFailedSourceCount = failedSourceCount;
            return 1;
        }

        @Override
        public Optional<RssIngestionRun> findRunById(Long runId) {
            return Optional.empty();
        }

        @Override
        public List<RssIngestionRun> findRecentRuns(int limit) {
            return List.of();
        }

        @Override
        public List<RssIngestionSourceRun> findSourcesByRunId(Long runId) {
            return List.of();
        }
    }
}
