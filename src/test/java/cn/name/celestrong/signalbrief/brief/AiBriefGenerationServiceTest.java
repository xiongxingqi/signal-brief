package cn.name.celestrong.signalbrief.brief;

import cn.name.celestrong.signalbrief.ai.AiSummaryService;
import cn.name.celestrong.signalbrief.ai.AiSummaryUnavailableException;
import cn.name.celestrong.signalbrief.article.Article;
import cn.name.celestrong.signalbrief.article.ArticleQueryService;
import cn.name.celestrong.signalbrief.config.AiSummaryProperties;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AiBriefGenerationServiceTest {

    @Test
    void generatesDraftBeforeSummarizingWithAi() {
        Instant startInclusive = Instant.parse("2026-07-01T00:00:00Z");
        Instant endExclusive = Instant.parse("2026-07-16T00:00:00Z");
        List<String> events = new ArrayList<>();
        RecordingBriefGenerationService briefGenerationService = new RecordingBriefGenerationService(events);
        RecordingAiSummaryService aiSummaryService = new RecordingAiSummaryService(events);
        AiBriefGenerationService service = new AiBriefGenerationService(briefGenerationService, aiSummaryService);

        AiBriefGenerationResult result = service.generate(startInclusive, endExclusive);

        assertEquals(startInclusive, result.startInclusive());
        assertEquals(endExclusive, result.endExclusive());
        assertEquals("# 草稿", result.draftMarkdown());
        assertEquals("## 摘要", result.summaryMarkdown());
        assertEquals(startInclusive, briefGenerationService.startInclusive);
        assertEquals(endExclusive, briefGenerationService.endExclusive);
        assertEquals("# 草稿", aiSummaryService.markdownDraft);
        assertEquals(List.of("draft", "summary:# 草稿"), events);
    }

    @Test
    void rejectsMissingCollaborators() {
        RecordingBriefGenerationService briefGenerationService = new RecordingBriefGenerationService(new ArrayList<>());
        RecordingAiSummaryService aiSummaryService = new RecordingAiSummaryService(new ArrayList<>());

        assertThrows(
                NullPointerException.class,
                () -> new AiBriefGenerationService(null, aiSummaryService)
        );
        assertThrows(
                NullPointerException.class,
                () -> new AiBriefGenerationService(briefGenerationService, null)
        );
    }

    @Test
    void checksAiAvailabilityBeforeGeneratingDraft() {
        List<String> events = new ArrayList<>();
        RecordingBriefGenerationService briefGenerationService = new RecordingBriefGenerationService(events);
        DisabledAiSummaryService aiSummaryService = new DisabledAiSummaryService();
        AiBriefGenerationService service = new AiBriefGenerationService(briefGenerationService, aiSummaryService);

        AiSummaryUnavailableException exception = assertThrows(
                AiSummaryUnavailableException.class,
                () -> service.generate(
                        Instant.parse("2026-07-01T00:00:00Z"),
                        Instant.parse("2026-07-16T00:00:00Z")
                )
        );

        assertEquals("AI 摘要能力未启用", exception.getMessage());
        assertEquals(List.of(), events);
        assertEquals(0, briefGenerationService.calls);
    }

    private static class RecordingBriefGenerationService extends BriefGenerationService {

        private final List<String> events;
        private int calls;
        private Instant startInclusive;
        private Instant endExclusive;

        private RecordingBriefGenerationService(List<String> events) {
            super(
                    new ArticleQueryService((startInclusive, endExclusive) -> {
                        throw new AssertionError("测试 fake 会覆盖 generate，不应查询文章");
                    }),
                    new BriefMarkdownRenderer() {
                        @Override
                        public String render(
                                Instant startInclusive,
                                Instant endExclusive,
                                List<Article> articles
                        ) {
                            throw new AssertionError("测试 fake 会覆盖 generate，不应渲染 Markdown");
                        }
                    }
            );
            this.events = events;
        }

        @Override
        public String generate(Instant startInclusive, Instant endExclusive) {
            calls++;
            this.startInclusive = startInclusive;
            this.endExclusive = endExclusive;
            events.add("draft");
            return "# 草稿";
        }
    }

    private static class RecordingAiSummaryService extends AiSummaryService {

        private final List<String> events;
        private String markdownDraft;

        private RecordingAiSummaryService(List<String> events) {
            super(
                    new AiSummaryProperties(
                            true,
                            "https://api.example.com/v1",
                            "test-key",
                            "test-model",
                            null,
                            null,
                            null,
                            null
                    ),
                    (systemPrompt, userContent) -> {
                        throw new AssertionError("测试 fake 会覆盖 summarizeMarkdown，不应调用底层 client");
                    }
            );
            this.events = events;
        }

        @Override
        public String summarizeMarkdown(String markdownDraft) {
            this.markdownDraft = markdownDraft;
            events.add("summary:" + markdownDraft);
            return "## 摘要";
        }
    }

    private static class DisabledAiSummaryService extends AiSummaryService {

        private DisabledAiSummaryService() {
            super(
                    new AiSummaryProperties(false, null, null, null, null, null, null, null),
                    (systemPrompt, userContent) -> {
                        throw new AssertionError("AI 不可用时不应调用底层 client");
                    }
            );
        }

        @Override
        public void requireAvailable() {
            throw new AiSummaryUnavailableException("AI 摘要能力未启用");
        }
    }
}
