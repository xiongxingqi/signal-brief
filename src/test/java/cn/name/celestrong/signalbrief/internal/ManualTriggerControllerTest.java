package cn.name.celestrong.signalbrief.internal;

import cn.name.celestrong.signalbrief.article.ArticleCategory;
import cn.name.celestrong.signalbrief.brief.BriefGenerationService;
import cn.name.celestrong.signalbrief.ingestion.FeedIngestionOperations;
import cn.name.celestrong.signalbrief.ingestion.FeedIngestionResult;
import cn.name.celestrong.signalbrief.ingestion.IngestionRunStatus;
import cn.name.celestrong.signalbrief.ingestion.IngestionSourceRunStatus;
import cn.name.celestrong.signalbrief.ingestion.IngestionTriggerType;
import cn.name.celestrong.signalbrief.ingestion.RssIngestionRun;
import cn.name.celestrong.signalbrief.ingestion.RssIngestionRunDetail;
import cn.name.celestrong.signalbrief.ingestion.RssIngestionRunNotFoundException;
import cn.name.celestrong.signalbrief.ingestion.RssIngestionRunQueryService;
import cn.name.celestrong.signalbrief.ingestion.RssIngestionSourceRun;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = ManualTriggerController.class,
        properties = "signal-brief.internal-api.enabled=true"
)
@Import({
        ManualTriggerExceptionHandler.class,
        ManualTriggerControllerTest.InternalApiTestConfiguration.class
})
class ManualTriggerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RecordingFeedIngestionOperations feedIngestionOperations;

    @Autowired
    private RecordingBriefGenerationService briefGenerationService;

    @Autowired
    private RecordingRssIngestionRunQueryService runQueryService;

    @Test
    void triggersRssIngestion() throws Exception {
        mockMvc.perform(post("/internal/ingestions/rss"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runId").value(12))
                .andExpect(jsonPath("$.sourceCount").value(2))
                .andExpect(jsonPath("$.fetchedCount").value(8))
                .andExpect(jsonPath("$.insertedCount").value(3))
                .andExpect(jsonPath("$.skippedCount").value(5))
                .andExpect(jsonPath("$.failedSourceCount").value(1));

        assertEquals(1, feedIngestionOperations.calls);
        assertEquals(IngestionTriggerType.MANUAL, feedIngestionOperations.triggerType);
    }

    @Test
    void listsRssIngestionRuns() throws Exception {
        mockMvc.perform(get("/internal/ingestions/rss/runs").param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(12))
                .andExpect(jsonPath("$[0].status").value("PARTIAL_SUCCESS"));

        assertEquals(5, runQueryService.limit);
    }

    @Test
    void getsRssIngestionRunDetail() throws Exception {
        mockMvc.perform(get("/internal/ingestions/rss/runs/12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.run.id").value(12))
                .andExpect(jsonPath("$.sources[0].sourceName").value("Spring Blog"));
    }

    @Test
    void returnsNotFoundWhenRssIngestionRunMissing() throws Exception {
        mockMvc.perform(get("/internal/ingestions/rss/runs/404"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("RSS 入库运行记录不存在: 404"));
    }

    @Test
    void rejectsInvalidRssIngestionRunLimit() throws Exception {
        mockMvc.perform(get("/internal/ingestions/rss/runs").param("limit", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("请求参数格式不正确"));
    }

    @Test
    void rejectsInvalidRssIngestionRunId() throws Exception {
        mockMvc.perform(get("/internal/ingestions/rss/runs/not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("请求参数格式不正确"));
    }

    @Test
    void generatesMarkdownBriefForRequestedWindow() throws Exception {
        mockMvc.perform(post("/internal/briefs/markdown")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "startInclusive": "2026-07-01T00:00:00Z",
                                  "endExclusive": "2026-07-16T00:00:00Z"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.startInclusive").value("2026-07-01T00:00:00Z"))
                .andExpect(jsonPath("$.endExclusive").value("2026-07-16T00:00:00Z"))
                .andExpect(jsonPath("$.markdown").value("# SignalBrief 技术半月报\n"));

        assertEquals(1, briefGenerationService.calls);
        assertEquals(Instant.parse("2026-07-01T00:00:00Z"), briefGenerationService.startInclusive);
        assertEquals(Instant.parse("2026-07-16T00:00:00Z"), briefGenerationService.endExclusive);
    }

    @Test
    void rejectsMissingMarkdownWindowField() throws Exception {
        mockMvc.perform(post("/internal/briefs/markdown")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "startInclusive": "2026-07-01T00:00:00Z"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("startInclusive 和 endExclusive 必须提供"));
    }

    @Test
    void rejectsInvalidMarkdownWindow() throws Exception {
        mockMvc.perform(post("/internal/briefs/markdown")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "startInclusive": "2026-07-16T00:00:00Z",
                                  "endExclusive": "2026-07-01T00:00:00Z"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Brief candidate start time must be before end time"));
    }

    @Test
    void rejectsMalformedMarkdownWindow() throws Exception {
        mockMvc.perform(post("/internal/briefs/markdown")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "startInclusive": "not-an-instant",
                                  "endExclusive": "2026-07-16T00:00:00Z"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("请求体格式不正确"));
    }

    @Test
    void mapsUnexpectedExceptionToServerError() throws Exception {
        briefGenerationService.failUnexpectedly = true;

        mockMvc.perform(post("/internal/briefs/markdown")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "startInclusive": "2026-07-01T00:00:00Z",
                                  "endExclusive": "2026-07-16T00:00:00Z"
                                }
                                """))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("内部接口执行失败"));
    }

    @TestConfiguration
    static class InternalApiTestConfiguration {

        @Bean
        RecordingFeedIngestionOperations feedIngestionOperations() {
            return new RecordingFeedIngestionOperations();
        }

        @Bean
        RecordingBriefGenerationService briefGenerationService() {
            return new RecordingBriefGenerationService();
        }

        @Bean
        RecordingRssIngestionRunQueryService runQueryService() {
            return new RecordingRssIngestionRunQueryService();
        }
    }

    static class RecordingFeedIngestionOperations implements FeedIngestionOperations {

        private int calls;
        private IngestionTriggerType triggerType;

        @Override
        public FeedIngestionResult ingestEnabledFeeds(IngestionTriggerType triggerType) {
            calls++;
            this.triggerType = triggerType;
            return new FeedIngestionResult(12L, 2, 8, 3, 5, 1);
        }
    }

    static class RecordingRssIngestionRunQueryService extends RssIngestionRunQueryService {

        private Integer limit;

        RecordingRssIngestionRunQueryService() {
            super(null);
        }

        @Override
        public List<RssIngestionRun> findRecentRuns(Integer limit) {
            this.limit = limit;
            return List.of(run());
        }

        @Override
        public RssIngestionRunDetail findRunDetail(Long runId) {
            if (runId.equals(404L)) {
                throw new RssIngestionRunNotFoundException(runId);
            }
            return new RssIngestionRunDetail(run(), List.of(sourceRun()));
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
                    null,
                    null,
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

    static class RecordingBriefGenerationService extends BriefGenerationService {

        private int calls;
        private Instant startInclusive;
        private Instant endExclusive;
        private boolean failUnexpectedly;

        RecordingBriefGenerationService() {
            super(null, null);
        }

        @Override
        public String generate(Instant startInclusive, Instant endExclusive) {
            if (failUnexpectedly) {
                throw new IllegalStateException("boom");
            }
            if (!startInclusive.isBefore(endExclusive)) {
                throw new IllegalArgumentException("Brief candidate start time must be before end time");
            }
            calls++;
            this.startInclusive = startInclusive;
            this.endExclusive = endExclusive;
            return "# SignalBrief 技术半月报\n";
        }
    }
}
