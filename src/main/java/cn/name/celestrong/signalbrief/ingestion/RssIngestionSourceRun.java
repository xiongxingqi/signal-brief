package cn.name.celestrong.signalbrief.ingestion;

import cn.name.celestrong.signalbrief.article.ArticleCategory;
import cn.name.celestrong.signalbrief.feed.FeedFetchFailureType;

import java.time.Instant;

/**
 * 单个 RSS 源在一次入库批次中的运行记录。
 */
public record RssIngestionSourceRun(
        Long id,
        Long runId,
        String sourceName,
        String sourceUrl,
        ArticleCategory category,
        IngestionSourceRunStatus status,
        FeedFetchFailureType failureType,
        Integer httpStatus,
        Integer attemptCount,
        Integer maxAttempts,
        String errorMessage,
        int fetchedCount,
        int insertedCount,
        int skippedCount,
        Instant startedAt,
        Instant finishedAt,
        long durationMillis,
        Instant createdAt
) {
}
