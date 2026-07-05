package cn.name.celestrong.signalbrief.ingestion;

/**
 * RSS 入库应用服务入口。
 */
public interface FeedIngestionOperations {

    FeedIngestionResult ingestEnabledFeeds(IngestionTriggerType triggerType);
}
