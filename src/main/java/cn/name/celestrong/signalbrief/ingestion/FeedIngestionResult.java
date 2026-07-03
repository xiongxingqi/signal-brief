package cn.name.celestrong.signalbrief.ingestion;

public record FeedIngestionResult(
        int sourceCount,
        int fetchedCount,
        int insertedCount,
        int skippedCount,
        int failedSourceCount
) {
    public FeedIngestionResult plus(FeedIngestionResult other) {
        return new FeedIngestionResult(
                sourceCount + other.sourceCount,
                fetchedCount + other.fetchedCount,
                insertedCount + other.insertedCount,
                skippedCount + other.skippedCount,
                failedSourceCount + other.failedSourceCount
        );
    }

    public static FeedIngestionResult empty() {
        return new FeedIngestionResult(0, 0, 0, 0, 0);
    }
}
