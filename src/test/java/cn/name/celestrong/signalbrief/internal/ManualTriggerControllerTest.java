package cn.name.celestrong.signalbrief.internal;

import cn.name.celestrong.signalbrief.brief.BriefGenerationService;
import cn.name.celestrong.signalbrief.ingestion.FeedIngestionResult;
import cn.name.celestrong.signalbrief.ingestion.FeedIngestionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    private RecordingFeedIngestionService feedIngestionService;

    @Autowired
    private RecordingBriefGenerationService briefGenerationService;

    @Test
    void triggersRssIngestion() throws Exception {
        mockMvc.perform(post("/internal/ingestions/rss"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceCount").value(2))
                .andExpect(jsonPath("$.fetchedCount").value(8))
                .andExpect(jsonPath("$.insertedCount").value(3))
                .andExpect(jsonPath("$.skippedCount").value(5))
                .andExpect(jsonPath("$.failedSourceCount").value(1));

        assertEquals(1, feedIngestionService.calls);
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
        RecordingFeedIngestionService feedIngestionService() {
            return new RecordingFeedIngestionService();
        }

        @Bean
        RecordingBriefGenerationService briefGenerationService() {
            return new RecordingBriefGenerationService();
        }
    }

    static class RecordingFeedIngestionService extends FeedIngestionService {

        private int calls;

        RecordingFeedIngestionService() {
            super(null, null, null, null);
        }

        @Override
        public FeedIngestionResult ingestEnabledFeeds() {
            calls++;
            return new FeedIngestionResult(2, 8, 3, 5, 1);
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
