package cn.name.celestrong.signalbrief.ingestion;

/**
 * RSS 入库批次统计结果。
 *
 * <p>作为不可变累加器在多源入库过程中合并统计值。</p>
 */
public record FeedIngestionResult(
        int sourceCount,
        int fetchedCount,
        int insertedCount,
        int skippedCount,
        int failedSourceCount
) {
    /**
     * 合并另一个源或批次的统计结果。
     */
    public FeedIngestionResult plus(FeedIngestionResult other) {
        return new FeedIngestionResult(
                sourceCount + other.sourceCount,
                fetchedCount + other.fetchedCount,
                insertedCount + other.insertedCount,
                skippedCount + other.skippedCount,
                failedSourceCount + other.failedSourceCount
        );
    }

    /**
     * 空批次统计，用作累加起点。
     */
    public static FeedIngestionResult empty() {
        return new FeedIngestionResult(0, 0, 0, 0, 0);
    }
}
