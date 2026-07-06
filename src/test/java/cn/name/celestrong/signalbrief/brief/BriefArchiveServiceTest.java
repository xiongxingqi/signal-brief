package cn.name.celestrong.signalbrief.brief;

import cn.name.celestrong.signalbrief.ai.AiSummaryException;
import cn.name.celestrong.signalbrief.ai.AiSummaryService;
import cn.name.celestrong.signalbrief.ai.AiSummaryUnavailableException;
import cn.name.celestrong.signalbrief.article.Article;
import cn.name.celestrong.signalbrief.article.ArticleQueryService;
import cn.name.celestrong.signalbrief.config.AiSummaryProperties;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BriefArchiveServiceTest {

    private static final Instant START_INCLUSIVE = Instant.parse("2026-07-01T00:00:00Z");
    private static final Instant END_EXCLUSIVE = Instant.parse("2026-07-16T00:00:00Z");
    private static final Instant COMPLETED_AT = Instant.parse("2026-07-16T01:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(COMPLETED_AT, ZoneOffset.UTC);

    private static BriefArchiveService archiveService(
            BriefGenerationService briefGenerationService,
            AiSummaryService aiSummaryService,
            BriefGenerationMapper mapper
    ) {
        return archiveService(briefGenerationService, aiSummaryService, mapper, FIXED_CLOCK);
    }

    private static BriefArchiveService archiveService(
            BriefGenerationService briefGenerationService,
            AiSummaryService aiSummaryService,
            BriefGenerationMapper mapper,
            Clock clock
    ) {
        return new BriefArchiveService(
                briefGenerationService,
                aiSummaryService,
                mapper,
                clock
        );
    }

    @Test
    void archivesSuccessfulAiSummary() {
        RecordingBriefGenerationService briefService = new RecordingBriefGenerationService("# draft\n");
        RecordingAiSummaryService aiService = new RecordingAiSummaryService("## summary\n");
        RecordingBriefGenerationMapper mapper = new RecordingBriefGenerationMapper();
        BriefArchiveService service = archiveService(briefService, aiService, mapper);

        BriefGeneration archive = service.archiveAiSummary(START_INCLUSIVE, END_EXCLUSIVE);

        assertEquals(BriefGenerationStatus.SUCCESS, archive.status());
        assertEquals("# draft\n", archive.draftMarkdown());
        assertEquals("## summary\n", archive.summaryMarkdown());
        assertEquals(1, mapper.insertCalls);
        assertEquals(1, mapper.successCalls);
        assertEquals(COMPLETED_AT, mapper.completedAt);
        assertEquals(0, mapper.failedCalls);
    }

    @Test
    void savesFailedArchiveWhenProviderFails() {
        RecordingBriefGenerationService briefService = new RecordingBriefGenerationService("# draft\n");
        AiSummaryException providerFailure = new AiSummaryException("provider down");
        RecordingAiSummaryService aiService = new RecordingAiSummaryService(providerFailure);
        RecordingBriefGenerationMapper mapper = new RecordingBriefGenerationMapper();
        BriefArchiveService service = archiveService(briefService, aiService, mapper);

        BriefArchiveGenerationException exception = assertThrows(
                BriefArchiveGenerationException.class,
                () -> service.archiveAiSummary(START_INCLUSIVE, END_EXCLUSIVE)
        );

        assertEquals(100L, exception.briefGenerationId());
        assertSame(providerFailure, exception.getCause());
        assertEquals(1, mapper.failedCalls);
        assertEquals("provider down", mapper.errorSummary);
    }

    @Test
    void savesFailedArchiveWhenAiSummaryThrowsRuntimeException() {
        RecordingBriefGenerationService briefService = new RecordingBriefGenerationService("# draft\n");
        IllegalArgumentException providerFailure = new IllegalArgumentException("bad draft");
        RecordingAiSummaryService aiService = new RecordingAiSummaryService(providerFailure);
        RecordingBriefGenerationMapper mapper = new RecordingBriefGenerationMapper();
        BriefArchiveService service = archiveService(briefService, aiService, mapper);

        BriefArchiveGenerationException exception = assertThrows(
                BriefArchiveGenerationException.class,
                () -> service.archiveAiSummary(START_INCLUSIVE, END_EXCLUSIVE)
        );

        assertEquals(100L, exception.briefGenerationId());
        assertSame(providerFailure, exception.getCause());
        assertEquals(1, mapper.failedCalls);
        assertEquals("bad draft", mapper.errorSummary);
    }

    @Test
    void doesNotCreateArchiveWhenAiSummaryIsDisabled() {
        RecordingBriefGenerationService briefService = new RecordingBriefGenerationService("# draft\n");
        RecordingAiSummaryService aiService = new RecordingAiSummaryService(
                new AiSummaryUnavailableException("AI 摘要能力未启用")
        );
        RecordingBriefGenerationMapper mapper = new RecordingBriefGenerationMapper();
        BriefArchiveService service = archiveService(briefService, aiService, mapper, Clock.systemUTC());

        assertThrows(
                AiSummaryUnavailableException.class,
                () -> service.archiveAiSummary(START_INCLUSIVE, END_EXCLUSIVE)
        );
        assertEquals(0, briefService.calls);
        assertEquals(0, mapper.insertCalls);
    }

    @Test
    void failsWhenSuccessfulArchiveCannotBeMarkedSuccessful() {
        RecordingBriefGenerationService briefService = new RecordingBriefGenerationService("# draft\n");
        RecordingAiSummaryService aiService = new RecordingAiSummaryService("## summary\n");
        RecordingBriefGenerationMapper mapper = new RecordingBriefGenerationMapper();
        mapper.successUpdateCount = 0;
        BriefArchiveService service = archiveService(briefService, aiService, mapper);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service.archiveAiSummary(START_INCLUSIVE, END_EXCLUSIVE)
        );

        assertEquals("简报归档状态更新失败: 100", exception.getMessage());
        assertEquals(1, mapper.successCalls);
        assertEquals(0, mapper.failedCalls);
    }

    @Test
    void failsWhenUpdatedArchiveCannotBeLoaded() {
        RecordingBriefGenerationService briefService = new RecordingBriefGenerationService("# draft\n");
        RecordingAiSummaryService aiService = new RecordingAiSummaryService("## summary\n");
        RecordingBriefGenerationMapper mapper = new RecordingBriefGenerationMapper();
        mapper.findByIdEmpty = true;
        BriefArchiveService service = archiveService(briefService, aiService, mapper);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service.archiveAiSummary(START_INCLUSIVE, END_EXCLUSIVE)
        );

        assertEquals("简报归档记录不存在: 100", exception.getMessage());
        assertEquals(1, mapper.successCalls);
        assertEquals(1, mapper.findByIdCalls);
    }

    @Test
    void failsWhenFailedArchiveCannotBeMarkedFailed() {
        RecordingBriefGenerationService briefService = new RecordingBriefGenerationService("# draft\n");
        AiSummaryException providerFailure = new AiSummaryException("provider down");
        RecordingAiSummaryService aiService = new RecordingAiSummaryService(providerFailure);
        RecordingBriefGenerationMapper mapper = new RecordingBriefGenerationMapper();
        mapper.failedUpdateCount = 0;
        BriefArchiveService service = archiveService(briefService, aiService, mapper);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service.archiveAiSummary(START_INCLUSIVE, END_EXCLUSIVE)
        );

        assertEquals("简报归档失败状态更新失败: 100", exception.getMessage());
        assertSame(providerFailure, exception.getCause());
        assertEquals(1, mapper.failedCalls);
    }

    @Test
    void savesFallbackErrorSummaryWhenProviderMessageIsBlank() {
        RecordingBriefGenerationService briefService = new RecordingBriefGenerationService("# draft\n");
        RecordingAiSummaryService aiService = new RecordingAiSummaryService(new AiSummaryException(" "));
        RecordingBriefGenerationMapper mapper = new RecordingBriefGenerationMapper();
        BriefArchiveService service = archiveService(briefService, aiService, mapper);

        assertThrows(
                BriefArchiveGenerationException.class,
                () -> service.archiveAiSummary(START_INCLUSIVE, END_EXCLUSIVE)
        );

        assertEquals("AI 摘要生成失败", mapper.errorSummary);
    }

    @Test
    void savesFallbackErrorSummaryWhenRuntimeExceptionMessageIsBlank() {
        RecordingBriefGenerationService briefService = new RecordingBriefGenerationService("# draft\n");
        RecordingAiSummaryService aiService = new RecordingAiSummaryService(new IllegalArgumentException(" "));
        RecordingBriefGenerationMapper mapper = new RecordingBriefGenerationMapper();
        BriefArchiveService service = archiveService(briefService, aiService, mapper);

        assertThrows(
                BriefArchiveGenerationException.class,
                () -> service.archiveAiSummary(START_INCLUSIVE, END_EXCLUSIVE)
        );

        assertEquals("AI 摘要生成失败", mapper.errorSummary);
    }

    @Test
    void truncatesLongProviderErrorSummary() {
        RecordingBriefGenerationService briefService = new RecordingBriefGenerationService("# draft\n");
        String message = "x".repeat(1_001);
        RecordingAiSummaryService aiService = new RecordingAiSummaryService(new AiSummaryException(message));
        RecordingBriefGenerationMapper mapper = new RecordingBriefGenerationMapper();
        BriefArchiveService service = archiveService(briefService, aiService, mapper);

        assertThrows(
                BriefArchiveGenerationException.class,
                () -> service.archiveAiSummary(START_INCLUSIVE, END_EXCLUSIVE)
        );

        assertEquals(1_000, mapper.errorSummary.length());
        assertEquals("x".repeat(1_000), mapper.errorSummary);
    }

    private static class RecordingBriefGenerationService extends BriefGenerationService {

        private final String draftMarkdown;
        private int calls;

        private RecordingBriefGenerationService(String draftMarkdown) {
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
            this.draftMarkdown = draftMarkdown;
        }

        @Override
        public String generate(Instant startInclusive, Instant endExclusive) {
            calls++;
            return draftMarkdown;
        }
    }

    private static class RecordingAiSummaryService extends AiSummaryService {

        private final String summaryMarkdown;
        private final RuntimeException exception;

        private RecordingAiSummaryService(String summaryMarkdown) {
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
            this.summaryMarkdown = summaryMarkdown;
            this.exception = null;
        }

        private RecordingAiSummaryService(RuntimeException exception) {
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
            this.summaryMarkdown = null;
            this.exception = exception;
        }

        @Override
        public void requireAvailable() {
            if (exception instanceof AiSummaryUnavailableException unavailableException) {
                throw unavailableException;
            }
        }

        @Override
        public String summarizeMarkdown(String markdownDraft) {
            if (exception != null) {
                throw exception;
            }
            return summaryMarkdown;
        }
    }

    private static class RecordingBriefGenerationMapper implements BriefGenerationMapper {

        private int insertCalls;
        private int successCalls;
        private int failedCalls;
        private int findByIdCalls;
        private int successUpdateCount = 1;
        private int failedUpdateCount = 1;
        private boolean findByIdEmpty;
        private Long id;
        private Instant startInclusive;
        private Instant endExclusive;
        private BriefGenerationStatus status;
        private String draftMarkdown;
        private String summaryMarkdown;
        private String errorSummary;
        private Instant completedAt;

        @Override
        public Long insertGenerating(Instant startInclusive, Instant endExclusive, String draftMarkdown) {
            insertCalls++;
            id = 100L;
            this.startInclusive = startInclusive;
            this.endExclusive = endExclusive;
            this.draftMarkdown = draftMarkdown;
            status = BriefGenerationStatus.GENERATING;
            return id;
        }

        @Override
        public int markSuccess(Long id, String summaryMarkdown, Instant completedAt) {
            successCalls++;
            assertEquals(100L, id);
            this.summaryMarkdown = summaryMarkdown;
            this.completedAt = completedAt;
            if (successUpdateCount == 1) {
                status = BriefGenerationStatus.SUCCESS;
                errorSummary = null;
            }
            return successUpdateCount;
        }

        @Override
        public int markFailed(Long id, String errorSummary, Instant completedAt) {
            failedCalls++;
            assertEquals(100L, id);
            this.errorSummary = errorSummary;
            this.completedAt = completedAt;
            if (failedUpdateCount == 1) {
                status = BriefGenerationStatus.FAILED;
                summaryMarkdown = null;
            }
            return failedUpdateCount;
        }

        @Override
        public Optional<BriefGeneration> findById(Long id) {
            findByIdCalls++;
            if (findByIdEmpty) {
                return Optional.empty();
            }
            return Optional.of(new BriefGeneration(
                    this.id,
                    startInclusive,
                    endExclusive,
                    status,
                    draftMarkdown,
                    summaryMarkdown,
                    errorSummary,
                    Instant.parse("2026-07-16T00:00:00Z"),
                    completedAt,
                    completedAt
            ));
        }

        @Override
        public List<BriefGeneration> findRecent(int limit) {
            throw new UnsupportedOperationException("服务测试不应查询归档列表");
        }
    }
}
