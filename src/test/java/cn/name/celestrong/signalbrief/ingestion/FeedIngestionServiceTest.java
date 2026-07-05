package cn.name.celestrong.signalbrief.ingestion;

import cn.name.celestrong.signalbrief.article.ArticleCategory;
import cn.name.celestrong.signalbrief.article.ArticleIngestionService;
import cn.name.celestrong.signalbrief.config.FeedProperties;
import cn.name.celestrong.signalbrief.feed.FetchedArticle;
import cn.name.celestrong.signalbrief.feed.FeedClient;
import cn.name.celestrong.signalbrief.feed.FeedParser;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 使用手写 fake 保留批次统计和单源失败隔离语义，避免 mock 配置掩盖编排流程。
 */
class FeedIngestionServiceTest {

    @Test
    void ingestsEnabledFeedsAndAggregatesStatistics() {
        FeedProperties properties = new FeedProperties(List.of(
                new FeedProperties.FeedSource("Enabled", URI.create("https://example.com/feed.xml"), ArticleCategory.JAVA, true),
                new FeedProperties.FeedSource("Disabled", URI.create("https://example.com/disabled.xml"), ArticleCategory.JAVA, false)
        ));
        FakeArticleIngestionService articleIngestionService = new FakeArticleIngestionService();
        FakeIngestionRunRecorder runRecorder = new FakeIngestionRunRecorder();
        FeedIngestionService service = new FeedIngestionService(
                properties,
                new FakeFeedClient(),
                new FakeFeedParser(),
                articleIngestionService,
                runRecorder
        );

        FeedIngestionResult result = service.ingestEnabledFeeds(IngestionTriggerType.MANUAL);

        assertEquals(100L, result.runId());
        assertEquals(IngestionTriggerType.MANUAL, runRecorder.triggerType);
        assertEquals(List.of(IngestionSourceRunStatus.SUCCESS), runRecorder.sourceStatuses);
        assertEquals(IngestionRunStatus.SUCCESS, runRecorder.finishedBatchStatus);
        assertEquals(1, result.sourceCount());
        assertEquals(2, result.fetchedCount());
        assertEquals(1, result.insertedCount());
        assertEquals(1, result.skippedCount());
        assertEquals(0, result.failedSourceCount());
        assertEquals(2, articleIngestionService.calls);
    }

    @Test
    void continuesWhenOneSourceFails() {
        FeedProperties properties = new FeedProperties(List.of(
                new FeedProperties.FeedSource("Failing", URI.create("https://example.com/failing.xml"), ArticleCategory.JAVA, true),
                new FeedProperties.FeedSource("Enabled", URI.create("https://example.com/feed.xml"), ArticleCategory.JAVA, true)
        ));
        FakeArticleIngestionService articleIngestionService = new FakeArticleIngestionService();
        FakeIngestionRunRecorder runRecorder = new FakeIngestionRunRecorder();
        FeedIngestionService service = new FeedIngestionService(
                properties,
                new FakeFeedClient(),
                new FakeFeedParser(),
                articleIngestionService,
                runRecorder
        );

        FeedIngestionResult result = service.ingestEnabledFeeds(IngestionTriggerType.MANUAL);

        assertEquals(100L, result.runId());
        assertEquals(List.of(IngestionSourceRunStatus.FAILED, IngestionSourceRunStatus.SUCCESS), runRecorder.sourceStatuses);
        assertEquals(IngestionRunStatus.PARTIAL_SUCCESS, runRecorder.finishedBatchStatus);
        assertEquals(2, result.sourceCount());
        assertEquals(2, result.fetchedCount());
        assertEquals(1, result.failedSourceCount());
        assertEquals(2, articleIngestionService.calls);
    }

