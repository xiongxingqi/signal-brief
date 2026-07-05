package cn.name.celestrong.signalbrief.ingestion;

import java.time.Instant;

public record RssIngestionRun(
        Long id,
        IngestionTriggerType triggerType,
        IngestionRunStatus status,
        Instant startedAt,
        Instant finishedAt,
        Long durationMillis,
        int sourceCount,
        int fetchedCount,
        int insertedCount,
        int skippedCount,
        int failedSourceCount,
        Instant createdAt,
        Instant updatedAt
) {
}
