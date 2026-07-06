package cn.name.celestrong.signalbrief.brief;

import java.time.Instant;

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
