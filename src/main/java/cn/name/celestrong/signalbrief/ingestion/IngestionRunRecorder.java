package cn.name.celestrong.signalbrief.ingestion;

import cn.name.celestrong.signalbrief.config.FeedProperties;
import cn.name.celestrong.signalbrief.feed.FeedFetchException;
import cn.name.celestrong.signalbrief.feed.FeedFetchFailureType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * RSS 入库运行记录器。
 *
 * <p>只负责把批次和单源执行结果落库，避免抓取、解析和入库编排层直接感知运行记录表结构。</p>
 */
@Service
public class IngestionRunRecorder {

    private static final int MAX_ERROR_MESSAGE_LENGTH = 1_000;

    private final RssIngestionRunMapper mapper;
    private final Clock clock;

    @Autowired
    public IngestionRunRecorder(RssIngestionRunMapper mapper) {
        this(mapper, Clock.systemUTC());
    }

    IngestionRunRecorder(RssIngestionRunMapper mapper, Clock clock) {
        this.mapper = mapper;
        this.clock = clock;
    }

    public RunContext startRun(IngestionTriggerType triggerType, int sourceCount) {
        Instant startedAt = clock.instant();
        Long runId = mapper.insertRun(
                triggerType,
                IngestionRunStatus.RUNNING,
                startedAt,
                sourceCount
        );
        return new RunContext(runId, startedAt);
    }

    public SourceRunContext startSource(RunContext runContext, FeedProperties.FeedSource source) {
        return new SourceRunContext(runContext.runId(), source, clock.instant());
    }

    public void recordSourceSuccess(SourceRunContext context, FeedIngestionResult result) {
        Instant finishedAt = clock.instant();
        mapper.insertSourceRun(new NewRssIngestionSourceRun(
                context.runId(),
                context.source().name(),
                context.source().url().toString(),
                context.source().category(),
                IngestionSourceRunStatus.SUCCESS,
                null,
                null,
                null,
                null,
                null,
                result.fetchedCount(),
                result.insertedCount(),
                result.skippedCount(),
                context.startedAt(),
                finishedAt,
                durationMillis(context.startedAt(), finishedAt)
        ));
    }

    public void recordSourceFailure(SourceRunContext context, Exception exception) {
        Instant finishedAt = clock.instant();
        FailureContext failureContext = failureContext(exception);
        mapper.insertSourceRun(new NewRssIngestionSourceRun(
                context.runId(),
                context.source().name(),
                context.source().url().toString(),
                context.source().category(),
                IngestionSourceRunStatus.FAILED,
                failureContext.failureType(),
                failureContext.httpStatus(),
                failureContext.attemptCount(),
                failureContext.maxAttempts(),
                truncateErrorMessage(exception.getMessage()),
                0,
                0,
                0,
                context.startedAt(),
                finishedAt,
                durationMillis(context.startedAt(), finishedAt)
        ));
    }

    public void finishRun(RunContext context, FeedIngestionResult result) {
        finishRun(context, statusFor(result), result);
    }

    public void failRun(RunContext context, FeedIngestionResult result) {
        finishRun(context, IngestionRunStatus.FAILED, result);
    }

    private void finishRun(RunContext context, IngestionRunStatus status, FeedIngestionResult result) {
        Instant finishedAt = clock.instant();
        mapper.finishRun(
                context.runId(),
                status,
                finishedAt,
                durationMillis(context.startedAt(), finishedAt),
                result.fetchedCount(),
                result.insertedCount(),
                result.skippedCount(),
                result.failedSourceCount()
        );
    }

    private IngestionRunStatus statusFor(FeedIngestionResult result) {
        if (result.failedSourceCount() == 0) {
            return IngestionRunStatus.SUCCESS;
        }
        if (result.failedSourceCount() >= result.sourceCount()) {
            return IngestionRunStatus.FAILED;
        }
        return IngestionRunStatus.PARTIAL_SUCCESS;
    }

    private FailureContext failureContext(Exception exception) {
        if (exception instanceof FeedFetchException fetchException) {
            return new FailureContext(
                    fetchException.failureType(),
                    fetchException.httpStatus(),
                    fetchException.attemptCount(),
                    fetchException.maxAttempts()
            );
        }
        return new FailureContext(FeedFetchFailureType.UNEXPECTED, null, null, null);
    }

    private String truncateErrorMessage(String errorMessage) {
        if (errorMessage == null || errorMessage.length() <= MAX_ERROR_MESSAGE_LENGTH) {
            return errorMessage;
        }
        return errorMessage.substring(0, MAX_ERROR_MESSAGE_LENGTH);
    }

    private long durationMillis(Instant startedAt, Instant finishedAt) {
        return Duration.between(startedAt, finishedAt).toMillis();
    }

    public record RunContext(Long runId, Instant startedAt) {
    }

    public record SourceRunContext(Long runId, FeedProperties.FeedSource source, Instant startedAt) {
    }

    private record FailureContext(
            FeedFetchFailureType failureType,
            Integer httpStatus,
            Integer attemptCount,
            Integer maxAttempts
    ) {
    }
}
