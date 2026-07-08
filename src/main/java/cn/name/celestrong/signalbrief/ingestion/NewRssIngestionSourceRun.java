package cn.name.celestrong.signalbrief.ingestion;

import cn.name.celestrong.signalbrief.article.ArticleCategory;
import cn.name.celestrong.signalbrief.feed.FeedFetchFailureType;

import java.time.Instant;

/**
 * 新增 RSS 源级运行记录命令。
 *
 * <p>该模型只用于写库，和查询模型分离，避免新增审计字段时影响内部接口响应。</p>
 */
public record NewRssIngestionSourceRun(
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
        long durationMillis
) {
}
