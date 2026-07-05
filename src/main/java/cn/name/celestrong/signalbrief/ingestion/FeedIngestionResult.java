package cn.name.celestrong.signalbrief.ingestion;

/**
 * RSS 入库批次统计结果。
 *
 * <p>作为不可变累加器在多源入库过程中合并统计值。</p>
 */
public record FeedIngestionResult(
        Long runId,
        int sourceCount,
        int fetchedCount,
        int insertedCount,
        int skippedCount,
        int failedSourceCount
) {
    public FeedIngestionResult(
            int sourceCount,
            int fetchedCount,
            int insertedCount,
            int skippedCount,
            int failedSourceCount
    ) {
        this(null, sourceCount, fetchedCount, insertedCount, skippedCount, failedSourceCount);
    }

    /**
     * 合并另一个源或批次的统计结果。
     */
    public FeedIngestionResult plus(FeedIngestionResult other) {
        Long mergedRunId = mergedRunId(other);
        return new FeedIngestionResult(
                mergedRunId,
                sourceCount + other.sourceCount,
                fetchedCount + other.fetchedCount,
                insertedCount + other.insertedCount,
                skippedCount + other.skippedCount,
                failedSourceCount + other.failedSourceCount
        );
    }

    public FeedIngestionResult withRunId(Long runId) {
        return new FeedIngestionResult(
                runId,
                sourceCount,
                fetchedCount,
                insertedCount,
                skippedCount,
                failedSourceCount
        );
    }

    private Long mergedRunId(FeedIngestionResult other) {
        if (runId == null) {
            return other.runId;
        }
        if (other.runId == null || runId.equals(other.runId)) {
            return runId;
        }
        throw new IllegalArgumentException("Cannot merge ingestion results from different runs");
    }

    /**
     * 空批次统计，用作累加起点。
     */
    public static FeedIngestionResult empty() {
        return empty(null);
    }

    public static FeedIngestionResult empty(Long runId) {
        return new FeedIngestionResult(runId, 0, 0, 0, 0, 0);
    }
}
