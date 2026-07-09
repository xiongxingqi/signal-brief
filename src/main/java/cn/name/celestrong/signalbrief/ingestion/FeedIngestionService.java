package cn.name.celestrong.signalbrief.ingestion;

import cn.name.celestrong.signalbrief.article.ArticleIngestionService;
import cn.name.celestrong.signalbrief.config.FeedProperties;
import cn.name.celestrong.signalbrief.feed.FetchedArticle;
import cn.name.celestrong.signalbrief.feed.FeedClient;
import cn.name.celestrong.signalbrief.feed.FeedFetchException;
import cn.name.celestrong.signalbrief.feed.FeedParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.List;

/**
 * RSS 入库批次编排服务。
 *
 * <p>单个源失败只影响当前源，批次继续处理后续源，并在结果中累计失败源数量。</p>
 */
@Service
public class FeedIngestionService implements FeedIngestionOperations {

    private static final Logger log = LoggerFactory.getLogger(FeedIngestionService.class);

    private final FeedProperties feedProperties;
    private final FeedClient feedClient;
    private final FeedParser feedParser;
    private final ArticleIngestionService articleIngestionService;
    private final IngestionRunRecorder runRecorder;

    @Autowired
    public FeedIngestionService(
            FeedProperties feedProperties,
            FeedClient feedClient,
            FeedParser feedParser,
            ArticleIngestionService articleIngestionService,
            IngestionRunRecorder runRecorder
    ) {
        this.feedProperties = feedProperties;
        this.feedClient = feedClient;
        this.feedParser = feedParser;
        this.articleIngestionService = articleIngestionService;
        this.runRecorder = runRecorder;
    }

    @Override
    public FeedIngestionResult ingestEnabledFeeds(IngestionTriggerType triggerType) {
        List<FeedProperties.FeedSource> enabledFeeds = feedProperties.enabledFeeds();
        IngestionRunRecorder.RunContext runContext = runRecorder.startRun(triggerType, enabledFeeds.size());
        FeedIngestionResult result = FeedIngestionResult.empty(runContext.runId());
        log.info(
                "Feed ingestion started: runId={}, triggerType={}, sources={}",
                runContext.runId(),
                triggerType,
                enabledFeeds.size()
        );
        for (FeedProperties.FeedSource source : enabledFeeds) {
            try {
                result = result.plus(ingestSource(runContext, source));
            } catch (SourceRecordingException ex) {
                result = result.plus(ex.sourceResult());
                failRunPreservingOriginal(runContext, result, ex.original());
                throw ex.original();
            } catch (RuntimeException ex) {
                failRunPreservingOriginal(runContext, result, ex);
                throw ex;
            }
        }
        try {
            runRecorder.finishRun(runContext, result);
        } catch (RuntimeException ex) {
            failRunPreservingOriginal(runContext, result, ex);
            throw ex;
        }
        log.info(
                "Feed ingestion completed: runId={}, sources={}, fetched={}, inserted={}, skipped={}, failedSources={}",
                result.runId(),
                result.sourceCount(),
                result.fetchedCount(),
                result.insertedCount(),
                result.skippedCount(),
                result.failedSourceCount()
        );
        return result;
    }

    private FeedIngestionResult ingestSource(
            IngestionRunRecorder.RunContext runContext,
            FeedProperties.FeedSource source
    ) {
        IngestionRunRecorder.SourceRunContext sourceContext = runRecorder.startSource(runContext, source);
        FeedIngestionResult result;
        // FeedClient 契约要求调用方关闭输入流，避免远程响应或临时资源泄露。
        try (InputStream inputStream = feedClient.fetch(source)) {
            List<FetchedArticle> articles = feedParser.parse(source, inputStream);
            int insertedCount = 0;
            int skippedCount = 0;
            for (FetchedArticle article : articles) {
                ArticleIngestionService.Result articleResult = articleIngestionService.ingest(article);
                insertedCount += articleResult.insertedCount();
                skippedCount += articleResult.skippedCount();
            }
            result = new FeedIngestionResult(null, 1, articles.size(), insertedCount, skippedCount, 0);
        } catch (FeedFetchException ex) {
            log.warn(
                    "Failed to fetch feed source runId={}, name={}, url={}, failureType={}, httpStatus={}, attempts={}/{}",
                    runContext.runId(),
                    source.name(),
                    source.url(),
                    ex.failureType(),
                    ex.httpStatus(),
                    ex.attemptCount(),
                    ex.maxAttempts(),
                    ex
            );
            FeedIngestionResult failureResult = new FeedIngestionResult(null, 1, 0, 0, 0, 1);
            recordSourceFailurePreservingOriginal(sourceContext, failureResult, ex);
            return failureResult;
        } catch (Exception ex) {
            log.warn(
                    "Failed to ingest feed source runId={}, name={}, url={}",
                    runContext.runId(),
                    source.name(),
                    source.url(),
                    ex
            );
            FeedIngestionResult failureResult = new FeedIngestionResult(null, 1, 0, 0, 0, 1);
            recordSourceFailurePreservingOriginal(sourceContext, failureResult, ex);
            return failureResult;
        }
        recordSourceSuccess(sourceContext, result);
        log.info(
                "Feed source ingestion completed: runId={}, name={}, fetched={}, inserted={}, skipped={}",
                runContext.runId(),
                source.name(),
                result.fetchedCount(),
                result.insertedCount(),
                result.skippedCount()
        );
        return result;
    }

    private void recordSourceSuccess(
            IngestionRunRecorder.SourceRunContext sourceContext,
            FeedIngestionResult sourceResult
    ) {
        try {
            runRecorder.recordSourceSuccess(sourceContext, sourceResult);
        } catch (RuntimeException recorderException) {
            throw new SourceRecordingException(sourceResult, recorderException);
        }
    }

    private void failRunPreservingOriginal(
            IngestionRunRecorder.RunContext runContext,
            FeedIngestionResult result,
            RuntimeException original
    ) {
        try {
            runRecorder.failRun(runContext, result);
        } catch (RuntimeException recorderException) {
            original.addSuppressed(recorderException);
        }
    }

    private void recordSourceFailurePreservingOriginal(
            IngestionRunRecorder.SourceRunContext sourceContext,
            FeedIngestionResult sourceResult,
            Exception original
    ) {
        try {
            runRecorder.recordSourceFailure(sourceContext, original);
        } catch (RuntimeException recorderException) {
            RuntimeException runtimeOriginal = runtimeExceptionFor(original);
            runtimeOriginal.addSuppressed(recorderException);
            throw new SourceRecordingException(sourceResult, runtimeOriginal);
        }
    }

    private RuntimeException runtimeExceptionFor(Exception original) {
        if (original instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        return new IllegalStateException(original.getMessage(), original);
    }

    private static final class SourceRecordingException extends RuntimeException {
        private final FeedIngestionResult sourceResult;
        private final RuntimeException original;

        private SourceRecordingException(FeedIngestionResult sourceResult, RuntimeException original) {
            super(original.getMessage(), original);
            this.sourceResult = sourceResult;
            this.original = original;
        }

        private FeedIngestionResult sourceResult() {
            return sourceResult;
        }

        private RuntimeException original() {
            return original;
        }
    }
}