    @Test
    void preservesFinishRunFailureWhenFailRunAlsoFails() {
        FeedProperties properties = new FeedProperties(List.of(
                new FeedProperties.FeedSource("Enabled", URI.create("https://example.com/feed.xml"), ArticleCategory.JAVA, true)
        ));
        FakeIngestionRunRecorder runRecorder = new FakeIngestionRunRecorder();
        RuntimeException finishRunException = new IllegalStateException("finish run failed");
        RuntimeException failRunException = new IllegalStateException("fail run failed");
        runRecorder.finishRunException = finishRunException;
        runRecorder.failRunException = failRunException;
        FeedIngestionService service = new FeedIngestionService(
                properties,
                new FakeFeedClient(),
                new FakeFeedParser(),
                new FakeArticleIngestionService(),
                runRecorder
        );

        RuntimeException thrown = assertThrows(
                RuntimeException.class,
                () -> service.ingestEnabledFeeds(IngestionTriggerType.MANUAL)
        );

        assertSame(finishRunException, thrown);
        assertEquals(1, thrown.getSuppressed().length);
        assertSame(failRunException, thrown.getSuppressed()[0]);
    }

    @Test
    void preservesSourceFailureWhenRecordingSourceFailureFails() {
        RuntimeException sourceException = new IllegalStateException("feed unavailable");
        RuntimeException recorderException = new IllegalStateException("record source failure failed");
        RuntimeException failRunException = new IllegalStateException("fail run failed");
        FeedProperties properties = new FeedProperties(List.of(
                new FeedProperties.FeedSource("Failing", URI.create("https://example.com/failing.xml"), ArticleCategory.JAVA, true)
        ));
        FakeIngestionRunRecorder runRecorder = new FakeIngestionRunRecorder();
        runRecorder.recordSourceFailureException = recorderException;
        runRecorder.failRunException = failRunException;
        FeedIngestionService service = new FeedIngestionService(
                properties,
                new FakeFeedClient(sourceException),
                new FakeFeedParser(),
                new FakeArticleIngestionService(),
                runRecorder
        );

        RuntimeException thrown = assertThrows(
                RuntimeException.class,
                () -> service.ingestEnabledFeeds(IngestionTriggerType.MANUAL)
        );

        assertSame(sourceException, thrown);
        assertEquals(2, thrown.getSuppressed().length);
        assertSame(recorderException, thrown.getSuppressed()[0]);
        assertSame(failRunException, thrown.getSuppressed()[1]);
        assertEquals(1, runRecorder.failedRunResult.sourceCount());
        assertEquals(1, runRecorder.failedRunResult.failedSourceCount());
    }

    @Test
    void preservesSourceSuccessRecordFailureAsBatchFailure() {
        RuntimeException sourceSuccessException = new IllegalStateException("source success record failed");
        RuntimeException failRunException = new IllegalStateException("fail run failed");
        FeedProperties properties = new FeedProperties(List.of(
                new FeedProperties.FeedSource("Enabled", URI.create("https://example.com/feed.xml"), ArticleCategory.JAVA, true)
        ));
        FakeIngestionRunRecorder runRecorder = new FakeIngestionRunRecorder();
        runRecorder.recordSourceSuccessException = sourceSuccessException;
        runRecorder.failRunException = failRunException;
        FeedIngestionService service = new FeedIngestionService(
                properties,
                new FakeFeedClient(),
                new FakeFeedParser(),
                new FakeArticleIngestionService(),
                runRecorder
        );

        RuntimeException thrown = assertThrows(
                RuntimeException.class,
                () -> service.ingestEnabledFeeds(IngestionTriggerType.MANUAL)
        );

        assertSame(sourceSuccessException, thrown);
        assertEquals(1, thrown.getSuppressed().length);
        assertSame(failRunException, thrown.getSuppressed()[0]);
        assertEquals(1, runRecorder.failedRunResult.sourceCount());
        assertEquals(2, runRecorder.failedRunResult.fetchedCount());
        assertEquals(1, runRecorder.failedRunResult.insertedCount());
        assertEquals(1, runRecorder.failedRunResult.skippedCount());
        assertEquals(0, runRecorder.failedRunResult.failedSourceCount());
    }

    private static final class FakeFeedClient implements FeedClient {
        private final RuntimeException failureException;

