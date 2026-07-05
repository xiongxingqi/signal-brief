package cn.name.celestrong.signalbrief.ingestion;

import cn.name.celestrong.signalbrief.article.ArticleCategory;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RssIngestionRunQueryServiceTest {

    @Test
    void findsRecentRunsWithDefaultLimit() {
        RecordingRssIngestionRunMapper mapper = new RecordingRssIngestionRunMapper();
        RssIngestionRunQueryService service = new RssIngestionRunQueryService(mapper);

        service.findRecentRuns(null);

        assertEquals(20, mapper.limit);
    }

    @Test
    void clampsRecentRunsLimitToMinimum() {
        RecordingRssIngestionRunMapper mapper = new RecordingRssIngestionRunMapper();
        RssIngestionRunQueryService service = new RssIngestionRunQueryService(mapper);

        service.findRecentRuns(0);

        assertEquals(1, mapper.limit);
    }

    @Test
    void clampsRecentRunsLimitToMaximum() {
        RecordingRssIngestionRunMapper mapper = new RecordingRssIngestionRunMapper();
        RssIngestionRunQueryService service = new RssIngestionRunQueryService(mapper);

        service.findRecentRuns(101);

        assertEquals(100, mapper.limit);
    }

    @Test
    void findsRunDetailWithSources() {
        RecordingRssIngestionRunMapper mapper = new RecordingRssIngestionRunMapper();
        RssIngestionRunQueryService service = new RssIngestionRunQueryService(mapper);

        RssIngestionRunDetail detail = service.findRunDetail(12L);

        assertSame(mapper.run, detail.run());
        assertSame(mapper.sources, detail.sources());
        assertEquals(12L, mapper.runId);
        assertEquals(12L, mapper.sourcesRunId);
    }

    @Test
    void rejectsMissingRunDetail() {
        RecordingRssIngestionRunMapper mapper = new RecordingRssIngestionRunMapper();
        RssIngestionRunQueryService service = new RssIngestionRunQueryService(mapper);

        RssIngestionRunNotFoundException exception = assertThrows(
                RssIngestionRunNotFoundException.class,
                () -> service.findRunDetail(404L)
        );

        assertEquals("RSS 入库运行记录不存在: 404", exception.getMessage());
    }

    static class RecordingRssIngestionRunMapper implements RssIngestionRunMapper {

        private final RssIngestionRun run = run();
        private final List<RssIngestionSourceRun> sources = List.of(sourceRun());
        private Integer limit;
        private Long runId;
        private Long sourcesRunId;

        @Override
        public Long insertRun(
                IngestionTriggerType triggerType,
                IngestionRunStatus status,
                Instant startedAt,
                int sourceCount
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int insertSourceRun(NewRssIngestionSourceRun sourceRun) {
            throw new UnsupportedOperationException();
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
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<RssIngestionRun> findRunById(Long runId) {
            this.runId = runId;
            if (runId.equals(404L)) {
                return Optional.empty();
            }
            return Optional.of(run);
        }

        @Override
        public List<RssIngestionRun> findRecentRuns(int limit) {
            this.limit = limit;
            return List.of(run);
        }

        @Override
        public List<RssIngestionSourceRun> findSourcesByRunId(Long runId) {
            sourcesRunId = runId;
            return sources;
        }

        private RssIngestionRun run() {
            Instant startedAt = Instant.parse("2026-07-05T01:00:00Z");
            Instant finishedAt = Instant.parse("2026-07-05T01:00:10Z");
            return new RssIngestionRun(
                    12L,
                    IngestionTriggerType.MANUAL,
                    IngestionRunStatus.PARTIAL_SUCCESS,
                    startedAt,
                    finishedAt,
                    10_000L,
                    2,
                    8,
                    3,
                    5,
                    1,
                    startedAt,
                    finishedAt
            );
        }

        private RssIngestionSourceRun sourceRun() {
            Instant startedAt = Instant.parse("2026-07-05T01:00:00Z");
            Instant finishedAt = Instant.parse("2026-07-05T01:00:03Z");
            return new RssIngestionSourceRun(
                    101L,
                    12L,
                    "Spring Blog",
                    "https://spring.io/blog.atom",
                    ArticleCategory.FRAMEWORK,
                    IngestionSourceRunStatus.SUCCESS,
                    null,
                    null,
                    1,
                    1,
                    null,
                    8,
                    3,
                    5,
                    startedAt,
                    finishedAt,
                    3_000L,
                    finishedAt
            );
        }
    }
}
