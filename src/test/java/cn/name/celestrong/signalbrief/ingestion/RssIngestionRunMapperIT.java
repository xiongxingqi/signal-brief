package cn.name.celestrong.signalbrief.ingestion;

import cn.name.celestrong.signalbrief.article.ArticleCategory;
import cn.name.celestrong.signalbrief.feed.FeedFetchFailureType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.jdbc.Sql;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@Sql(
        statements = "TRUNCATE TABLE rss_ingestion_source_run, rss_ingestion_run RESTART IDENTITY",
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD
)
class RssIngestionRunMapperIT {

    @Autowired
    private RssIngestionRunMapper mapper;

    @Test
    void insertsRunAndSourceRecordsThenFindsDetail() {
        Instant startedAt = Instant.parse("2026-07-05T00:00:00Z");
        Long runId = mapper.insertRun(
                IngestionTriggerType.MANUAL,
                IngestionRunStatus.RUNNING,
                startedAt,
                2
        );

        assertNotNull(runId);

        mapper.insertSourceRun(new NewRssIngestionSourceRun(
                runId,
                "Spring Blog",
                "https://spring.io/blog.atom",
                ArticleCategory.FRAMEWORK,
                IngestionSourceRunStatus.SUCCESS,
                null,
                null,
                null,
                null,
                null,
                5,
                3,
                2,
                startedAt,
                startedAt.plusSeconds(2),
                2_000L
        ));
        mapper.insertSourceRun(new NewRssIngestionSourceRun(
                runId,
                "Failing",
                "https://example.com/failing.xml",
                ArticleCategory.JAVA,
                IngestionSourceRunStatus.FAILED,
                FeedFetchFailureType.HTTP_STATUS,
                503,
                2,
                2,
                "Feed fetch failed with HTTP status 503",
                0,
                0,
                0,
                startedAt.plusSeconds(3),
                startedAt.plusSeconds(5),
                2_000L
        ));

        mapper.finishRun(
                runId,
                IngestionRunStatus.PARTIAL_SUCCESS,
                startedAt.plusSeconds(6),
                6_000L,
                5,
                3,
                2,
                1
        );

        RssIngestionRun run = mapper.findRunById(runId).orElseThrow();
        List<RssIngestionSourceRun> sources = mapper.findSourcesByRunId(runId);

        assertEquals(IngestionTriggerType.MANUAL, run.triggerType());
        assertEquals(IngestionRunStatus.PARTIAL_SUCCESS, run.status());
        assertEquals(startedAt.plusSeconds(6), run.finishedAt());
        assertEquals(6_000L, run.durationMillis());
        assertEquals(2, run.sourceCount());
        assertEquals(5, run.fetchedCount());
        assertEquals(3, run.insertedCount());
        assertEquals(2, run.skippedCount());
        assertEquals(1, run.failedSourceCount());
        assertEquals(List.of("Spring Blog", "Failing"), sources.stream().map(RssIngestionSourceRun::sourceName).toList());

        RssIngestionSourceRun failedSource = sources.get(1);
        assertEquals(IngestionSourceRunStatus.FAILED, failedSource.status());
        assertEquals(FeedFetchFailureType.HTTP_STATUS, failedSource.failureType());
        assertEquals(503, failedSource.httpStatus());
        assertEquals(2, failedSource.attemptCount());
        assertEquals(2, failedSource.maxAttempts());
        assertEquals("Feed fetch failed with HTTP status 503", failedSource.errorMessage());
        assertEquals(0, failedSource.fetchedCount());
        assertEquals(0, failedSource.insertedCount());
        assertEquals(0, failedSource.skippedCount());
        assertEquals(startedAt.plusSeconds(3), failedSource.startedAt());
        assertEquals(startedAt.plusSeconds(5), failedSource.finishedAt());
        assertEquals(2_000L, failedSource.durationMillis());
    }

    @Test
    void findsRecentRunsByStartedAtDescendingThenIdDescending() {
        Long older = mapper.insertRun(
                IngestionTriggerType.MANUAL,
                IngestionRunStatus.SUCCESS,
                Instant.parse("2026-07-04T00:00:00Z"),
                1
        );
        Long newer = mapper.insertRun(
                IngestionTriggerType.SCHEDULED,
                IngestionRunStatus.SUCCESS,
                Instant.parse("2026-07-05T00:00:00Z"),
                1
        );
        Instant sameStartedAt = Instant.parse("2026-07-06T00:00:00Z");
        Long sameTimeFirst = mapper.insertRun(
                IngestionTriggerType.MANUAL,
                IngestionRunStatus.SUCCESS,
                sameStartedAt,
                1
        );
        Long sameTimeSecond = mapper.insertRun(
                IngestionTriggerType.SCHEDULED,
                IngestionRunStatus.SUCCESS,
                sameStartedAt,
                1
        );

        List<RssIngestionRun> runs = mapper.findRecentRuns(10);

        assertEquals(List.of(sameTimeSecond, sameTimeFirst, newer, older), runs.stream().map(RssIngestionRun::id).toList());
    }
}