        private FakeFeedClient() {
            this(new IllegalStateException("feed unavailable"));
        }

        private FakeFeedClient(RuntimeException failureException) {
            this.failureException = failureException;
        }

        @Override
        public InputStream fetch(FeedProperties.FeedSource source) {
            if ("Failing".equals(source.name())) {
                throw failureException;
            }
            return new ByteArrayInputStream("<feed/>".getBytes(StandardCharsets.UTF_8));
        }
    }

    private static final class FakeFeedParser implements FeedParser {
        @Override
        public List<FetchedArticle> parse(FeedProperties.FeedSource source, InputStream inputStream) {
            try {
                assertEquals("<feed/>", new String(inputStream.readAllBytes(), StandardCharsets.UTF_8));
            } catch (IOException ex) {
                throw new AssertionError(ex);
            }
            return List.of(
                    article(source, "guid-1"),
                    article(source, "guid-2")
            );
        }

        private FetchedArticle article(FeedProperties.FeedSource source, String guid) {
            return new FetchedArticle(
                    source.name(),
                    source.url(),
                    source.category(),
                    "Title " + guid,
                    "https://example.com/" + guid,
                    guid,
                    Instant.parse("2026-07-03T00:00:00Z"),
                    "Summary"
            );
        }
    }

    private static final class FakeArticleIngestionService extends ArticleIngestionService {
        private int calls;

        private FakeArticleIngestionService() {
            super(null, null);
        }

        @Override
        public Result ingest(FetchedArticle article) {
            calls++;
            return "guid-1".equals(article.guid()) ? new Result(1, 0) : new Result(0, 1);
        }
    }

    private static final class FakeIngestionRunRecorder extends IngestionRunRecorder {
        private IngestionTriggerType triggerType;
        private final List<IngestionSourceRunStatus> sourceStatuses = new ArrayList<>();
        private IngestionRunStatus finishedBatchStatus;
        private RuntimeException recordSourceSuccessException;
        private RuntimeException recordSourceFailureException;
        private RuntimeException finishRunException;
        private RuntimeException failRunException;
        private FeedIngestionResult failedRunResult;

        private FakeIngestionRunRecorder() {
            super(null);
        }

        @Override
        public RunContext startRun(IngestionTriggerType triggerType, int sourceCount) {
            this.triggerType = triggerType;
            return new RunContext(100L, Instant.parse("2026-07-03T00:00:00Z"));
        }

        @Override
        public SourceRunContext startSource(RunContext runContext, FeedProperties.FeedSource source) {
            return new SourceRunContext(runContext.runId(), source, Instant.parse("2026-07-03T00:00:01Z"));
        }

        @Override
        public void recordSourceSuccess(SourceRunContext context, FeedIngestionResult result) {
            sourceStatuses.add(IngestionSourceRunStatus.SUCCESS);
            if (recordSourceSuccessException != null) {
                throw recordSourceSuccessException;
            }
        }

        @Override
        public void recordSourceFailure(SourceRunContext context, Exception exception) {
            sourceStatuses.add(IngestionSourceRunStatus.FAILED);
            if (recordSourceFailureException != null) {
                throw recordSourceFailureException;
            }
        }

        @Override
        public void finishRun(RunContext context, FeedIngestionResult result) {
            if (finishRunException != null) {
                throw finishRunException;
            }
            if (result.failedSourceCount() == 0) {
                finishedBatchStatus = IngestionRunStatus.SUCCESS;
            } else if (result.failedSourceCount() >= result.sourceCount()) {
                finishedBatchStatus = IngestionRunStatus.FAILED;
            } else {
                finishedBatchStatus = IngestionRunStatus.PARTIAL_SUCCESS;
            }
        }

        @Override
        public void failRun(RunContext context, FeedIngestionResult result) {
            failedRunResult = result;
            if (failRunException != null) {
                throw failRunException;
            }
            finishedBatchStatus = IngestionRunStatus.FAILED;
        }
    }
}
