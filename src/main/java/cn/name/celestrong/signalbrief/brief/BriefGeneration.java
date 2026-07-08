package cn.name.celestrong.signalbrief.brief;

import java.time.Instant;

/**
 * AI 简报归档记录。
 *
 * <p>草稿、摘要和失败摘要共存于同一模型，调用方通过 {@link BriefGenerationStatus} 判断当前可用字段。</p>
 */
public record BriefGeneration(
        Long id,
        Instant startInclusive,
        Instant endExclusive,
        BriefGenerationStatus status,
        String draftMarkdown,
        String summaryMarkdown,
        String errorSummary,
        Instant createdAt,
        Instant updatedAt,
        Instant completedAt
) {
}
